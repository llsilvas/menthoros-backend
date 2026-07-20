-- =====================================================================
-- V59: Cria tb_athlete_baseline_snapshot (athlete-onboarding-baseline)
--
-- Persiste o baseline calculado no onboarding (CTL/ATL/TSB + flags
-- ESTIMATED/MEASURED por componente) + score/tier de confianca. Uma
-- linha por atleta (upsert), atualizada a cada re-baseline semanal
-- durante CALIBRATION (design.md Decisao 5).
--
-- NAO e o mesmo tipo do record AthleteBaseline.java (contrato minimo de
-- leitura reservado por deterministic-planner-engine, 2 campos) -- esta
-- tabela e o lado de escrita/persistencia completo; o record e mapeado
-- a partir dela na borda do OnboardingContext.
--
-- Rollback: DROP TABLE IF EXISTS tb_athlete_baseline_snapshot;
-- Feature aditiva pura -- sem impacto em dado existente; reversao segura.
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_athlete_baseline_snapshot (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    atleta_id            UUID        NOT NULL REFERENCES tb_atleta(id) ON DELETE CASCADE,
    tenant_id            UUID        NOT NULL,
    ctl_estimado         NUMERIC(6,2),
    atl_estimado         NUMERIC(6,2),
    tsb_estimado         NUMERIC(6,2),
    ctl_flag             VARCHAR(20) CHECK (ctl_flag IN ('ESTIMATED', 'MEASURED')),
    atl_flag             VARCHAR(20) CHECK (atl_flag IN ('ESTIMATED', 'MEASURED')),
    tsb_flag             VARCHAR(20) CHECK (tsb_flag IN ('ESTIMATED', 'MEASURED')),
    confidence_score     INTEGER CHECK (confidence_score BETWEEN 0 AND 100),
    confidence_tier      VARCHAR(1) CHECK (confidence_tier IN ('A', 'B', 'C')),
    calculated_at        TIMESTAMPTZ NOT NULL,
    criado_em            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_baseline_snapshot_atleta_tenant UNIQUE (atleta_id, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_baseline_snapshot_tenant ON tb_athlete_baseline_snapshot(tenant_id);

DO $$
BEGIN
    RAISE NOTICE '✅ V59 - tb_athlete_baseline_snapshot criada com sucesso';
END$$;
