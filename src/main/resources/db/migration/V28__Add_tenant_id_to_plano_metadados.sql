-- =====================================================================
-- V28: Adiciona tenant_id (FK para tb_assessoria) em tb_plano_metadados
--      para garantir isolamento multi-tenant dos metadados de plano
-- =====================================================================

ALTER TABLE tb_plano_metadados
    ADD COLUMN IF NOT EXISTS tenant_id UUID;

CREATE INDEX IF NOT EXISTS idx_plano_metadados_tenant_id
    ON tb_plano_metadados(tenant_id);

CREATE INDEX IF NOT EXISTS idx_plano_metadados_atleta_tenant
    ON tb_plano_metadados(atleta_id, tenant_id);

DO $$
BEGIN
    RAISE NOTICE '✅ V28 - tenant_id adicionado em tb_plano_metadados com sucesso';
END$$;
