-- =====================================================================
-- V14: Adiciona campos Strava em tb_etapa_realizada
--      elevacao_ganho_metros e elevacao_perda_metros já existem via TreinoBase
--      split_index identifica a ordem do split no payload Strava
-- =====================================================================

ALTER TABLE tb_etapa_realizada
    ADD COLUMN IF NOT EXISTS split_index INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tb_etapa_realizada'
          AND column_name = 'elevacao_ganho_metros'
    ) THEN
        ALTER TABLE tb_etapa_realizada
            ADD COLUMN elevacao_ganho_metros INTEGER,
            ADD COLUMN elevacao_perda_metros INTEGER;
    END IF;
END $$;

DO $$
BEGIN
    RAISE NOTICE '✅ V14 - Campos Strava adicionados em tb_etapa_realizada';
    RAISE NOTICE '   - split_index (ordem do split no payload Strava)';
    RAISE NOTICE '   - elevacao_ganho_metros, elevacao_perda_metros (se ausentes)';
END$$;
