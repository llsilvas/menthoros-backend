CREATE TABLE tb_analise_workout (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    treino_realizado_id         UUID NOT NULL,
    tenant_id                   UUID NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    summary_pt                  TEXT,
    technical_interpretation_pt TEXT,
    primary_cause               VARCHAR(50),
    recommendation_pt           TEXT,
    tags                        TEXT[],
    execution_score             INTEGER CHECK (execution_score BETWEEN 1 AND 10),
    rationale_pt                TEXT,
    translation_failed          BOOLEAN NOT NULL DEFAULT FALSE,
    error_message               TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    analyzed_at                 TIMESTAMPTZ,
    CONSTRAINT uq_analise_treino UNIQUE (treino_realizado_id)
);

CREATE INDEX idx_analise_tenant ON tb_analise_workout (tenant_id);
