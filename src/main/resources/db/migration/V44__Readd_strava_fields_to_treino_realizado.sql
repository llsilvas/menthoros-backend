-- =====================================================================
-- V44: Re-garante os campos Strava em tb_treino_realizado (heal de drift)
--
-- Contexto: a V13 adicionou estas colunas, e o flyway_schema_history de
-- produção marca a V13 como aplicada ("up to date" na versão 43). Porém as
-- colunas não existem na tabela em produção — schema e histórico ficaram
-- dessincronizados (provável restore/baseline de um dump antigo carregando o
-- histórico junto). Como o Flyway considera a V13 já aplicada, ele não a
-- re-executa, e toda query em tb_treino_realizado falha com
-- "column device_name does not exist" (SQLState 42703) -> HTTP 500.
--
-- Esta migration é forward-only e idempotente (ADD COLUMN IF NOT EXISTS):
-- no-op onde as colunas já existem (dev/test/local), e as recria onde faltam
-- (produção). Mesma definição de tipos/limites da V13.
-- =====================================================================

ALTER TABLE tb_treino_realizado
    ADD COLUMN IF NOT EXISTS sincronizado_em         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS url_externo             VARCHAR(500),
    ADD COLUMN IF NOT EXISTS metadados_sincronizacao TEXT,
    ADD COLUMN IF NOT EXISTS elapsed_time_seg        INTEGER,
    ADD COLUMN IF NOT EXISTS suffer_score            INTEGER,
    ADD COLUMN IF NOT EXISTS device_name             VARCHAR(200),
    ADD COLUMN IF NOT EXISTS gear_name               VARCHAR(200);

DO $$
BEGIN
    RAISE NOTICE '✅ V44 - Campos Strava re-garantidos em tb_treino_realizado (heal de drift da V13)';
END$$;
