package br.com.menthoros.backend.services;


import br.com.menthoros.backend.dto.output.PlanoSemanalOutputDto;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import jakarta.transaction.Transactional;

import java.util.UUID;

public interface PlanoService {
    @Transactional
    PlanoSemanal gerarPlanoTreino(UUID atletaId, ModoGeracaoPlano modoGeracao);

    void deletePlanoSemanal(UUID planoSemanalId);

    /**
     * Busca o plano semanal mais recente do atleta.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES — usa TenantContext internamente
     *
     * @param atletaId      ID do atleta
     * @param apenasAprovados true para ATLETA (só vê APROVADO); false para TECNICO/ADMIN
     */
    PlanoSemanalOutputDto buscarPlanoPorAtleta(UUID atletaId, boolean apenasAprovados);
}
