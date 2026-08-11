package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.CoachSignupInputDto;
import br.com.menthoros.backend.dto.output.CoachSignupOutputDto;

public interface CoachSignupService {

    /**
     * Provisiona assessoria, organização, usuário no Keycloak e usuário local.
     *
     * @param idempotencyKey chave enviada pelo cliente; amarra o reenvio ao resultado original
     * @param correlationId  fio que liga o rastro ao log da requisição
     */
    CoachSignupOutputDto cadastrar(CoachSignupInputDto input, String idempotencyKey, String correlationId);
}
