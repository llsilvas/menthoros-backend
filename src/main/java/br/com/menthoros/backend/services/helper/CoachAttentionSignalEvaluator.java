package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.output.CoachAttentionItemOutputDto.Evidencia;
import br.com.menthoros.backend.enums.FaixaTsb;
import br.com.menthoros.backend.enums.MotivoAtencao;
import br.com.menthoros.backend.enums.Severidade;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Deriva, por fonte, um {@link SinalAtencao} a partir de valores já carregados pelo serviço.
 *
 * <p>Cada método é uma função pura (entrada simples → {@code Optional<SinalAtencao>}), sem Spring nem
 * IO — todo o acesso a repositório/entidade fica no serviço. Não recalcula sinais: apenas classifica
 * em severidade/motivo os sinais que o backend já produz.
 *
 * <p>Severidade MEDIA é derivada normalmente; o corte de exibição da v1 (apenas ALTA/CRITICA) é
 * aplicado a jusante, no serviço.
 */
@Component
public class CoachAttentionSignalEvaluator {

    // Constantes de sourceRules — centralizadas para facilitar atualização em caso de renomeação
    private static final String SOURCE_FADIGA = "CoachAttentionSignalEvaluator.avaliarFadiga";
    private static final String SOURCE_FAIXA_TSB_PREFIX = "FaixaTsb.";
    private static final String SOURCE_SOBRECARGA = "CoachAttentionSignalEvaluator.avaliarSobrecarga";
    private static final String SOURCE_ADERENCIA = "CoachAttentionSignalEvaluator.avaliarAderencia";
    private static final String SOURCE_INATIVIDADE = "CoachAttentionSignalEvaluator.avaliarInatividade";
    private static final String SOURCE_ZONAS_VENCIDAS = "CoachAttentionSignalEvaluator.avaliarZonasVencidas";
    private static final String SOURCE_SEM_PLANO = "CoachAttentionSignalEvaluator.avaliarSemPlano";

    /** Fadiga/forma via classificação de TSB ({@link FaixaTsb}); INFO ou TSB nulo → sem sinal. */
    public Optional<SinalAtencao> avaliarFadiga(Double tsb) {
        FaixaTsb faixa = FaixaTsb.classificar(tsb);
        if (faixa == null) {
            return Optional.empty();
        }
        Severidade severidade = switch (faixa.getNivelAlerta()) {
            case CRITICO -> Severidade.CRITICA;
            case ALTO -> Severidade.ALTA;
            case ATENCAO -> Severidade.MEDIA;
            case INFO -> null; // faixa saudável/INFO não gera item de atenção
        };
        if (severidade == null) {
            return Optional.empty();
        }
        String valorEvidencia = String.format(Locale.US, "%.1f (%s)", tsb, faixa.getInterpretacao());
        String descricaoRisco = severidade == Severidade.CRITICA
                ? "fadiga excessiva com risco de overtraining"
                : "fadiga elevada acima da capacidade de recuperação";
        String rationale = String.format(Locale.US,
                "TSB em %.1f situa-se na zona %s (%s), indicando %s.",
                tsb, faixa.name(), faixa.getInterpretacao(), descricaoRisco);
        List<String> sourceRules = List.of(SOURCE_FADIGA, SOURCE_FAIXA_TSB_PREFIX + faixa.name());
        return Optional.of(new SinalAtencao(MotivoAtencao.FADIGA, severidade,
                List.of(new Evidencia("TSB", valorEvidencia)), rationale, sourceRules));
    }

    /** Sobrecarga/progressão via flags do plano. sobrecarga/necessita-descanso → ALTA; ramp/dias → MEDIA. */
    public Optional<SinalAtencao> avaliarSobrecarga(boolean sobrecarga, boolean necessitaDescanso,
                                                    boolean rampAlto, boolean diasConsecutivos,
                                                    Integer diasConsecutivosTreino) {
        Severidade severidade;
        if (sobrecarga || necessitaDescanso) {
            severidade = Severidade.ALTA;
        } else if (rampAlto || diasConsecutivos) {
            severidade = Severidade.MEDIA;
        } else {
            return Optional.empty();
        }
        // rationale = primeiro flag ativo por prioridade (sobrecarga > descanso > ramp > dias)
        String rationale;
        if (sobrecarga) rationale = "Plano sinaliza sobrecarga ativa.";
        else if (necessitaDescanso) rationale = "Plano sinaliza necessidade de descanso.";
        else if (rampAlto) rationale = "Plano sinaliza rampa de carga elevada.";
        else rationale = "Plano sinaliza dias consecutivos de treino excessivos.";

        // evidencias e sourceRules = TODOS os flags ativos
        List<Evidencia> evidencias = new ArrayList<>();
        List<String> sourceRules = new ArrayList<>();
        sourceRules.add(SOURCE_SOBRECARGA);
        if (sobrecarga) {
            evidencias.add(new Evidencia("Sobrecarga", "sim"));
            sourceRules.add("PlanoMetaDados.alertaSobrecarga");
        }
        if (necessitaDescanso) {
            evidencias.add(new Evidencia("Necessita descanso", "sim"));
            sourceRules.add("PlanoMetaDados.alertaNecessitaDescanso");
        }
        if (rampAlto) {
            evidencias.add(new Evidencia("Rampa de carga alta", "sim"));
            sourceRules.add("PlanoMetaDados.alertaRampAlto");
        }
        if (diasConsecutivos) {
            evidencias.add(new Evidencia("Dias consecutivos de treino",
                    diasConsecutivosTreino != null ? diasConsecutivosTreino.toString() : "sim"));
            sourceRules.add("PlanoMetaDados.alertaDiasConsecutivos");
        }
        return Optional.of(new SinalAtencao(MotivoAtencao.SOBRECARGA, severidade,
                List.copyOf(evidencias), rationale, List.copyOf(sourceRules)));
    }

    /** Aderência via treinos perdidos/parciais na janela. ≥3 → ALTA; 1-2 → MEDIA; 0 → sem sinal. */
    public Optional<SinalAtencao> avaliarAderencia(long perdidosNaJanela) {
        if (perdidosNaJanela <= 0) {
            return Optional.empty();
        }
        Severidade severidade = perdidosNaJanela >= 3 ? Severidade.ALTA : Severidade.MEDIA;
        String rationale = perdidosNaJanela + " treino(s) não cumprido(s) nos últimos 14 dias (PERDIDO ou PARCIAL).";
        return Optional.of(new SinalAtencao(MotivoAtencao.ADERENCIA, severidade,
                List.of(new Evidencia("Treinos não cumpridos (14d)", Long.toString(perdidosNaJanela))),
                rationale,
                List.of(SOURCE_ADERENCIA, "TreinoExecucaoStatus.PERDIDO|PARCIAL")));
    }

    /** Inatividade via dias desde a última atividade. ≥14 → ALTA; 7-13 → MEDIA; <7 ou nulo → sem sinal. */
    public Optional<SinalAtencao> avaliarInatividade(Long diasInativos) {
        if (diasInativos == null) {
            return Optional.empty();
        }
        Severidade severidade;
        if (diasInativos >= 14) {
            severidade = Severidade.ALTA;
        } else if (diasInativos >= 7) {
            severidade = Severidade.MEDIA;
        } else {
            return Optional.empty();
        }
        String rationale = "Sem atividade registrada há " + diasInativos + " dias.";
        return Optional.of(new SinalAtencao(MotivoAtencao.INATIVIDADE, severidade,
                List.of(new Evidencia("Dias sem atividade", Long.toString(diasInativos))),
                rationale,
                List.of(SOURCE_INATIVIDADE)));
    }

    /** Zonas de FC/pace vencidas (3+ meses sem teste) → MEDIA. */
    public Optional<SinalAtencao> avaliarZonasVencidas(boolean precisaAtualizarTestes) {
        if (!precisaAtualizarTestes) {
            return Optional.empty();
        }
        String rationale = "Último teste de FC/pace há mais de 3 meses; zonas de treinamento potencialmente desatualizadas.";
        return Optional.of(new SinalAtencao(MotivoAtencao.ZONAS_VENCIDAS, Severidade.MEDIA,
                List.of(new Evidencia("Zonas", "teste de FC/pace há 3+ meses")),
                rationale,
                List.of(SOURCE_ZONAS_VENCIDAS, "Atleta.precisaAtualizarTestes")));
    }

    /** Atleta sem plano ativo → SEM_PLANO (ALTA), para não desaparecer da fila. */
    public Optional<SinalAtencao> avaliarSemPlano(boolean temPlanoAtivo) {
        if (temPlanoAtivo) {
            return Optional.empty();
        }
        String rationale = "Atleta sem plano ativo; impossível avaliar carga ou progressão.";
        return Optional.of(new SinalAtencao(MotivoAtencao.SEM_PLANO, Severidade.ALTA,
                List.of(new Evidencia("Plano", "sem plano ativo")),
                rationale,
                List.of(SOURCE_SEM_PLANO)));
    }
}
