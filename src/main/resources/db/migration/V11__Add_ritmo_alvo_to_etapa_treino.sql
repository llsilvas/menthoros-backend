-- =====================================================================
-- V11: Adiciona coluna ritmo_alvo em tb_etapa_treino
--      Mapeada em EtapaTreino.ritmoAlvo desde P1.2 (backlog assertividade)
-- =====================================================================

ALTER TABLE tb_etapa_treino
    ADD COLUMN IF NOT EXISTS ritmo_alvo VARCHAR(50);

DO $$
BEGIN
    RAISE NOTICE '✅ V11 - ritmo_alvo adicionado em tb_etapa_treino';
END$$;
