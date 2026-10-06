-- =====================================================================
-- V100: Auditoria da decisão do coach em tb_sugestao_coach
-- Aditivo, sem backfill — linhas legadas ficam com reviewed_by NULL
-- (predatam a auditoria). (add-coach-suggestion-decision-audit)
-- =====================================================================

ALTER TABLE tb_sugestao_coach
    ADD COLUMN IF NOT EXISTS reviewed_by UUID,
    ADD COLUMN IF NOT EXISTS motivo_rejeicao TEXT;

COMMENT ON COLUMN tb_sugestao_coach.reviewed_by IS
    'Usuario.id do técnico/admin que aprovou ou rejeitou a sugestão. Resolvido do security '
    'context (JWT), nunca do request body — auditoria não forjável. NULL para decisões '
    'anteriores a esta coluna ou para sugestões ainda PENDING.';

COMMENT ON COLUMN tb_sugestao_coach.motivo_rejeicao IS
    'Texto livre opcional do porquê da rejeição. Só preenchido em REJECTED; aprovar limpa '
    'para NULL (decisão final é aprovar).';

DO $$
BEGIN
    RAISE NOTICE '✅ V100 - colunas reviewed_by e motivo_rejeicao adicionadas a tb_sugestao_coach';
END$$;
