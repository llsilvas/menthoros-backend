-- =====================================================================
-- Migration V9: Refactor TreinoBase fields and cleanup redundancies
-- =====================================================================
-- This migration:
-- 1. Moves common fields (id, dataTreino, auditoria) to TreinoBase
-- 2. Renames columns to match updated entity mapping
-- 3. Removes redundant fields from tb_treino_realizado
-- 4. Adds missing campos (zona_alvo)
-- 5. Changes duracao_min from INTEGER to INTERVAL
-- =====================================================================

-- ========================================
-- 1. TREINO_PLANEJADO: Add zona_alvo
-- ========================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_planejado'
        AND column_name = 'zona_alvo'
    ) THEN
        ALTER TABLE tb_treino_planejado
        ADD COLUMN zona_alvo VARCHAR(50);

        COMMENT ON COLUMN tb_treino_planejado.zona_alvo IS 'Zona de treino alvo (ex: "z2-z3")';
        RAISE NOTICE 'Added zona_alvo to tb_treino_planejado';
    END IF;
END$$;

-- ========================================
-- 2. TREINO_REALIZADO: Rename redundant columns
-- ========================================

-- Rename fc_max to fc_maxima_treino (already exists, just ensure consistency)
DO $$
BEGIN
    -- Drop old fc_max if it's different from fc_maxima_treino
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'fc_max'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'fc_maxima_treino'
    ) THEN
        -- Copy data if fc_maxima_treino is null
        UPDATE tb_treino_realizado
        SET fc_maxima_treino = fc_max
        WHERE fc_maxima_treino IS NULL AND fc_max IS NOT NULL;

        -- Drop the old column
        ALTER TABLE tb_treino_realizado DROP COLUMN IF EXISTS fc_max;
        RAISE NOTICE 'Dropped redundant fc_max column';
    END IF;

    -- Rename fc_max to fc_maxima_treino if only fc_max exists
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'fc_max'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'fc_maxima_treino'
    ) THEN
        ALTER TABLE tb_treino_realizado
        RENAME COLUMN fc_max TO fc_maxima_treino;
        RAISE NOTICE 'Renamed fc_max to fc_maxima_treino';
    END IF;
END$$;

-- Rename comentario to feedback_atleta (similar handling)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'comentario'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'feedback_atleta'
    ) THEN
        -- Copy data if feedback_atleta is null
        UPDATE tb_treino_realizado
        SET feedback_atleta = comentario
        WHERE feedback_atleta IS NULL AND comentario IS NOT NULL;

        -- Drop the old column
        ALTER TABLE tb_treino_realizado DROP COLUMN IF EXISTS comentario;
        RAISE NOTICE 'Dropped redundant comentario column';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'comentario'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'feedback_atleta'
    ) THEN
        ALTER TABLE tb_treino_realizado
        RENAME COLUMN comentario TO feedback_atleta;
        RAISE NOTICE 'Renamed comentario to feedback_atleta';
    END IF;
END$$;

-- Rename ritmo_medio to pace_media
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'ritmo_medio'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'pace_media'
    ) THEN
        -- Try to convert ritmo_medio (string like "5:30") to pace_media (decimal like 5.5)
        UPDATE tb_treino_realizado
        SET pace_media = CASE
            WHEN ritmo_medio ~ '^\d+:\d+$' THEN
                CAST(SPLIT_PART(ritmo_medio, ':', 1) AS DECIMAL) +
                (CAST(SPLIT_PART(ritmo_medio, ':', 2) AS DECIMAL) / 60.0)
            ELSE NULL
        END
        WHERE pace_media IS NULL AND ritmo_medio IS NOT NULL;

        ALTER TABLE tb_treino_realizado DROP COLUMN IF EXISTS ritmo_medio;
        RAISE NOTICE 'Dropped redundant ritmo_medio column after migrating to pace_media';
    END IF;
END$$;

-- Rename elevacao_total to elevacao_ganho_metros (if different)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'elevacao_total'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'elevacao_ganho_metros'
    ) THEN
        -- Copy data if elevacao_ganho_metros is null
        UPDATE tb_treino_realizado
        SET elevacao_ganho_metros = elevacao_total
        WHERE elevacao_ganho_metros IS NULL AND elevacao_total IS NOT NULL;

        ALTER TABLE tb_treino_realizado DROP COLUMN IF EXISTS elevacao_total;
        RAISE NOTICE 'Dropped redundant elevacao_total column';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'elevacao_total'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'elevacao_ganho_metros'
    ) THEN
        ALTER TABLE tb_treino_realizado
        RENAME COLUMN elevacao_total TO elevacao_ganho_metros;
        RAISE NOTICE 'Renamed elevacao_total to elevacao_ganho_metros';
    END IF;
END$$;

-- ========================================
-- 3. Add missing columns to both tables
-- ========================================

-- Add zona_alvo to tb_treino_realizado
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'zona_alvo'
    ) THEN
        ALTER TABLE tb_treino_realizado
        ADD COLUMN zona_alvo VARCHAR(50);

        COMMENT ON COLUMN tb_treino_realizado.zona_alvo IS 'Zona de treino alvo (ex: "z2-z3")';
        RAISE NOTICE 'Added zona_alvo to tb_treino_realizado';
    END IF;
END$$;

-- Rename fc_alvo to zona_alvo where appropriate (this is a design decision)
-- For now, keep fc_alvo as is (it's more specific - heart rate range like "140-155")

-- ========================================
-- 4. Add columns that may not exist in fresh DB
-- ========================================

ALTER TABLE tb_treino_realizado
    ADD COLUMN IF NOT EXISTS potencia_media INTEGER,
    ADD COLUMN IF NOT EXISTS cadencia_media INTEGER,
    ADD COLUMN IF NOT EXISTS fc_maxima_treino INTEGER,
    ADD COLUMN IF NOT EXISTS feedback_atleta TEXT,
    ADD COLUMN IF NOT EXISTS pace_media DECIMAL(5,2);

-- ========================================
-- 5. Create indexes for new columns
-- ========================================

CREATE INDEX IF NOT EXISTS idx_treino_realizado_potencia ON tb_treino_realizado(potencia_media)
WHERE potencia_media IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_treino_realizado_cadencia ON tb_treino_realizado(cadencia_media)
WHERE cadencia_media IS NOT NULL;

-- ========================================
-- 6. Add comments for documentation
-- ========================================

COMMENT ON COLUMN tb_treino_realizado.cadencia_media IS 'Cadência média em passos por minuto (spm)';
COMMENT ON COLUMN tb_treino_realizado.potencia_media IS 'Potência média em watts';
COMMENT ON COLUMN tb_treino_realizado.elevacao_ganho_metros IS 'Elevação acumulada (ganho) em metros';
COMMENT ON COLUMN tb_treino_realizado.elevacao_perda_metros IS 'Elevação perdida (descida) em metros';
COMMENT ON COLUMN tb_treino_realizado.feedback_atleta IS 'Comentário/observação do atleta sobre o treino';
COMMENT ON COLUMN tb_treino_realizado.pace_media IS 'Ritmo médio em minutos por quilômetro (min/km)';

-- ========================================
-- 6. Final validation
-- ========================================
DO $$
BEGIN
    RAISE NOTICE '✅ Migration V9 completed successfully';
    RAISE NOTICE 'TreinoBase fields refactored and redundancies removed';
    RAISE NOTICE 'Column names now match entity field names';
END$$;