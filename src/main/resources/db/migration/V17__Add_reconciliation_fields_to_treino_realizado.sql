-- =====================================================================
-- V17: Adiciona campos de reconciliação em tb_treino_realizado
--      Suporta reconciliação de atividades Strava com plano de treinamento
-- =====================================================================

-- ========================================
-- 1. ADICIONAR COLUNAS DE RECONCILIAÇÃO
-- ========================================

ALTER TABLE tb_treino_realizado
    ADD COLUMN IF NOT EXISTS reconciliation_status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS treino_planejado_id BIGINT,
    ADD COLUMN IF NOT EXISTS reconciliation_score NUMERIC(3,2),
    ADD COLUMN IF NOT EXISTS reconciliation_reason_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS reconciled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reconciled_by VARCHAR(255);

-- ========================================
-- 2. CRIAR ÍNDICE PARA FILTROS DE RECONCILIAÇÃO
-- ========================================

CREATE INDEX IF NOT EXISTS idx_treino_realizado_reconciliation_status
    ON tb_treino_realizado(tenant_id, reconciliation_status)
    WHERE reconciliation_status IS NOT NULL;

-- ========================================
-- 3. ADICIONAR CONSTRAINT PARA RECONCILIATION_SCORE
-- ========================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'tb_treino_realizado' AND constraint_name = 'chk_reconciliation_score_range'
    ) THEN
        ALTER TABLE tb_treino_realizado
            ADD CONSTRAINT chk_reconciliation_score_range
            CHECK (reconciliation_score IS NULL OR (reconciliation_score >= 0 AND reconciliation_score <= 1));
    END IF;
END $$;

-- ========================================
-- 4. LOGGING
-- ========================================

DO $$
BEGIN
    RAISE NOTICE '✅ V17 - Campos de reconciliação adicionados em tb_treino_realizado';
    RAISE NOTICE '   - reconciliation_status: status da reconciliação';
    RAISE NOTICE '   - treino_planejado_id: referência ao treino planejado';
    RAISE NOTICE '   - reconciliation_score: score de compatibilidade (0-1)';
    RAISE NOTICE '   - reconciliation_reason_code: código do motivo da reconciliação';
    RAISE NOTICE '   - reconciled_at: timestamp da reconciliação';
    RAISE NOTICE '   - reconciled_by: usuário que realizou a reconciliação';
    RAISE NOTICE '   - Índice criado: idx_treino_realizado_reconciliation_status';
    RAISE NOTICE '   - Constraint criado: chk_reconciliation_score_range (0-1)';
END$$;
