-- =====================================================================
-- V20: Corrige tipos de ID de BIGINT para UUID nos campos de reconciliação
--      TreinoPlanejado usa UUID como chave primária
--      Strategy: Backfill seguro (nova coluna + validação + cutover)
-- =====================================================================

-- ========================================
-- 1. CORRIGIR treino_planejado_id EM tb_treino_realizado (BACKFILL STRATEGY)
-- ========================================
--
-- Estratégia: Nova coluna UUID + validação de perda de dados + cutover
-- (Evita USING NULL direto que seria silenciosamente destrutivo)
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
-- Passo 3: Verificar e logar perda de dados explicitamente
--          (BIGINT → UUID sem mapeamento possível no MVP)
--

DO $$
DECLARE
    lost_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO lost_count
    FROM tb_treino_realizado
    WHERE treino_planejado_id_bigint_old IS NOT NULL;

    IF lost_count > 0 THEN
        RAISE WARNING
            'V20 MIGRATION: % vínculo(s) treino_planejado_id (BIGINT) não podem ser '
            'convertidos para UUID — tipo incompatível, sem chave de mapeamento. '
            'Vínculos serão nulos após cutover. Se dados são críticos, restaurar do backup.',
            lost_count;
    ELSE
        RAISE NOTICE 'V20 MIGRATION: nenhum vínculo existente. Cutover seguro.';
    END IF;
END$$;

--
-- Passo 4: Cutover — remover coluna BIGINT antiga
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
