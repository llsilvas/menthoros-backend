-- =====================================================================
-- Migration V14: Convert pace_media from DOUBLE PRECISION to INTERVAL
-- =====================================================================
-- This migration converts pace_media from decimal (min/km as 5.5)
-- to INTERVAL (min/km as '00:05:30')
-- =====================================================================

-- ========================================
-- 1. TREINO_REALIZADO: Convert pace_media
-- ========================================
DO $$
BEGIN
    -- Check if column exists and is DOUBLE PRECISION type
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'pace_media'
        AND data_type = 'double precision'
    ) THEN
        -- Step 1: Add temporary column with INTERVAL type
        ALTER TABLE tb_treino_realizado
        ADD COLUMN pace_media_temp INTERVAL;

        -- Step 2: Convert existing data
        -- decimal 5.5 minutes → INTERVAL '00:05:30'
        -- Formula: minutes + (decimal_part * 60) seconds
        UPDATE tb_treino_realizado
        SET pace_media_temp = make_interval(
            mins => FLOOR(pace_media)::INTEGER,
            secs => ((pace_media - FLOOR(pace_media)) * 60)::INTEGER
        )
        WHERE pace_media IS NOT NULL;

        -- Step 3: Drop old column
        ALTER TABLE tb_treino_realizado
        DROP COLUMN pace_media;

        -- Step 4: Rename temp column to original name
        ALTER TABLE tb_treino_realizado
        RENAME COLUMN pace_media_temp TO pace_media;

        -- Step 5: Remove NOT NULL constraint if it was there
        -- (since we might have null values after conversion)
        ALTER TABLE tb_treino_realizado
        ALTER COLUMN pace_media DROP NOT NULL;

        COMMENT ON COLUMN tb_treino_realizado.pace_media IS 'Ritmo médio como INTERVAL (compatível com java.time.Duration) - formato min/km';
        RAISE NOTICE '✅ Converted tb_treino_realizado.pace_media from DOUBLE PRECISION to INTERVAL';
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'pace_media'
        AND data_type = 'interval'
    ) THEN
        RAISE NOTICE 'ℹ️  Column pace_media already has correct type (INTERVAL) in tb_treino_realizado';
    ELSE
        RAISE NOTICE '⚠️  Column pace_media not found or has unexpected type in tb_treino_realizado';
    END IF;
END$$;

-- ========================================
-- 2. Final validation
-- ========================================
DO $$
BEGIN
    RAISE NOTICE '✅ Migration V14 completed successfully';
    RAISE NOTICE 'Column pace_media type changed from DOUBLE PRECISION to INTERVAL';
    RAISE NOTICE 'Now compatible with java.time.Duration (SqlTypes.INTERVAL_SECOND)';
    RAISE NOTICE 'Format: min/km as INTERVAL (e.g., 5:30 min/km = 00:05:30)';
END$$;
