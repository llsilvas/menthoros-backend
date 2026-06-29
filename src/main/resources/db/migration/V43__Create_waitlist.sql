-- =====================================================================
-- V43: Cria a tabela de waitlist (captura pública de interessados no beta)
-- Sem tenant — cadastro global, pré-signup.
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_waitlist (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nome              VARCHAR(120) NOT NULL,
    email             VARCHAR(180) NOT NULL,
    email_normalized  VARCHAR(180) NOT NULL,
    telefone          VARCHAR(20),
    perfil            VARCHAR(20)  NOT NULL,
    qtd_atletas       VARCHAR(20),
    aceite_lgpd       BOOLEAN      NOT NULL DEFAULT FALSE,
    origem            VARCHAR(40)  DEFAULT 'landing',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_waitlist_email_normalized UNIQUE (email_normalized)
);

COMMENT ON TABLE tb_waitlist IS
    'Interessados em testar o Menthoros (waitlist pública, pré-signup, sem tenant)';
COMMENT ON COLUMN tb_waitlist.email_normalized IS
    'E-mail normalizado (trim + lowercase) para unicidade case-insensitive';
COMMENT ON COLUMN tb_waitlist.perfil IS
    'TREINADOR ou ATLETA';
COMMENT ON COLUMN tb_waitlist.qtd_atletas IS
    'Faixa de atletas atendidos (só treinador): ATE_10, DE_11_A_30, DE_31_A_100, MAIS_DE_100';
COMMENT ON COLUMN tb_waitlist.aceite_lgpd IS
    'Consentimento LGPD aceito no momento do cadastro';

DO $$
BEGIN
    RAISE NOTICE '✅ V43 - tb_waitlist criada com sucesso';
END$$;
