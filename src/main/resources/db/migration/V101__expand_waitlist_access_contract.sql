-- =====================================================================
-- V101: Expande o contrato de acesso da waitlist (expand-waitlist-access-contract)
-- Aditivo, sem backfill — linhas legadas ficam com os 4 campos NULL.
-- =====================================================================

ALTER TABLE tb_waitlist
    ADD COLUMN IF NOT EXISTS watch_brand VARCHAR(20),
    ADD COLUMN IF NOT EXISTS landing_path VARCHAR(255),
    ADD COLUMN IF NOT EXISTS referrer VARCHAR(255),
    ADD COLUMN IF NOT EXISTS policy_version VARCHAR(20);

COMMENT ON COLUMN tb_waitlist.watch_brand IS
    'Marca de relógio predominante dos atletas — opcional, só relevante para TREINADOR/PROPRIETARIO.';

COMMENT ON COLUMN tb_waitlist.landing_path IS
    'Caminho da página de origem da inscrição (ex.: /waitlist, /).';

COMMENT ON COLUMN tb_waitlist.referrer IS
    'Referrer HTTP no momento da inscrição, quando disponível.';

COMMENT ON COLUMN tb_waitlist.policy_version IS
    'Versão da Política de Privacidade vigente no momento do aceite — carimbada pelo servidor '
    '(LgpdProperties.policyVersion), nunca recebida do cliente. NULL para inscrições anteriores '
    'a esta coluna.';

DO $$
BEGIN
    RAISE NOTICE '✅ V101 - watch_brand, landing_path, referrer e policy_version adicionadas a tb_waitlist';
END$$;
