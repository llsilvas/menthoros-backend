-- =====================================================================
-- V6: Adiciona campos fisiológicos completos do atleta
-- =====================================================================
-- Consolida: os campos comentados de V6 original + V22
-- =====================================================================

-- ========================================
-- 1. COMPLETAR CAMPOS DE IDENTIFICAÇÃO
-- ========================================

DO $$
BEGIN
    -- Garantir que email tem constraint UNIQUE
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE table_name = 'tb_atleta' AND constraint_name = 'uk_atleta_email'
    ) THEN
        ALTER TABLE tb_atleta ADD CONSTRAINT uk_atleta_email UNIQUE (email);
    END IF;
    
    -- Garantir que sexo tem constraint CHECK
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE table_name = 'tb_atleta' AND constraint_name = 'ck_atleta_sexo'
    ) THEN
        ALTER TABLE tb_atleta ADD CONSTRAINT ck_atleta_sexo CHECK (sexo IS NULL OR sexo IN ('M', 'F', 'O'));
    END IF;
END $$;

-- ========================================
-- 2. ADICIONAR CAMPOS DE FISIOLOGIA FALTANTES
-- ========================================

ALTER TABLE tb_atleta
    ADD COLUMN IF NOT EXISTS fc_maxima INTEGER,
    ADD COLUMN IF NOT EXISTS fc_repouso INTEGER,
    ADD COLUMN IF NOT EXISTS fc_limiar INTEGER,
    ADD COLUMN IF NOT EXISTS data_ultimo_teste_fc DATE,
    ADD COLUMN IF NOT EXISTS pace_limiar DECIMAL(5,2),
    ADD COLUMN IF NOT EXISTS velocidade_limiar DECIMAL(5,2),
    ADD COLUMN IF NOT EXISTS data_ultimo_teste_pace DATE,
    ADD COLUMN IF NOT EXISTS vo2max_estimado DECIMAL(5,2),
    ADD COLUMN IF NOT EXISTS distancia_maxima_longo INTEGER,
    ADD COLUMN IF NOT EXISTS volume_semanal_max INTEGER,
    ADD COLUMN IF NOT EXISTS data_ultima_lesao DATE,
    ADD COLUMN IF NOT EXISTS historico_lesoes TEXT;

DO $$
BEGIN
    -- FC constraints
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE table_name = 'tb_atleta' AND constraint_name = 'ck_atleta_fc_maxima'
    ) THEN
        ALTER TABLE tb_atleta ADD CONSTRAINT ck_atleta_fc_maxima CHECK (fc_maxima IS NULL OR (fc_maxima >= 120 AND fc_maxima <= 220));
    END IF;
    
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE table_name = 'tb_atleta' AND constraint_name = 'ck_atleta_fc_repouso'
    ) THEN
        ALTER TABLE tb_atleta ADD CONSTRAINT ck_atleta_fc_repouso CHECK (fc_repouso IS NULL OR (fc_repouso >= 30 AND fc_repouso <= 100));
    END IF;
    
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE table_name = 'tb_atleta' AND constraint_name = 'ck_atleta_fc_limiar'
    ) THEN
        ALTER TABLE tb_atleta ADD CONSTRAINT ck_atleta_fc_limiar CHECK (fc_limiar IS NULL OR (fc_limiar >= 100 AND fc_limiar <= 200));
    END IF;
    
    -- Pace/Velocidade constraints
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE table_name = 'tb_atleta' AND constraint_name = 'ck_atleta_pace_limiar'
    ) THEN
        ALTER TABLE tb_atleta ADD CONSTRAINT ck_atleta_pace_limiar CHECK (pace_limiar IS NULL OR (pace_limiar >= 2.5 AND pace_limiar <= 12.0));
    END IF;
    
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE table_name = 'tb_atleta' AND constraint_name = 'ck_atleta_velocidade_limiar'
    ) THEN
        ALTER TABLE tb_atleta ADD CONSTRAINT ck_atleta_velocidade_limiar CHECK (velocidade_limiar IS NULL OR (velocidade_limiar >= 5.0 AND velocidade_limiar <= 25.0));
    END IF;
    
    -- VO2max constraint
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE table_name = 'tb_atleta' AND constraint_name = 'ck_atleta_vo2max'
    ) THEN
        ALTER TABLE tb_atleta ADD CONSTRAINT ck_atleta_vo2max CHECK (vo2max_estimado IS NULL OR (vo2max_estimado >= 20 AND vo2max_estimado <= 90));
    END IF;
END $$;

-- ========================================
-- 3. ADICIONAR CAMPOS DE LESÃO COMPLETOS
-- ========================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_atleta' AND column_name = 'tem_lesao') THEN
        ALTER TABLE tb_atleta
            ADD COLUMN tem_lesao BOOLEAN DEFAULT FALSE,
            ADD COLUMN descricao_lesao VARCHAR(1000);
    END IF;
END $$;

-- ========================================
-- 4. ADICIONAR CAMPO de dia preferido para long run
-- ========================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_atleta' AND column_name = 'dia_preferido_longo') THEN
        ALTER TABLE tb_atleta
            ADD COLUMN dia_preferido_longo VARCHAR(20);
    END IF;
END $$;

-- ========================================
-- Finalização
-- ========================================

DO $$
BEGIN
    RAISE NOTICE '✅ V6 - Campos fisiológicos do atleta completados';
    RAISE NOTICE '   - Constraints de FC, Pace, VO2max ativadas';
    RAISE NOTICE '   - Email UNIQUE e sexo CHECK adicionados';
    RAISE NOTICE '   - Histórico de lesões conectado';
    RAISE NOTICE '   - Preferências de treino adicionadas';
END$$;