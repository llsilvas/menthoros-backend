package com.menthoros.services.impl;

import com.menthoros.dto.AlertaMetricas;
import com.menthoros.dto.output.ResultadoAnalise;
import com.menthoros.entity.PlanoMetaDados;
import com.menthoros.enums.FaixaTsb;
import com.menthoros.enums.MetricasThresholds;
import com.menthoros.enums.NivelAlerta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Serviço responsável por analisar métricas de treino e gerar alertas, status e recomendações.
 *
 * <p>Centraliza a lógica que antes estava distribuída em múltiplos métodos privados
 * de {@link PlanoMetaDados} ({@code atualizarAlertas}, {@code atualizarStatusGeral},
 * {@code atualizarRecomendacao}, {@code getAlertasAtivos}).
 *
 * <p>Deve ser chamado explicitamente antes de persistir o {@link PlanoMetaDados}.
 */
@Slf4j
@Service
public class MetricasAlertaService {

    private record RampRateInfo(Double pontosSemana, Double percentualSemana) {
        static RampRateInfo from(Double ctlAtual, Double rampRatePontosSemana) {
            if (rampRatePontosSemana == null) return new RampRateInfo(null, null);
            if (ctlAtual == null) return new RampRateInfo(rampRatePontosSemana, null);

            double pontos = rampRatePontosSemana;
            double ctlAnterior = ctlAtual - pontos;
            double ctlEfetivo = Math.max(ctlAnterior, MetricasThresholds.CTL_MINIMO_RAMP_RELATIVO);

            double percentual = (pontos / ctlEfetivo) * 100.0;
            return new RampRateInfo(pontos, percentual);
        }

        String formatarResumo() {
            if (pontosSemana == null) return null;
            if (percentualSemana == null) {
                return String.format("%.1f pts/sem", pontosSemana);
            }
            return String.format("%.1f%%/sem (%.1f pts)", percentualSemana, pontosSemana);
        }
    }

    /**
     * Analisa as métricas atuais do PlanoMetaDados e retorna o resultado completo.
     *
     * @param metaDados entidade com métricas atuais (CTL, ATL, TSB, etc.)
     * @return resultado com status, recomendação, alertas e booleans
     */
    public ResultadoAnalise analisarMetricas(PlanoMetaDados metaDados) {
        Double tsbAtual = metaDados.getTsbAtual();
        Double ctlAtual = metaDados.getCtlAtual();
        Double rampRateAtual = metaDados.getRampRateAtual();
        Integer diasConsecutivosTreino = metaDados.getDiasConsecutivosTreino();
        Integer semanasProgressaoContinua = metaDados.getSemanasProgressaoContinua();

        RampRateInfo rampInfo = RampRateInfo.from(ctlAtual, rampRateAtual);

        // 1. Calcular booleans de alerta
        boolean sobrecarga = tsbAtual != null && tsbAtual < MetricasThresholds.TSB_SOBRECARGA;
        boolean rampCritico = rampInfo.percentualSemana != null
                ? rampInfo.percentualSemana > MetricasThresholds.RAMP_RATE_RELATIVO_CRITICO
                : (rampRateAtual != null && rampRateAtual > MetricasThresholds.RAMP_RATE_CRITICO);
        boolean rampAlto = rampInfo.percentualSemana != null
                ? rampInfo.percentualSemana > MetricasThresholds.RAMP_RATE_RELATIVO_ALTO
                : (rampRateAtual != null && rampRateAtual > MetricasThresholds.RAMP_RATE_ALTO);
        boolean diasConsecutivos = diasConsecutivosTreino != null
                && diasConsecutivosTreino >= MetricasThresholds.DIAS_CONSECUTIVOS_ALTO;
        boolean necessitaDescanso = sobrecarga || diasConsecutivos;

        // 2. Gerar status
        String status = calcularStatus(tsbAtual, ctlAtual, rampCritico, sobrecarga, diasConsecutivosTreino);

        // 3. Gerar recomendação
        String recomendacao = calcularRecomendacao(tsbAtual, sobrecarga, rampCritico,
                diasConsecutivos, diasConsecutivosTreino, semanasProgressaoContinua);

        // 4. Gerar mensagem de alerta resumida
        String mensagem = gerarMensagemAlerta(tsbAtual, rampInfo, diasConsecutivosTreino,
                sobrecarga, rampCritico, diasConsecutivos);

        // 5. Gerar lista estruturada de alertas
        List<AlertaMetricas> alertas = gerarAlertasAtivos(tsbAtual, rampInfo,
                diasConsecutivosTreino, semanasProgressaoContinua,
                sobrecarga, rampCritico, rampAlto, diasConsecutivos);

        return new ResultadoAnalise(
                status, recomendacao, mensagem,
                sobrecarga, rampCritico, diasConsecutivos, necessitaDescanso,
                alertas
        );
    }

    private String calcularStatus(Double tsbAtual, Double ctlAtual,
                                  boolean rampAlto, boolean sobrecarga,
                                  Integer diasConsecutivosTreino) {
        if (tsbAtual == null && ctlAtual == null) {
            return "COLETANDO DADOS";
        }

        boolean tsbCritico = tsbAtual != null && tsbAtual < MetricasThresholds.TSB_CRITICO;

        // Prioridade 1: Alertas críticos combinados
        if (sobrecarga && rampAlto) {
            return (tsbCritico ? "FADIGA CRÍTICA" : "FADIGA ALTA") + " + PROGRESSÃO RÁPIDA";
        }

        // Prioridade 2: Usar FaixaTsb para classificar, com overrides para alertas compostos
        if (rampAlto) {
            return "PROGRESSÃO MUITO RÁPIDA";
        }

        if (diasConsecutivosTreino != null && diasConsecutivosTreino >= MetricasThresholds.DIAS_CONSECUTIVOS_CRITICO) {
            // Dias consecutivos tem prioridade sobre faixas intermediárias de TSB
            if (tsbCritico) {
                return "FADIGA CRÍTICA";
            }
            if (sobrecarga) {
                return "FADIGA ALTA"; // sobrecarga tem prioridade sobre dias consecutivos
            }
            return "MUITOS DIAS CONSECUTIVOS";
        }

        if (sobrecarga) {
            return tsbCritico ? "FADIGA CRÍTICA" : "FADIGA ALTA";
        }

        // Sem alertas compostos: classificar puramente por FaixaTsb
        FaixaTsb faixa = FaixaTsb.classificar(tsbAtual);
        return faixa != null ? faixa.getStatus() : "NORMAL";
    }

    private String calcularRecomendacao(Double tsbAtual, boolean sobrecarga, boolean rampAlto,
                                        boolean diasConsecutivos, Integer diasConsecutivosTreino,
                                        Integer semanasProgressaoContinua) {
        StringBuilder rec = new StringBuilder();

        // Recomendações críticas primeiro
        if (tsbAtual != null && tsbAtual < MetricasThresholds.TSB_CRITICO) {
            rec.append("Dia de descanso completo OBRIGATÓRIO ou apenas atividade regenerativa leve (30min). ");
        } else if (sobrecarga) {
            rec.append("Reduzir volume em 30-40%. Priorizar treinos regenerativos e descanso. ");
        }

        if (rampAlto) {
            rec.append("Reduzir volume em 20-30% nas próximas 2 semanas. Não aumentar carga. ");
        }

        if (diasConsecutivosTreino != null && diasConsecutivosTreino >= MetricasThresholds.DIAS_CONSECUTIVOS_CRITICO) {
            rec.append("Incluir dia de descanso completo IMEDIATAMENTE. ");
        } else if (diasConsecutivos) {
            rec.append("Incluir dia de descanso ou treino regenerativo nos próximos 1-2 dias. ");
        }

        if (semanasProgressaoContinua != null && semanasProgressaoContinua >= MetricasThresholds.SEMANAS_PROGRESSAO_ALERTA) {
            rec.append("Considerar semana regenerativa (reduzir volume em 40-50%) para assimilação. ");
        }

        // Recomendações por faixa de TSB (quando não há alertas críticos)
        if (rec.isEmpty() && tsbAtual != null) {
            FaixaTsb faixa = FaixaTsb.classificar(tsbAtual);
            if (faixa != null) {
                rec.append(faixa.getRecomendacao()).append(" ");
            }
        } else if (rec.isEmpty()) {
            rec.append("Continuar treinamento normalmente, respeitando os princípios de progressão. ");
        }

        return rec.length() > 0 ? rec.toString().trim() : null;
    }

    private String gerarMensagemAlerta(Double tsbAtual, RampRateInfo rampInfo,
                                       Integer diasConsecutivosTreino,
                                       boolean sobrecarga, boolean rampAlto,
                                       boolean diasConsecutivos) {
        List<String> mensagens = new ArrayList<>();

        if (sobrecarga) {
            mensagens.add("TSB crítico (" + tsbAtual + "). Descanso recomendado.");
        }
        if (rampAlto) {
            String rr = rampInfo != null ? rampInfo.formatarResumo() : null;
            mensagens.add("Progressão muito rápida (" + (rr != null ? rr : "sem dados") + "). Reduzir volume.");
        }
        if (diasConsecutivos) {
            mensagens.add(diasConsecutivosTreino + " dias seguidos treinando. Dia de descanso necessário.");
        }

        return mensagens.isEmpty() ? null : String.join(" ", mensagens);
    }

    private List<AlertaMetricas> gerarAlertasAtivos(Double tsbAtual, RampRateInfo rampInfo,
                                                     Integer diasConsecutivosTreino,
                                                     Integer semanasProgressaoContinua,
                                                     boolean sobrecarga, boolean rampCritico,
                                                     boolean rampAlto,
                                                     boolean diasConsecutivos) {
        List<AlertaMetricas> alertas = new ArrayList<>();

        // Alerta TSB
        if (tsbAtual != null && tsbAtual < MetricasThresholds.TSB_CRITICO) {
            alertas.add(new AlertaMetricas(
                    NivelAlerta.CRITICO, "TSB_CRITICO",
                    String.format("TSB crítico (%.1f). Risco de overtraining.", tsbAtual),
                    "Dia de descanso completo OBRIGATÓRIO ou apenas atividade regenerativa leve (30min caminhada)."
            ));
        } else if (sobrecarga) {
            alertas.add(new AlertaMetricas(
                    NivelAlerta.ALTO, "TSB_BAIXO",
                    String.format("TSB baixo (%.1f). Fadiga alta acumulada.", tsbAtual),
                    "Reduzir volume em 30-40%. Priorizar treinos regenerativos e descanso."
            ));
        } else if (tsbAtual != null && tsbAtual < MetricasThresholds.TSB_FADIGA_MODERADA) {
            alertas.add(new AlertaMetricas(
                    NivelAlerta.ATENCAO, "TSB_MODERADO",
                    String.format("TSB moderado (%.1f). Fadiga moderada.", tsbAtual),
                    "Monitorar sinais de fadiga. Considerar reduzir intensidade dos treinos."
            ));
        }

        // Alerta Ramp Rate
        if (rampCritico) {
            alertas.add(new AlertaMetricas(
                    NivelAlerta.CRITICO, "RAMP_RATE_ALTO",
                    "Progressão muito rápida (" + (rampInfo != null ? rampInfo.formatarResumo() : "sem dados") + "). Risco de lesão!",
                    "Reduzir volume em 20-30% nas próximas 2 semanas. Não aumentar carga."
            ));
        } else if (rampAlto) {
            alertas.add(new AlertaMetricas(
                    NivelAlerta.ALTO, "RAMP_RATE_MODERADO",
                    "Progressão rápida (" + (rampInfo != null ? rampInfo.formatarResumo() : "sem dados") + ").",
                    "Manter volume atual sem aumentar. Monitorar sinais de fadiga."
            ));
        }

        // Alerta Dias Consecutivos
        if (diasConsecutivosTreino != null && diasConsecutivosTreino >= MetricasThresholds.DIAS_CONSECUTIVOS_CRITICO) {
            alertas.add(new AlertaMetricas(
                    NivelAlerta.CRITICO, "DIAS_CONSECUTIVOS_CRITICO",
                    String.format("%d dias consecutivos treinando. Risco de overtraining!", diasConsecutivosTreino),
                    "Incluir dia de descanso completo IMEDIATAMENTE."
            ));
        } else if (diasConsecutivos) {
            alertas.add(new AlertaMetricas(
                    NivelAlerta.ALTO, "DIAS_CONSECUTIVOS_ALTO",
                    String.format("%d dias consecutivos treinando.", diasConsecutivosTreino),
                    "Incluir dia de descanso ou treino regenerativo nos próximos 1-2 dias."
            ));
        }

        // Alerta Semanas Progressão
        if (semanasProgressaoContinua != null && semanasProgressaoContinua >= MetricasThresholds.SEMANAS_PROGRESSAO_ALERTA) {
            alertas.add(new AlertaMetricas(
                    NivelAlerta.ATENCAO, "PROGRESSAO_CONTINUA",
                    String.format("%d semanas de progressão contínua.", semanasProgressaoContinua),
                    "Considerar semana regenerativa (reduzir volume em 40-50%) para assimilação."
            ));
        }

        // Alerta positivo - Forma Ideal
        FaixaTsb faixa = FaixaTsb.classificar(tsbAtual);
        if (alertas.isEmpty() && faixa != null && faixa.isFormaIdeal()) {
            alertas.add(new AlertaMetricas(
                    NivelAlerta.INFO, "FORMA_IDEAL",
                    String.format("TSB em forma ideal (%.1f). Condições ótimas!", tsbAtual),
                    "Janela ideal para treinos intensos ou provas importantes."
            ));
        }

        return alertas;
    }
}
