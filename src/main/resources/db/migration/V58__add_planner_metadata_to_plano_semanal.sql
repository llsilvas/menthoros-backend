-- =====================================================================
-- V58: adiciona colunas de auditoria do PlannerEngine a tb_plano_semanal
--
-- Suporte ao shadow mode de deterministic-planner-engine: o motor deterministico
-- roda apos a geracao legada e persiste um snapshot de auditoria, sem alterar
-- prompt/plano/persistencia do treino (CA12). Com planner_enabled=false (shadow
-- desligado, default), estas colunas ficam null.
--
-- planner_metadata_json guarda resumo compacto do skeleton (fase, review,
-- compliance, contagem de violacoes) -- sem prompt nem dado sensivel.
-- planner_skeleton_hash correlaciona logs sem repetir o payload grande.
--
-- Rollback: ALTER TABLE tb_plano_semanal DROP COLUMN IF EXISTS planner_enabled,
--   DROP COLUMN IF EXISTS planner_version, DROP COLUMN IF EXISTS planner_phase,
--   DROP COLUMN IF EXISTS planner_requires_coach_review,
--   DROP COLUMN IF EXISTS planner_skeleton_hash,
--   DROP COLUMN IF EXISTS planner_compliance_status,
--   DROP COLUMN IF EXISTS planner_metadata_json;
-- Feature aditiva pura (colunas nullable) -- sem impacto em dado existente;
-- reversao segura mesmo com o shadow desligado (design.md "Rollback / Riscos").
-- =====================================================================

ALTER TABLE tb_plano_semanal
    ADD COLUMN IF NOT EXISTS planner_enabled BOOLEAN,
    ADD COLUMN IF NOT EXISTS planner_version VARCHAR(20),
    ADD COLUMN IF NOT EXISTS planner_phase VARCHAR(30),
    ADD COLUMN IF NOT EXISTS planner_requires_coach_review BOOLEAN,
    ADD COLUMN IF NOT EXISTS planner_skeleton_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS planner_compliance_status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS planner_metadata_json JSONB;

DO $$
BEGIN
    RAISE NOTICE '✅ V58 - colunas de auditoria do PlannerEngine adicionadas a tb_plano_semanal';
END$$;
