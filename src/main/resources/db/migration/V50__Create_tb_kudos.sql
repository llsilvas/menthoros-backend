-- =====================================================================
-- V50: Cria a tabela tb_kudos (reconhecimento do coach para o atleta)
--
-- Rollback (se necessário): DROP TABLE IF EXISTS tb_kudos;
-- Feature aditiva pura — sem impacto em dado existente; reversão segura.
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_kudos (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    atleta_id   UUID        NOT NULL REFERENCES tb_atleta(id) ON DELETE CASCADE,
    coach_id    UUID        NOT NULL REFERENCES tb_usuario(id) ON DELETE CASCADE,
    motivo      VARCHAR(20) NOT NULL CHECK (motivo IN ('CONSISTENCIA','MELHORA','ESFORCO','SUPERACAO','VOLTA')),
    data        DATE        NOT NULL DEFAULT CURRENT_DATE,
    tenant_id   UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_kudos_atleta_coach_motivo_data UNIQUE (atleta_id, coach_id, motivo, data)
);

CREATE INDEX IF NOT EXISTS idx_kudos_tenant_atleta ON tb_kudos(tenant_id, atleta_id, created_at DESC);

DO $$
BEGIN
    RAISE NOTICE '✅ V50 - tb_kudos criada com sucesso';
END$$;
