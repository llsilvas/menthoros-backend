-- =====================================================================
-- V96: Contrato do atleta e mensalidades (add-contrato-atleta-mensalidade, design D1/D7)
-- Substitui os campos soltos tipo_plano_atleta/data_vencimento_plano de tb_atleta por um
-- contrato por atleta (tb_athlete_contract) que gera mensalidades (tb_athlete_invoice).
-- Expand-only: as colunas legadas FICAM em tb_atleta, apenas deixam de ser mapeadas; a
-- remocao delas vai em V97 (add-aviso-mensalidade), depois de uma janela em producao.
-- Backfill SO do contrato: nenhuma mensalidade nasce aqui — a primeira vem da renovacao, e
-- nunca no passado (o sistema nao inventa divida que nao viu nascer).
-- Rollback: reverter o binario; as colunas legadas continuam intactas em tb_atleta.
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_athlete_contract (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,                                        -- solto (regra do projeto)
    athlete_id              UUID NOT NULL REFERENCES tb_atleta(id) ON DELETE CASCADE,
    periodicity             VARCHAR(20) NOT NULL,
    amount                  NUMERIC(10,2),                                        -- nulo no backfill; obrigatorio na UI
    due_day                 INTEGER NOT NULL,
    start_date              DATE NOT NULL,
    ended_at                TIMESTAMPTZ,                                          -- contrato ativo = ended_at IS NULL
    athlete_notice_enabled  BOOLEAN NOT NULL DEFAULT TRUE,                        -- consumido por add-aviso-mensalidade
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    CONSTRAINT chk_athlete_contract_periodicity CHECK (
        periodicity IN ('MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL')
    ),
    CONSTRAINT chk_athlete_contract_due_day CHECK (due_day BETWEEN 1 AND 31),
    CONSTRAINT chk_athlete_contract_amount CHECK (amount IS NULL OR amount >= 0)
);

-- Um contrato ATIVO por atleta; encerrados coexistem (porta aberta para historico).
CREATE UNIQUE INDEX IF NOT EXISTS uq_athlete_contract_active
    ON tb_athlete_contract(athlete_id) WHERE ended_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_athlete_contract_tenant_athlete
    ON tb_athlete_contract(tenant_id, athlete_id);

CREATE TABLE IF NOT EXISTS tb_athlete_invoice (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL,
    contract_id   UUID NOT NULL REFERENCES tb_athlete_contract(id) ON DELETE CASCADE,
    due_date      DATE NOT NULL,
    amount        NUMERIC(10,2),                                                  -- copia do contrato na geracao
    status        VARCHAR(20) NOT NULL,
    paid_at       DATE,
    paid_amount   NUMERIC(10,2),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    CONSTRAINT chk_athlete_invoice_status CHECK (status IN ('OPEN', 'PAID', 'CANCELLED')),
    CONSTRAINT uq_athlete_invoice_contract_due_date UNIQUE (contract_id, due_date)
);

CREATE INDEX IF NOT EXISTS idx_athlete_invoice_tenant_status_due
    ON tb_athlete_invoice(tenant_id, status, due_date);

COMMENT ON TABLE tb_athlete_contract IS
    'Contrato comercial atleta <-> assessoria: periodicidade, valor e dia de vencimento. O Menthoros nao movimenta dinheiro.';
COMMENT ON TABLE tb_athlete_invoice IS
    'Mensalidade gerada pelo contrato. "Vencida" e derivada (OPEN com due_date no passado), nunca persistida.';

-- Backfill: todo atleta com data legada vira contrato ativo, sem mensalidade. O CASE (nao
-- COALESCE) defende contra valor fora do enum: V57 criou tipo_plano_atleta sem CHECK. O NOT
-- EXISTS torna o bloco reexecutavel (e o que permite testa-lo).
INSERT INTO tb_athlete_contract (tenant_id, athlete_id, periodicity, due_day, start_date)
SELECT a.tenant_id,
       a.id,
       CASE a.tipo_plano_atleta
           WHEN 'TRIMESTRAL' THEN 'QUARTERLY'
           WHEN 'SEMESTRAL'  THEN 'SEMIANNUAL'
           WHEN 'ANUAL'      THEN 'ANNUAL'
           ELSE 'MONTHLY'
       END,
       EXTRACT(DAY FROM a.data_vencimento_plano)::INTEGER,
       a.data_vencimento_plano
FROM tb_atleta a
WHERE a.data_vencimento_plano IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM tb_athlete_contract c
      WHERE c.athlete_id = a.id AND c.ended_at IS NULL
  );

DO $$
BEGIN
    RAISE NOTICE '✅ V96 - tb_athlete_contract e tb_athlete_invoice criadas; contratos legados migrados';
END$$;
