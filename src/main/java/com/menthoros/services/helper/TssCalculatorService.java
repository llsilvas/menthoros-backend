package com.menthoros.services.helper;

import com.menthoros.entity.Atleta;
import com.menthoros.entity.TreinoRealizado;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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

    private enum MetodoCalculoTss {
        FC, PACE, RPE
    }

    /**
     * Calcula TSS total do dia (soma de todos os treinos)
     */
    public Integer calcularTssDia(List<TreinoRealizado> treinos) {
        return treinos.stream()
                .mapToInt(this::calcularTss)
                .sum();
    }

    /**
     * Cálculo de TSS para corrida baseado em FC, Pace ou RPE.
     * Aplica fator de impacto por tipo de treino.
     */
    public int calcularTss(TreinoRealizado treino) {
        int tssBase;
        MetodoCalculoTss metodo;

        // Se tem dados de FC, usar método de FC
        if (treino.getFcMedia() != null && treino.getFcMedia() > 0) {
            tssBase = calcularTssFrequenciaCardiaca(treino);
            metodo = MetodoCalculoTss.FC;
        }
        // Se tem dados de pace, usar método de pace
        else if (treino.getPaceMedia() != null) {
            tssBase = calcularTssPace(treino);
            metodo = MetodoCalculoTss.PACE;
        }
        // Fallback: usar RPE (menos preciso mas melhor que nada)
        else {
            tssBase = calcularTssRpe(treino);
            metodo = MetodoCalculoTss.RPE;
        }

        // Aplicar fator de impacto por tipo de treino
        return aplicarFatorImpactoTreino(tssBase, treino, metodo);
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
    private int aplicarFatorImpactoTreino(int tssBase, TreinoRealizado treino, MetodoCalculoTss metodo) {
        if (treino.getTipoTreino() == null) {
            log.debug("Treino {} sem tipo definido. Usando TSS base: {}", treino.getId(), tssBase);
            return tssBase;
        }

        double fator = treino.getTipoTreino().getFatorImpacto();

        // ISSUE-04: evitar dupla contagem no cálculo por FC.
        // A FC já captura boa parte da intensidade; aplicar apenas parte do "componente extra" do fator.
        if (metodo == MetodoCalculoTss.FC && fator > 1.0) {
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
        Atleta atleta = treino.getAtleta();

        // Validar dados necessários
        if (atleta.getFcMaxima() == null || atleta.getFcRepouso() == null) {
            log.warn("Atleta {} sem FC máxima/repouso configurada", atleta.getId());
            return calcularTssRpe(treino);
        }

        Integer fcMax = atleta.getFcMaxima();
        Integer fcRepouso = atleta.getFcRepouso();
        Integer fcLimiar = atleta.getFcLimiar() != null
                ? atleta.getFcLimiar()
                : (int) (fcRepouso + (fcMax - fcRepouso) * 0.85); // Estimativa 85%

        double fcMedia = treino.getFcMedia();
        double duracaoHoras = treino.getDuracaoMin() != null
            ? treino.getDuracaoMin().toMinutes() / 60.0
            : 0.0;

        // Calcular HR Reserve %
        double hrReserve = fcMax - fcRepouso;
        double workingHR = fcMedia - fcRepouso;
        double hrReservePercent = workingHR / hrReserve;

        // Calcular Intensity Factor
        double thresholdPercent = (fcLimiar - fcRepouso) / (double) hrReserve;
        double intensityFactor = hrReservePercent / thresholdPercent;

        // Limitar IF entre 0.5 e 1.5 (valores realistas)
        intensityFactor = Math.max(0.5, Math.min(1.5, intensityFactor));

        // TSS = duração_horas × IF × 100 × IF
        double tss = duracaoHoras * intensityFactor * 100 * intensityFactor;

        return (int) Math.round(tss);
    }

    /**
     * TSS baseado em Pace (velocidade)
     * Útil quando não há dados de FC
     */
    private int calcularTssPace(TreinoRealizado treino) {
        Atleta atleta = treino.getAtleta();

        if (atleta.getPaceLimiar() == null) {
            log.warn("Atleta {} sem pace limiar configurado", atleta.getId());
            return calcularTssRpe(treino);
        }

        // Converter Duration (pace) para minutos decimais (5:30 → 5.5)
        double paceMedia = treino.getPaceMedia() != null
            ? treino.getPaceMedia().toMillis() / 60000.0 // millis → minutos
            : 0.0;

        if (paceMedia == 0) {
            log.warn("Treino {} sem pace válido", treino.getId());
            return calcularTssRpe(treino);
        }

        double paceLimiar = atleta.getPaceLimiar().doubleValue(); // min/km
        double duracaoHoras = treino.getDuracaoMin() != null
            ? treino.getDuracaoMin().toMinutes() / 60.0
            : 0.0;

        // IF = pace_limiar / pace_media (quanto menor o pace, maior o IF)
        double intensityFactor = paceLimiar / paceMedia;

        // Limitar IF entre 0.5 e 1.5
        intensityFactor = Math.max(0.5, Math.min(1.5, intensityFactor));

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
        if (treino.getPercepcaoEsforco() == null) {
            log.warn("Treino {} sem dados para calcular TSS", treino.getId());
            return 0;
        }

        double duracaoHoras = treino.getDuracaoMin() != null
            ? treino.getDuracaoMin().toMinutes() / 60.0
            : 0.0;
        double rpe = treino.getPercepcaoEsforco(); // Escala 1-10

        // Converter RPE para IF com mapeamento fisiológico (ISSUE-02)
        // Referências aproximadas:
        // - RPE 3-4: zona aeróbica fácil (IF ~0.55-0.65)
        // - RPE 5-6: zona aeróbica moderada (IF ~0.70-0.80)
        // - RPE 7: tempo/sublimiar (IF ~0.88-0.93)
        // - RPE 8: limiar anaeróbico (IF ~1.00 por definição)
        // - RPE 9: VO2max (IF ~1.10-1.15)
        // - RPE 10: máximo/sprint (IF ~1.20-1.30)
        double intensityFactor = converterRpeParaIf(rpe);

        // Limitar IF entre 0.5 e 1.5 (consistente com outros métodos)
        intensityFactor = Math.max(0.5, Math.min(1.5, intensityFactor));

        double tss = duracaoHoras * intensityFactor * 100 * intensityFactor;

        return (int) Math.round(tss);
    }

    private double converterRpeParaIf(double rpe) {
        if (rpe <= 1) return 0.45;
        if (rpe <= 3) return 0.45 + (rpe - 1) * 0.075; // 1→0.45, 3→0.60
        if (rpe <= 6) return 0.60 + (rpe - 3) * 0.067; // 3→0.60, 6→0.80
        if (rpe <= 8) return 0.80 + (rpe - 6) * 0.10;  // 6→0.80, 8→1.00
        return 1.00 + (rpe - 8) * 0.125;               // 8→1.00, 10→1.25
    }
}
