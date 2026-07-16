-- =====================================================================
-- V54: adiciona auto_sync_pausado a tb_integracao_externa
--
-- Flag por atleta que pausa a sincronizacao automatica do Strava (scheduler
-- diario + webhook em tempo real) quando o atleta tambem esta ativo no
-- intervals.icu, evitando duplicacao cross-fonte de TreinoRealizado. Setada
-- automaticamente ao conectar qualquer uma das duas integracoes
-- (IntervalsIcuConnectionServiceImpl.conectar, StravaOAuthServiceImpl
-- .exchangeCodeForToken); os endpoints pausar-sync/retomar-sync permanecem
-- como override explicito do coach.
--
-- NOT NULL DEFAULT false: linhas existentes assumem "nao pausado" (sem
-- backfill necessario -- o comportamento pre-change era sempre sincronizar).
--
-- Rollback: ALTER TABLE tb_integracao_externa DROP COLUMN IF EXISTS auto_sync_pausado;
-- Feature aditiva pura -- sem impacto em dado existente; reversao segura.
-- =====================================================================

ALTER TABLE tb_integracao_externa
    ADD COLUMN IF NOT EXISTS auto_sync_pausado BOOLEAN NOT NULL DEFAULT false;

DO $$
BEGIN
    RAISE NOTICE '✅ V54 - coluna auto_sync_pausado adicionada a tb_integracao_externa';
END$$;
