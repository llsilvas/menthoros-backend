-- =====================================================================
-- V63: Adiciona origem_aprovacao a tb_plano_semanal (athlete-onboarding-baseline)
--
-- Sessao de grilling 2026-07-21: PlanoReviewStatus.APROVADO e alcancado por dois
-- caminhos (aprovacao manual do coach, ou auto-approve para Cenario A de
-- confianca) sem nenhum campo distinguindo qual foi. Sem isso, a origem da
-- aprovacao e perdida permanentemente assim que persistida.
--
-- NULL = plano gerado antes desta migration (nunca teve o campo) ou plano
-- ainda AGUARDANDO_REVISAO/REJEITADO (origem so faz sentido apos aprovacao).
--
-- Rollback: ALTER TABLE tb_plano_semanal DROP COLUMN IF EXISTS origem_aprovacao;
-- Feature aditiva pura (coluna nullable) -- sem impacto em dado existente.
-- =====================================================================

ALTER TABLE tb_plano_semanal
    ADD COLUMN IF NOT EXISTS origem_aprovacao VARCHAR(30);

DO $$
BEGIN
    RAISE NOTICE '✅ V63 - coluna origem_aprovacao adicionada a tb_plano_semanal';
END$$;
