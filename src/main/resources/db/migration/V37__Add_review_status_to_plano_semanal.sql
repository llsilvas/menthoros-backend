-- =====================================================================
-- V37: Adiciona revisão editorial de planos gerados pela IA
--      Novo campo review_status (AGUARDANDO_REVISAO | APROVADO | REJEITADO)
--      e review_comment (motivo de rejeição, texto livre)
-- =====================================================================

ALTER TABLE tb_plano_semanal
    ADD COLUMN review_status  VARCHAR(30) NOT NULL DEFAULT 'AGUARDANDO_REVISAO',
    ADD COLUMN review_comment TEXT;

-- Backfill: planos existentes já eram visíveis ao atleta → retrocompatibilidade
UPDATE tb_plano_semanal SET review_status = 'APROVADO';

CREATE INDEX IF NOT EXISTS idx_plano_review_status_tenant
    ON tb_plano_semanal(tenant_id, review_status);

DO $$
BEGIN
    RAISE NOTICE '✅ V37 - review_status e review_comment adicionados a tb_plano_semanal';
END$$;
