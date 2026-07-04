package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.KudosInputDto;
import br.com.menthoros.backend.dto.output.KudosOutputDto;
import br.com.menthoros.backend.dto.output.KudosRecenteOutputDto;

import java.util.List;
import java.util.UUID;

public interface KudosService {

    /**
     * Registra um kudo (reconhecimento) do coach autenticado para um atleta do seu tenant.
     *
     * Idempotent: NO — cria um novo registro por combinação distinta de atleta/motivo/dia;
     * repetir a mesma combinação (mesmo atleta, mesmo coach, mesmo motivo, mesmo dia) lança
     * {@link br.com.menthoros.backend.exception.DuplicateResourceException} (409) em vez de
     * criar duplicata ou retornar silenciosamente.
     * Side Effects: Database insert (1 registro em tb_kudos).
     * Tenant-aware: YES.
     *
     * @param atletaId atleta reconhecido (já validado como pertencente ao tenant pelo
     *                 {@code @RequireTenant} do controller)
     * @param input    motivo do reconhecimento
     * @return kudo criado
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o atleta ou o
     *         coach autenticado não forem encontrados no tenant
     * @throws br.com.menthoros.backend.exception.DuplicateResourceException se já existe um
     *         kudo do mesmo motivo, do mesmo coach, para o mesmo atleta, no mesmo dia
     */
    KudosOutputDto registrar(UUID atletaId, KudosInputDto input);

    /**
     * Idempotent: YES — leitura. Side Effects: NONE. Tenant-aware: YES.
     *
     * @param atletaId atleta cujos kudos serão listados
     * @return até os 10 kudos mais recentes do atleta, mais recente primeiro; lista vazia se
     *         não houver nenhum
     */
    List<KudosRecenteOutputDto> listarRecentes(UUID atletaId);
}
