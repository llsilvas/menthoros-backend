-- =====================================================================
-- V67: Adiciona canal de integracao e dispositivo do atleta em
-- tb_perfil_onboarding_atleta (retrofit 10.6, sessao de grilling
-- 2026-07-21).
--
-- canal_integracao: INTERVALS_ICU/MANUAL — STRAVA nao e oferecido para
-- atletas novos (ADR-0003, descontinuacao anunciada).
-- dispositivo_marca: obrigatorio, alimenta o ConfidenceScorer como prior
-- (FontePriority) antes de qualquer atividade real existir.
-- dispositivo_modelo: texto livre opcional, sem uso funcional ainda.
--
-- Rollback: ALTER TABLE tb_perfil_onboarding_atleta DROP COLUMN canal_integracao,
-- DROP COLUMN dispositivo_marca, DROP COLUMN dispositivo_modelo;
-- Feature aditiva pura -- sem impacto em dado existente; reversao segura.
-- =====================================================================

ALTER TABLE tb_perfil_onboarding_atleta
    ADD COLUMN IF NOT EXISTS canal_integracao VARCHAR(20),
    ADD COLUMN IF NOT EXISTS dispositivo_marca VARCHAR(20),
    ADD COLUMN IF NOT EXISTS dispositivo_modelo VARCHAR(100);

DO $$
BEGIN
    RAISE NOTICE '✅ V67 - canal_integracao e dispositivo adicionados a tb_perfil_onboarding_atleta';
END$$;
