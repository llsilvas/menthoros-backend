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
            case INFO -> null;
        };
        if (severidade == null) {
            return Optional.empty();
        }
        String valor = String.format(Locale.US, "%.1f (%s)", tsb, faixa.getInterpretacao());
        return Optional.of(new SinalAtencao(MotivoAtencao.FADIGA, severidade,
                List.of(new Evidencia("TSB", valor))));
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
        List<Evidencia> evidencias = new ArrayList<>();
        if (sobrecarga) evidencias.add(new Evidencia("Sobrecarga", "sim"));
        if (necessitaDescanso) evidencias.add(new Evidencia("Necessita descanso", "sim"));
        if (rampAlto) evidencias.add(new Evidencia("Rampa de carga alta", "sim"));
        if (diasConsecutivos) {
            evidencias.add(new Evidencia("Dias consecutivos de treino",
                    diasConsecutivosTreino != null ? diasConsecutivosTreino.toString() : "sim"));
        }
        return Optional.of(new SinalAtencao(MotivoAtencao.SOBRECARGA, severidade, List.copyOf(evidencias)));
    }

    /** Aderência via treinos perdidos/parciais na janela. ≥3 → ALTA; 1-2 → MEDIA; 0 → sem sinal. */
    public Optional<SinalAtencao> avaliarAderencia(long perdidosNaJanela) {
        if (perdidosNaJanela <= 0) {
            return Optional.empty();
        }
        Severidade severidade = perdidosNaJanela >= 3 ? Severidade.ALTA : Severidade.MEDIA;
        return Optional.of(new SinalAtencao(MotivoAtencao.ADERENCIA, severidade,
                List.of(new Evidencia("Treinos não cumpridos (14d)", Long.toString(perdidosNaJanela)))));
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
        return Optional.of(new SinalAtencao(MotivoAtencao.INATIVIDADE, severidade,
                List.of(new Evidencia("Dias sem atividade", Long.toString(diasInativos)))));
    }

    /** Zonas de FC/pace vencidas (3+ meses sem teste) → MEDIA. */
    public Optional<SinalAtencao> avaliarZonasVencidas(boolean precisaAtualizarTestes) {
        if (!precisaAtualizarTestes) {
            return Optional.empty();
        }
        return Optional.of(new SinalAtencao(MotivoAtencao.ZONAS_VENCIDAS, Severidade.MEDIA,
                List.of(new Evidencia("Zonas", "teste de FC/pace há 3+ meses"))));
    }

    /** Atleta sem plano ativo → SEM_PLANO (ALTA), para não desaparecer da fila. */
    public Optional<SinalAtencao> avaliarSemPlano(boolean temPlanoAtivo) {
        if (temPlanoAtivo) {
            return Optional.empty();
        }
        return Optional.of(new SinalAtencao(MotivoAtencao.SEM_PLANO, Severidade.ALTA,
                List.of(new Evidencia("Plano", "sem plano ativo"))));
    }
}
