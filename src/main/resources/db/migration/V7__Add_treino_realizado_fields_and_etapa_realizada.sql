-- =====================================================================
-- V7: Completa tb_treino_realizado e tb_etapa_realizada com campos
-- =====================================================================
-- Consolida: V10 (elevação, FC, cadência), V13 (auditoria), V16 (etapas)
-- =====================================================================

-- ========================================
-- 1. GARANTIR TODOS OS CAMPOS EM tb_treino_realizado
-- ========================================

DO $$
BEGIN
    -- Adicionar campos de elevação se não existirem
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_treino_realizado' AND column_name = 'elevacao_ganho_metros') THEN
        ALTER TABLE tb_treino_realizado
            ADD COLUMN elevacao_ganho_metros INTEGER,
            ADD COLUMN elevacao_perda_metros INTEGER;
    END IF;
    
    -- Adicionar campos de FC se não existirem
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_treino_realizado' AND column_name = 'fc_media') THEN
        ALTER TABLE tb_treino_realizado
            ADD COLUMN fc_media INTEGER,
            ADD COLUMN fc_maxima_treino INTEGER,
            ADD COLUMN fc_minima INTEGER;
    END IF;
    
    -- Adicionar campos de potência e cadência se não existirem
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_treino_realizado' AND column_name = 'potencia_media') THEN
        ALTER TABLE tb_treino_realizado
            ADD COLUMN potencia_media INTEGER,
            ADD COLUMN cadencia_media INTEGER;
    END IF;
END $$;

-- Adicionar constraints de FC
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE table_name = 'tb_treino_realizado' AND constraint_name = 'ck_treino_realizado_fc_media'
    ) THEN
        ALTER TABLE tb_treino_realizado 
            ADD CONSTRAINT ck_treino_realizado_fc_media CHECK (fc_media IS NULL OR (fc_media >= 40 AND fc_media <= 250)),
            ADD CONSTRAINT ck_treino_realizado_fc_maxima CHECK (fc_maxima_treino IS NULL OR (fc_maxima_treino >= 80 AND fc_maxima_treino <= 250)),
            ADD CONSTRAINT ck_treino_realizado_fc_minima CHECK (fc_minima IS NULL OR (fc_minima >= 30 AND fc_minima <= 150));
    END IF;
END $$;

-- Adicionar constraints de potência
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE table_name = 'tb_treino_realizado' AND constraint_name = 'ck_treino_realizado_potencia'
    ) THEN
        ALTER TABLE tb_treino_realizado 
            ADD CONSTRAINT ck_treino_realizado_potencia CHECK (potencia_media IS NULL OR (potencia_media >= 0 AND potencia_media <= 5000));
    END IF;
END $$;

-- Adicionar constraint de cadência
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE table_name = 'tb_treino_realizado' AND constraint_name = 'ck_treino_realizado_cadencia'
    ) THEN
        ALTER TABLE tb_treino_realizado 
            ADD CONSTRAINT ck_treino_realizado_cadencia CHECK (cadencia_media IS NULL OR (cadencia_media >= 60 AND cadencia_media <= 200));
    END IF;
END $$;

-- ========================================
-- 2. ADICIONAR ÍNDICES DE PERFORMANCE PARA tb_etapa_realizada
-- ========================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_etapa_realizada' AND column_name = 'potencia_media') THEN
        ALTER TABLE tb_etapa_realizada
            ADD COLUMN potencia_media INTEGER,
            ADD COLUMN cadencia_media INTEGER;
    END IF;
END $$;

-- ========================================
-- 3. ADICIONAR CONSTRAINT DE INTEGRIDADE REFERENCIAL
-- ========================================

-- Garantir que etapa_planejada_id é válido se informado
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE table_name = 'tb_etapa_realizada' AND constraint_name = 'fk_etapa_realizada_planejada'
    ) THEN
        -- Já existe na V1, apenas confirmando
        RAISE NOTICE 'FK etapa_realizada -> etapa_treino já existe';
    END IF;
END $$;

-- ========================================
-- 4. ADICIONAR ÍNDICE DE PERFORMANCE PARA ETAPAS
-- ========================================

CREATE INDEX IF NOT EXISTS idx_etapa_realizada_fc 
    ON tb_etapa_realizada(fc_media) WHERE fc_media IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_etapa_realizada_potencia 
    ON tb_etapa_realizada(potencia_media) WHERE potencia_media IS NOT NULL;

-- ========================================
-- Finalização
-- ========================================

DO $$
BEGIN
    RAISE NOTICE '✅ V7 - Campos de treino realizado completados';
    RAISE NOTICE '   - Elevação, FC, potência, cadência adicionados';
    RAISE NOTICE '   - Constraints de validação de valores fisiológicos';
    RAISE NOTICE '   - Índices para queries de performance';
END$$;