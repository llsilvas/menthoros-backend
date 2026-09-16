package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.ai.ledger.Violacao;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Monta a mensagem de correção do turno de reparo ({@code plan-generation-repair-turn}) a partir
 * das violações completas que reprovaram a tentativa anterior — lista as chaves e mensagens
 * (nunca só a primeira, ao contrário do truncamento em 300 chars que este passo substitui) e
 * instrui o modelo a corrigir exatamente isso, mantendo o resto do plano inalterado (design.md,
 * Decisão 6: o JSON completo da tentativa anterior já viaja como {@code AssistantMessage}
 * separado; esta mensagem só lista o que está errado).
 *
 * <p>Idempotent: YES — função pura, mesma entrada sempre produz a mesma saída. Side Effects:
 * NONE. Tenant-aware: NO — opera só sobre o texto das violações.</p>
 */
@Component
public class RepairTurnMessageBuilder {

    /**
     * Constrói a mensagem de correção a partir das violações da tentativa anterior.
     *
     * <p>Idempotent: YES. Side Effects: NONE. Tenant-aware: NO.</p>
     *
     * @param violacoes violações completas da tentativa anterior (nunca truncadas); lista vazia
     *                  não deveria ocorrer em uso normal (o turno de reparo só existe porque algo
     *                  falhou), mas não lança — devolve uma instrução genérica.
     * @return texto da mensagem de correção, para enviar como a última {@code UserMessage}
     */
    public String construirCorrecao(List<Violacao> violacoes) {
        if (violacoes == null || violacoes.isEmpty()) {
            return "A tentativa anterior foi rejeitada. Corrija o plano e gere novamente, "
                    + "mantendo o resto do plano inalterado.";
        }

        StringBuilder sb = new StringBuilder(
                "A tentativa anterior foi rejeitada pelas seguintes violações:\n");
        for (Violacao violacao : violacoes) {
            sb.append("- [").append(violacao.key()).append("] ");
            String mensagem = violacao.mensagem();
            sb.append(mensagem == null || mensagem.isBlank() ? "(sem detalhe adicional)" : mensagem);
            sb.append('\n');
        }
        sb.append("\nCorrija exatamente isso, mantendo o resto do plano inalterado.");
        return sb.toString();
    }
}
