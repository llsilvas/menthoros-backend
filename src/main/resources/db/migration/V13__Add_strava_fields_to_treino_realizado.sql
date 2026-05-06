-- =====================================================================
-- V13: Adiciona campos Strava em tb_treino_realizado
--      status_sincronizacao já existe via V8 (StatusSincronizacao enum)
-- =====================================================================

ALTER TABLE tb_treino_realizado
    ADD COLUMN IF NOT EXISTS sincronizado_em       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS url_externo           VARCHAR(500),
    ADD COLUMN IF NOT EXISTS metadados_sincronizacao TEXT,
    ADD COLUMN IF NOT EXISTS elapsed_time_seg      INTEGER,
    ADD COLUMN IF NOT EXISTS suffer_score          INTEGER,
    ADD COLUMN IF NOT EXISTS device_name           VARCHAR(200),
    ADD COLUMN IF NOT EXISTS gear_name             VARCHAR(200);

DO $$
BEGIN
    RAISE NOTICE '✅ V13 - Campos Strava adicionados em tb_treino_realizado';
    RAISE NOTICE '   - sincronizado_em, url_externo, metadados_sincronizacao';
    RAISE NOTICE '   - elapsed_time_seg, suffer_score, device_name, gear_name';
END$$;
