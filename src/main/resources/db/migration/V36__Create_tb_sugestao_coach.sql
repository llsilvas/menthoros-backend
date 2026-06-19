-- =====================================================================
-- V36: Cria tb_sugestao_coach — inbox persistente de sugestões de IA
--      geradas pelo job diário a partir da fila de atenção do treinador
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_sugestao_coach (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    atleta_id   UUID NOT NULL REFERENCES tb_atleta(id) ON DELETE CASCADE,
    tipo        VARCHAR NOT NULL,
    status      VARCHAR NOT NULL DEFAULT 'PENDING',
    confidence  VARCHAR NOT NULL DEFAULT 'MEDIUM',
    summary     TEXT NOT NULL,
    reasoning   JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ,
    expires_at  TIMESTAMPTZ,
    CONSTRAINT chk_sugestao_tipo   CHECK (tipo       IN ('PLAN_ADJUST', 'RECOVERY', 'NEW_PLAN')),
    CONSTRAINT chk_sugestao_status CHECK (status     IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_sugestao_conf   CHECK (confidence IN ('HIGH', 'MEDIUM', 'LOW'))
);

CREATE INDEX IF NOT EXISTS idx_sugestao_coach_atleta
    ON tb_sugestao_coach(atleta_id);

CREATE INDEX IF NOT EXISTS idx_sugestao_coach_tenant_status
    ON tb_sugestao_coach(tenant_id, status);

-- Garante que exista no máximo uma sugestão pending por (atleta, tipo).
-- Quando a sugestão é aprovada/rejeitada o índice parcial deixa de cobrir a
-- linha, permitindo nova pending no próximo ciclo do job (Decisão 3 do design).
CREATE UNIQUE INDEX IF NOT EXISTS uk_sugestao_pending
    ON tb_sugestao_coach(atleta_id, tipo)
    WHERE status = 'PENDING';

DO $$
BEGIN
    RAISE NOTICE '✅ V36 - tb_sugestao_coach criada com sucesso';
END$$;
