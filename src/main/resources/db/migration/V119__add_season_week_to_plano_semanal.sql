-- =====================================================================
-- V119: Semana da temporada no plano semanal (add-season-plan-weekly-context)
--
-- season_week_id: a semana da temporada ACTIVE que serviu de contexto à geração. Nulo em plano
--   gerado sem temporada (e em todo plano anterior a esta change). ON DELETE SET NULL: apagar a
--   temporada não apaga o plano semanal já gerado.
-- target_deviation_pct: (TSS planejado - target_tss da semana) / target_tss * 100, uma casa.
--   Nulo sem temporada ou com target_tss nulo/zero.
--
-- Aditiva, sem backfill. Rollback (V nova): remover o índice e as duas colunas.
-- =====================================================================

ALTER TABLE tb_plano_semanal
    ADD COLUMN IF NOT EXISTS season_week_id UUID
        CONSTRAINT fk_plano_semanal_season_week REFERENCES tb_season_week (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS target_deviation_pct NUMERIC(6, 1);

CREATE INDEX IF NOT EXISTS idx_plano_semanal_season_week
    ON tb_plano_semanal (season_week_id);

DO $$
BEGIN
    RAISE NOTICE 'V119 - season_week_id e target_deviation_pct em tb_plano_semanal';
END$$;
