-- =====================================================================
-- V30: Adiciona campos de semântica TSB início/fim de dia em MetricasDiarias
--
-- tsbInicioDia = CTL_ontem - ATL_ontem (prontidão pré-treino)
-- tsbFimDia    = CTL_hoje  - ATL_hoje  (estado pós-carga)
-- =====================================================================

ALTER TABLE tb_metricas_diarias
    ADD COLUMN IF NOT EXISTS ctl_inicio_dia DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS atl_inicio_dia DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS tsb_inicio_dia DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS ctl_fim_dia    DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS atl_fim_dia    DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS tsb_fim_dia    DOUBLE PRECISION;

DO $$
BEGIN
    RAISE NOTICE '✅ V30 - Colunas tsb_inicio_dia/tsb_fim_dia adicionadas em tb_metricas_diarias';
END$$;
