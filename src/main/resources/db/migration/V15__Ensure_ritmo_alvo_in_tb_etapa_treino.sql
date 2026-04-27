-- =====================================================================
-- V15: Garantia defensiva para coluna ritmo_alvo em tb_etapa_treino
--
-- Contexto:
-- Alguns ambientes legados ficaram sem a coluna mapeada em EtapaTreino.ritmoAlvo,
-- causando falha no Hibernate schema-validation durante o boot.
-- =====================================================================

ALTER TABLE tb_etapa_treino
    ADD COLUMN IF NOT EXISTS ritmo_alvo VARCHAR(50);

DO $$
BEGIN
    RAISE NOTICE '✅ V15 - Garantida coluna tb_etapa_treino.ritmo_alvo';
END$$;
