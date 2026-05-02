-- =====================================================================
-- V20: Corrige tipos de ID de BIGINT para UUID nos campos de reconciliação
--      TreinoPlanejado usa UUID como chave primária
-- =====================================================================

-- ========================================
-- 1. CORRIGIR treino_planejado_id EM tb_treino_realizado
-- ========================================

ALTER TABLE tb_treino_realizado
    ALTER COLUMN treino_planejado_id SET DATA TYPE UUID USING NULL;

-- ========================================
-- 2. ADICIONAR COLUNAS UUID EM tb_treino_reconciliacao
-- ========================================

ALTER TABLE tb_treino_reconciliacao
    ADD COLUMN IF NOT EXISTS before_planned_id_uuid UUID,
    ADD COLUMN IF NOT EXISTS after_planned_id_uuid UUID;

-- ========================================
-- 3. LOGGING
-- ========================================

DO $$
BEGIN
    RAISE NOTICE '✅ V20 - IDs de reconciliação corrigidos para UUID';
    RAISE NOTICE '   - treino_planejado_id: BIGINT → UUID';
    RAISE NOTICE '   - before_planned_id_uuid: adicionado em tb_treino_reconciliacao';
    RAISE NOTICE '   - after_planned_id_uuid: adicionado em tb_treino_reconciliacao';
END$$;
