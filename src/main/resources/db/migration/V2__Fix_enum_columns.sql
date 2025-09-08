-- Migration to fix enum columns casting issues
-- This migration converts VARCHAR enum columns to use SMALLINT for JPA ordinal mapping

-- Fix status_treino column in tb_treino_planejado
-- First, convert existing string values to their ordinal equivalents
-- REALIZADO = 0, PERDIDO = 1, PARCIAL = 2, LIVRE = 3

-- Create a temporary column to store the converted values
ALTER TABLE tb_treino_planejado ADD COLUMN IF NOT EXISTS status_treino_temp SMALLINT;

-- Update temp column with ordinal values based on existing string values
UPDATE tb_treino_planejado
SET status_treino_temp = CASE
    WHEN status_treino = 'REALIZADO' THEN 0
    WHEN status_treino = 'PERDIDO' THEN 1
    WHEN status_treino = 'PARCIAL' THEN 2
    WHEN status_treino = 'LIVRE' THEN 3
    ELSE NULL
END;

-- Drop the old column
ALTER TABLE tb_treino_planejado DROP COLUMN IF EXISTS status_treino;

-- Rename temp column to the original name
ALTER TABLE tb_treino_planejado RENAME COLUMN status_treino_temp TO status_treino;

-- Also update tb_treino_realizado status column if it exists and has similar issues
-- Check if the column exists first and handle accordingly
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'tb_treino_realizado'
               AND column_name = 'status'
               AND data_type = 'character varying') THEN

        -- Add temp column
        ALTER TABLE tb_treino_realizado ADD COLUMN status_temp SMALLINT;

        -- Convert values
        UPDATE tb_treino_realizado
        SET status_temp = CASE
            WHEN status = 'REALIZADO' THEN 0
            WHEN status = 'PERDIDO' THEN 1
            WHEN status = 'PARCIAL' THEN 2
            WHEN status = 'LIVRE' THEN 3
            ELSE NULL
        END;

        -- Drop old and rename
        ALTER TABLE tb_treino_realizado DROP COLUMN status;
        ALTER TABLE tb_treino_realizado RENAME COLUMN status_temp TO status;
    END IF;
END $$;