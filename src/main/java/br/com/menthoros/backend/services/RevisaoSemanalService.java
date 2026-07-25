package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.RevisaoSemanalOutputDto;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.RevisaoSemanal;

import java.util.UUID;

/**
 * Consolidação determinística da revisão semanal (Fatia 1 — add-weekly-review-consolidation).
 */
public interface RevisaoSemanalService {

    /**
     * Consolida — <b>sem persistir</b> — o sinal determinístico da revisão a partir de um
     * {@code PlanoSemanal} encerrado: aderência por contagem na janela do plano,
     * {@code sufficientData} e {@code recommendationType} sobre {@code tsbFim} (ADR-0006).
     *
     * <p><b>Idempotent:</b> YES — função pura sobre o plano + treinos da janela.
     * <b>Side Effects:</b> NONE (não persiste).
     * <b>Tenant-aware:</b> YES — busca de treinos escopada pelo tenant do próprio plano.
     * <b>Pré-condição:</b> deve rodar dentro de uma transação — acessa relações lazy do plano.
     */
    RevisaoSemanal consolidar(PlanoSemanal plano);

    /**
     * Gera e <b>congela</b> a revisão de um plano recém-encerrado (hook do
     * {@code SemanaEncerradaEvent}).
     *
     * <p><b>Idempotent:</b> YES — insert-if-absent: se o plano não está {@code CONCLUIDO}
     * (evento de perdidos-only) ou já tem revisão, é no-op (preserva o congelamento).
     * <b>Side Effects:</b> Database insert (uma {@code RevisaoSemanal} quando ausente).
     * <b>Tenant-aware:</b> YES — carrega o plano por {@code (id, tenantId)} e consulta a revisão
     * existente tenant-scoped (CA6, CA7).
     */
    void gerarNoEncerramento(UUID planoId, UUID tenantId);

    /**
     * Última revisão do atleta, com {@code weekOverWeekDelta} computado. Devolve o sinal
     * <b>persistido</b> — não recomputa (CA-Congelamento). Coach-only na camada de controller.
     *
     * <p><b>Idempotent:</b> YES — leitura pura.
     * <b>Side Effects:</b> NONE.
     * <b>Tenant-aware:</b> YES — resolve o tenant via {@code TenantContext.getRequiredTenantId()}
     * e filtra a query por ele.
     *
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o atleta não tem
     *         revisão (nenhuma semana fechada) — mapeado para 404.
     */
    RevisaoSemanalOutputDto buscarUltima(UUID atletaId);
}
