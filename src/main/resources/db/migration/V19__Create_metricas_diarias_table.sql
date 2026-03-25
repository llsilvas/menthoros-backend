-- V19: Cria tabela tb_metricas_diarias
-- Inclui tenant_id (V17 pulou esta tabela por não existir na época)
-- Usa IF NOT EXISTS para ser idempotente (tabela pode já existir no banco)

CREATE TABLE IF NOT EXISTS tb_metricas_diarias
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL REFERENCES tb_assessoria (id) ON DELETE CASCADE,
    atleta_id           UUID         NOT NULL REFERENCES tb_atleta (id) ON DELETE CASCADE,
    data                DATE         NOT NULL,
    tss                 INTEGER      NOT NULL DEFAULT 0,
    ctl                 DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    atl                 DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    tsb                 DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    ramp_rate           DOUBLE PRECISION          DEFAULT 0.0,
    fatigue_ratio       DOUBLE PRECISION          DEFAULT 0.0,
    forma_percentual    DOUBLE PRECISION,
    treinos_realizados  INTEGER               DEFAULT 0,
    volume_km           DECIMAL(6, 2),
    foi_dia_descanso    BOOLEAN               DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_atleta_data ON tb_metricas_diarias (atleta_id, data);
CREATE INDEX IF NOT EXISTS idx_atleta_data_desc ON tb_metricas_diarias (atleta_id, data DESC);
CREATE INDEX IF NOT EXISTS idx_metricas_diarias_tenant ON tb_metricas_diarias (tenant_id);
