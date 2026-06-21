-- =====================================================================
-- V39: Adiciona editado_pelo_coach e versao a tb_treino_planejado
-- =====================================================================

ALTER TABLE tb_treino_planejado
    ADD COLUMN IF NOT EXISTS editado_pelo_coach BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE tb_treino_planejado
    ADD COLUMN IF NOT EXISTS versao BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
    RAISE NOTICE '✅ V39 - colunas editado_pelo_coach e versao adicionadas a tb_treino_planejado';
END$$;
