package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.WaitlistDocsNotificationResultDto;

/** Aviso em massa, por e-mail, aos treinadores da waitlist de que a central de ajuda está no ar. */
public interface WaitlistDocsNotificationService {

    /**
     * Envia o aviso a todo inscrito TREINADOR ainda não avisado. Idempotente entre chamadas e
     * seguro sob concorrência: cada inscrito é reivindicado atomicamente antes do envio, e a
     * falha de um não interrompe os demais.
     */
    WaitlistDocsNotificationResultDto notificar();
}
