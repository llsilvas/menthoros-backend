package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.TreinoPlanejadoAddDto;
import br.com.menthoros.backend.dto.input.TreinoPlanejadoPatchDto;
import br.com.menthoros.backend.dto.output.TreinoPlanejadoOutputDto;

import java.util.UUID;

public interface TreinoPlanejadoService {

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
