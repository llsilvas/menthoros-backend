-- =====================================================================
-- V65: Adiciona os campos espelhados de tb_atleta como staging em
-- tb_perfil_onboarding_atleta (retrofit 10.3, sessao de grilling
-- 2026-07-21 — substitui a Decisao 10 original de design.md, que mandava
-- escrever esses campos DIRETO em tb_atleta a cada step do onboarding).
--
-- Ver apps/menthoros-backend/docs/adr/0002-draft-onboarding-nao-escreve-direto-em-atleta.md.
-- Durante RASCUNHO, esses campos vivem SOMENTE aqui; a conclusao do
-- onboarding e que migra para tb_atleta numa unica transacao, com
-- checagem de conflito (Atleta.updatedAt posterior ao criado_em do
-- rascunho -> DomainConflictException).
--
-- Rollback: ALTER TABLE tb_perfil_onboarding_atleta DROP COLUMN ...;
-- DROP TABLE IF EXISTS tb_dias_disponiveis_onboarding;
-- Feature aditiva pura -- sem impacto em dado existente; reversao segura.
-- =====================================================================

ALTER TABLE tb_perfil_onboarding_atleta
    ADD COLUMN IF NOT EXISTS objetivo VARCHAR(500),
    ADD COLUMN IF NOT EXISTS nivel_experiencia VARCHAR(30),
    ADD COLUMN IF NOT EXISTS volume_semanal_max INTEGER,
    ADD COLUMN IF NOT EXISTS tem_lesao BOOLEAN,
    ADD COLUMN IF NOT EXISTS descricao_lesao VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS data_ultima_lesao DATE,
    ADD COLUMN IF NOT EXISTS historico_lesoes TEXT;

CREATE TABLE IF NOT EXISTS tb_dias_disponiveis_onboarding (
    perfil_onboarding_atleta_id UUID NOT NULL REFERENCES tb_perfil_onboarding_atleta(id) ON DELETE CASCADE,
    dia VARCHAR(20) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dias_disponiveis_onboarding_perfil
    ON tb_dias_disponiveis_onboarding(perfil_onboarding_atleta_id);

DO $$
BEGIN
    RAISE NOTICE '✅ V65 - campos de staging do onboarding adicionados a tb_perfil_onboarding_atleta';
END$$;
