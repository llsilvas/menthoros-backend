-- =====================================================================
-- V117: Semanas da temporada (add-season-plan-model-integrity)
--
-- Uma linha por semana do plano de temporada (glossário: Semana da temporada).
-- A semana não existe sem a temporada, daí o CASCADE; a temporada em si não é mais
-- apagada por cascata (V116).
-- phase: só as fases que o plano de temporada emite (subconjunto de TrainingPhase).
-- milestone_race_id: prova que cai nesta semana (ex.: prova-teste); SET NULL se a prova sumir.
--
-- Rollback (V nova): remover a tabela tb_season_week.
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_season_week (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID          NOT NULL,
    season_plan_id     UUID          NOT NULL REFERENCES tb_plano_treino(id) ON DELETE CASCADE,
    week_index         INTEGER       NOT NULL,
    week_start         DATE          NOT NULL,
    phase              VARCHAR(20)   NOT NULL
        CHECK (phase IN ('BASE', 'BUILD', 'PEAK', 'TAPER', 'RACE_WEEK')),
    target_volume_km   DECIMAL(10,3),
    target_tss         DOUBLE PRECISION,
    long_run_km        DECIMAL(10,3),
    deload             BOOLEAN       NOT NULL DEFAULT FALSE,
    key_sessions       TEXT,
    value_source       VARCHAR(20)   NOT NULL
        CHECK (value_source IN ('ENGINE', 'COACH', 'AI_ACCEPTED')),
    milestone_race_id  UUID          REFERENCES tb_prova(id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ,
    CONSTRAINT uk_season_week_plan_index UNIQUE (season_plan_id, week_index)
);

CREATE INDEX IF NOT EXISTS idx_season_week_tenant_plan ON tb_season_week (tenant_id, season_plan_id);

DO $$
BEGIN
    RAISE NOTICE 'V117 - tb_season_week criada';
END$$;
