-- =====================================================================
-- Migration V10: Cleanup redundant columns in treino tables
-- =====================================================================
-- Removes duplicate columns after TreinoBase refactoring:
-- - fc_max → use fc_maxima_treino
-- - comentario → use feedback_atleta
-- - ritmo_medio → use pace_media
-- - elevacao_total → use elevacao_ganho_metros
-- =====================================================================

-- ========================================
-- 1. TREINO_REALIZADO: Remove redundant columns
-- ========================================

-- Check if columns exist before migrating data
DO $$
BEGIN
    -- Migrate fc_max data to fc_maxima_treino (if column exists)
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado' AND column_name = 'fc_max'
    ) THEN
        UPDATE tb_treino_realizado
        SET fc_maxima_treino = COALESCE(fc_maxima_treino, fc_max)
        WHERE fc_max IS NOT NULL;

        RAISE NOTICE 'Migrated fc_max to fc_maxima_treino';
    END IF;

    -- Migrate comentario data to feedback_atleta (if column exists)
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado' AND column_name = 'comentario'
    ) THEN
        UPDATE tb_treino_realizado
        SET feedback_atleta = COALESCE(feedback_atleta, comentario)
        WHERE comentario IS NOT NULL;

        RAISE NOTICE 'Migrated comentario to feedback_atleta';
    END IF;

    -- Migrate ritmo_medio (string "5:30") to pace_media (decimal 5.5) (if column exists)
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado' AND column_name = 'ritmo_medio'
    ) THEN
        UPDATE tb_treino_realizado
        SET pace_media = COALESCE(
            pace_media,
            CASE
                WHEN ritmo_medio ~ '^\d+:\d+$' THEN
                    CAST(SPLIT_PART(ritmo_medio, ':', 1) AS DECIMAL) +
                    (CAST(SPLIT_PART(ritmo_medio, ':', 2) AS DECIMAL) / 60.0)
                ELSE NULL
            END
        )
        WHERE ritmo_medio IS NOT NULL;

        RAISE NOTICE 'Migrated ritmo_medio to pace_media';
    END IF;

    -- Migrate elevacao_total to elevacao_ganho_metros (if column exists)
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado' AND column_name = 'elevacao_total'
    ) THEN
        UPDATE tb_treino_realizado
        SET elevacao_ganho_metros = COALESCE(elevacao_ganho_metros, elevacao_total)
        WHERE elevacao_total IS NOT NULL;

        RAISE NOTICE 'Migrated elevacao_total to elevacao_ganho_metros';
    END IF;
END$$;

-- Now drop the redundant columns (only if they exist)
ALTER TABLE tb_treino_realizado
DROP COLUMN IF EXISTS fc_max,
DROP COLUMN IF EXISTS comentario,
DROP COLUMN IF EXISTS ritmo_medio,
DROP COLUMN IF EXISTS elevacao_total;

-- ========================================
-- 2. Add zona_alvo to treino tables
-- ========================================

ALTER TABLE tb_treino_planejado
ADD COLUMN IF NOT EXISTS zona_alvo VARCHAR(50);

ALTER TABLE tb_treino_realizado
ADD COLUMN IF NOT EXISTS zona_alvo VARCHAR(50);

-- ========================================
-- 3. Create indexes for performance
-- ========================================

CREATE INDEX IF NOT EXISTS idx_treino_realizado_potencia
ON tb_treino_realizado(potencia_media)
WHERE potencia_media IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_treino_realizado_cadencia
ON tb_treino_realizado(cadencia_media)
WHERE cadencia_media IS NOT NULL;

-- ========================================
-- 4. Add column comments for documentation
-- ========================================

COMMENT ON COLUMN tb_treino_realizado.fc_maxima_treino IS 'Frequência cardíaca máxima atingida durante o treino (bpm)';
COMMENT ON COLUMN tb_treino_realizado.feedback_atleta IS 'Comentário/observação do atleta sobre o treino';
COMMENT ON COLUMN tb_treino_realizado.pace_media IS 'Ritmo médio em minutos por quilômetro (min/km) - decimal';
COMMENT ON COLUMN tb_treino_realizado.elevacao_ganho_metros IS 'Elevação acumulada (ganho) em metros';
COMMENT ON COLUMN tb_treino_realizado.cadencia_media IS 'Cadência média em passos por minuto (spm)';
COMMENT ON COLUMN tb_treino_realizado.potencia_media IS 'Potência média em watts';
COMMENT ON COLUMN tb_treino_realizado.zona_alvo IS 'Zona de treino alvo (ex: "z2-z3")';

COMMENT ON COLUMN tb_treino_planejado.zona_alvo IS 'Zona de treino alvo planejada (ex: "z2-z3", "limiar")';

-- ========================================
-- 5. Final validation
-- ========================================
DO $$
BEGIN
    RAISE NOTICE '✅ Migration V10 completed successfully';
    RAISE NOTICE 'Redundant columns removed from tb_treino_realizado:';
    RAISE NOTICE '  - fc_max (use fc_maxima_treino)';
    RAISE NOTICE '  - comentario (use feedback_atleta)';
    RAISE NOTICE '  - ritmo_medio (use pace_media)';
    RAISE NOTICE '  - elevacao_total (use elevacao_ganho_metros)';
    RAISE NOTICE 'Added zona_alvo to both treino tables';
END$$;