-- =====================================================================
-- V108: Modo de zonas por eixo e faixas manuais do atleta (coach-zonas-manuais, D1)
--
-- Aditiva. Atletas existentes ficam em LIMIAR (o cálculo de hoje), sem faixas.
-- Faixas em JSONB: [{"zona":1,"min":120,"max":143}, ...] — FC em bpm, pace em s/km.
--
-- Desligar sem migration: UPDATE tb_atleta SET modo_zonas_fc = 'LIMIAR', modo_zonas_pace = 'LIMIAR',
--   zonas_manuais_fc = NULL, zonas_manuais_pace = NULL;
-- Rollback: ALTER TABLE tb_atleta DROP CONSTRAINT IF EXISTS ck_atleta_zonas_manuais_fc,
--   DROP CONSTRAINT IF EXISTS ck_atleta_zonas_manuais_pace, DROP COLUMN IF EXISTS zonas_manuais_pace,
--   DROP COLUMN IF EXISTS zonas_manuais_fc, DROP COLUMN IF EXISTS modo_zonas_pace, DROP COLUMN IF EXISTS modo_zonas_fc;
-- =====================================================================

ALTER TABLE tb_atleta
    ADD COLUMN IF NOT EXISTS modo_zonas_fc VARCHAR(10) NOT NULL DEFAULT 'LIMIAR'
        CONSTRAINT ck_atleta_modo_zonas_fc CHECK (modo_zonas_fc IN ('LIMIAR', 'MANUAL')),
    ADD COLUMN IF NOT EXISTS modo_zonas_pace VARCHAR(10) NOT NULL DEFAULT 'LIMIAR'
        CONSTRAINT ck_atleta_modo_zonas_pace CHECK (modo_zonas_pace IN ('LIMIAR', 'MANUAL')),
    ADD COLUMN IF NOT EXISTS zonas_manuais_fc JSONB NULL,
    ADD COLUMN IF NOT EXISTS zonas_manuais_pace JSONB NULL;

ALTER TABLE tb_atleta
    ADD CONSTRAINT ck_atleta_zonas_manuais_fc CHECK (modo_zonas_fc <> 'MANUAL' OR zonas_manuais_fc IS NOT NULL),
    ADD CONSTRAINT ck_atleta_zonas_manuais_pace CHECK (modo_zonas_pace <> 'MANUAL' OR zonas_manuais_pace IS NOT NULL);
