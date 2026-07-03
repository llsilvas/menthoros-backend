-- =====================================================================
-- V46: Cria a tabela tb_checkin_prontidao (check-in diário de prontidão subjetiva)
--
-- Rollback (se necessário): DROP TABLE IF EXISTS tb_checkin_prontidao;
-- Reversão de comportamento sem tocar em dados: feature flag em
-- app.readiness.enabled (ReadinessProperties) desabilita a leitura pelo motor
-- de elegibilidade sem remover o histórico de checkins.
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_checkin_prontidao (
    id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    atleta_id         UUID           NOT NULL REFERENCES tb_atleta(id) ON DELETE CASCADE,
    data              DATE           NOT NULL,
    qualidade_sono    SMALLINT       NOT NULL,
    humor             SMALLINT       NOT NULL,
    dores_musculares  SMALLINT       NOT NULL,
    nivel_energia     SMALLINT       NOT NULL,
    estresse          SMALLINT       NOT NULL,
    observacoes       TEXT,
    readiness_score   NUMERIC(4,3)   NOT NULL,
    nivel_prontidao   VARCHAR(20)    NOT NULL,
    tenant_id         UUID           NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_checkin_prontidao_atleta_data UNIQUE (atleta_id, data),
    CONSTRAINT chk_checkin_qualidade_sono CHECK (qualidade_sono BETWEEN 1 AND 10),
    CONSTRAINT chk_checkin_humor CHECK (humor BETWEEN 1 AND 10),
    CONSTRAINT chk_checkin_dores_musculares CHECK (dores_musculares BETWEEN 0 AND 10),
    CONSTRAINT chk_checkin_nivel_energia CHECK (nivel_energia BETWEEN 1 AND 10),
    CONSTRAINT chk_checkin_estresse CHECK (estresse BETWEEN 0 AND 10),
    CONSTRAINT chk_checkin_readiness_score CHECK (readiness_score >= 0 AND readiness_score <= 1)
);

CREATE INDEX IF NOT EXISTS idx_checkin_prontidao_atleta_data ON tb_checkin_prontidao(atleta_id, data);
CREATE INDEX IF NOT EXISTS idx_checkin_prontidao_tenant ON tb_checkin_prontidao(tenant_id);

DO $$
BEGIN
    RAISE NOTICE '✅ V46 - tb_checkin_prontidao criada com sucesso';
END$$;
