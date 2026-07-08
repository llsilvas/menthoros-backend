package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.MetricasThresholds;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Formatador de periodização e provas para prompts de IA.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Formatação de prova alvo e provas preparatórias</li>
 *   <li>Determinação de fase de preparação (BASE/BUILD/ESPECÍFICO/TAPER)</li>
 *   <li>Foco por fase e semana</li>
 *   <li>Cálculo de TSS alvo semanal (base e ajustado)</li>
 *   <li>Determinação do tipo de semana (regenerativa, desenvolvimento, etc.)</li>
 *   <li>Periodização estruturada para o prompt</li>
 * </ul>
 */
@Component
public class PeriodizacaoPromptFormatter {

    private static final DateTimeFormatter DATA_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Formata prova alvo com fase atual, foco e provas preparatórias.
     * Recebe provas preparatórias pré-carregadas (sem acesso a repository).
     */
    public String formatarProvas(Prova provaAlvo, List<Prova> provasPreparatorias) {
        if (provaAlvo == null) {
            return "Nenhuma prova alvo definida. Treinos focados em desenvolvimento geral.";
        }

        int diasFaltando = provaAlvo.diasFaltando();
        int semanasFaltando = diasFaltando / 7;
        String fase = determinarFasePreparacao(diasFaltando);

        BigDecimal distanciaKm = resolverDistanciaKm(provaAlvo);
        String dataProva = provaAlvo.getDataProva() != null ? provaAlvo.getDataProva().format(DATA_FMT) : "N/A";
        String tempoObjetivo = provaAlvo.getTempoObjetivo() != null ? provaAlvo.getTempoObjetivo().toString() : "N/A";
        String paceObjetivo = provaAlvo.getPaceObjetivo() != null
                ? String.format("%.2f min/km", provaAlvo.getPaceObjetivo())
                : "N/A";
        String tsbIdeal = provaAlvo.getTsbIdealProva() != null
                ? String.format("%.1f", provaAlvo.getTsbIdealProva())
                : "N/A";

        return String.format("""
                        **PROVA ALVO:** %s
                        - Data: %s (%d dias / %d semanas)
                        - Distância: %s (%.1f km)
                        - Objetivo: %s
                        - Pace alvo: %s
                        - TSB ideal na prova: %s

                        **FASE ATUAL:** %s
                        **FOCO DESTA SEMANA:** %s

                        **Provas preparatórias:**
                        %s
                        """,
                provaAlvo.getNomeProva(),
                dataProva,
                diasFaltando,
                semanasFaltando,
                provaAlvo.getDistancia(),
                distanciaKm != null ? distanciaKm : BigDecimal.ZERO,
                tempoObjetivo,
                paceObjetivo,
                tsbIdeal,
                fase,
                getFocoPorFase(fase, semanasFaltando),
                formatarProvasPreparatorias(provasPreparatorias)
        );
    }

    /**
     * Formatação simples de periodização para o prompt otimizado.
     */
    public String formatarPeriodizacaoProva(Prova provaAlvo) {
        if (provaAlvo == null) return "N/A - sem prova alvo";

        int semanas = provaAlvo.diasFaltando() / 7;

        if (semanas > 12) {
            return "Fase BASE: Construir volume aeróbico. 80% treinos fáceis, 20% moderados.";
        } else if (semanas > 8) {
            return "Fase BUILD: Adicionar qualidade. 70% fáceis, 20% específicos, 10% intensos.";
        } else if (semanas > 3) {
            return "Fase ESPECÍFICO: Treinos no pace de prova. 60% fáceis, 30% específicos, 10% regenerativos.";
        } else if (semanas > 1) {
            return "Fase TAPER: Reduzir volume 40-60%, manter intensidade. Foco em recuperação.";
        } else {
            return "SEMANA DA PROVA: Apenas treinos leves curtíssimos. TSB deve estar entre +5 e +10.";
        }
    }

    /**
     * Gera uma instrucao mandatória quando existe evento competitivo dentro da semana planejada.
     * A avaliacao considera a semana do plano (inicioSemana..inicioSemana+6), nao apenas dias faltando.
     */
    public String formatarEventoCompetitivoSemana(Prova provaAlvo,
                                                  List<Prova> provasPreparatorias,
                                                  LocalDate inicioSemana) {
        LocalDate inicio = inicioSemana != null ? inicioSemana : LocalDate.now();
        LocalDate fim = inicio.plusDays(6);

        List<Prova> eventosSemana = new ArrayList<>();
        if (provaAlvo != null && estaNaSemana(provaAlvo, inicio, fim)) {
            eventosSemana.add(provaAlvo);
        }

        if (provasPreparatorias != null) {
            provasPreparatorias.stream()
                    .filter(prova -> estaNaSemana(prova, inicio, fim))
                    .forEach(eventosSemana::add);
        }

        if (eventosSemana.isEmpty()) {
            return String.format("""
                    ## EVENTO COMPETITIVO NA SEMANA - INSTRUCAO OBRIGATORIA

                    [NAO]
                    - Semana planejada: %s a %s.
                    - Nenhuma prova cadastrada dentro desta semana.
                    - Aplicar periodizacao normal da fase atual.

                    """,
                    inicio.format(DATA_FMT),
                    fim.format(DATA_FMT));
        }

        Prova eventoPrincipal = eventosSemana.stream()
                .filter(Prova::isProvaAlvo)
                .findFirst()
                .orElse(eventosSemana.getFirst());

        boolean provaAlvoNaSemana = eventoPrincipal.isProvaAlvo();
        long diasAteEvento = Math.max(0, ChronoUnit.DAYS.between(inicio, eventoPrincipal.getDataProva()));
        String papel = provaAlvoNaSemana
                ? "competicao principal da semana"
                : "prova preparatoria / tune-up controlado";
        String reducaoVolume = provaAlvoNaSemana ? "30-50%" : "15-30%";
        String ajusteCarga = provaAlvoNaSemana
                ? "Priorize frescor, estrategia e confianca para competir bem."
                : "Use a prova como estimulo especifico, sem acumular fadiga residual desnecessaria.";

        StringBuilder sb = new StringBuilder();
        sb.append("## EVENTO COMPETITIVO NA SEMANA - INSTRUCAO OBRIGATORIA\n\n");
        sb.append("[SIM]\n");
        sb.append(String.format("- Semana planejada: %s a %s.\n", inicio.format(DATA_FMT), fim.format(DATA_FMT)));
        sb.append(String.format("- Evento principal da semana: %s.\n", eventoPrincipal.getNomeProva()));
        sb.append(String.format("- Tipo: %s.\n", provaAlvoNaSemana ? "PROVA_ALVO" : "PROVA_PREPARATORIA"));
        sb.append(String.format("- Data: %s (%s).\n",
                eventoPrincipal.getDataProva().format(DATA_FMT),
                formatarDiaSemana(eventoPrincipal.getDataProva())));
        sb.append(String.format("- Distancia: %s (%.1f km).\n",
                eventoPrincipal.getDistancia(),
                resolverDistanciaKm(eventoPrincipal) != null ? resolverDistanciaKm(eventoPrincipal).doubleValue() : 0.0));
        sb.append(String.format("- Papel na periodizacao: %s.\n", papel));
        sb.append(String.format("- Dias entre o inicio da semana e a prova: %d.\n", diasAteEvento));
        sb.append("- Esta prova SUBSTITUI o treino-chave e o longo tradicional da semana, se houver conflito.\n");
        sb.append(String.format("- Reduza o volume semanal total em %s em relacao a uma semana normal desta fase.\n", reducaoVolume));
        sb.append("- Nao prescreva intervalado pesado, tiro longo ou longo estressor nas 48-72h anteriores a prova.\n");
        sb.append("- Permita no maximo 1 treino de ativacao leve e curta 24-48h antes da prova.\n");
        sb.append("- O dia seguinte a prova deve ser descanso completo ou regenerativo muito leve.\n");
        sb.append("- Se houver conflito entre TSS alvo e frescor para competir, priorize frescor para a prova.\n");
        sb.append(String.format("- Diretriz do treinador: %s\n", ajusteCarga));

        if (eventosSemana.size() > 1) {
            sb.append("- Outros eventos competitivos nesta semana:\n");
            eventosSemana.stream()
                    .filter(prova -> !prova.equals(eventoPrincipal))
                    .forEach(prova -> sb.append(String.format("  - %s em %s (%s)\n",
                            prova.getNomeProva(),
                            prova.getDataProva().format(DATA_FMT),
                            prova.getDistancia())));
        }

        sb.append("\n");
        return sb.toString();
    }

    public String determinarFasePreparacao(int diasFaltando) {
        int semanas = diasFaltando / 7;

        if (semanas > 12) {
            return "BASE";
        } else if (semanas > 8) {
            return "BUILD";
        } else if (semanas > 3) {
            return "ESPECÍFICO";
        } else if (semanas > 1) {
            return "TAPER";
        } else if (semanas == 1) {
            return "SEMANA DA PROVA";
        } else {
            return "PÓS-PROVA";
        }
    }

    public String getFocoPorFase(String fase, int semanasFaltando) {
        return switch (fase) {
            case "BASE" -> "Construir base aeróbica com volume progressivo. Foco em Z2.";
            case "BUILD" -> semanasFaltando > 10
                    ? "Introduzir treinos de qualidade (tempo run, intervalados Z3-Z4)."
                    : "Aumentar volume de treinos específicos. Manter base aeróbica.";
            case "ESPECÍFICO" -> semanasFaltando > 5
                    ? "Treinos no pace de prova. Simulações parciais da distância."
                    : "Refinar pace de prova. Últimos ajustes técnicos e táticos.";
            case "TAPER" -> {
                if (semanasFaltando == 3) yield "Reduzir volume 20-30%. Manter intensidade em treinos curtos.";
                else if (semanasFaltando == 2) yield "Reduzir volume 40-50%. Treinos de manutenção apenas.";
                else yield "Reduzir volume 60-70%. Foco total em recuperação.";
            }
            case "SEMANA DA PROVA" -> "Apenas treinos leves curtíssimos. TSB deve estar +5 a +10.";
            case "PÓS-PROVA" -> "Recuperação ativa. Treinos regenerativos por 1-2 semanas.";
            default -> "Desenvolvimento geral. Manter consistência.";
        };
    }

    /**
     * Calcula TSS alvo semanal baseado em CTL, Ramp Rate e TSB.
     */
    public int calcularTssAlvo(PlanoMetaDados metaDados) {
        if (metaDados == null) {
            return 150;
        }

        Double ctlAtual = metaDados.getCtlAtual();
        Double rampRate = metaDados.getRampRateAtual();
        Double tsbAtual = metaDados.getTsbAtual();

        if (ctlAtual == null || ctlAtual == 0.0) {
            return 150;
        }

        rampRate = rampRate != null ? rampRate : 0.0;
        tsbAtual = tsbAtual != null ? tsbAtual : 0.0;

        double progressaoSegura = calcularProgressaoSegura(rampRate, tsbAtual);
        double ctlAlvo = ctlAtual + progressaoSegura;

        int tssAlvo = (int) (ctlAlvo * 7);

        if (tsbAtual < MetricasThresholds.TSB_SOBRECARGA) {
            tssAlvo = (int) (tssAlvo * 0.7);
        } else if (tsbAtual < MetricasThresholds.TSB_FADIGA_MODERADA) {
            tssAlvo = (int) (tssAlvo * 0.85);
        } else if (tsbAtual > MetricasThresholds.TSB_FORMA_IDEAL_MAX) {
            tssAlvo = (int) (tssAlvo * 1.1);
        }

        return Math.max(tssAlvo, 100);
    }

    /**
     * TSS alvo ajustado por semana regenerativa e fadiga acumulada.
     */
    public int calcularTssAlvoAjustado(PlanoMetaDados metaDados, Atleta atleta) {
        int tssAlvoBase = calcularTssAlvo(metaDados);

        Integer semanasProgressao = metaDados != null ? metaDados.getSemanasProgressaoContinua() : null;
        Double tsb = metaDados != null ? metaDados.getTsbAtual() : null;

        if ((semanasProgressao != null && semanasProgressao >= MetricasThresholds.SEMANAS_PROGRESSAO_ALERTA)
                || (tsb != null && tsb < MetricasThresholds.TSB_SOBRECARGA)) {
            return (int) Math.round(tssAlvoBase * 0.55);
        } else if (tsb != null && tsb < MetricasThresholds.TSB_FADIGA_MODERADA) {
            return (int) Math.round(tssAlvoBase * 0.75);
        }

        return tssAlvoBase;
    }

    /**
     * Determina o tipo de semana baseado nas métricas e recomendações.
     */
    public String determinarTipoSemana(PlanoMetaDados metaDados, Atleta atleta, int tssAlvo) {
        if (metaDados == null) {
            return "DESENVOLVIMENTO";
        }

        Double tsb = metaDados.getTsbAtual();
        Integer semanasProgressao = metaDados.getSemanasProgressaoContinua();

        if ((semanasProgressao != null && semanasProgressao >= MetricasThresholds.SEMANAS_PROGRESSAO_ALERTA) ||
                (tsb != null && tsb < MetricasThresholds.TSB_SOBRECARGA)) {
            return "REGENERATIVA (redução de carga)";
        }

        if (tsb != null && tsb < MetricasThresholds.TSB_FADIGA_MODERADA) {
            return "RECUPERAÇÃO (foco em treinos leves)";
        }

        if (tsb != null && tsb >= MetricasThresholds.TSB_ACUMULANDO_FADIGA && tsb <= MetricasThresholds.TSB_FORMA_IDEAL_MAX) {
            return "DESENVOLVIMENTO (progressão normal)";
        }

        if (tsb != null && tsb > MetricasThresholds.TSB_FORMA_IDEAL_MAX) {
            return "PICO (ótimo para treinos intensos)";
        }

        return "DESENVOLVIMENTO";
    }

    /**
     * Formata o bloco de decisão de progressão para o prompt de IA.
     * Retorna string vazia quando decisao é null (fallback gracioso — D7).
     */
    public String formatarDecisaoProgressao(@Nullable DecisaoProgressao decisao) {
        if (decisao == null) return "";

        String ajusteVolume = decisao.ajusteVolumePercentual() >= 0
                ? String.format("+%.0f%%", decisao.ajusteVolumePercentual() * 100)
                : String.format("%.0f%%", decisao.ajusteVolumePercentual() * 100);

        String ajusteLongo = decisao.ajusteLongoMinutos() >= 0
                ? String.format("+%d min", decisao.ajusteLongoMinutos())
                : String.format("%d min", decisao.ajusteLongoMinutos());

        String intensidade = decisao.permitirProgressaoIntensidade()
                ? "Permitir progressão de intensidade nesta semana."
                : "Não progredir intensidade nesta semana.";

        return String.format("""
                ## 📈 DECISÃO DE PROGRESSÃO (últimos 21 dias)

                - **Estado:** %s
                - **Ajuste de volume:** %s em relação à semana base
                - **Longão:** %s no treino longo desta semana
                - **Intensidade:** %s
                - **Motivo:** %s

                (O teto de TSS acima tem precedência sobre o ajuste de volume. Esta diretriz é calculada automaticamente a partir do histórico real do atleta.)

                """,
                decisao.estado().name(),
                ajusteVolume,
                ajusteLongo,
                intensidade,
                decisao.motivo()
        );
    }

    private double calcularProgressaoSegura(Double rampRate, Double tsb) {
        if (rampRate == null || tsb == null) {
            return 0.0;
        }

        double progressaoBase;

        if (tsb < -30) {
            return -2.0;
        } else if (tsb < -20) {
            return -1.0;
        } else if (tsb < -10) {
            progressaoBase = 0.5;
        } else if (tsb < 0) {
            progressaoBase = 1.0;
        } else if (tsb < 10) {
            progressaoBase = 1.5;
        } else {
            progressaoBase = 2.0;
        }

        if (rampRate > 10) {
            return -1.5;
        } else if (rampRate > 8) {
            return Math.min(progressaoBase, 0.5);
        } else if (rampRate > 6) {
            return Math.min(progressaoBase, 1.0);
        } else if (rampRate < 0) {
            // Ramp negativo sugere destreino/queda de carga; só permitir acelerar se TSB estiver positivo.
            if (tsb >= 0) {
                return Math.min(progressaoBase + 0.5, 2.0);
            }
            return progressaoBase;
        }

        return progressaoBase;
    }

    private String formatarProvasPreparatorias(List<Prova> provasPreparatorias) {
        if (provasPreparatorias == null || provasPreparatorias.isEmpty()) {
            return "Nenhuma prova preparatória cadastrada.";
        }

        StringBuilder sb = new StringBuilder();
        provasPreparatorias.forEach(prova -> {
            int diasFaltando = prova.diasFaltando();
            sb.append(String.format("- %s: %s (%d dias) - %s\n",
                    prova.getDataProva() != null ? prova.getDataProva().format(DATA_FMT) : "N/A",
                    prova.getNomeProva(),
                    diasFaltando,
                    prova.getDistancia()
            ));
        });

        return sb.toString();
    }

    private boolean estaNaSemana(Prova prova, LocalDate inicioSemana, LocalDate fimSemana) {
        return prova != null
                && prova.getDataProva() != null
                && !prova.getDataProva().isBefore(inicioSemana)
                && !prova.getDataProva().isAfter(fimSemana);
    }

    private String formatarDiaSemana(LocalDate data) {
        return switch (data.getDayOfWeek()) {
            case MONDAY -> "SEGUNDA-FEIRA";
            case TUESDAY -> "TERCA-FEIRA";
            case WEDNESDAY -> "QUARTA-FEIRA";
            case THURSDAY -> "QUINTA-FEIRA";
            case FRIDAY -> "SEXTA-FEIRA";
            case SATURDAY -> "SABADO";
            case SUNDAY -> "DOMINGO";
        };
    }

    private BigDecimal resolverDistanciaKm(Prova prova) {
        if (prova == null) return null;
        if (prova.getDistanciaKm() != null) return prova.getDistanciaKm();

        DistanciaProva distancia = prova.getDistancia();
        if (distancia == null) return null;

        return switch (distancia) {
            case KM_5 -> BigDecimal.valueOf(5.0);
            case KM_10 -> BigDecimal.valueOf(10.0);
            case KM_21 -> BigDecimal.valueOf(21.0975);
            case KM_42 -> BigDecimal.valueOf(42.195);
        };
    }
}
