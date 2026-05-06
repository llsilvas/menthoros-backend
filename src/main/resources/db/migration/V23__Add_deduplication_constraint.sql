-- =====================================================================
-- V23: Adiciona constraint UNIQUE composto para deduplicação
--      Garante que (externalId, atletaId) é único em tb_treino_realizado
-- =====================================================================

-- Passo 1: Remover índice antigo (apenas externalId)
DROP INDEX IF EXISTS uk_treino_realizado_external_id;

-- Passo 2: Criar índice UNIQUE composto (externalId, atletaId)
--         Implementa deduplicação em nível de BD
--         Task 1.2: Garantir deduplicação por externalId + atletaId

CREATE UNIQUE INDEX IF NOT EXISTS uk_treino_realizado_external_id_atleta
    ON tb_treino_realizado(external_id, atleta_id)
    WHERE external_id IS NOT NULL;

-- Passo 3: Logging

DO $$
BEGIN
    RAISE NOTICE '✅ V23 — Deduplication constraint added';
    RAISE NOTICE '   - Index: uk_treino_realizado_external_id_atleta (external_id, atleta_id)';
    RAISE NOTICE '   - Garantido: (externalId, atletaId) é único';
    RAISE NOTICE '   - Task 1.2: Deduplicação por externalId + atletaId implementada';
END$$;
