-- =====================================================================
-- V51: adiciona origem_encerramento ao tb_plano_semanal
--
-- Rastreia se a semana foi encerrada pelo treinador (ON_DEMAND) ou pelo
-- scheduler (AUTOMATICO), habilitando a métrica-farol de adoção.
-- Coluna nullable, sem default e sem backfill: planos pré-existentes ficam NULL.
--
-- Rollback: ALTER TABLE tb_plano_semanal DROP COLUMN IF EXISTS origem_encerramento;
-- Feature aditiva pura — sem impacto em dado existente; reversão segura.
-- =====================================================================

ALTER TABLE tb_plano_semanal
    ADD COLUMN IF NOT EXISTS origem_encerramento VARCHAR(15)
    CHECK (origem_encerramento IN ('ON_DEMAND', 'AUTOMATICO'));

DO $$
BEGIN
    RAISE NOTICE '✅ V51 - coluna origem_encerramento adicionada a tb_plano_semanal';
END$$;
