package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.BatchPlanJob;
import br.com.menthoros.backend.enums.BatchJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BatchPlanJobRepository extends JpaRepository<BatchPlanJob, UUID> {

    /**
     * Busca o job garantindo o isolamento de tenant (usado no GET de status).
     */
    Optional<BatchPlanJob> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Jobs órfãos: presos em estado não-terminal (PENDENTE/EM_PROGRESSO) e criados
     * antes do limiar — candidatos ao recovery no startup. Não é tenant-scoped:
     * é uma tarefa de manutenção do sistema, sobre todos os tenants.
     */
    List<BatchPlanJob> findByStatusInAndCriadoEmBefore(List<BatchJobStatus> statuses, Instant limite);

    /**
     * Incremento atômico do contador de gerados — UPDATE direto no banco, sem
     * ler/reatribuir em memória. Evita race condition e perda de incremento
     * entre virtual threads concorrentes escrevendo no mesmo job. Transação
     * própria (curta): torna o progresso visível ao polling imediatamente.
     */
    @Transactional
    @Modifying
    @Query("UPDATE BatchPlanJob b SET b.gerados = b.gerados + 1 WHERE b.id = :id")
    void incrementarGerados(@Param("id") UUID id);

    /**
     * Incremento atômico do contador de erros — mesma garantia de
     * {@link #incrementarGerados(UUID)}.
     */
    @Transactional
    @Modifying
    @Query("UPDATE BatchPlanJob b SET b.erros = b.erros + 1 WHERE b.id = :id")
    void incrementarErros(@Param("id") UUID id);

    /**
     * Atualiza apenas o status do job (transição para EM_PROGRESSO). Colunas
     * simples — UPDATE atômico, sem carregar a entidade.
     */
    @Transactional
    @Modifying
    @Query("UPDATE BatchPlanJob b SET b.status = :status WHERE b.id = :id")
    void atualizarStatus(@Param("id") UUID id, @Param("status") BatchJobStatus status);
}
