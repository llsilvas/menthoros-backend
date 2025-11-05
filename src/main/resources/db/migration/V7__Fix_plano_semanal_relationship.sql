-- =====================================================================
-- Migration V7: Fix PlanoSemanal → PlanoMetaDados relationship
-- =====================================================================
-- Changes @OneToOne to @ManyToOne relationship
-- Allows multiple PlanoSemanal records to reference the same PlanoMetaDados (history)
-- =====================================================================

-- 1. Drop any UNIQUE constraint on plano_metadados_id if exists
-- This allows multiple PlanoSemanal to share the same PlanoMetaDados
DO $$
BEGIN
    -- Drop unique constraint if it exists (name varies by creation method)
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname LIKE '%plano_metadados%'
        AND contype = 'u'
        AND conrelid = 'tb_plano_semanal'::regclass
    ) THEN
        EXECUTE (
            SELECT 'ALTER TABLE tb_plano_semanal DROP CONSTRAINT ' || conname || ';'
            FROM pg_constraint
            WHERE conname LIKE '%plano_metadados%'
            AND contype = 'u'
            AND conrelid = 'tb_plano_semanal'::regclass
        );
        RAISE NOTICE 'Unique constraint on plano_metadados_id dropped successfully';
    END IF;

    -- Drop unique index if exists
    IF EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE indexdef LIKE '%UNIQUE%'
        AND indexdef LIKE '%plano_metadados_id%'
        AND tablename = 'tb_plano_semanal'
    ) THEN
        EXECUTE (
            SELECT 'DROP INDEX IF EXISTS ' || indexname || ';'
            FROM pg_indexes
            WHERE indexdef LIKE '%UNIQUE%'
            AND indexdef LIKE '%plano_metadados_id%'
            AND tablename = 'tb_plano_semanal'
        );
        RAISE NOTICE 'Unique index on plano_metadados_id dropped successfully';
    END IF;
END$$;

-- 2. Ensure the column exists (it might have been created by Hibernate)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_plano_semanal'
        AND column_name = 'plano_metadados_id'
    ) THEN
        ALTER TABLE tb_plano_semanal
        ADD COLUMN plano_metadados_id UUID;

        RAISE NOTICE 'Column plano_metadados_id added to tb_plano_semanal';
    END IF;
END$$;

-- 3. Set plano_metadados_id as NOT NULL (business rule)
ALTER TABLE tb_plano_semanal
ALTER COLUMN plano_metadados_id SET NOT NULL;

-- 4. Add foreign key constraint if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_plano_semanal_metadados'
    ) THEN
        ALTER TABLE tb_plano_semanal
        ADD CONSTRAINT fk_plano_semanal_metadados
        FOREIGN KEY (plano_metadados_id)
        REFERENCES tb_plano_metadados(id)
        ON DELETE RESTRICT;

        RAISE NOTICE 'Foreign key constraint fk_plano_semanal_metadados created';
    END IF;
END$$;

-- 5. Create index for better query performance (many-to-one relationship)
CREATE INDEX IF NOT EXISTS idx_plano_semanal_metadados
ON tb_plano_semanal(plano_metadados_id);

-- 6. Add missing columns to tb_plano_semanal if they don't exist
DO $$
BEGIN
    -- Add semana_inicio if not exists
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_plano_semanal'
        AND column_name = 'semana_inicio'
    ) THEN
        ALTER TABLE tb_plano_semanal
        ADD COLUMN semana_inicio DATE;

        -- Migrate data from data_inicio if exists
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'tb_plano_semanal'
            AND column_name = 'data_inicio'
        ) THEN
            UPDATE tb_plano_semanal SET semana_inicio = data_inicio WHERE semana_inicio IS NULL;
        END IF;

        ALTER TABLE tb_plano_semanal ALTER COLUMN semana_inicio SET NOT NULL;
        RAISE NOTICE 'Column semana_inicio added to tb_plano_semanal';
    END IF;

    -- Add semana_fim if not exists
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_plano_semanal'
        AND column_name = 'semana_fim'
    ) THEN
        ALTER TABLE tb_plano_semanal
        ADD COLUMN semana_fim DATE;

        -- Migrate data from data_fim if exists
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'tb_plano_semanal'
            AND column_name = 'data_fim'
        ) THEN
            UPDATE tb_plano_semanal SET semana_fim = data_fim WHERE semana_fim IS NULL;
        END IF;

        ALTER TABLE tb_plano_semanal ALTER COLUMN semana_fim SET NOT NULL;
        RAISE NOTICE 'Column semana_fim added to tb_plano_semanal';
    END IF;

    -- Add volume columns
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_plano_semanal'
        AND column_name = 'volume_planejado_km'
    ) THEN
        ALTER TABLE tb_plano_semanal
        ADD COLUMN volume_planejado_km DECIMAL(10,3) NOT NULL DEFAULT 0;
        RAISE NOTICE 'Column volume_planejado_km added to tb_plano_semanal';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_plano_semanal'
        AND column_name = 'volume_realizado_km'
    ) THEN
        ALTER TABLE tb_plano_semanal
        ADD COLUMN volume_realizado_km DECIMAL(10,3);
        RAISE NOTICE 'Column volume_realizado_km added to tb_plano_semanal';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_plano_semanal'
        AND column_name = 'volume_alvo_km'
    ) THEN
        ALTER TABLE tb_plano_semanal
        ADD COLUMN volume_alvo_km DECIMAL(10,3);
        RAISE NOTICE 'Column volume_alvo_km added to tb_plano_semanal';
    END IF;

    -- Add TSB columns
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_plano_semanal'
        AND column_name = 'tsb_inicio'
    ) THEN
        ALTER TABLE tb_plano_semanal
        ADD COLUMN tsb_inicio DECIMAL(10,3);
        RAISE NOTICE 'Column tsb_inicio added to tb_plano_semanal';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_plano_semanal'
        AND column_name = 'tsb_fim'
    ) THEN
        ALTER TABLE tb_plano_semanal
        ADD COLUMN tsb_fim DECIMAL(10,3);
        RAISE NOTICE 'Column tsb_fim added to tb_plano_semanal';
    END IF;

    -- Add status column
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_plano_semanal'
        AND column_name = 'status'
    ) THEN
        ALTER TABLE tb_plano_semanal
        ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'RASCUNHO';
        RAISE NOTICE 'Column status added to tb_plano_semanal';
    END IF;

    -- Add objetivo_semana column
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_plano_semanal'
        AND column_name = 'objetivo_semana'
    ) THEN
        ALTER TABLE tb_plano_semanal
        ADD COLUMN objetivo_semana VARCHAR(500);
        RAISE NOTICE 'Column objetivo_semana added to tb_plano_semanal';
    END IF;

    -- Add versao column for optimistic locking
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_plano_semanal'
        AND column_name = 'versao'
    ) THEN
        ALTER TABLE tb_plano_semanal
        ADD COLUMN versao BIGINT DEFAULT 0;
        RAISE NOTICE 'Column versao added to tb_plano_semanal';
    END IF;
END$$;

-- 7. Add comment explaining the relationship
COMMENT ON COLUMN tb_plano_semanal.plano_metadados_id IS
'Reference to PlanoMetaDados (Many-to-One). Multiple PlanoSemanal records can share the same PlanoMetaDados for historical tracking.';

-- Final message
DO $$
BEGIN
    RAISE NOTICE '✅ Migration V7 completed successfully';
    RAISE NOTICE 'PlanoSemanal → PlanoMetaDados relationship is now Many-to-One';
    RAISE NOTICE 'Multiple weekly plans can now reference the same metadata snapshot';
END$$;