-- =====================================================================
-- V103: Delta de edição do coach em tb_sugestao_coach
-- Aditivo, sem backfill — linhas existentes ficam como nunca editadas.
-- (add-coach-suggestion-edit-delta)
-- =====================================================================

ALTER TABLE tb_sugestao_coach
    ADD COLUMN IF NOT EXISTS summary_original TEXT,
    ADD COLUMN IF NOT EXISTS editado_pelo_coach BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS edited_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS versao BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN tb_sugestao_coach.summary_original IS
    'Texto gerado pela IA, copiado na PRIMEIRA edição do coach e nunca sobrescrito depois. '
    'NULL enquanto a sugestão nunca foi editada.';

COMMENT ON COLUMN tb_sugestao_coach.versao IS
    'Contador comparado e incrementado nos UPDATEs condicionais (editar/aprovar/rejeitar) para '
    'detectar aba desatualizada. Coluna comum — não é @Version do Hibernate.';

DO $$
BEGIN
    RAISE NOTICE '✅ V103 - colunas de delta de edição adicionadas a tb_sugestao_coach';
END$$;
