-- =====================================================================
-- V40: Adiciona campos de inferência de limiares à tb_plano_metadados
-- =====================================================================
ALTER TABLE tb_plano_metadados
    ADD COLUMN IF NOT EXISTS fc_limiar_estimado        INTEGER,
    ADD COLUMN IF NOT EXISTS pace_limiar_estimado      DECIMAL(5,4),
    ADD COLUMN IF NOT EXISTS confianca_inferencia_fc   VARCHAR(10),
    ADD COLUMN IF NOT EXISTS confianca_inferencia_pace VARCHAR(10),
    ADD COLUMN IF NOT EXISTS data_inferencia_limiar    DATE;

DO $$
BEGIN
    RAISE NOTICE '✅ V40 - campos de inferência de limiares adicionados a tb_plano_metadados';
END$$;
