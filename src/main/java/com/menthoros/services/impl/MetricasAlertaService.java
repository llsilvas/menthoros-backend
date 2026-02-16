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

        // 1. Calcular booleans de alerta
        boolean sobrecarga = tsbAtual != null && tsbAtual < MetricasThresholds.TSB_SOBRECARGA;
        boolean rampAlto = rampRateAtual != null && rampRateAtual > MetricasThresholds.RAMP_RATE_CRITICO;
        boolean diasConsecutivos = diasConsecutivosTreino != null
                && diasConsecutivosTreino >= MetricasThresholds.DIAS_CONSECUTIVOS_ALTO;
        boolean necessitaDescanso = sobrecarga || diasConsecutivos;

        // 2. Gerar status
        String status = calcularStatus(tsbAtual, ctlAtual, rampAlto, sobrecarga, diasConsecutivosTreino);

        // 3. Gerar recomendação
        String recomendacao = calcularRecomendacao(tsbAtual, sobrecarga, rampAlto,
                diasConsecutivos, diasConsecutivosTreino, semanasProgressaoContinua);

        // 4. Gerar mensagem de alerta resumida
        String mensagem = gerarMensagemAlerta(tsbAtual, rampRateAtual, diasConsecutivosTreino,
                sobrecarga, rampAlto, diasConsecutivos);

        // 5. Gerar lista estruturada de alertas
        List<AlertaMetricas> alertas = gerarAlertasAtivos(tsbAtual, rampRateAtual,
                diasConsecutivosTreino, semanasProgressaoContinua,
                sobrecarga, rampAlto, diasConsecutivos);

        return new ResultadoAnalise(
                status, recomendacao, mensagem,
                sobrecarga, rampAlto, diasConsecutivos, necessitaDescanso,
                alertas
        );
    }

    private String calcularStatus(Double tsbAtual, Double ctlAtual,
                                  boolean rampAlto, boolean sobrecarga,
                                  Integer diasConsecutivosTreino) {
        if (tsbAtual == null && ctlAtual == null) {
            return "COLETANDO DADOS";
        }

        // Prioridade 1: Alertas críticos combinados
        if (sobrecarga && rampAlto) {
            return "FADIGA CRÍTICA + PROGRESSÃO RÁPIDA";
        }

        // Prioridade 2: Usar FaixaTsb para classificar, com overrides para alertas compostos
        if (rampAlto) {
            return "PROGRESSÃO MUITO RÁPIDA";
        }

        if (diasConsecutivosTreino != null && diasConsecutivosTreino >= MetricasThresholds.DIAS_CONSECUTIVOS_CRITICO) {
            // Dias consecutivos tem prioridade sobre faixas intermediárias de TSB
            FaixaTsb faixa = FaixaTsb.classificar(tsbAtual);
            if (faixa != null && faixa.isFadigaCritica()) {
                return faixa.getStatus(); // fadiga crítica tem prioridade
            }
            if (sobrecarga) {
                return "FADIGA ALTA"; // sobrecarga tem prioridade sobre dias consecutivos
            }
            return "MUITOS DIAS CONSECUTIVOS";
        }

        if (sobrecarga) {
            return "FADIGA ALTA";
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

    private String gerarMensagemAlerta(Double tsbAtual, Double rampRateAtual,
                                       Integer diasConsecutivosTreino,
                                       boolean sobrecarga, boolean rampAlto,
                                       boolean diasConsecutivos) {
        List<String> mensagens = new ArrayList<>();

        if (sobrecarga) {
            mensagens.add("TSB crítico (" + tsbAtual + "). Descanso recomendado.");
        }
        if (rampAlto) {
            mensagens.add("Progressão muito rápida (" + rampRateAtual + " pts/sem). Reduzir volume.");
        }
        if (diasConsecutivos) {
            mensagens.add(diasConsecutivosTreino + " dias seguidos treinando. Dia de descanso necessário.");
        }

        return mensagens.isEmpty() ? null : String.join(" ", mensagens);
    }

    private List<AlertaMetricas> gerarAlertasAtivos(Double tsbAtual, Double rampRateAtual,
                                                     Integer diasConsecutivosTreino,
                                                     Integer semanasProgressaoContinua,
                                                     boolean sobrecarga, boolean rampAlto,
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
        if (rampAlto) {
            alertas.add(new AlertaMetricas(
                    NivelAlerta.CRITICO, "RAMP_RATE_ALTO",
                    String.format("Progressão muito rápida (%.1f pts/sem). Risco de lesão!", rampRateAtual),
                    "Reduzir volume em 20-30% nas próximas 2 semanas. Não aumentar carga."
            ));
        } else if (rampRateAtual != null && rampRateAtual > MetricasThresholds.RAMP_RATE_ALTO) {
            alertas.add(new AlertaMetricas(
                    NivelAlerta.ALTO, "RAMP_RATE_MODERADO",
                    String.format("Progressão rápida (%.1f pts/sem).", rampRateAtual),
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
