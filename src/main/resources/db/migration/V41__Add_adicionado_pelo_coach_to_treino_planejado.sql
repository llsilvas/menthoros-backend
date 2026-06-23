-- =====================================================================
-- V41: Adiciona rastreabilidade de treino adicionado manualmente pelo coach
-- =====================================================================

ALTER TABLE tb_treino_planejado
    ADD COLUMN IF NOT EXISTS adicionado_pelo_coach BOOLEAN NOT NULL DEFAULT FALSE;

DO $$
BEGIN
    RAISE NOTICE '✅ V41 - adicionado_pelo_coach adicionado a tb_treino_planejado';
END$$;
