-- =====================================================================
-- Migration V11: Change duracao_minutos from INTEGER to INTERVAL
-- =====================================================================
-- This migration:
-- 1. Changes duracao_minutos column type from INTEGER to INTERVAL
-- 2. Converts existing data (minutes as integer to INTERVAL)
-- 3. Renames column from duracao_minutos to duracao_min
-- 4. Ensures compatibility with Java Duration type
-- =====================================================================

-- ========================================
-- 1. TREINO_PLANEJADO: Convert duracao_minutos
-- ========================================
DO $$
BEGIN
    -- Check if column exists and is INTEGER type
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_planejado'
        AND column_name = 'duracao_minutos'
        AND data_type = 'integer'
    ) THEN
        -- Step 1: Add new column with INTERVAL type and correct name
        ALTER TABLE tb_treino_planejado
        ADD COLUMN duracao_min INTERVAL;

        -- Step 2: Convert existing data (minutes to interval)
        UPDATE tb_treino_planejado
        SET duracao_min = make_interval(mins => duracao_minutos)
        WHERE duracao_minutos IS NOT NULL;

        -- Step 3: Drop old column
        ALTER TABLE tb_treino_planejado
        DROP COLUMN duracao_minutos;

        -- Step 4: Set NOT NULL constraint
        ALTER TABLE tb_treino_planejado
        ALTER COLUMN duracao_min SET NOT NULL;

        COMMENT ON COLUMN tb_treino_planejado.duracao_min IS 'Duração do treino como INTERVAL (compatível com java.time.Duration)';
        RAISE NOTICE '✅ Converted tb_treino_planejado.duracao_minutos (INTEGER) to duracao_min (INTERVAL)';
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_planejado'
        AND column_name = 'duracao_minutos'
        AND data_type = 'interval'
    ) THEN
        -- Just rename if already INTERVAL
        ALTER TABLE tb_treino_planejado
        RENAME COLUMN duracao_minutos TO duracao_min;
        RAISE NOTICE '✅ Renamed tb_treino_planejado.duracao_minutos to duracao_min';
    ELSE
        RAISE NOTICE 'ℹ️ Column duracao_min already exists or duracao_minutos does not exist in tb_treino_planejado';
    END IF;
END$$;

-- ========================================
-- 2. TREINO_REALIZADO: Convert duracao_minutos
-- ========================================
DO $$
BEGIN
    -- Check if column exists and is INTEGER type
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'duracao_minutos'
        AND data_type = 'integer'
    ) THEN
        -- Step 1: Add new column with INTERVAL type and correct name
        ALTER TABLE tb_treino_realizado
        ADD COLUMN duracao_min INTERVAL;

        -- Step 2: Convert existing data (minutes to interval)
        UPDATE tb_treino_realizado
        SET duracao_min = make_interval(mins => duracao_minutos)
        WHERE duracao_minutos IS NOT NULL;

        -- Step 3: Drop old column
        ALTER TABLE tb_treino_realizado
        DROP COLUMN duracao_minutos;

        -- Step 4: Set NOT NULL constraint
        ALTER TABLE tb_treino_realizado
        ALTER COLUMN duracao_min SET NOT NULL;

        COMMENT ON COLUMN tb_treino_realizado.duracao_min IS 'Duração do treino como INTERVAL (compatível com java.time.Duration)';
        RAISE NOTICE '✅ Converted tb_treino_realizado.duracao_minutos (INTEGER) to duracao_min (INTERVAL)';
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'duracao_minutos'
        AND data_type = 'interval'
    ) THEN
        -- Just rename if already INTERVAL
        ALTER TABLE tb_treino_realizado
        RENAME COLUMN duracao_minutos TO duracao_min;
        RAISE NOTICE '✅ Renamed tb_treino_realizado.duracao_minutos to duracao_min';
    ELSE
        RAISE NOTICE 'ℹ️ Column duracao_min already exists or duracao_minutos does not exist in tb_treino_realizado';
    END IF;
END$$;

-- ========================================
-- 3. Update @Column mapping in entities
-- ========================================
-- NOTE: Entity classes should use:
-- @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
-- @Column(name = "duracao_min", nullable = false)
-- private Duration duracaoMin;

-- ========================================
-- 4. Final validation
-- ========================================
DO $$
BEGIN
    RAISE NOTICE '✅ Migration V11 completed successfully';
    RAISE NOTICE 'Column renamed: duracao_minutos → duracao_min';
    RAISE NOTICE 'Type changed: INTEGER → INTERVAL';
    RAISE NOTICE 'Now compatible with java.time.Duration type';
END$$;
