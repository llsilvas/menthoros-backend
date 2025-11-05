-- =====================================================================
-- Migration V12: Fix duracao_min column type from INTEGER to INTERVAL
-- =====================================================================
-- This migration fixes the duracao_min column that already exists
-- but with wrong type (INTEGER instead of INTERVAL)
-- =====================================================================

-- ========================================
-- 1. TREINO_PLANEJADO: Convert duracao_min from INTEGER to INTERVAL
-- ========================================
DO $$
BEGIN
    -- Check if column exists and is INTEGER type
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_planejado'
        AND column_name = 'duracao_min'
        AND data_type = 'integer'
    ) THEN
        -- Convert INTEGER (minutes) to INTERVAL
        ALTER TABLE tb_treino_planejado
        ALTER COLUMN duracao_min TYPE INTERVAL
        USING make_interval(mins => duracao_min);

        COMMENT ON COLUMN tb_treino_planejado.duracao_min IS 'Duração do treino como INTERVAL (compatível com java.time.Duration)';
        RAISE NOTICE '✅ Converted tb_treino_planejado.duracao_min from INTEGER to INTERVAL';
    ELSE
        RAISE NOTICE 'ℹ️  Column duracao_min already has correct type in tb_treino_planejado';
    END IF;
END$$;

-- ========================================
-- 2. TREINO_REALIZADO: Convert duracao_min from INTEGER to INTERVAL
-- ========================================
DO $$
BEGIN
    -- Check if column exists and is INTEGER type
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'duracao_min'
        AND data_type = 'integer'
    ) THEN
        -- Convert INTEGER (minutes) to INTERVAL
        ALTER TABLE tb_treino_realizado
        ALTER COLUMN duracao_min TYPE INTERVAL
        USING make_interval(mins => duracao_min);

        COMMENT ON COLUMN tb_treino_realizado.duracao_min IS 'Duração do treino como INTERVAL (compatível com java.time.Duration)';
        RAISE NOTICE '✅ Converted tb_treino_realizado.duracao_min from INTEGER to INTERVAL';
    ELSE
        RAISE NOTICE 'ℹ️  Column duracao_min already has correct type in tb_treino_realizado';
    END IF;
END$$;

-- ========================================
-- 3. Final validation
-- ========================================
DO $$
BEGIN
    RAISE NOTICE '✅ Migration V12 completed successfully';
    RAISE NOTICE 'Column duracao_min type changed from INTEGER to INTERVAL';
    RAISE NOTICE 'Now compatible with java.time.Duration (SqlTypes.INTERVAL_SECOND)';
END$$;
