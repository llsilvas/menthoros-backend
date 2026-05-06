package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.enums.MetricasThresholds;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Formatador de métricas de carga e fadiga para prompts de IA.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Formatação de CTL, ATL, TSB e Ramp Rate</li>
 *   <li>Interpretação textual de métricas (TSB, Ramp Rate)</li>
 *   <li>Recomendações baseadas em métricas</li>
 *   <li>Avaliação de status geral do atleta</li>
 * </ul>
 */
@Component
public class MetricasPromptFormatter {

    /**
     * Formata o bloco completo de métricas para o prompt.
     */
    public String formatarMetricas(PlanoMetaDados metaDados) {
        if (metaDados == null) {
            return """
                    ## MÉTRICAS DE CARGA E FADIGA

                    ⚠️ Metadados não disponíveis. Aguardando coleta de métricas.

                    ---
                    """;
        }

        String statusGeral = metaDados.getStatusGeral() != null
                ? metaDados.getStatusGeral()
                : "COLETANDO DADOS";

        String recomendacao = metaDados.getRecomendacaoTreino() != null
                ? metaDados.getRecomendacaoTreino()
                : "Continuar treinamento normalmente, respeitando os princípios de progressão.";

        return String.format("""
                        ## MÉTRICAS DE CARGA E FADIGA

                        **Status Geral:** %s
                        **Recomendação:** %s

                        ### Métricas Principais
                        - **CTL (Fitness):** %.1f pontos - Forma física acumulada (6 semanas)
                        - **ATL (Fadiga):** %.1f pontos - Fadiga recente (última semana)
                        - **TSB (Prontidão):** %.1f pontos - %s
                        - **Ramp Rate:** %s - %s

                        ### Médias Semanais
                        - Volume: %.1f km | TSS: %d pts | Treinos: %.1f sessões

                        ### Padrões de Treino
                        - Dias consecutivos: %d | Desde último descanso: %d | Progressão: %d semanas
                        - Dia preferido longo: %s

                        ---
                        """,
                statusGeral,
                recomendacao,
                metaDados.getCtlAtual() != null ? metaDados.getCtlAtual() : 0.0,
                metaDados.getAtlAtual() != null ? metaDados.getAtlAtual() : 0.0,
                metaDados.getTsbAtual() != null ? metaDados.getTsbAtual() : 0.0,
                metaDados.getInterpretacaoTsb(),
                formatarRampRateResumo(metaDados.getCtlAtual(), metaDados.getRampRateAtual()),
                interpretarRampRate(metaDados.getCtlAtual(), metaDados.getRampRateAtual()),
                metaDados.getVolumeSemanalMedio() != null ? metaDados.getVolumeSemanalMedio().doubleValue() : 0.0,
                metaDados.getTssSemanalMedio() != null ? metaDados.getTssSemanalMedio() : 0,
                metaDados.getTreinosPorSemanaMedio(),
                metaDados.getDiasConsecutivosTreino() != null ? metaDados.getDiasConsecutivosTreino() : 0,
                metaDados.getDiasDesdeUltimoDescanso() != null ? metaDados.getDiasDesdeUltimoDescanso() : 0,
                metaDados.getSemanasProgressaoContinua() != null ? metaDados.getSemanasProgressaoContinua() : 0,
                metaDados.getDiaPreferidoLongo() != null ? metaDados.getDiaPreferidoLongo().toString() : "Não definido"
        );
    }

    /**
     * Interpreta o Ramp Rate com análise relativa ao CTL quando disponível.
     */
    public String interpretarRampRate(Double rampRate) {
        return interpretarRampRate(null, rampRate);
    }

    public String interpretarRampRate(Double ctlAtual, Double rampRatePontosSemana) {
        if (rampRatePontosSemana == null) {
            return "Sem dados suficientes";
        }

        Double rampRel = calcularRampRateRelativoPercentual(ctlAtual, rampRatePontosSemana);
        if (rampRel != null) {
            return interpretarPorPercentual(rampRel);
        }

        // Fallback: sem CTL, usa thresholds absolutos
        return interpretarPorPontosAbsolutos(rampRatePontosSemana);
    }

    public String formatarRampRateResumo(Double ctlAtual, Double rampRatePontosSemana) {
        if (rampRatePontosSemana == null) {
            return "0.0 pts/sem";
        }

        Double rampRel = calcularRampRateRelativoPercentual(ctlAtual, rampRatePontosSemana);
        if (rampRel == null) {
            return String.format("%.1f pts/sem", rampRatePontosSemana);
        }
        return String.format("%.1f%%/sem (%.1f pts)", rampRel, rampRatePontosSemana);
    }

    public Double calcularRampRateRelativoPercentual(Double ctlAtual, Double rampRatePontosSemana) {
        if (ctlAtual == null || rampRatePontosSemana == null) {
            return null;
        }
        double ctlAnterior = ctlAtual - rampRatePontosSemana;
        double ctlEfetivo = Math.max(ctlAnterior, MetricasThresholds.CTL_MINIMO_RAMP_RELATIVO);
        return (rampRatePontosSemana / ctlEfetivo) * 100.0;
    }

    /**
     * Interpreta TSB (Training Stress Balance) com texto descritivo.
     */
    public String interpretarTsb(Double tsb) {
        if (tsb == null) {
            return "Sem dados suficientes";
        }

        if (tsb < -35) {
            return "FADIGA CRÍTICA - Risco de overtraining, descanso obrigatório";
        } else if (tsb < -25) {
            return "FADIGA ALTA - Reduzir volume, priorizar recuperação";
        } else if (tsb < -15) {
            return "FADIGA MODERADA - Monitorar, considerar dia mais leve";
        } else if (tsb < -5) {
            return "ACUMULANDO FADIGA - Normal em fase de treino intenso";
        } else if (tsb < 5) {
            return "RECUPERANDO - Corpo assimilando treinos";
        } else if (tsb < 15) {
            return "FORMA IDEAL - Prontidão ótima para performance";
        } else if (tsb < 25) {
            return "BEM RECUPERADO - Ótimo para treinos intensos ou provas";
        } else {
            return "MUITO DESCANSADO - Considerar aumentar volume/intensidade";
        }
    }

    /**
     * Recomendação de ação baseada no TSB.
     */
    public String getRecomendacaoTsb(Double tsb) {
        if (tsb == null) return "";

        if (tsb < -35) {
            return "\nAÇÃO: Dia de descanso completo ou atividade regenerativa (30min caminhada leve)";
        } else if (tsb < -25) {
            return "\nAÇÃO: Reduzir volume em 40%, manter apenas treinos regenerativos";
        } else if (tsb < -15) {
            return "\nSUGESTÃO: Reduzir intensidade, aumentar dias de recuperação";
        } else if (tsb >= 5 && tsb <= 15) {
            return "\nOPORTUNIDADE: Janela ideal para treinos intensos ou provas importantes";
        } else if (tsb > 25) {
            return "\nSUGESTÃO: Aumentar volume ou incluir sessão de qualidade";
        }

        return "";
    }

    /**
     * Recomendação de ação baseada no Ramp Rate.
     */
    public String getRecomendacaoRampRate(Double rampRate) {
        if (rampRate == null) return "";

        if (rampRate > 10) {
            return "\nAÇÃO: Reduzir volume em 20-30% nas próximas 2 semanas";
        } else if (rampRate > 8) {
            return "\nSUGESTÃO: Manter volume atual sem aumentar mais";
        } else if (rampRate < 0) {
            return "\nSUGESTÃO: Volume pode ser aumentado gradualmente";
        }

        return "";
    }

    /**
     * Avalia o status geral do atleta considerando TSB, Ramp Rate e dias consecutivos.
     * Inclui interpretação textual do TSB e recomendação de ação quando disponíveis.
     */
    public String avaliarStatusGeral(PlanoMetaDados metaDados) {
        if (metaDados == null) {
            return "DADOS INSUFICIENTES - Aguardando coleta de métricas";
        }

        Double tsb = metaDados.getTsbAtual();
        Double rampRate = metaDados.getRampRateAtual();
        Integer diasConsecutivos = metaDados.getDiasConsecutivosTreino();

        List<String> alertas = new ArrayList<>();

        if (tsb != null && tsb < -35) {
            alertas.add("FADIGA CRÍTICA");
        }
        if (rampRate != null && rampRate > 10) {
            alertas.add("PROGRESSÃO PERIGOSA");
        }
        if (diasConsecutivos != null && diasConsecutivos >= 6) {
            alertas.add("EXCESSO DE DIAS CONSECUTIVOS");
        }

        if (tsb != null && tsb >= -35 && tsb < -25) {
            alertas.add("FADIGA ELEVADA");
        }
        if (rampRate != null && rampRate > 8 && rampRate <= 10) {
            alertas.add("RAMP RATE ALTO");
        }
        if (diasConsecutivos != null && diasConsecutivos == 5) {
            alertas.add("5 DIAS CONSECUTIVOS");
        }

        StringBuilder status = new StringBuilder();

        if (alertas.isEmpty()) {
            if (tsb == null && rampRate == null) {
                return "COLETANDO DADOS - Aguardando histórico de treinos para análise";
            }
            if (tsb != null && tsb >= 5 && tsb <= 15) {
                status.append("FORMA IDEAL - Atleta pronto para treinos de qualidade ou provas");
            } else if (rampRate != null && rampRate >= 3 && rampRate <= 8) {
                status.append("PROGRESSÃO SAUDÁVEL - Continue o bom trabalho");
            } else {
                status.append("SEM ALERTAS - Atleta em condições normais de treino");
            }
        } else {
            status.append("REQUER ATENÇÃO: ").append(String.join(", ", alertas));
        }

        if (tsb != null) {
            status.append(" | TSB: ").append(metaDados.interpretarTsb());
            String recomendacaoTsb = metaDados.getRecomendacaoTsb();
            if (!recomendacaoTsb.isEmpty()) {
                status.append("\n").append(recomendacaoTsb);
            }
        }

        return status.toString();
    }

    /**
     * Gera texto de status dos dias consecutivos vs limite.
     */
    public String gerarStatusDiasConsecutivos(Integer diasConsecutivos, int maxDiasConsecutivos) {
        if (diasConsecutivos == null || diasConsecutivos == 0) {
            return "✅ Descansado - Pronto para iniciar semana";
        }

        if (diasConsecutivos >= maxDiasConsecutivos + 1) {
            return "🔴 CRÍTICO - Excedeu limite seguro! DESCANSO OBRIGATÓRIO";
        } else if (diasConsecutivos == maxDiasConsecutivos) {
            return "⚠️ NO LIMITE - Próximo treino DEVE ser descanso ou regenerativo";
        } else if (diasConsecutivos >= maxDiasConsecutivos - 1) {
            return "🟡 ATENÇÃO - Próximo do limite, planejar descanso";
        } else if (diasConsecutivos >= 3) {
            return "✅ DENTRO DO LIMITE - Monitorar sinais de fadiga";
        } else if (diasConsecutivos >= 1) {
            return "✅ SAUDÁVEL - Padrão seguro de treino";
        }

        return "✅ Normal";
    }

    private String interpretarPorPercentual(double rampRel) {
        if (rampRel > MetricasThresholds.RAMP_RATE_RELATIVO_CRITICO) {
            return "MUITO ALTO - Progressão perigosa, risco de lesão!";
        } else if (rampRel > MetricasThresholds.RAMP_RATE_RELATIVO_ALTO) {
            return "ALTO - Progressão rápida, monitorar sinais de fadiga";
        } else if (rampRel >= 5) {
            return "IDEAL - Progressão segura e efetiva";
        } else if (rampRel >= 3) {
            return "BOM - Progressão moderada e sustentável";
        } else if (rampRel >= 0) {
            return "BAIXO - Manutenção ou progressão muito lenta";
        } else if (rampRel > -3) {
            return "NEGATIVO - Destreino leve, normal em recuperação";
        } else {
            return "MUITO NEGATIVO - Perda significativa de forma";
        }
    }

    private String interpretarPorPontosAbsolutos(double rampRate) {
        if (rampRate > MetricasThresholds.RAMP_RATE_CRITICO) {
            return "MUITO ALTO - Progressão perigosa, risco de lesão!";
        } else if (rampRate > MetricasThresholds.RAMP_RATE_ALTO) {
            return "ALTO - Progressão rápida, monitorar sinais de fadiga";
        } else if (rampRate >= 5) {
            return "IDEAL - Progressão segura e efetiva";
        } else if (rampRate >= 3) {
            return "BOM - Progressão moderada e sustentável";
        } else if (rampRate >= 0) {
            return "BAIXO - Manutenção ou progressão muito lenta";
        } else if (rampRate > -3) {
            return "NEGATIVO - Destreino leve, normal em recuperação";
        } else {
            return "MUITO NEGATIVO - Perda significativa de forma";
        }
    }
}
