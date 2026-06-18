package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.CategoriaIntervalado;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.services.prompt.constraint.Constraint;

import java.util.Optional;

/**
 * Resultado da avaliação determinística de elegibilidade para treino INTERVALADO.
 *
 * <p>Produzido por {@link IntervaladoElegibilidadeService#avaliar} após passar pelos
 * 5 portões fisiológicos. O campo {@code instrucaoParaLlm} é o texto formatado que será
 * injetado diretamente no prompt do Claude como instrução mandatória.</p>
 *
 * <ul>
 *   <li>{@link Elegivel}    — atleta apto; categoria e instrução definidas.</li>
 *   <li>{@link Degradado}   — apto para versão segura de menor intensidade.</li>
 *   <li>{@link Substituido} — não pode fazer nenhuma forma de intervalado esta semana.</li>
 * </ul>
 */
public sealed interface RecomendacaoIntervalado
        permits RecomendacaoIntervalado.Elegivel,
                RecomendacaoIntervalado.Degradado,
                RecomendacaoIntervalado.Substituido {

    /**
     * Converte a decisão na {@link Constraint} declarativa equivalente.
     * {@code Elegivel} é mera permissão → emite zero constraints ({@code Optional.empty()}).
     */
    Optional<Constraint> toConstraint();

    /**
     * Atleta passou todos os portões. O LLM pode incluir INTERVALADO na categoria indicada.
     *
     * @param categoria       categoria fisiologicamente adequada para esta semana (A–E)
     * @param motivo          justificativa baseada em fase de periodização e histórico
     * @param instrucaoParaLlm texto completo inserido no prompt como diretriz mandatória
     */
    record Elegivel(
            CategoriaIntervalado categoria,
            String motivo,
            String instrucaoParaLlm
    ) implements RecomendacaoIntervalado {
        @Override
        public Optional<Constraint> toConstraint() {
            return Optional.empty(); // permissão — não restringe
        }
    }

    /**
     * Atleta não está pronto para o intervalo ideal, mas pode fazer uma categoria segura.
     * Geralmente resultado de TSB abaixo do limiar do nível ou RPE médio elevado.
     *
     * @param categoriaSegura  categoria degradada (normalmente C ou D)
     * @param motivo           razão fisiológica (ex: "TSB=-18 abaixo do limiar para INTERMEDIARIO")
     * @param instrucaoParaLlm texto inserido no prompt
     */
    record Degradado(
            CategoriaIntervalado categoriaSegura,
            String motivo,
            String instrucaoParaLlm
    ) implements RecomendacaoIntervalado {
        @Override
        public Optional<Constraint> toConstraint() {
            String descricao = "Intensidade permitida APENAS na versão segura (categoria "
                    + categoriaSegura.name() + "). " + instrucaoParaLlm;
            return Optional.of(Constraint.intervaladoMaxCategoria(descricao, categoriaSegura));
        }
    }

    /**
     * Atleta não pode realizar nenhuma forma de intervalado.
     * O tipo de treino de fallback deve substituir completamente qualquer intensidade.
     *
     * @param tipoFallback     tipo substituto obrigatório — {@code REGENERATIVO} ou {@code CONTINUO}
     * @param motivo           razão absoluta (ex: "Lesão ativa: tendinite no joelho")
     * @param instrucaoParaLlm texto inserido no prompt com instrução de bloqueio
     */
    record Substituido(
            TipoTreino tipoFallback,
            String motivo,
            String instrucaoParaLlm
    ) implements RecomendacaoIntervalado {
        @Override
        public Optional<Constraint> toConstraint() {
            String descricao = "PROIBIDO qualquer treino intensivo esta semana. Tipo substituto obrigatório: "
                    + tipoFallback.getLabel() + ". " + instrucaoParaLlm;
            return Optional.of(Constraint.intervaladoProibido(descricao));
        }
    }
}
