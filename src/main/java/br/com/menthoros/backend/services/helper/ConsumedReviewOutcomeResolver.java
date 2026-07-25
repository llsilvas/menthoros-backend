package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.ConsumedReviewOutcome;
import br.com.menthoros.backend.enums.OrigemAprovacao;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Infere o desfecho da revisão consumida a partir de sinais que já existem no domínio (D9) —
 * sem exigir que o treinador declare nada, porque não há superfície de UI para isso.
 *
 * <p><b>É um proxy de segunda ordem:</b> mede se algum treino do plano foi mexido, o que capta
 * tanto discordância do foco quanto ajuste por logística (o atleta viajou e o coach trocou o
 * treino de terça, concordando com o foco). Serve como taxa agregada de adoção; não deve ser lido
 * como julgamento individual do coach sobre a proposta.
 */
@Component
public class ConsumedReviewOutcomeResolver {

    /**
     * Idempotent: YES — função pura sobre o estado do plano.
     * Side Effects: NONE
     * Tenant-aware: N/A — opera sobre um plano já resolvido no tenant.
     *
     * @param origem quem aprovou; {@code AUTO_CONFIANCA_ALTA} nunca conta como aceitação, porque
     *               nesse caminho não existe coach para julgar o foco — contar inflaria o sinal
     *               com planos que ninguém revisou.
     */
    public ConsumedReviewOutcome naAprovacao(PlanoSemanal plano, OrigemAprovacao origem) {
        if (!consumiuRevisao(plano)) {
            return ConsumedReviewOutcome.NOT_CONSUMED;
        }
        if (origem == OrigemAprovacao.AUTO_CONFIANCA_ALTA) {
            return ConsumedReviewOutcome.NO_COACH_IN_LOOP;
        }
        return houveAjusteDoCoach(plano)
                ? ConsumedReviewOutcome.ADJUSTED
                : ConsumedReviewOutcome.NO_ADJUSTMENT;
    }

    /**
     * Idempotent: YES — função pura sobre o estado do plano.
     * Side Effects: NONE
     * Tenant-aware: N/A
     */
    public ConsumedReviewOutcome naRejeicao(PlanoSemanal plano) {
        return consumiuRevisao(plano)
                ? ConsumedReviewOutcome.PLAN_REJECTED
                : ConsumedReviewOutcome.NOT_CONSUMED;
    }

    private boolean consumiuRevisao(PlanoSemanal plano) {
        return plano != null && plano.getConsumedReview() != null;
    }

    private boolean houveAjusteDoCoach(PlanoSemanal plano) {
        List<TreinoPlanejado> treinos = plano.getTreinosPlanejados();
        if (treinos == null || treinos.isEmpty()) {
            return false; // sem treinos carregados não se inventa ajuste
        }
        return treinos.stream().anyMatch(t -> t.isEditadoPeloCoach() || t.isAdicionadoPeloCoach());
    }
}
