-- =====================================================================
-- V66: Adiciona calibracao_iniciada_em em tb_athlete_baseline_state
-- (retrofit 10.4, sessao de grilling 2026-07-21).
--
-- Marca quando o atleta entrou na fase TrainingPhase.CALIBRATION —
-- setado na 1a vez que confidenceTier != 'A' apos um recalculo do
-- OnboardingContext; limpo (NULL) quando CalibrationEvaluation
-- (design.md Decisao 5) considera o atleta elegivel para sair da
-- calibracao. Ver OnboardingServiceImpl.persistirBaselineSnapshot e
-- OnboardingServiceImpl.avaliarCalibracaoSeAplicavel.
--
-- Rollback: ALTER TABLE tb_athlete_baseline_state DROP COLUMN calibracao_iniciada_em;
-- Feature aditiva pura -- sem impacto em dado existente; reversao segura.
-- =====================================================================

ALTER TABLE tb_athlete_baseline_state
    ADD COLUMN IF NOT EXISTS calibracao_iniciada_em TIMESTAMPTZ;

DO $$
BEGIN
    RAISE NOTICE '✅ V66 - calibracao_iniciada_em adicionada a tb_athlete_baseline_state';
END$$;
