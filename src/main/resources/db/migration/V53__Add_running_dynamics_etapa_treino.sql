-- =====================================================================
-- V53: Running dynamics + contexto por etapa e sessão (import .fit)
--
-- Tempo de contato com o solo (GCT), equilíbrio E/D, comprimento de
-- passada, oscilação e proporção vertical, temperatura, tempo em
-- movimento e calorias — dado que o .fit já carrega e o Menthoros
-- descartava no parse. Colunas nullable: running dynamics é opcional
-- por natureza (depende de sensor HRM-Pro/RD-Pod ou relógio recente).
--
-- tempo_movimento (por lap e por sessão) também alimenta a correção de
-- pace/velocidade em laps com pausa (fit-running-dynamics-ingestion,
-- design D6) — não é só payload de leitura do drilldown.
--
-- Rollback:
--   ALTER TABLE tb_etapa_realizada
--       DROP COLUMN IF EXISTS gct_medio_ms, DROP COLUMN IF EXISTS gct_equilibrio_pct,
--       DROP COLUMN IF EXISTS passada_media_m, DROP COLUMN IF EXISTS oscilacao_vertical_cm,
--       DROP COLUMN IF EXISTS proporcao_vertical_pct, DROP COLUMN IF EXISTS temperatura_media_c,
--       DROP COLUMN IF EXISTS tempo_movimento;
--   ALTER TABLE tb_treino_realizado
--       DROP COLUMN IF EXISTS gct_medio_ms, DROP COLUMN IF EXISTS gct_equilibrio_pct,
--       DROP COLUMN IF EXISTS passada_media_m, DROP COLUMN IF EXISTS oscilacao_vertical_cm,
--       DROP COLUMN IF EXISTS proporcao_vertical_pct, DROP COLUMN IF EXISTS temperatura_media_c,
--       DROP COLUMN IF EXISTS tempo_movimento, DROP COLUMN IF EXISTS calorias;
-- Feature aditiva — nenhum dado existente é alterado ou removido.
-- =====================================================================

ALTER TABLE tb_etapa_realizada
    ADD COLUMN IF NOT EXISTS gct_medio_ms           INTEGER,
    ADD COLUMN IF NOT EXISTS gct_equilibrio_pct     NUMERIC(4,1),
    ADD COLUMN IF NOT EXISTS passada_media_m        NUMERIC(4,2),
    ADD COLUMN IF NOT EXISTS oscilacao_vertical_cm  NUMERIC(4,1),
    ADD COLUMN IF NOT EXISTS proporcao_vertical_pct NUMERIC(4,1),
    ADD COLUMN IF NOT EXISTS temperatura_media_c    NUMERIC(4,1),
    ADD COLUMN IF NOT EXISTS tempo_movimento        INTERVAL;

ALTER TABLE tb_treino_realizado
    ADD COLUMN IF NOT EXISTS gct_medio_ms           INTEGER,
    ADD COLUMN IF NOT EXISTS gct_equilibrio_pct     NUMERIC(4,1),
    ADD COLUMN IF NOT EXISTS passada_media_m        NUMERIC(4,2),
    ADD COLUMN IF NOT EXISTS oscilacao_vertical_cm  NUMERIC(4,1),
    ADD COLUMN IF NOT EXISTS proporcao_vertical_pct NUMERIC(4,1),
    ADD COLUMN IF NOT EXISTS temperatura_media_c    NUMERIC(4,1),
    ADD COLUMN IF NOT EXISTS tempo_movimento        INTERVAL,
    ADD COLUMN IF NOT EXISTS calorias               INTEGER;

DO $$
BEGIN
    RAISE NOTICE '✅ V53 - running dynamics + contexto adicionados a tb_etapa_realizada e tb_treino_realizado';
END$$;
