package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.FeedbackTreinoInputDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;

import java.util.UUID;

/** "Como foi?" — feedback do atleta sobre um treino realizado, de qualquer origem. */
public interface AtletaTreinoFeedbackService {

    /**
     * Grava RPE, sensações e comentário e carimba {@code feedbackRegistradoEm}. Um segundo envio
     * substitui tudo (último vence, idempotente).
     *
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException realizado inexistente,
     *                                                                     fora do tenant ou de outro atleta
     */
    TreinoRealizadoOutputDto registrarFeedback(UUID atletaId, UUID treinoRealizadoId, FeedbackTreinoInputDto input);
}
