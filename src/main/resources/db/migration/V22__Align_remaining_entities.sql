-- V22: Alinha tb_prova, tb_treino_planejado e tb_treino_realizado com as entidades

-- ============================================================
-- 1. tb_prova: adicionar colunas ausentes
-- ============================================================
ALTER TABLE tb_prova
    ADD COLUMN IF NOT EXISTS distancia               VARCHAR(50),
    ADD COLUMN IF NOT EXISTS tempo_objetivo          TIME,
    ADD COLUMN IF NOT EXISTS pace_objetivo           DECIMAL(5, 2),
    ADD COLUMN IF NOT EXISTS tsb_ideal_prova         DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS foi_realizada           BOOLEAN          DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS tempo_realizado         TIME,
    ADD COLUMN IF NOT EXISTS posicao_geral           INTEGER,
    ADD COLUMN IF NOT EXISTS posicao_categoria       INTEGER,
    ADD COLUMN IF NOT EXISTS tss_prova               INTEGER,
    ADD COLUMN IF NOT EXISTS percepcao_esforco_prova INTEGER,
    ADD COLUMN IF NOT EXISTS feedback_prova          TEXT,
    ADD COLUMN IF NOT EXISTS semanas_preparacao      INTEGER,
    ADD COLUMN IF NOT EXISTS inicio_preparacao       DATE;

-- ============================================================
-- 2. tb_treino_planejado: fix status_treino e adicionar colunas
-- ============================================================

-- status_treino estava como smallint (ordinal), entidade espera VARCHAR (STRING)
-- Não há dados nesta tabela — conversão direta é segura
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_planejado'
          AND column_name = 'status_treino'
          AND data_type = 'smallint'
    ) THEN
        ALTER TABLE tb_treino_planejado
            ALTER COLUMN status_treino TYPE VARCHAR(50) USING 'PENDENTE';
    END IF;
END $$;

ALTER TABLE tb_treino_planejado
    ADD COLUMN IF NOT EXISTS tss_planejado                  INTEGER,
    ADD COLUMN IF NOT EXISTS intensidade_planejada          DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS justificativa_ia               TEXT,
    ADD COLUMN IF NOT EXISTS fonte_dados                    VARCHAR(50),
    ADD COLUMN IF NOT EXISTS url_externo                    VARCHAR(500),
    ADD COLUMN IF NOT EXISTS status_sincronizacao           VARCHAR(50),
    ADD COLUMN IF NOT EXISTS sincronizado_em                TIMESTAMP,
    ADD COLUMN IF NOT EXISTS ultima_tentativa_sincronizacao TIMESTAMP,
    ADD COLUMN IF NOT EXISTS tentativas_sincronizacao       INTEGER          DEFAULT 0,
    ADD COLUMN IF NOT EXISTS exportado_para                 TEXT,
    ADD COLUMN IF NOT EXISTS erro_sincronizacao             TEXT,
    ADD COLUMN IF NOT EXISTS metadados_sincronizacao        TEXT;

-- ============================================================
-- 3. tb_treino_realizado: fix status, pace_media e adicionar colunas
-- ============================================================

-- status estava como smallint, entidade espera VARCHAR
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
          AND column_name = 'status'
          AND data_type = 'smallint'
    ) THEN
        ALTER TABLE tb_treino_realizado
            ALTER COLUMN status TYPE VARCHAR(50) USING 'PENDENTE';
    END IF;
END $$;

-- pace_media estava como numeric(5,2) — V14 só convertia de double precision
-- Converter para interval (compatível com java.time.Duration)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_treino_realizado'
          AND column_name = 'pace_media'
          AND data_type = 'numeric'
    ) THEN
        ALTER TABLE tb_treino_realizado ADD COLUMN pace_media_tmp INTERVAL;
        UPDATE tb_treino_realizado
        SET pace_media_tmp = make_interval(
            mins => FLOOR(pace_media)::INTEGER,
            secs => ((pace_media - FLOOR(pace_media)) * 60)::INTEGER
        )
        WHERE pace_media IS NOT NULL;
        ALTER TABLE tb_treino_realizado DROP COLUMN pace_media;
        ALTER TABLE tb_treino_realizado RENAME COLUMN pace_media_tmp TO pace_media;
    END IF;
END $$;

ALTER TABLE tb_treino_realizado
    ADD COLUMN IF NOT EXISTS tss_calculado                 INTEGER,
    ADD COLUMN IF NOT EXISTS metodo_calculo_tss            VARCHAR(50),
    ADD COLUMN IF NOT EXISTS velocidade_media              DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS intensidade_real              DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS qualidade_sono_noite_anterior INTEGER,
    ADD COLUMN IF NOT EXISTS nivel_estresse                INTEGER;
