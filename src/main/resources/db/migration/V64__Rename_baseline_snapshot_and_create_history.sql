-- =====================================================================
-- V64: Renomeia tb_athlete_baseline_snapshot -> tb_athlete_baseline_state e cria
-- tb_athlete_baseline_history (athlete-onboarding-baseline)
--
-- Sessao de grilling 2026-07-21: "Snapshot" sugeria um recorte imutavel no
-- tempo, mas a tabela sempre teve 1 linha por atleta, sobrescrita a cada
-- re-baseline -- e o estado ATUAL, nao um historico. Renomeada para refletir
-- isso. A trilha de auditoria de verdade (evolucao do score ao longo da
-- calibracao) vira uma tabela append-only separada, nova -- mesmas colunas
-- de dado de tb_athlete_baseline_state, sem a UNIQUE(atleta_id, tenant_id) e
-- sem atualizado_em (nunca e alterada apos o insert).
--
-- Rollback: ALTER TABLE tb_athlete_baseline_state RENAME TO tb_athlete_baseline_snapshot;
--   DROP TABLE IF EXISTS tb_athlete_baseline_history;
-- Rename + tabela nova aditiva -- sem impacto em dado existente.
-- =====================================================================

ALTER TABLE tb_athlete_baseline_snapshot RENAME TO tb_athlete_baseline_state;
ALTER INDEX IF EXISTS idx_baseline_snapshot_tenant RENAME TO idx_baseline_state_tenant;
ALTER TABLE tb_athlete_baseline_state RENAME CONSTRAINT uk_baseline_snapshot_atleta_tenant TO uk_baseline_state_atleta_tenant;

CREATE TABLE IF NOT EXISTS tb_athlete_baseline_history (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    atleta_id            UUID        NOT NULL REFERENCES tb_atleta(id) ON DELETE CASCADE,
    tenant_id            UUID        NOT NULL,
    evento               VARCHAR(30) NOT NULL,
    ctl_estimado         DOUBLE PRECISION,
    atl_estimado         DOUBLE PRECISION,
    tsb_estimado         DOUBLE PRECISION,
    ctl_flag             VARCHAR(20) CHECK (ctl_flag IN ('ESTIMATED', 'MEASURED')),
    atl_flag             VARCHAR(20) CHECK (atl_flag IN ('ESTIMATED', 'MEASURED')),
    tsb_flag             VARCHAR(20) CHECK (tsb_flag IN ('ESTIMATED', 'MEASURED')),
    confidence_score     INTEGER CHECK (confidence_score BETWEEN 0 AND 100),
    confidence_tier      VARCHAR(1) CHECK (confidence_tier IN ('A', 'B', 'C')),
    calculated_at        TIMESTAMPTZ NOT NULL,
    criado_em            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_baseline_history_atleta_tenant
    ON tb_athlete_baseline_history (tenant_id, atleta_id);

DO $$
BEGIN
    RAISE NOTICE '✅ V64 - tb_athlete_baseline_snapshot renomeada para tb_athlete_baseline_state; tb_athlete_baseline_history criada';
END$$;
