package br.com.menthoros.backend.exception;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Já existe um plano ativo (review status diferente de REJEITADO) para o atleta
 * na semana alvo. Subtipo de {@link DomainRuleViolationException} para permitir
 * distinguir duplicidade de outras violações de regra (ex.: "sem dias
 * disponíveis") sem depender do texto da mensagem.
 *
 * <p>A duplicidade é barrada em três camadas (refactor-llm-call-outside-transaction, D3):
 * checagem cedo antes do LLM, re-checagem na transação de escrita e, como autoridade final, o
 * índice parcial {@link #INDICE_PLANO_ATIVO} da V52. As fábricas abaixo mantêm a mensagem única
 * entre as três.
 */
public class PlanoJaExistenteException extends DomainRuleViolationException {

    /** Índice único parcial da V52 sobre (atleta_id, semana_inicio) WHERE review_status <> 'REJEITADO'. */
    public static final String INDICE_PLANO_ATIVO = "uk_plano_semanal_atleta_semana_ativo";

    public PlanoJaExistenteException(String message) {
        super(message);
    }

    public static PlanoJaExistenteException paraSemana(UUID atletaId, LocalDate semanaInicio) {
        return new PlanoJaExistenteException(
                "Já existe um plano semanal ativo para o atleta " + atletaId +
                        " iniciando em " + semanaInicio + ". Não é possível gerar planos duplicados.");
    }

    /** Duas gerações passaram pelas checagens e commitaram juntas: o índice decidiu. */
    public static PlanoJaExistenteException paraCorridaNoIndice(UUID atletaId) {
        return new PlanoJaExistenteException(
                "Já existe um plano semanal ativo para o atleta " + atletaId +
                        " nesta semana (gerado simultaneamente). Não é possível gerar planos duplicados.");
    }

    /**
     * Só a violação do índice de plano ativo deve virar esta exceção; qualquer outra constraint
     * segue como conflito genérico, sem ser mascarada. Percorre a cadeia de causas porque o nome
     * da constraint vem na mensagem do driver, embrulhada pelo Hibernate e pelo Spring.
     */
    public static boolean causadaPeloIndiceDePlanoAtivo(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && msg.contains(INDICE_PLANO_ATIVO)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
