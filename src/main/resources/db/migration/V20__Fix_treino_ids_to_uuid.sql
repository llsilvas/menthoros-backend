-- =====================================================================
-- V20: Corrige tipos de ID de BIGINT para UUID nos campos de reconciliação
--      TreinoPlanejado usa UUID como chave primária
-- =====================================================================

-- ========================================
-- 1. CORRIGIR treino_planejado_id EM tb_treino_realizado
-- ========================================
--
-- ⚠️ AVISO CRÍTICO: USING NULL DESTRÓI DADOS EXISTENTES
--
-- Esta migração converte treino_planejado_id de BIGINT para UUID usando NULL.
-- Isso significa: TODOS os valores existentes serão zerados.
--
-- Contexto MVP:
-- - Tabela criada recentemente (V17) para reconciliação Strava
-- - Nenhum dado em produção ainda (MVP)
-- - Uso seguro por enquanto
--
-- Futuro (pós-MVP):
-- - Se houver dados a preservar, necessário backfill com conversão UUID segura
-- - Usar migração separada com COPY + validação de integridade
--
-- Assinado: Code Review com BLOCKER identificado e resolvido
-- Data: 2026-05-01

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
