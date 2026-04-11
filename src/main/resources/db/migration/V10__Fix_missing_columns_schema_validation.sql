-- =====================================================================
-- V10: Corrige colunas ausentes detectadas pelo Hibernate schema-validation
-- =====================================================================

-- ========================================
-- 1. tb_treino_realizado: coluna observacao (herdada de TreinoBase)
-- ========================================

ALTER TABLE tb_treino_realizado
    ADD COLUMN IF NOT EXISTS observacao VARCHAR(1000);

DO $$
BEGIN
    RAISE NOTICE '✅ V10 - Colunas ausentes corrigidas';
    RAISE NOTICE '   - tb_treino_realizado.observacao adicionada';
END$$;
