package br.com.menthoros.backend.services;


import br.com.menthoros.backend.dto.output.PlanoSemanalOutputDto;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;

import java.util.Optional;
import java.util.UUID;

public interface PlanoService {
    /**
     * Gera e persiste o plano semanal do atleta.
     *
     * <p>Deliberadamente SEM {@code @Transactional} — o Spring honra a anotação também na
     * interface, e a chamada ao LLM precisa ficar fora de qualquer transação
     * (refactor-llm-call-outside-transaction). As transações vivem nos colaboradores de leitura
     * e de escrita; ver {@code PlanoServiceImpl#gerarPlanoTreino}.
     *
     * Idempotent: NÃO — cria o plano.
     * Side Effects: persiste plano e metadados.
     * Tenant-aware: YES.
     */
    PlanoSemanal gerarPlanoTreino(UUID atletaId, ModoGeracaoPlano modoGeracao);

    /**
     * Indica se o atleta já possui um plano ATIVO (review status diferente de
     * REJEITADO) na semana alvo do modo informado — mesma invariante que
     * {@link #gerarPlanoTreino} aplica antes de persistir. Exposto para o
     * fast-path de duplicidade do lote (evita chamar o LLM quando já existe).
     *
     * <p>Encapsula o cálculo da semana (history-dependent para
     * {@code PROXIMA_SEMANA}) + a checagem de existência tenant-scoped, mantendo
     * a regra de negócio em um único ponto.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE.
     * Tenant-aware: YES — usa {@code TenantContext.getRequiredTenantId()}.
     *
     * @param atletaId    ID do atleta
     * @param modoGeracao modo de geração (define a semana alvo)
     * @return true se já existe plano ativo na semana alvo
     */
    boolean existePlanoParaSemana(UUID atletaId, ModoGeracaoPlano modoGeracao);

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
