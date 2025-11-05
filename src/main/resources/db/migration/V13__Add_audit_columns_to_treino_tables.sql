-- =====================================================================
-- Migration V13: Add audit columns to treino tables
-- =====================================================================
-- Adds criado_em, atualizado_em, criado_por columns to both
-- tb_treino_planejado and tb_treino_realizado
-- =====================================================================

-- ========================================
-- 1. TREINO_PLANEJADO: Add audit columns
-- ========================================
DO $$
BEGIN
    -- Add criado_em
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_planejado'
        AND column_name = 'criado_em'
    ) THEN
        ALTER TABLE tb_treino_planejado
        ADD COLUMN criado_em TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

        COMMENT ON COLUMN tb_treino_planejado.criado_em IS 'Data/hora de criação do registro';
        RAISE NOTICE '✅ Added criado_em to tb_treino_planejado';
    END IF;

    -- Add atualizado_em
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_planejado'
        AND column_name = 'atualizado_em'
    ) THEN
        ALTER TABLE tb_treino_planejado
        ADD COLUMN atualizado_em TIMESTAMP WITHOUT TIME ZONE;

        COMMENT ON COLUMN tb_treino_planejado.atualizado_em IS 'Data/hora da última atualização';
        RAISE NOTICE '✅ Added atualizado_em to tb_treino_planejado';
    END IF;

    -- Add criado_por
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_planejado'
        AND column_name = 'criado_por'
    ) THEN
        ALTER TABLE tb_treino_planejado
        ADD COLUMN criado_por VARCHAR(50);

        COMMENT ON COLUMN tb_treino_planejado.criado_por IS 'Origem da criação: IA, USUARIO, GARMIN, STRAVA, etc';
        RAISE NOTICE '✅ Added criado_por to tb_treino_planejado';
    END IF;
END$$;

-- ========================================
-- 2. TREINO_REALIZADO: Add audit columns
-- ========================================
DO $$
BEGIN
    -- Add criado_em
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'criado_em'
    ) THEN
        ALTER TABLE tb_treino_realizado
        ADD COLUMN criado_em TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

        COMMENT ON COLUMN tb_treino_realizado.criado_em IS 'Data/hora de criação do registro';
        RAISE NOTICE '✅ Added criado_em to tb_treino_realizado';
    END IF;

    -- Add atualizado_em
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'atualizado_em'
    ) THEN
        ALTER TABLE tb_treino_realizado
        ADD COLUMN atualizado_em TIMESTAMP WITHOUT TIME ZONE;

        COMMENT ON COLUMN tb_treino_realizado.atualizado_em IS 'Data/hora da última atualização';
        RAISE NOTICE '✅ Added atualizado_em to tb_treino_realizado';
    END IF;

    -- Add criado_por
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
        AND column_name = 'criado_por'
    ) THEN
        ALTER TABLE tb_treino_realizado
        ADD COLUMN criado_por VARCHAR(50);

        COMMENT ON COLUMN tb_treino_realizado.criado_por IS 'Origem da criação: IA, USUARIO, GARMIN, STRAVA, etc';
        RAISE NOTICE '✅ Added criado_por to tb_treino_realizado';
    END IF;
END$$;

-- ========================================
-- 3. Final validation
-- ========================================
DO $$
BEGIN
    RAISE NOTICE '✅ Migration V13 completed successfully';
    RAISE NOTICE 'Audit columns added to both treino tables';
END$$;
