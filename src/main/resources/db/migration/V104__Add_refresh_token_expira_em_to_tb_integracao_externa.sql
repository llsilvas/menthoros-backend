-- =====================================================================
-- V104: Expiração do refresh token em tb_integracao_externa (garmin-connection)
-- Aditivo, sem backfill. Só o Garmin rotaciona refresh token com prazo (~90 dias);
-- Strava e intervals.icu ficam NULL.
-- =====================================================================

ALTER TABLE tb_integracao_externa
    ADD COLUMN IF NOT EXISTS refresh_token_expira_em TIMESTAMPTZ;

COMMENT ON COLUMN tb_integracao_externa.refresh_token_expira_em IS
    'Instante em que o refresh token deixa de valer no provedor. Gravado só no refresh; '
    'o job diário renova conexões GARMIN com valor a menos de 30 dias. NULL nas demais plataformas.';

DO $$
BEGIN
    RAISE NOTICE '✅ V104 - coluna refresh_token_expira_em adicionada a tb_integracao_externa';
END$$;
