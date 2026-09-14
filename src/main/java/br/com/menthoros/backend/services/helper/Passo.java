package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;

/**
 * Um passo de uma receita de normalização (ver {@code CONTEXT.md}): nome estável, usado no golden
 * da ordem e no log do runner, e a função que o executa. Um tipo só (decisão Q9): um passo de
 * validação devolve o treino intacto e lança {@code LLMException}; um passo de transformação
 * devolve um record novo.
 *
 * <p>A função é aninhada como {@code Passo.Fn}, não {@code Etapa}, porque "etapa" já é a unidade de
 * um treino planejado no domínio ({@code EtapaTreinoLlmDto}) — o nome do design foi trocado pra não
 * colidir.</p>
 */
public record Passo(String nome, Fn fn) {

    @FunctionalInterface
    public interface Fn {
        TreinoPlanejadoLlmDto aplicar(TreinoPlanejadoLlmDto treino, ContextoNormalizacao ctx);
    }
}
