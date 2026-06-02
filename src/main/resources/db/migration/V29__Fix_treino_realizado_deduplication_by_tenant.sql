-- =====================================================================
-- V29: Corrige deduplicação de tb_treino_realizado para escopo por tenant
--
-- Problema: uk_treino_realizado_external_id (V8) é global — sem tenant_id.
-- Um mesmo external_id de fontes diferentes (Strava de dois tenants distintos)
-- causaria violação de constraint, impedindo sincronização legítima.
--
-- Solução: substituir pelo índice composto (tenant_id, fonte_dados, external_id)
-- com filtro parcial (apenas quando ambos NOT NULL).
-- =====================================================================

-- 1. Remover índice global sem escopo de tenant
DROP INDEX IF EXISTS uk_treino_realizado_external_id;

-- 2. Criar índice único composto com escopo por tenant
--    Filtro parcial garante que registros manuais (fonte_dados IS NULL ou external_id IS NULL)
--    não participem da constraint de unicidade.
CREATE UNIQUE INDEX IF NOT EXISTS uk_treino_realizado_tenant_fonte_external
    ON tb_treino_realizado(tenant_id, fonte_dados, external_id)
    WHERE fonte_dados IS NOT NULL AND external_id IS NOT NULL;

-- 3. Índice de apoio para queries de deduplicação no serviço (lookup rápido por atleta)
CREATE INDEX IF NOT EXISTS idx_treino_realizado_tenant_atleta_external
    ON tb_treino_realizado(tenant_id, atleta_id, external_id)
    WHERE external_id IS NOT NULL;

DO $$
BEGIN
    RAISE NOTICE '✅ V29 - Deduplicação de tb_treino_realizado corrigida para escopo por tenant';
    RAISE NOTICE '   Removido: uk_treino_realizado_external_id (global, sem tenant_id)';
    RAISE NOTICE '   Criado: uk_treino_realizado_tenant_fonte_external (tenant_id + fonte_dados + external_id)';
END$$;
