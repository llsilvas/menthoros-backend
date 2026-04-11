-- =====================================================================
-- V8: Adiciona suporte a sincronização com Strava e outras fontes
-- =====================================================================
-- Consolida: V22 (sincronização), V24 (external_id), V25 (UNIQUE external_id)
-- =====================================================================

-- ========================================
-- 1. GARANTIR CAMPOS DE SINCRONIZAÇÃO EM tb_treino_planejado
-- ========================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_treino_planejado' AND column_name = 'external_id') THEN
        ALTER TABLE tb_treino_planejado
            ADD COLUMN external_id VARCHAR(255);
    END IF;
    
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_treino_planejado' AND column_name = 'criado_em') THEN
        ALTER TABLE tb_treino_planejado
            ADD COLUMN criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            ADD COLUMN atualizado_em TIMESTAMP,
            ADD COLUMN criado_por VARCHAR(50);
    END IF;
END $$;

-- ========================================
-- 2. GARANTIR CAMPOS DE SINCRONIZAÇÃO E external_id UNIQUE EM tb_treino_realizado
-- ========================================

DO $$
BEGIN
    -- Remover constraint antiga se existir (sem UNIQUE parcial)
    ALTER TABLE tb_treino_realizado DROP CONSTRAINT IF EXISTS uk_treino_realizado_external_id CASCADE;
    -- Remover constraint inline criada por V1 se existir
    ALTER TABLE tb_treino_realizado DROP CONSTRAINT IF EXISTS tb_treino_realizado_external_id_key CASCADE;
END $$;

-- Criar partial unique index (suporta múltiplos NULLs corretamente)
CREATE UNIQUE INDEX IF NOT EXISTS uk_treino_realizado_external_id
    ON tb_treino_realizado(external_id)
    WHERE external_id IS NOT NULL;

-- ========================================
-- 3. ADICIONAR ÍNDICE PARA EXTERNAL_IDs (queries de sincronização pendente)
-- ========================================

CREATE INDEX IF NOT EXISTS idx_treino_planejado_external_id 
    ON tb_treino_planejado(external_id) WHERE external_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_treino_realizado_external_id_upsert 
    ON tb_treino_realizado(external_id, status_sincronizacao) WHERE external_id IS NOT NULL;

-- ========================================
-- 4. ADICIONAR TABELA DE LOG DE SINCRONIZAÇÃO (futuro)
-- ========================================

CREATE TABLE IF NOT EXISTS tb_sync_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tb_assessoria(id) ON DELETE CASCADE,
    atleta_id UUID NOT NULL REFERENCES tb_atleta(id) ON DELETE CASCADE,
    tipo_recurso VARCHAR(50) NOT NULL,
    recurso_id UUID NOT NULL,
    acao VARCHAR(20) NOT NULL,
    fonte_dados VARCHAR(50) NOT NULL,
    external_id VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    mensagem_erro TEXT,
    tentativas INTEGER DEFAULT 1,
    proxima_tentativa TIMESTAMP,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_acao_sync CHECK (acao IN ('CRIAR', 'ATUALIZAR', 'DELETAR', 'SINCRONIZAR'))
);

CREATE INDEX IF NOT EXISTS idx_sync_log_tenant_status 
    ON tb_sync_log(tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_sync_log_proxima_tentativa 
    ON tb_sync_log(proxima_tentativa) WHERE status = 'PENDENTE';

COMMENT ON TABLE tb_sync_log IS 'Log de tentativas de sincronização para debug e retry';

-- ========================================
-- 5. ADICIONAR FUNÇÃO PARA ATUALIZAR updated_at AUTOMATICAMENTE
-- ========================================

CREATE OR REPLACE FUNCTION update_sync_log_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.atualizado_em = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_sync_log_updated_at ON tb_sync_log;
CREATE TRIGGER trigger_sync_log_updated_at
    BEFORE UPDATE ON tb_sync_log
    FOR EACH ROW
    EXECUTE FUNCTION update_sync_log_updated_at();

-- ========================================
-- Finalização
-- ========================================

DO $$
BEGIN
    RAISE NOTICE '✅ V8 - Sincronização externa implementada';
    RAISE NOTICE '   - external_id UNIQUE em tb_treino_realizado';
    RAISE NOTICE '   - Campos de sincronização verificados';
    RAISE NOTICE '   - tb_sync_log criada para tracking de sincronização';
    RAISE NOTICE '   - Índices para queries de pendência adicionados';
END$$;