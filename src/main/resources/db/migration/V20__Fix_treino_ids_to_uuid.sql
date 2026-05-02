-- =====================================================================
-- V20: Corrige tipos de ID de BIGINT para UUID nos campos de reconciliação
--      TreinoPlanejado usa UUID como chave primária
--      Strategy: Backfill seguro (nova coluna + validação + cutover)
-- =====================================================================

-- ========================================
-- 1. CORRIGIR treino_planejado_id EM tb_treino_realizado (BACKFILL STRATEGY)
-- ========================================
--
-- Estratégia: Validar ambiente vazio → Nova coluna UUID → Cutover
-- (Evita USING NULL direto que seria silenciosamente destrutivo)
--
-- BLOQUEADOR AMBIENTAL: Falha se houver dados existentes
-- Razão: BIGINT → UUID sem mapeamento seguro. Requer ação manual:
-- - Backup antes de executar
-- - OU esperar confirmação formal que ambiente está vazio
--

DO $$
DECLARE
    existing_count INTEGER;
BEGIN
    -- Verificar se há dados a perder ANTES de alterar estrutura
    SELECT COUNT(*) INTO existing_count
    FROM tb_treino_realizado
    WHERE treino_planejado_id IS NOT NULL;

    IF existing_count > 0 THEN
        RAISE EXCEPTION
            'V20 MIGRATION BLOCKED: % registro(s) com treino_planejado_id existente(s) '
            'não podem ser convertidos de BIGINT para UUID (tipos incompatíveis). '
            'Ação necessária: '
            '1) Fazer backup completo do banco '
            '2) Validar que é ambiente descartável/vazio '
            '3) Ou implementar estratégia de backfill com mapping table '
            '4) Contactar DevOps antes de retentar.',
            existing_count;
    END IF;

    -- Se chegou aqui, ambiente está vazio - seguro prosseguir
    RAISE NOTICE 'V20 MIGRATION: Ambiente vazio confirmado. Prosseguindo com conversão segura.';
END$$;

--
-- Passo 1: Renomear coluna BIGINT antiga (preserva estado enquanto nova é preparada)
--

ALTER TABLE tb_treino_realizado
    RENAME COLUMN treino_planejado_id TO treino_planejado_id_bigint_old;

--
-- Passo 2: Adicionar nova coluna UUID (nullable, sem default)
--

ALTER TABLE tb_treino_realizado
    ADD COLUMN treino_planejado_id UUID;

--
-- Passo 3: Cutover — remover coluna BIGINT antiga (agora seguro, ambiente validado)
--

ALTER TABLE tb_treino_realizado
    DROP COLUMN treino_planejado_id_bigint_old;

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
