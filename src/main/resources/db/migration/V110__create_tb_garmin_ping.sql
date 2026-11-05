-- =====================================================================
-- V110: Fila de pings activityFiles do Garmin (garmin-activity-ingestion, D1)
--
-- Aditiva. Cada aviso de arquivo de atividade vira uma linha antes do 200 ao Garmin; o
-- binário do FIT nunca é guardado. summary_id é UNIQUE global: o summaryId é único no Garmin
-- e o dedup acontece antes de se saber o tenant.
-- callback_url carrega o token de download do Garmin (válido 24 h): não logar.
-- Some em cascata com o atleta (LGPD).
--
-- Rollback: remover a tabela tb_garmin_ping (nada mais depende dela).
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_garmin_ping (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL,
    athlete_id           UUID NOT NULL REFERENCES tb_atleta(id) ON DELETE CASCADE,
    garmin_user_id       VARCHAR(100) NOT NULL,
    summary_id           VARCHAR(100) NOT NULL,
    activity_id          VARCHAR(100) NULL,
    callback_url         VARCHAR(1000) NOT NULL,
    activity_type        VARCHAR(50) NULL,
    file_type            VARCHAR(10) NULL,
    device_name          VARCHAR(255) NULL,
    start_time           TIMESTAMPTZ NOT NULL,
    manual               BOOLEAN NOT NULL DEFAULT FALSE,
    status               VARCHAR(20) NOT NULL
        CONSTRAINT ck_garmin_ping_status
            CHECK (status IN ('PENDING', 'SYNCING', 'PROCESSED', 'DISCARDED', 'ERROR')),
    reason               VARCHAR(500) NULL,
    attempts             INT NOT NULL DEFAULT 0,
    next_attempt_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    syncing_since        TIMESTAMPTZ NULL,
    completed_workout_id UUID NULL REFERENCES tb_treino_realizado(id) ON DELETE SET NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at         TIMESTAMPTZ NULL,
    CONSTRAINT uk_garmin_ping_summary_id UNIQUE (summary_id)
);

CREATE INDEX IF NOT EXISTS idx_garmin_ping_tenant_status ON tb_garmin_ping(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_garmin_ping_status_created_at ON tb_garmin_ping(status, created_at);
-- FK do cascade: sem índice, apagar um atleta varre a tabela inteira.
CREATE INDEX IF NOT EXISTS idx_garmin_ping_athlete_id ON tb_garmin_ping(athlete_id);

DO $$
BEGIN
    RAISE NOTICE 'V110 - tb_garmin_ping criada com sucesso';
END$$;
