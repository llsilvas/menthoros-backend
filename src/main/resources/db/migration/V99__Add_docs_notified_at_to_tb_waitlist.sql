-- =====================================================================
-- V99: Adiciona coluna de idempotência do aviso da central de ajuda à tb_waitlist
-- Aditivo e opcional — nenhum cliente existente quebra (notify-waitlist-docs-site).
-- =====================================================================

ALTER TABLE tb_waitlist
    ADD COLUMN IF NOT EXISTS docs_notified_at TIMESTAMPTZ;

COMMENT ON COLUMN tb_waitlist.docs_notified_at IS
    'Instante em que o inscrito foi avisado por e-mail de que a central de ajuda (menthoros-docs) '
    'está no ar. NULL = ainda não avisado. Gravado só depois do envio confirmado.';

DO $$
BEGIN
    RAISE NOTICE '✅ V99 - coluna docs_notified_at adicionada a tb_waitlist';
END$$;
