package com.menthoros.services.impl;

import com.menthoros.dto.AlertaMetricas;
import com.menthoros.dto.output.ResultadoAnalise;
import com.menthoros.entity.PlanoMetaDados;
import com.menthoros.enums.FaixaTsb;
import com.menthoros.enums.MetricasThresholds;
import com.menthoros.enums.NivelAlerta;
import com.menthoros.enums.NivelExperiencia;
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

    // ===== API com nível de experiência =====

    /**
     * Analisa métricas considerando o nível de experiência do atleta.
     *
     * <p>Adapta os thresholds de progressão, ramp rate e dias consecutivos de acordo com
     * o {@link NivelExperiencia}, além de incluir contexto específico na recomendação.
     * Use este overload quando o nível do atleta estiver disponível (ex: após recálculo
     * de histórico).
     *
     * @param metaDados entidade com métricas atuais
     * @param nivel     nível de experiência do atleta
     * @return resultado com status, recomendação adaptada ao nível, alertas e booleans
     */
    public ResultadoAnalise analisarMetricas(PlanoMetaDados metaDados, NivelExperiencia nivel) {
        Double tsbAtual = metaDados.getTsbAtual();
        Double ctlAtual = metaDados.getCtlAtual();
        Double rampRateAtual = metaDados.getRampRateAtual();
        Integer diasConsecutivosTreino = metaDados.getDiasConsecutivosTreino();
        Integer semanasProgressaoContinua = metaDados.getSemanasProgressaoContinua();

        RampRateInfo rampInfo = RampRateInfo.from(ctlAtual, rampRateAtual);

        // Thresholds adaptados ao nível
        double rampRelativoCritico = rampRelativoCriticoParaNivel(nivel);
        double rampRelativoAlto    = rampRelativoAltoParaNivel(nivel);
        int diasConsecutivosAlto   = diasConsecutivosAltoParaNivel(nivel);
        int diasConsecutivosCritico = diasConsecutivosCriticoParaNivel(nivel);
        int semanasProgressaoAlerta = semanasProgressaoAlertaParaNivel(nivel);

        // 1. Booleans de alerta com thresholds adaptados
        boolean sobrecarga = tsbAtual != null && tsbAtual < MetricasThresholds.TSB_SOBRECARGA;
        boolean rampCritico = rampInfo.percentualSemana != null
                ? rampInfo.percentualSemana > rampRelativoCritico
                : (rampRateAtual != null && rampRateAtual > MetricasThresholds.RAMP_RATE_CRITICO);
        boolean rampAlto = rampInfo.percentualSemana != null
                ? rampInfo.percentualSemana > rampRelativoAlto
                : (rampRateAtual != null && rampRateAtual > MetricasThresholds.RAMP_RATE_ALTO);
        boolean diasConsecutivosFlag = diasConsecutivosTreino != null
                && diasConsecutivosTreino >= diasConsecutivosAlto;
        boolean necessitaDescanso = sobrecarga || diasConsecutivosFlag;

        // 2. Status
        String status = calcularStatusComNivel(tsbAtual, ctlAtual, rampCritico, sobrecarga,
                diasConsecutivosTreino, diasConsecutivosCritico);

        // 3. Recomendação com contexto do nível
        String recomendacao = calcularRecomendacaoComNivel(tsbAtual, sobrecarga, rampCritico,
                diasConsecutivosFlag, diasConsecutivosTreino, diasConsecutivosCritico,
                semanasProgressaoContinua, semanasProgressaoAlerta, nivel);

        // 4. Mensagem de alerta resumida
        String mensagem = gerarMensagemAlerta(tsbAtual, rampInfo, diasConsecutivosTreino,
                sobrecarga, rampCritico, diasConsecutivosFlag);

        // 5. Alertas estruturados com thresholds adaptados
        List<AlertaMetricas> alertas = gerarAlertasAtivosComNivel(tsbAtual, rampInfo,
                diasConsecutivosTreino, semanasProgressaoContinua,
                sobrecarga, rampCritico, rampAlto, diasConsecutivosFlag,
                diasConsecutivosCritico, semanasProgressaoAlerta);

        return new ResultadoAnalise(
                status, recomendacao, mensagem,
                sobrecarga, rampCritico, diasConsecutivosFlag, necessitaDescanso,
                alertas
        );
    }

    // ===== Helpers de threshold por nível =====

    private int semanasProgressaoAlertaParaNivel(NivelExperiencia nivel) {
        if (nivel == null) return MetricasThresholds.SEMANAS_PROGRESSAO_ALERTA;
        return switch (nivel) {
            case INICIANTE     -> 2;
            case INTERMEDIARIO -> 3;
            case AVANCADO      -> 4;
            case ELITE         -> 5;
        };
    }

    private int diasConsecutivosAltoParaNivel(NivelExperiencia nivel) {
        if (nivel == null) return MetricasThresholds.DIAS_CONSECUTIVOS_ALTO;
        return switch (nivel) {
            case INICIANTE, INTERMEDIARIO -> 4;
            case AVANCADO, ELITE         -> 5;
        };
    }

    private int diasConsecutivosCriticoParaNivel(NivelExperiencia nivel) {
        if (nivel == null) return MetricasThresholds.DIAS_CONSECUTIVOS_CRITICO;
        return switch (nivel) {
            case INICIANTE, INTERMEDIARIO -> 5;
            case AVANCADO                 -> 6;
            case ELITE                    -> 7;
        };
    }

    private double rampRelativoCriticoParaNivel(NivelExperiencia nivel) {
        if (nivel == null) return MetricasThresholds.RAMP_RATE_RELATIVO_CRITICO;
        return switch (nivel) {
            case INICIANTE     -> 10.0;
            case INTERMEDIARIO -> 12.0;
            case AVANCADO      -> 15.0;
            case ELITE         -> 18.0;
        };
    }

    private double rampRelativoAltoParaNivel(NivelExperiencia nivel) {
        if (nivel == null) return MetricasThresholds.RAMP_RATE_RELATIVO_ALTO;
        return switch (nivel) {
            case INICIANTE     ->  7.0;
            case INTERMEDIARIO ->  8.0;
            case AVANCADO      -> 10.0;
            case ELITE         -> 12.0;
        };
    }

    private String contextoProgressaoParaNivel(NivelExperiencia nivel) {
        if (nivel == null) return null;
        return switch (nivel) {
            case INICIANTE     ->
                "Como atleta iniciante, aumentos de volume de até 10% por semana são seguros — priorize a recuperação.";
            case INTERMEDIARIO ->
                "Seu nível permite progressões de até 12% por semana. Monitore sinais de fadiga e respeite os dias de descanso.";
            case AVANCADO      ->
                "Atletas avançados toleram cargas maiores, mas semanas regenerativas periódicas são essenciais para a adaptação.";
            case ELITE         ->
                "Como atleta elite, sua tolerância a carga é alta. Equilibre carga e recuperação para atingir o pico de forma nos momentos certos.";
        };
    }

    // ===== Cálculos internos com suporte a nível =====

    private String calcularStatusComNivel(Double tsbAtual, Double ctlAtual,
                                          boolean rampAlto, boolean sobrecarga,
                                          Integer diasConsecutivosTreino,
                                          int diasConsecutivosCritico) {
        if (tsbAtual == null && ctlAtual == null) return "COLETANDO DADOS";

        boolean tsbCritico = tsbAtual != null && tsbAtual < MetricasThresholds.TSB_CRITICO;

        if (sobrecarga && rampAlto) {
            return (tsbCritico ? "FADIGA CRÍTICA" : "FADIGA ALTA") + " + PROGRESSÃO RÁPIDA";
        }
        if (rampAlto) return "PROGRESSÃO MUITO RÁPIDA";
        if (diasConsecutivosTreino != null && diasConsecutivosTreino >= diasConsecutivosCritico) {
            if (tsbCritico) return "FADIGA CRÍTICA";
            if (sobrecarga) return "FADIGA ALTA";
            return "MUITOS DIAS CONSECUTIVOS";
        }
        if (sobrecarga) return tsbCritico ? "FADIGA CRÍTICA" : "FADIGA ALTA";

        FaixaTsb faixa = FaixaTsb.classificar(tsbAtual);
        return faixa != null ? faixa.getStatus() : "NORMAL";
    }

    private String calcularRecomendacaoComNivel(Double tsbAtual, boolean sobrecarga,
                                                boolean rampAlto, boolean diasConsecutivos,
                                                Integer diasConsecutivosTreino,
                                                int diasConsecutivosCritico,
                                                Integer semanasProgressaoContinua,
                                                int semanasProgressaoAlerta,
                                                NivelExperiencia nivel) {
        StringBuilder rec = new StringBuilder();

        if (tsbAtual != null && tsbAtual < MetricasThresholds.TSB_CRITICO) {
            rec.append("Dia de descanso completo OBRIGATÓRIO ou apenas atividade regenerativa leve (30min). ");
        } else if (sobrecarga) {
            rec.append("Reduzir volume em 30-40%. Priorizar treinos regenerativos e descanso. ");
        }

        if (rampAlto) {
            rec.append("Reduzir volume em 20-30% nas próximas 2 semanas. Não aumentar carga. ");
        }

        if (diasConsecutivosTreino != null && diasConsecutivosTreino >= diasConsecutivosCritico) {
            rec.append("Incluir dia de descanso completo IMEDIATAMENTE. ");
        } else if (diasConsecutivos) {
            rec.append("Incluir dia de descanso ou treino regenerativo nos próximos 1-2 dias. ");
        }

        if (semanasProgressaoContinua != null && semanasProgressaoContinua >= semanasProgressaoAlerta) {
            rec.append("Considerar semana regenerativa (reduzir volume em 40-50%) para assimilação. ");
        }

        if (rec.isEmpty() && tsbAtual != null) {
            FaixaTsb faixa = FaixaTsb.classificar(tsbAtual);
            if (faixa != null) rec.append(faixa.getRecomendacao()).append(" ");
        } else if (rec.isEmpty()) {
            rec.append("Continuar treinamento normalmente, respeitando os princípios de progressão. ");
        }

        // Contexto de nível ao final
        String contexto = contextoProgressaoParaNivel(nivel);
        if (contexto != null) rec.append(contexto);

        return rec.length() > 0 ? rec.toString().trim() : null;
    }

    private List<AlertaMetricas> gerarAlertasAtivosComNivel(Double tsbAtual, RampRateInfo rampInfo,
                                                             Integer diasConsecutivosTreino,
                                                             Integer semanasProgressaoContinua,
                                                             boolean sobrecarga, boolean rampCritico,
                                                             boolean rampAlto, boolean diasConsecutivos,
                                                             int diasConsecutivosCritico,
                                                             int semanasProgressaoAlerta) {
        List<AlertaMetricas> alertas = new ArrayList<>();

        if (tsbAtual != null && tsbAtual < MetricasThresholds.TSB_CRITICO) {
            alertas.add(new AlertaMetricas(NivelAlerta.CRITICO, "TSB_CRITICO",
                    String.format("TSB crítico (%.1f). Risco de overtraining.", tsbAtual),
                    "Dia de descanso completo OBRIGATÓRIO ou apenas atividade regenerativa leve (30min caminhada)."));
        } else if (sobrecarga) {
            alertas.add(new AlertaMetricas(NivelAlerta.ALTO, "TSB_BAIXO",
                    String.format("TSB baixo (%.1f). Fadiga alta acumulada.", tsbAtual),
                    "Reduzir volume em 30-40%. Priorizar treinos regenerativos e descanso."));
        } else if (tsbAtual != null && tsbAtual < MetricasThresholds.TSB_FADIGA_MODERADA) {
            alertas.add(new AlertaMetricas(NivelAlerta.ATENCAO, "TSB_MODERADO",
                    String.format("TSB moderado (%.1f). Fadiga moderada.", tsbAtual),
                    "Monitorar sinais de fadiga. Considerar reduzir intensidade dos treinos."));
        }

        if (rampCritico) {
            alertas.add(new AlertaMetricas(NivelAlerta.CRITICO, "RAMP_RATE_ALTO",
                    "Progressão muito rápida (" + (rampInfo != null ? rampInfo.formatarResumo() : "sem dados") + "). Risco de lesão!",
                    "Reduzir volume em 20-30% nas próximas 2 semanas. Não aumentar carga."));
        } else if (rampAlto) {
            alertas.add(new AlertaMetricas(NivelAlerta.ALTO, "RAMP_RATE_MODERADO",
                    "Progressão rápida (" + (rampInfo != null ? rampInfo.formatarResumo() : "sem dados") + ").",
                    "Manter volume atual sem aumentar. Monitorar sinais de fadiga."));
        }

        if (diasConsecutivosTreino != null && diasConsecutivosTreino >= diasConsecutivosCritico) {
            alertas.add(new AlertaMetricas(NivelAlerta.CRITICO, "DIAS_CONSECUTIVOS_CRITICO",
                    String.format("%d dias consecutivos treinando. Risco de overtraining!", diasConsecutivosTreino),
                    "Incluir dia de descanso completo IMEDIATAMENTE."));
        } else if (diasConsecutivos) {
            alertas.add(new AlertaMetricas(NivelAlerta.ALTO, "DIAS_CONSECUTIVOS_ALTO",
                    String.format("%d dias consecutivos treinando.", diasConsecutivosTreino),
                    "Incluir dia de descanso ou treino regenerativo nos próximos 1-2 dias."));
        }

        if (semanasProgressaoContinua != null && semanasProgressaoContinua >= semanasProgressaoAlerta) {
            alertas.add(new AlertaMetricas(NivelAlerta.ATENCAO, "PROGRESSAO_CONTINUA",
                    String.format("%d semanas de progressão contínua.", semanasProgressaoContinua),
                    "Considerar semana regenerativa (reduzir volume em 40-50%) para assimilação."));
        }

        FaixaTsb faixa = FaixaTsb.classificar(tsbAtual);
        if (alertas.isEmpty() && faixa != null && faixa.isFormaIdeal()) {
            alertas.add(new AlertaMetricas(NivelAlerta.INFO, "FORMA_IDEAL",
                    String.format("TSB em forma ideal (%.1f). Condições ótimas!", tsbAtual),
                    "Janela ideal para treinos intensos ou provas importantes."));
        }

        return alertas;
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
