-- =====================================================================
-- V31: Adiciona campos de semântica TSB prontidão/pós-carga em PlanoMetaDados
--
-- tsb_prontidao_atual = TSB pré-treino (prontidão para treinar hoje)
-- tsb_pos_carga_atual  = TSB pós-treino (estado após carga do dia)
-- =====================================================================

ALTER TABLE tb_plano_metadados
    ADD COLUMN IF NOT EXISTS tsb_prontidao_atual  DOUBLE PRECISION DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS tsb_pos_carga_atual   DOUBLE PRECISION DEFAULT 0.0;

DO $$
BEGIN
    RAISE NOTICE '✅ V31 - Colunas tsb_prontidao_atual/tsb_pos_carga_atual adicionadas em tb_plano_metadados';
END$$;
