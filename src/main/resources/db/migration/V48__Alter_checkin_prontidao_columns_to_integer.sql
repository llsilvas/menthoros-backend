-- =====================================================================
-- V48: Alinha o tipo das colunas numéricas de tb_checkin_prontidao com o
-- padrão do projeto (INTEGER, não SMALLINT — ver demais migrations com
-- campos Integer, ex.: V3, V40, V42, V44). A V46 já foi aplicada em SMALLINT;
-- corrige aqui em vez de editar a migration já aplicada.
--
-- Rollback (se necessário):
--   ALTER TABLE tb_checkin_prontidao
--       ALTER COLUMN qualidade_sono TYPE SMALLINT,
--       ALTER COLUMN humor TYPE SMALLINT,
--       ALTER COLUMN dores_musculares TYPE SMALLINT,
--       ALTER COLUMN nivel_energia TYPE SMALLINT,
--       ALTER COLUMN estresse TYPE SMALLINT;
-- =====================================================================

ALTER TABLE tb_checkin_prontidao
    ALTER COLUMN qualidade_sono TYPE INTEGER,
    ALTER COLUMN humor TYPE INTEGER,
    ALTER COLUMN dores_musculares TYPE INTEGER,
    ALTER COLUMN nivel_energia TYPE INTEGER,
    ALTER COLUMN estresse TYPE INTEGER;

DO $$
BEGIN
    RAISE NOTICE '✅ V48 - colunas numericas de tb_checkin_prontidao alteradas para INTEGER';
END$$;
