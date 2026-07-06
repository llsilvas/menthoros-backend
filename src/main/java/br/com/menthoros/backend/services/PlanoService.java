package br.com.menthoros.backend.services;


import br.com.menthoros.backend.dto.output.PlanoSemanalOutputDto;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface PlanoService {
    @Transactional
    PlanoSemanal gerarPlanoTreino(UUID atletaId, ModoGeracaoPlano modoGeracao);

    /**
     * Calcula a semana-início alvo que {@link #gerarPlanoTreino} usaria para o
     * atleta/modo — mesma lógica interna, exposta para o fast-path de
     * duplicidade do lote (evita chamar o LLM quando o plano já existe).
     *
     * <p>Para {@code PROXIMA_SEMANA} é history-dependent (encadeia a partir do
     * último plano do atleta); por isso não pode ser replicada fora do serviço.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE.
     * Tenant-aware: YES.
     *
     * @param atletaId    ID do atleta
     * @param modoGeracao modo de geração (define a semana alvo)
     * @return a segunda-feira (ISO) de início da semana alvo
     */
    LocalDate calcularSemanaInicioAlvo(UUID atletaId, ModoGeracaoPlano modoGeracao);

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

    /**
     * Busca o plano mais recente do atleta cuja semana ainda não encerrou.
     * Carrega treinos planejados com JOIN FETCH para evitar LazyInitializationException.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE.
     * Tenant-aware: YES.
     *
     * @param atletaId  ID do atleta
     * @param tenantId  ID do tenant (assessoria)
     * @return Optional com o plano vigente ou empty se não houver
     */
    Optional<PlanoSemanal> findPlanoVigenteRelevante(UUID atletaId, UUID tenantId);
}
