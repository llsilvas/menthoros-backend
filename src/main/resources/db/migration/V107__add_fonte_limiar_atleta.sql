-- =====================================================================
-- V107: Origem dos limiares de FC e pace do atleta (coach-cadastro-ancoras-zonas, D5)
--
-- Aditiva, nullable, sem backfill: NULL = limiar sem origem registrada.
--
-- Rollback: ALTER TABLE tb_atleta DROP COLUMN IF EXISTS fonte_limiar_fc, DROP COLUMN IF EXISTS fonte_limiar_pace;
-- =====================================================================

ALTER TABLE tb_atleta
    ADD COLUMN IF NOT EXISTS fonte_limiar_fc VARCHAR(20) NULL
        CONSTRAINT ck_atleta_fonte_limiar_fc CHECK (fonte_limiar_fc IN ('TESTE_CAMPO', 'PROVA', 'LABORATORIO', 'RELOGIO_PLATAFORMA')),
    ADD COLUMN IF NOT EXISTS fonte_limiar_pace VARCHAR(20) NULL
        CONSTRAINT ck_atleta_fonte_limiar_pace CHECK (fonte_limiar_pace IN ('TESTE_CAMPO', 'PROVA', 'LABORATORIO', 'RELOGIO_PLATAFORMA'));
