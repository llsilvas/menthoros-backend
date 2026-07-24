-- =====================================================================
-- V70: Remove campos legados de cobrança de tb_assessoria (assessoria-billing-asaas)
--
-- data_assinatura/data_expiracao migram conceitualmente para tb_assinatura
-- (data_proxima_cobranca); trial/data_fim_trial sao removidos sem substituto
-- (trial deixa de ser conceito rastreado no Menthoros — ver ADR-0005).
-- Assessoria.ativo passa a ser escrito so pela sincronizacao a partir de
-- Assinatura (design.md Decisao 2/8).
--
-- Sem backfill — zero assessorias em producao com este modelo (proposal.md).
-- Confirmado pelo usuario antes de rodar (guardrail do CLAUDE.md da raiz).
-- Reversao: V71 re-adiciona as colunas como NULL (nao ha dado a restaurar);
-- nunca editar esta migration aplicada.
-- =====================================================================

ALTER TABLE tb_assessoria
    DROP COLUMN IF EXISTS data_assinatura,
    DROP COLUMN IF EXISTS data_expiracao,
    DROP COLUMN IF EXISTS trial,
    DROP COLUMN IF EXISTS data_fim_trial;

DO $$
BEGIN
    RAISE NOTICE '✅ V70 - campos legados de cobranca removidos de tb_assessoria';
END$$;
