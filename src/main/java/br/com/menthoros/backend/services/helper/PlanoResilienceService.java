package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.LLMException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Orquestra a geração resiliente de plano: gera → valida (reparo já aplicado a montante) →, se a
 * validação ainda falhar com {@link LLMException} estrutural, re-chama o LLM <b>uma única vez</b>
 * injetando o motivo da rejeição; esgotado, falha com {@link DomainRuleViolationException}
 * (mensagem orientada ao treinador, mapeada no GlobalExceptionHandler — não 503).
 *
 * <p>Teto = 1 retry (cada tentativa ~80s; 2 retries seriam ~4min, inaceitável em fluxo síncrono).
 * Falhas de <i>geração</i> (LLM/infra) propagam — só falhas de <i>validação</i> disparam retry.</p>
 *
 * <p>Idempotent: NO — re-chama o LLM (saída não-determinística), mas sem escrita de estado por tentativa
 * (persistência acontece a jusante, uma vez). Side Effects: chamada ao LLM + contadores Micrometer.
 * Tenant-aware: NO — recebe as funções já vinculadas ao atleta/tenant pelo chamador.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanoResilienceService {

    /** 1 geração + 1 retry. */
    static final int MAX_TENTATIVAS = 2;

    private final MeterRegistry meterRegistry;

    /**
     * @param gerar    prompt → plano (chama o LLM); falhas de geração propagam (não são retry)
     * @param validar  plano → plano validado/normalizado; lança {@link LLMException} em violação estrutural residual
     * @param promptBase prompt inicial; no retry é acrescido do motivo da rejeição anterior
     */
    public PlanoSemanalLlmDto gerarComResiliencia(Function<String, PlanoSemanalLlmDto> gerar,
                                                  Function<PlanoSemanalLlmDto, PlanoSemanalLlmDto> validar,
                                                  String promptBase) {
        Counter.builder("plano_geracao_total").register(meterRegistry).increment(); // denominador da taxa de sucesso
        String prompt = promptBase;
        LLMException ultimaFalha = null;

        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            PlanoSemanalLlmDto plano = gerar.apply(prompt); // falha de geração propaga (infra → 503)
            try {
                return validar.apply(plano);
            } catch (LLMException e) {
                ultimaFalha = e;
                if (tentativa >= MAX_TENTATIVAS) break;
                Counter.builder("plano_retry").tag("motivo", "estrutural").register(meterRegistry).increment();
                String motivo = truncar(e.getMessage());
                log.warn("Plano rejeitado na tentativa {} ({}); re-gerando 1x com feedback", tentativa, motivo);
                prompt = promptBase
                        + "\n\n## CORRECAO OBRIGATORIA (a tentativa anterior foi rejeitada)\n"
                        + "Motivo: " + motivo + "\n"
                        + "Gere o plano novamente corrigindo exatamente esse ponto, mantendo as demais regras.";
            }
        }

        Counter.builder("plano_geracao_falha_final").register(meterRegistry).increment();
        log.error("Geração de plano falhou após reparo + {} tentativa(s): {}",
                MAX_TENTATIVAS, ultimaFalha != null ? truncar(ultimaFalha.getMessage()) : "desconhecido");
        throw new DomainRuleViolationException(
                "Não foi possível gerar o plano desta semana. Tente novamente ou ajuste os parâmetros do atleta.");
    }

    /** Teto defensivo: a mensagem da rejeição vai para o prompt do LLM e para o log — evita reinjetar
     *  payload extenso (ou eventual conteúdo de erro de SDK) por inteiro. */
    private static final int MAX_MOTIVO_CHARS = 300;

    private static String truncar(String motivo) {
        if (motivo == null) return "motivo desconhecido";
        return motivo.length() <= MAX_MOTIVO_CHARS ? motivo : motivo.substring(0, MAX_MOTIVO_CHARS) + "…";
    }
}
