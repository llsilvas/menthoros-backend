-- =====================================================================
-- V55: indice composto para findAllActiveByPlataforma (plataforma+ativo+auto_sync_pausado)
--
-- Achado do QA gate (intervals-icu-activity-ingestion, 2026-07-16): V54 adicionou
-- auto_sync_pausado sem indice cobrindo a query usada pelo StravaActivitySyncScheduler e pelo
-- webhook Strava a cada ciclo/evento. Composto com tenant_id primeiro, seguindo a convencao do
-- CLAUDE.md para tabelas tenant-scoped.
--
-- Rollback: DROP INDEX IF EXISTS idx_integracao_externa_tenant_plataforma_auto_sync;
-- =====================================================================

CREATE INDEX IF NOT EXISTS idx_integracao_externa_tenant_plataforma_auto_sync
    ON tb_integracao_externa(tenant_id, plataforma, ativo, auto_sync_pausado);

DO $$
BEGIN
    RAISE NOTICE '✅ V55 - indice idx_integracao_externa_tenant_plataforma_auto_sync criado';
END$$;
