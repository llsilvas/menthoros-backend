-- =====================================================================
-- V12: Cria tabela tb_integracao_externa para tokens OAuth2 por plataforma
--      Suporta Strava agora; extensível para Garmin, TrainingPeaks etc.
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_integracao_externa (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    atleta_id             UUID NOT NULL REFERENCES tb_atleta(id) ON DELETE CASCADE,
    plataforma            VARCHAR(50) NOT NULL,
    external_athlete_id   VARCHAR(100),
    access_token          TEXT,
    refresh_token         TEXT,
    token_expira_em       TIMESTAMPTZ,
    scopes                VARCHAR(500),
    ativo                 BOOLEAN NOT NULL DEFAULT TRUE,
    ultima_sincronizacao  TIMESTAMPTZ,
    tenant_id             UUID NOT NULL,
    criado_em             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em         TIMESTAMP,

    CONSTRAINT uk_integracao_atleta_plataforma UNIQUE (atleta_id, plataforma)
);

CREATE INDEX IF NOT EXISTS idx_integracao_atleta
    ON tb_integracao_externa(atleta_id);

CREATE INDEX IF NOT EXISTS idx_integracao_tenant
    ON tb_integracao_externa(tenant_id);

CREATE INDEX IF NOT EXISTS idx_integracao_external_athlete
    ON tb_integracao_externa(external_athlete_id, plataforma)
    WHERE external_athlete_id IS NOT NULL;

DO $$
BEGIN
    RAISE NOTICE '✅ V12 - tb_integracao_externa criada com suporte OAuth2 multi-plataforma';
END$$;
