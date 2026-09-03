-- =====================================================================
-- V88: vínculo TreinoPlanejado -> Prova (prova-no-plano-semanal, D1)
-- =====================================================================
--
-- A prova aparece no plano da semana como um TreinoPlanejado do tipo PROVA. O vínculo é
-- persistido (não inferido por data+tipo) porque duas provas na semana ou uma prova movida
-- quebrariam a inferência. ON DELETE SET NULL cobre a deleção física por ADMIN — o treino
-- fica órfão em vez de impedir a exclusão da prova.
--
-- Aditiva; rollback: reverter o código, coluna fica inerte.

ALTER TABLE tb_treino_planejado
    ADD COLUMN IF NOT EXISTS prova_id uuid NULL REFERENCES tb_prova(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_treino_planejado_prova
    ON tb_treino_planejado (prova_id)
    WHERE prova_id IS NOT NULL;

DO $$
BEGIN
    RAISE NOTICE '✅ V88 - prova_id adicionada a tb_treino_planejado';
END$$;
