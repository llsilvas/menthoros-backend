package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.TreinoRealizado;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Serviço dedicado ao cálculo de TSS (Training Stress Score).
 *
 * <p>Responsável por calcular o stress de treino usando diferentes métodos:
 * <ul>
 *   <li>Frequência Cardíaca (mais preciso)</li>
 *   <li>Pace/Velocidade (quando não há FC)</li>
 *   <li>RPE - Percepção de Esforço (fallback)</li>
 * </ul>
 *
 * <p>Também aplica fatores de impacto por tipo de treino e elevação.
 */
@Slf4j
@Component
public class TssCalculatorService {

    private static final double MIN_IF_FC_PACE = 0.30;
    private static final double MIN_IF_RPE = 0.45;
    private static final double MAX_IF = 1.50;

    private enum MetodoCalculoTss {
        FC, PACE, RPE
    }

    private record ResultadoCalculo(int tssBase, MetodoCalculoTss metodo, boolean calculadoPorEtapas) {}

    /**
     * Calcula TSS total do dia (soma de todos os treinos).
     *
     * Idempotent: YES — cálculo puro, sem estado.
     * Side Effects: NONE
     * Tenant-aware: NO
     */
    public Integer calcularTssDia(List<TreinoRealizado> treinos) {
        return treinos.stream()
                .mapToInt(this::calcularTss)
                .sum();
    }

    /**
     * TSS estimado para treinos planejados, usando duração e RPE.
     *
     * <p>Unificado com {@link #calcularTssRpe} — ambos usam o pipeline
     * RPE → IF → TSS (h × IF² × 100). Correção BUG-CONF-001: antes usava
     * min × RPE² / 90, que divergia ~245% do TSS realizado.</p>
     *
     * <p>Fórmula: TSS = round(duracaoHoras × converterRpeParaIf(rpe)² × 100).
     * RPE default 5 quando nulo. IF clampado entre {@link #MIN_IF_RPE} e {@link #MAX_IF}.</p>
     *
     * <p>Idempotent: YES — cálculo puro, sem estado.
     * Side Effects: NONE
     * Tenant-aware: NO</p>
     */
    public int calcularTssEstimado(Duration duracaoMin, Integer rpe) {
        long minutos = duracaoMin != null ? duracaoMin.toMinutes() : 0L;
        int r = rpe != null ? rpe : 5;
        return calcularTssPorRpe(minutos / 60.0, r);
    }

    /**
     * Núcleo único do pipeline RPE → IF → TSS: {@code h × IF² × 100}, com o IF clampado entre
     * {@link #MIN_IF_RPE} e {@link #MAX_IF}.
     *
     * <p>Existe para que o caminho planejado e o realizado não mantenham cópias próprias da mesma
     * conta. Foi exatamente essa duplicação que produziu o BUG-CONF-001: as duas evoluíram
     * separadamente até divergirem 2,4×–6×. Alterar a fórmula aqui muda os dois caminhos de uma vez,
     * que é a propriedade que se quer preservar.</p>
     */
    private int calcularTssPorRpe(double duracaoHoras, double rpe) {
        double intensityFactor = converterRpeParaIf(rpe);
        intensityFactor = Math.max(MIN_IF_RPE, Math.min(MAX_IF, intensityFactor));
        return (int) Math.round(duracaoHoras * intensityFactor * intensityFactor * 100.0);
    }

    /**
     * Cálculo de TSS para corrida baseado em FC, Pace ou RPE.
     * Aplica fator de impacto por tipo de treino.
     *
     * Idempotent: YES — cálculo puro, sem estado.
     * Side Effects: NONE
     * Tenant-aware: NO
     */
    public int calcularTss(TreinoRealizado treino) {
        if (treino == null) {
            return 0;
        }

        ResultadoCalculo resultado = calcularTssBase(treino);

        // Aplicar fator de impacto por tipo de treino
        return aplicarFatorImpactoTreino(resultado.tssBase(), treino, resultado.metodo(), resultado.calculadoPorEtapas());
    }

    /**
     * Aplica fator de impacto fisiológico baseado no tipo de treino
     *
     * <p>Fundamento: Treinos com mesma intensidade (TSS) têm impactos diferentes:
     * <ul>
     *   <li>Intervalados causam maior fadiga neuromuscular</li>
     *   <li>Longões causam maior depleção de glicogênio</li>
     *   <li>Tiros de subida causam maior fadiga muscular</li>
     * </ul>
     *
     * @param tssBase TSS calculado por FC/Pace/RPE
     * @param treino Treino com tipo definido
     * @return TSS ajustado pelo fator de impacto
     */
    private int aplicarFatorImpactoTreino(int tssBase, TreinoRealizado treino, MetodoCalculoTss metodo, boolean calculadoPorEtapas) {
        if (tssBase <= 0) {
            return 0;
        }

        if (treino.getTipoTreino() == null) {
            log.debug("Treino {} sem tipo definido. Usando TSS base: {}", treino.getId(), tssBase);
            return tssBase;
        }

        double fator = treino.getTipoTreino().getFatorImpacto();

        // ISSUE-04: evitar dupla contagem no cálculo por FC.
        // A FC já captura boa parte da intensidade; aplicar apenas parte do "componente extra" do fator.
        if ((metodo == MetodoCalculoTss.FC || calculadoPorEtapas) && fator > 1.0) {
            double componenteExtra = fator - 1.0;
            fator = 1.0 + (componenteExtra * 0.5);
        }
        int tssAjustado = (int) Math.round(tssBase * fator);

        log.debug("TSS ajustado para treino {}: {} (base) × {} ({}) = {}",
                treino.getId(), tssBase, fator,
                treino.getTipoTreino().getLabel(), tssAjustado);

        return tssAjustado;
    }

    /**
     * TSS baseado em Frequência Cardíaca
     * Método mais preciso quando disponível
     */
    private int calcularTssFrequenciaCardiaca(TreinoRealizado treino) {
        return calcularTssFrequenciaCardiaca(treino, treino.getFcMedia(), treino.getPaceMedia(),
                treino.getPercepcaoEsforco(), treino.getDuracaoMin(), true);
    }

    private int calcularTssFrequenciaCardiaca(TreinoRealizado treino, Integer fcMediaValor, Duration paceMedia,
                                              Integer rpe, Duration duracao, boolean permitirFallback) {
        Atleta atleta = treino.getAtleta();
        if (atleta == null) {
            log.warn("Treino {} sem atleta associado para cálculo por FC", treino.getId());
            return permitirFallback ? calcularTssSemFc(treino, duracao, paceMedia, rpe) : 0;
        }

        // Validar dados necessários
        if (atleta.getFcMaximaCalculada() == null || atleta.getFcRepouso() == null) {
            log.warn("Atleta {} sem FC máxima/repouso configurada", atleta.getId());
            return permitirFallback ? calcularTssSemFc(treino, duracao, paceMedia, rpe) : 0;
        }

        Integer fcMax = atleta.getFcMaximaCalculada();
        Integer fcRepouso = atleta.getFcRepouso();
        Integer fcLimiar = atleta.getFcLimiarCalculada();

        double fcMedia = fcMediaValor != null ? fcMediaValor : 0.0;
        double duracaoHoras = obterDuracaoHoras(duracao);

        if (duracaoHoras <= 0) {
            return 0;
        }

        if (fcMax <= fcRepouso || fcLimiar <= fcRepouso || fcMedia <= fcRepouso) {
            log.warn("Dados de FC inconsistentes para treino {}: fcMax={}, fcRepouso={}, fcLimiar={}, fcMedia={}",
                    treino.getId(), fcMax, fcRepouso, fcLimiar, fcMedia);
            return permitirFallback ? calcularTssSemFc(treino, duracao, paceMedia, rpe) : 0;
        }

        // Calcular HR Reserve %
        double hrReserve = fcMax - fcRepouso;
        double workingHR = fcMedia - fcRepouso;
        double hrReservePercent = workingHR / hrReserve;

        // Calcular Intensity Factor
        double thresholdPercent = (fcLimiar - fcRepouso) / (double) hrReserve;
        double intensityFactor = hrReservePercent / thresholdPercent;

        // Limitar IF entre 0.5 e 1.5 (valores realistas)
        if (!Double.isFinite(intensityFactor) || intensityFactor <= 0) {
            log.warn("IF inválido no cálculo por FC para treino {}: {}", treino.getId(), intensityFactor);
            return permitirFallback ? calcularTssSemFc(treino, duracao, paceMedia, rpe) : 0;
        }

        intensityFactor = Math.clamp(intensityFactor, MIN_IF_FC_PACE, MAX_IF);

        // TSS = duração_horas × IF × 100 × IF
        double tss = duracaoHoras * intensityFactor * 100 * intensityFactor;

        return (int) Math.round(tss);
    }

    /**
     * TSS baseado em Pace (velocidade)
     * Útil quando não há dados de FC
     */
    private int calcularTssPace(TreinoRealizado treino) {
        return calcularTssPace(treino, treino.getPaceMedia(), treino.getDuracaoMin(), true);
    }

    private int calcularTssPace(TreinoRealizado treino, Duration paceMediaDuracao, Duration duracao, boolean permitirFallback) {
        Atleta atleta = treino.getAtleta();
        if (atleta == null) {
            log.warn("Treino {} sem atleta associado para cálculo por pace", treino.getId());
            return permitirFallback ? calcularTssRpe(treino, duracao, treino.getPercepcaoEsforco()) : 0;
        }

        if (atleta.getPaceLimiar() == null) {
            log.warn("Atleta {} sem pace limiar configurado", atleta.getId());
            return permitirFallback ? calcularTssRpe(treino, duracao, treino.getPercepcaoEsforco()) : 0;
        }

        // Converter Duration (pace) para minutos decimais (5:30 → 5.5)
        double paceMedia = paceMediaDuracao != null
            ? paceMediaDuracao.toMillis() / 60000.0 // millis → minutos
            : 0.0;

        if (paceMedia == 0) {
            log.warn("Treino {} sem pace válido", treino.getId());
            return permitirFallback ? calcularTssRpe(treino, duracao, treino.getPercepcaoEsforco()) : 0;
        }

        double paceLimiar = atleta.getPaceLimiar().doubleValue(); // min/km
        double duracaoHoras = obterDuracaoHoras(duracao);
        if (duracaoHoras <= 0) {
            return 0;
        }

        // IF = pace_limiar / pace_media (quanto menor o pace, maior o IF)
        double intensityFactor = paceLimiar / paceMedia;

        // Limitar IF entre 0.5 e 1.5
        intensityFactor = Math.clamp(intensityFactor, MIN_IF_FC_PACE, MAX_IF);

        // Aplicar fator de elevação (terreno)
        double fatorElevacao = calcularFatorElevacao(treino);

        // TSS = duração_horas × IF² × 100 × fator_elevação
        double tss = duracaoHoras * intensityFactor * 100 * intensityFactor * fatorElevacao;

        if (fatorElevacao > 1.0) {
            log.debug("TSS ajustado por elevação: {} (fator {})", tss, fatorElevacao);
        }

        return (int) Math.round(tss);
    }

    /**
     * Calcula fator de correção baseado em elevação acumulada
     *
     * <p><b>Fundamento Fisiológico:</b>
     * <ul>
     *   <li>Cada 100m de elevação ≈ 1km extra de corrida plana (custo energético)</li>
     *   <li>Gradiente médio determina o aumento de TSS</li>
     *   <li>Baseado em estudos de Minetti et al. (2002) - Journal of Applied Physiology</li>
     * </ul>
     *
     * <p><b>Fórmula aplicada:</b>
     * <pre>
     * Gradiente (m/km) | Aumento TSS
     * -----------------|--------------
     * 0-20 m/km        | +0.5% por m/km (subidas suaves)
     * 20-50 m/km       | +1.0% por m/km (subidas moderadas)
     * &gt;50 m/km         | +1.5% por m/km (subidas íngremes)
     * </pre>
     *
     * @param treino Treino com dados de elevação e distância
     * @return Fator multiplicador (1.0 = plano, 1.5 = montanha pesada)
     */
    private double calcularFatorElevacao(TreinoRealizado treino) {
        Integer elevacaoMetros = treino.getElevacaoGanhoMetros();
        BigDecimal distanciaKm = treino.getDistanciaKm();

        // Se não tem dados de elevação ou distância, assume plano
        if (elevacaoMetros == null || elevacaoMetros <= 0 ||
                distanciaKm == null || distanciaKm.doubleValue() <= 0) {
            return 1.0;
        }

        double distancia = distanciaKm.doubleValue();
        double gradienteMedio = elevacaoMetros / distancia; // m/km

        // Fórmula baseada em custo energético de subidas
        // Fonte: Minetti, A. E., Moia, C., Roi, G. S., Susta, D., & Ferretti, G. (2002)
        // "Energy cost of walking and running at extreme gradients on a treadmill"
        double fator;

        if (gradienteMedio < 20) {
            // Subidas suaves: +0.5% por m/km
            fator = 1.0 + (gradienteMedio * 0.005);
        } else if (gradienteMedio < 50) {
            // Subidas moderadas: progressão para +1.0% por m/km adicional
            fator = 1.0 + (20 * 0.005) + ((gradienteMedio - 20) * 0.01);
        } else {
            // Subidas íngremes: +1.5% por m/km adicional
            fator = 1.0 + (20 * 0.005) + (30 * 0.01) + ((gradienteMedio - 50) * 0.015);
        }

        // Limitar fator entre 1.0 e 2.0 (evitar valores absurdos)
        fator = Math.min(fator, 2.0);

        log.debug("Fator elevação: {} ({}m D+ em {}km = {} m/km)",
                String.format("%.2f", fator), elevacaoMetros, distancia,
                String.format("%.1f", gradienteMedio));

        return fator;
    }

    /**
     * TSS baseado em RPE (Rating of Perceived Exertion)
     * Método menos preciso, usado como fallback
     */
    private int calcularTssRpe(TreinoRealizado treino) {
        return calcularTssRpe(treino, treino.getDuracaoMin(), treino.getPercepcaoEsforco());
    }

    private int calcularTssRpe(TreinoRealizado treino, Duration duracao, Integer rpeValor) {
        double duracaoHoras = obterDuracaoHoras(duracao);
        if (duracaoHoras <= 0) {
            return 0;
        }

        if (rpeValor == null) {
            return calcularTssEstimado(treino, duracaoHoras);
        }

        // Mapeamento fisiológico RPE → IF (ISSUE-02), referências aproximadas:
        // - RPE 3-4: zona aeróbica fácil (IF ~0.55-0.65)
        // - RPE 5-6: zona aeróbica moderada (IF ~0.70-0.80)
        // - RPE 7: tempo/sublimiar (IF ~0.88-0.93)
        // - RPE 8: limiar anaeróbico (IF ~1.00 por definição)
        // - RPE 9: VO2max (IF ~1.10-1.15)
        // - RPE 10: máximo/sprint (IF ~1.20-1.30)
        return calcularTssPorRpe(duracaoHoras, rpeValor); // rpeValor na escala 1-10
    }

    private int calcularTssRpeOuTipoEtapa(TreinoRealizado treino, EtapaRealizada etapa) {
        double duracaoHoras = obterDuracaoHoras(etapa.getDuracao());
        if (duracaoHoras <= 0) {
            return 0;
        }

        if (etapa.getPercepcaoEsforco() != null) {
            return calcularTssRpe(treino, etapa.getDuracao(), etapa.getPercepcaoEsforco());
        }

        Double intensityFactorEtapa = obterIfPorTipoEtapa(treino, etapa);
        if (intensityFactorEtapa != null) {
            double tss = duracaoHoras * intensityFactorEtapa * 100 * intensityFactorEtapa;
            int tssEstimado = (int) Math.round(tss);

            log.debug("TSS estimado por tipo de etapa para treino {} etapa {} ({}): {}",
                    treino.getId(), etapa.getOrdem(), etapa.getTipoEtapa(), tssEstimado);

            return tssEstimado;
        }

        return calcularTssEstimado(treino, duracaoHoras);
    }

    private double converterRpeParaIf(double rpe) {
        if (rpe <= 1) return 0.45;
        if (rpe <= 3) return 0.45 + (rpe - 1) * 0.075; // 1→0.45, 3→0.60
        if (rpe <= 6) return 0.60 + (rpe - 3) * 0.067; // 3→0.60, 6→0.80
        if (rpe <= 8) return 0.80 + (rpe - 6) * 0.10;  // 6→0.80, 8→1.00
        return 1.00 + (rpe - 8) * 0.125;               // 8→1.00, 10→1.25
    }

    private int calcularTssSemFc(TreinoRealizado treino) {
        return calcularTssSemFc(treino, treino.getDuracaoMin(), treino.getPaceMedia(), treino.getPercepcaoEsforco());
    }

    private int calcularTssSemFc(TreinoRealizado treino, Duration duracao, Duration paceMedia, Integer rpe) {
        if (paceMedia != null) {
            return calcularTssPace(treino, paceMedia, duracao, false);
        }
        return calcularTssRpe(treino, duracao, rpe);
    }

    private double obterDuracaoHoras(TreinoRealizado treino) {
        return treino.getDuracaoMin() != null ? treino.getDuracaoMin().toMinutes() / 60.0 : 0.0;
    }

    private double obterDuracaoHoras(Duration duracao) {
        return duracao != null ? duracao.toMinutes() / 60.0 : 0.0;
    }

    private int calcularTssEstimado(TreinoRealizado treino, double duracaoHoras) {
        if (treino.getTipoTreino() == null) {
            log.warn("Treino {} sem FC, pace, RPE e tipo para estimar TSS", treino.getId());
            return 0;
        }

        double intensityFactor = switch (treino.getTipoTreino()) {
            case DESCANSO -> 0.0;
            case REGENERATIVO -> 0.50;
            case FACIL -> 0.65;
            case CONTINUO, LONGO -> 0.75;
            case FARTLEK -> 0.85;
            case TEMPO_RUN -> 0.95;
            case INTERVALADO, SUBIDA -> 1.00;
            case TIRO, PROVA -> 1.05;
        };

        double tss = duracaoHoras * intensityFactor * 100 * intensityFactor;
        int tssEstimado = (int) Math.round(tss);

        log.warn("Treino {} sem FC, pace ou RPE. Estimando TSS por tipo {}: {}",
                treino.getId(), treino.getTipoTreino(), tssEstimado);

        return tssEstimado;
    }

    private Double obterIfPorTipoEtapa(TreinoRealizado treino, EtapaRealizada etapa) {
        if (etapa == null || etapa.getTipoEtapa() == null || etapa.getTipoEtapa().isBlank()) {
            return null;
        }

        String tipoEtapa = etapa.getTipoEtapa().trim().toUpperCase();

        return switch (tipoEtapa) {
            case "AQUECIMENTO" -> 0.55;
            case "RECUPERACAO" -> 0.45;
            case "DESAQUECIMENTO" -> 0.50;
            case "PRINCIPAL" -> 0.80;
            case "INTERVALADO" -> obterIfIntervaladoPorTipoTreino(treino);
            default -> null;
        };
    }

    private double obterIfIntervaladoPorTipoTreino(TreinoRealizado treino) {
        if (treino == null || treino.getTipoTreino() == null) {
            return 0.95;
        }

        return switch (treino.getTipoTreino()) {
            case TIRO -> 1.10;
            case INTERVALADO, SUBIDA -> 1.00;
            case TEMPO_RUN -> 0.95;
            case FARTLEK -> 0.90;
            default -> 0.95;
        };
    }

    private ResultadoCalculo calcularTssBase(TreinoRealizado treino) {
        ResultadoCalculo porEtapas = calcularTssPorEtapas(treino);
        if (porEtapas != null) {
            return porEtapas;
        }

        if (treino.getFcMedia() != null && treino.getFcMedia() > 0) {
            return new ResultadoCalculo(calcularTssFrequenciaCardiaca(treino), MetodoCalculoTss.FC, false);
        }
        if (treino.getPaceMedia() != null) {
            return new ResultadoCalculo(calcularTssPace(treino), MetodoCalculoTss.PACE, false);
        }
        return new ResultadoCalculo(calcularTssRpe(treino), MetodoCalculoTss.RPE, false);
    }

    private ResultadoCalculo calcularTssPorEtapas(TreinoRealizado treino) {
        if (treino.getEtapasRealizadas() == null || treino.getEtapasRealizadas().isEmpty()) {
            return null;
        }

        int tssTotal = 0;
        int etapasValidas = 0;
        int etapasComFc = 0;
        int etapasComPace = 0;

        for (EtapaRealizada etapa : treino.getEtapasRealizadas()) {
            if (etapa == null || etapa.getDuracao() == null || etapa.getDuracao().isZero() || etapa.getDuracao().isNegative()) {
                continue;
            }

            int tssEtapa;
            if (etapa.getFcMedia() != null && etapa.getFcMedia() > 0) {
                tssEtapa = calcularTssFrequenciaCardiaca(treino, etapa.getFcMedia(), etapa.getPaceMedia(),
                        etapa.getPercepcaoEsforco(), etapa.getDuracao(), true);
                if (tssEtapa > 0) {
                    etapasComFc++;
                }
            } else if (etapa.getPaceMedia() != null) {
                tssEtapa = calcularTssPace(treino, etapa.getPaceMedia(), etapa.getDuracao(), false);
                if (tssEtapa > 0) {
                    etapasComPace++;
                }
            } else {
                tssEtapa = calcularTssRpeOuTipoEtapa(treino, etapa);
            }

            if (tssEtapa <= 0) {
                continue;
            }

            etapasValidas++;
            tssTotal += tssEtapa;
        }

        if (etapasValidas == 0) {
            return null;
        }

        MetodoCalculoTss metodoPredominante;
        if (etapasComFc > 0) {
            metodoPredominante = MetodoCalculoTss.FC;
        } else if (etapasComPace > 0) {
            metodoPredominante = MetodoCalculoTss.PACE;
        } else {
            metodoPredominante = MetodoCalculoTss.RPE;
        }

        log.debug("TSS calculado por etapas para treino {}: {} pontos em {} etapas válidas",
                treino.getId(), tssTotal, etapasValidas);

        return new ResultadoCalculo(tssTotal, metodoPredominante, true);
    }
}
