-- =====================================================================
-- V62: Adiciona campos de calibracao a tb_treino_realizado (athlete-onboarding-baseline)
--
-- Extensao do feedback pos-treino durante TrainingPhase.CALIBRATION
-- (design.md, tasks.md 8.3/8.4): dor, fadiga e recuperacao entre sessoes,
-- mesmo padrao 1-10 de nivel_estresse/qualidade_sono_noite_anterior
-- (ja existentes, V1) -- nao recriar esses dois.
--
-- Rollback: ALTER TABLE tb_treino_realizado DROP COLUMN IF EXISTS nivel_dor,
--   DROP COLUMN IF EXISTS nivel_fadiga, DROP COLUMN IF EXISTS nivel_recuperacao;
-- Feature aditiva pura (colunas nullable) -- sem impacto em dado existente.
-- =====================================================================

ALTER TABLE tb_treino_realizado
    ADD COLUMN IF NOT EXISTS nivel_dor INTEGER CHECK (nivel_dor BETWEEN 1 AND 10),
    ADD COLUMN IF NOT EXISTS nivel_fadiga INTEGER CHECK (nivel_fadiga BETWEEN 1 AND 10),
    ADD COLUMN IF NOT EXISTS nivel_recuperacao INTEGER CHECK (nivel_recuperacao BETWEEN 1 AND 10);

DO $$
BEGIN
    RAISE NOTICE '✅ V62 - campos de calibracao (dor/fadiga/recuperacao) adicionados a tb_treino_realizado';
END$$;
