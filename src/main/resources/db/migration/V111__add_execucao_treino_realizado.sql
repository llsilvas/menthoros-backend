-- =====================================================================
-- V111: Dados de execução do treino realizado (coach-ver-treino-realizado, D1)
--
-- Aditiva, só colunas nulas. Treinos já importados ficam com NULL: o .fit original não é
-- guardado, então não há backfill.
-- tempo_em_zonas: {"fonte":"FIT_RECORDS","zonas":[{"zona":"Z1","bpmMin":93,"bpmMax":111,"segundos":4}, ...]}
-- com os limites vigentes no import, para o histórico não mudar quando as zonas do atleta mudam.
--
-- Rollback: ALTER TABLE tb_treino_realizado DROP COLUMN IF EXISTS tempo_em_zonas,
--   DROP COLUMN IF EXISTS temperatura_max_c, DROP COLUMN IF EXISTS temperatura_min_c,
--   DROP COLUMN IF EXISTS velocidade_max_kmh;
-- =====================================================================

ALTER TABLE tb_treino_realizado
    ADD COLUMN IF NOT EXISTS velocidade_max_kmh NUMERIC(5, 2) NULL,
    ADD COLUMN IF NOT EXISTS temperatura_min_c NUMERIC(4, 1) NULL,
    ADD COLUMN IF NOT EXISTS temperatura_max_c NUMERIC(4, 1) NULL,
    ADD COLUMN IF NOT EXISTS tempo_em_zonas JSONB NULL;
