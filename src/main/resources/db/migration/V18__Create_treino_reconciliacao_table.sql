-- =====================================================================
-- V18: Cria tabela append-only tb_treino_reconciliacao para auditoria
--      Registra histórico completo de eventos de reconciliação Strava
-- =====================================================================

-- ========================================
-- 1. CRIAR TABELA APPEND-ONLY
-- ========================================

CREATE TABLE IF NOT EXISTS tb_treino_reconciliacao (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               VARCHAR(255) NOT NULL,
    treino_realizado_id     UUID NOT NULL,
    action_type             VARCHAR(50) NOT NULL,
    before_status           VARCHAR(50),
    after_status            VARCHAR(50),
    before_planned_id       BIGINT,
    after_planned_id        BIGINT,
    score                   NUMERIC(3,2),
    reason_code             VARCHAR(100),
    reason_text             TEXT,
    actor_id                VARCHAR(255) NOT NULL,
    occurred_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ========================================
-- 2. ADICIONAR FOREIGN KEY PARA tb_treino_realizado
-- ========================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'tb_treino_reconciliacao' AND constraint_name = 'fk_treino_reconciliacao_realizado'
    ) THEN
        ALTER TABLE tb_treino_reconciliacao
            ADD CONSTRAINT fk_treino_reconciliacao_realizado
            FOREIGN KEY (treino_realizado_id)
            REFERENCES tb_treino_realizado(id)
            ON DELETE CASCADE;
    END IF;
END $$;

-- ========================================
-- 3. ADICIONAR CONSTRAINTS DE VALIDAÇÃO
-- ========================================

-- Constraint para validar score entre 0 e 1
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'tb_treino_reconciliacao' AND constraint_name = 'chk_score_range'
    ) THEN
        ALTER TABLE tb_treino_reconciliacao
            ADD CONSTRAINT chk_score_range
            CHECK (score IS NULL OR (score >= 0 AND score <= 1));
    END IF;
END $$;

-- Constraint para validar que antes ou depois é requerido
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'tb_treino_reconciliacao' AND constraint_name = 'chk_before_or_after_required'
    ) THEN
        ALTER TABLE tb_treino_reconciliacao
            ADD CONSTRAINT chk_before_or_after_required
            CHECK (before_status IS NOT NULL OR after_status IS NOT NULL);
    END IF;
END $$;

-- ========================================
-- 4. CRIAR ÍNDICES PARA PERFORMANCE DE QUERIES
-- ========================================

-- Índice para buscar por tenant_id e treino_realizado_id com ordenação por data descendente
CREATE INDEX IF NOT EXISTS idx_treino_reconciliacao_tenant_realizado
    ON tb_treino_reconciliacao(tenant_id, treino_realizado_id, occurred_at DESC);

-- Índice para buscar por tenant_id e actor_id com ordenação por data descendente
CREATE INDEX IF NOT EXISTS idx_treino_reconciliacao_actor
    ON tb_treino_reconciliacao(tenant_id, actor_id, occurred_at DESC);

-- ========================================
-- 5. LOGGING
-- ========================================

DO $$
BEGIN
    RAISE NOTICE '✅ V18 - Tabela append-only tb_treino_reconciliacao criada com sucesso';
    RAISE NOTICE '   - Registra auditoria completa de eventos de reconciliação Strava';
    RAISE NOTICE '   - id: BIGSERIAL, chave primária com auto-incremento';
    RAISE NOTICE '   - tenant_id: isolamento multi-tenant';
    RAISE NOTICE '   - treino_realizado_id: referência à atividade realizada com FK ON DELETE CASCADE';
    RAISE NOTICE '   - action_type: tipo de ação de reconciliação';
    RAISE NOTICE '   - before_status/after_status: captura mudança de status';
    RAISE NOTICE '   - before_planned_id/after_planned_id: rastreia mudança de treinamento planejado';
    RAISE NOTICE '   - score: compatibilidade entre 0 e 1';
    RAISE NOTICE '   - reason_code/reason_text: motivo da reconciliação';
    RAISE NOTICE '   - actor_id: usuário ou sistema que realizou a ação';
    RAISE NOTICE '   - occurred_at: timestamp da ação com DEFAULT NOW()';
    RAISE NOTICE '   - Constraint chk_score_range: valida score NULL ou [0,1]';
    RAISE NOTICE '   - Constraint chk_before_or_after_required: before_status OU after_status NOT NULL';
    RAISE NOTICE '   - Índices criados:';
    RAISE NOTICE '     idx_treino_reconciliacao_tenant_realizado(tenant_id, treino_realizado_id, occurred_at DESC)';
    RAISE NOTICE '     idx_treino_reconciliacao_actor(tenant_id, actor_id, occurred_at DESC)';
END$$;
