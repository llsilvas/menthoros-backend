package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.TreinoPlanejadoAddDto;
import br.com.menthoros.backend.dto.output.TreinoPlanejadoOutputDto;

import java.util.UUID;

public interface TreinoPlanejadoAddService {

    /**
     * Adiciona um treino manualmente ao plano durante a revisão do coach.
     *
     * Idempotent: NO — cria nova entidade a cada chamada.
     * Side Effects: Database insert (TreinoPlanejado + EtapaTreino via cascade)
     * Tenant-aware: YES
     *
     * @param planoId UUID do plano semanal
     * @param dto     dados do novo treino
     * @return DTO do treino criado
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException      se o plano não for encontrado no tenant
     * @throws br.com.menthoros.backend.exception.DomainRuleViolationException se o plano não estiver em AGUARDANDO_REVISAO,
     *                                                                          a data estiver fora do intervalo ou o limite de 14 treinos for atingido
     */
    TreinoPlanejadoOutputDto adicionarTreino(UUID planoId, TreinoPlanejadoAddDto dto);
}
