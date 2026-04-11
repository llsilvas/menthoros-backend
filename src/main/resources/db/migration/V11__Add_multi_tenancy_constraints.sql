-- =============================================================================
-- V11: Multi-tenancy constraints e índice de deduplicação por tenant
--
-- Contexto:
--   V8 criou uk_treino_realizado_external_id como índice GLOBAL (sem tenant),
--   o que impede que dois tenants diferentes sincronizem atividades com o mesmo
--   external_id (ex: IDs da Strava são únicos por plataforma, não por tenant).
--   Este script corrige o escopo do índice para (tenant_id, fonte_dados, external_id).
-- =============================================================================

-- 1. Remover índice global de external_id criado em V8
--    IF EXISTS garante idempotência caso o índice não exista no ambiente
DROP INDEX IF EXISTS uk_treino_realizado_external_id;

-- 2. Criar índice único composto por tenant
--    Filtro parcial: apenas registros com fonte_dados e external_id preenchidos
--    (treinos manuais não têm external_id e não devem ser afetados)
CREATE UNIQUE INDEX IF NOT EXISTS uk_treino_realizado_tenant_fonte_external
    ON tb_treino_realizado (tenant_id, fonte_dados, external_id)
    WHERE fonte_dados IS NOT NULL AND external_id IS NOT NULL;

-- Diagnóstico pré-aplicação (executar manualmente antes se houver dúvida sobre duplicatas):
-- SELECT tenant_id, fonte_dados, external_id, COUNT(*)
-- FROM tb_treino_realizado
-- WHERE fonte_dados IS NOT NULL AND external_id IS NOT NULL
-- GROUP BY tenant_id, fonte_dados, external_id
-- HAVING COUNT(*) > 1;
