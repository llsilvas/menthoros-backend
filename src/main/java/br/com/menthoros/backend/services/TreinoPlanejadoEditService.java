package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.TreinoPlanejadoPatchDto;
import br.com.menthoros.backend.dto.output.TreinoPlanejadoOutputDto;

import java.util.UUID;

public interface TreinoPlanejadoEditService {

    /**
     * Edita campos prescritos de um TreinoPlanejado dentro de um plano AGUARDANDO_REVISAO.
     * Aplica patch semântico: apenas os campos não-nulos do DTO são alterados.
     *
     * Idempotent: NO — altera o estado do treino a cada chamada.
     * Side Effects: Database update (TreinoPlanejado)
     * Tenant-aware: YES
     *
     * @param planoId  UUID do PlanoSemanal que contém o treino
     * @param treinoId UUID do TreinoPlanejado a editar
     * @param patch    campos a sobrescrever; campos null são ignorados
     * @return DTO atualizado do treino
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException     se plano ou treino não pertencer ao tenant
     * @throws br.com.menthoros.backend.exception.DomainRuleViolationException se plano não estiver em AGUARDANDO_REVISAO
     */
    TreinoPlanejadoOutputDto editarTreino(UUID planoId, UUID treinoId, TreinoPlanejadoPatchDto patch);
}
