-- =====================================================================
-- V27: Cria tb_race_projection_snapshot para snapshots imutáveis de projeção de prova
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_race_projection_snapshot (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    athlete_id                  UUID NOT NULL REFERENCES tb_atleta(id) ON DELETE CASCADE,
    race_id                     UUID REFERENCES tb_prova(id) ON DELETE SET NULL,
    tenant_id                   UUID NOT NULL,
    generated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    weeks_to_race_at_generation INTEGER,
    projections_json            JSONB NOT NULL,
    confidence                  VARCHAR(10) NOT NULL,
    is_official                 BOOLEAN NOT NULL DEFAULT FALSE,
    coach_id                    UUID NOT NULL,
    coach_reviewed_at           TIMESTAMPTZ,
    ctl_at_generation           DECIMAL(6,2),
    tsb_at_generation           DECIMAL(6,2),
    regression_r_squared        DECIMAL(5,4),
    riegel_exponent_used        DECIMAL(6,4),
    riegel_calibrated           BOOLEAN NOT NULL DEFAULT FALSE,
    training_weeks_used         INTEGER,
    model_used                  VARCHAR(100)
);

-- Snapshot mais recente por atleta/prova (query principal de listagem e busca de oficial)
CREATE INDEX IF NOT EXISTS idx_race_projection_athlete_race
    ON tb_race_projection_snapshot (athlete_id, race_id, generated_at DESC);

-- Todas as projeções de um tenant (acesso multi-tenant)
CREATE INDEX IF NOT EXISTS idx_race_projection_tenant
    ON tb_race_projection_snapshot (tenant_id);

-- Regra "no máximo uma oficial por atleta/prova":
-- garantida via UPDATE SET is_official=false antes de marcar nova (sem constraint declarativa — ver D5 no design.md)

DO $$
BEGIN
    RAISE NOTICE '✅ V27 - tb_race_projection_snapshot criada com sucesso';
END$$;
