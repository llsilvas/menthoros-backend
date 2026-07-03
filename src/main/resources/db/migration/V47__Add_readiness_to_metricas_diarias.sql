-- =====================================================================
-- V47: Adiciona readiness_score e nivel_prontidao em tb_metricas_diarias
--
-- Rollback (se necessário):
--   ALTER TABLE tb_metricas_diarias
--       DROP COLUMN IF EXISTS readiness_score,
--       DROP COLUMN IF EXISTS nivel_prontidao;
-- Como esta migration é apenas ADD, o forward não corrompe dado existente.
-- =====================================================================

ALTER TABLE tb_metricas_diarias
    ADD COLUMN IF NOT EXISTS readiness_score NUMERIC(4,3),
    ADD COLUMN IF NOT EXISTS nivel_prontidao VARCHAR(20);

DO $$
BEGIN
    RAISE NOTICE '✅ V47 - Colunas readiness_score/nivel_prontidao adicionadas em tb_metricas_diarias';
END$$;
