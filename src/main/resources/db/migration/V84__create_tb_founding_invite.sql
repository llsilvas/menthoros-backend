-- =====================================================================
-- V84: tb_founding_invite — convite das assessorias fundadoras
--
-- Liga um inscrito da waitlist a um token de uso único entregue por
-- e-mail. Só o HASH do token é persistido: vazamento do banco não entrega
-- links válidos.
--
-- ESTADO SEM ENUM: derivado das datas. Aberto = converted_at e
-- invalidated_at nulos; expirado = expires_at no passado; convertido é
-- terminal. Reenvio = linha nova, a anterior recebe invalidated_at.
--
-- O ÍNDICE PARCIAL ÚNICO NÃO OLHA expires_at de propósito — índice parcial
-- não pode usar now(). Quem garante que o reenvio não viola a UNIQUE é o
-- serviço, invalidando QUALQUER convite aberto (expirado incluso) antes
-- de inserir.
--
-- Também adiciona:
--   tb_assessoria.founding / founding_converted_at — marca da fundadora,
--     gravada no aceite; hoje não muda comportamento, mas sem ela não há
--     como distinguir o grupo depois.
--   tb_signup_provisioning.origin / invite_id — de onde veio o rastro e
--     a contagem de tentativas por convite (chave de idempotência por
--     tentativa: "<token_hash>:<n>").
--
-- Identificadores em inglês (CLAUDE.md do backend, "Identifier Language").
--
-- Rollback:
--   ALTER TABLE tb_signup_provisioning DROP COLUMN IF EXISTS invite_id, DROP COLUMN IF EXISTS origin;
--   ALTER TABLE tb_assessoria DROP COLUMN IF EXISTS founding_converted_at, DROP COLUMN IF EXISTS founding;
--   DROP TABLE IF EXISTS tb_founding_invite;
-- Migração puramente aditiva.
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_founding_invite (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    waitlist_id     UUID         NOT NULL REFERENCES tb_waitlist(id) ON DELETE CASCADE,

    -- SHA-256 em hex. Nunca o token em claro.
    token_hash      VARCHAR(64)  NOT NULL,

    -- Snapshot: a waitlist pode ser editada depois do convite. Largura da
    -- tb_waitlist; o serviço recusa (422) e-mail acima dos 100 que o signup
    -- e tb_usuario aceitam, antes de chegar aqui.
    email           VARCHAR(180) NOT NULL,

    expires_at      TIMESTAMPTZ  NOT NULL,

    -- Nulo quando o SMTP falhou: a linha fica para o reenvio invalidar.
    sent_at         TIMESTAMPTZ,
    invalidated_at  TIMESTAMPTZ,
    converted_at    TIMESTAMPTZ,

    -- SET NULL: a compensação da saga apaga a assessoria e não pode ser
    -- bloqueada pela FK nem levar o convite junto.
    assessoria_id   UUID         REFERENCES tb_assessoria(id) ON DELETE SET NULL,

    -- Subject (JWT) do ADMIN que emitiu.
    invited_by      VARCHAR(100) NOT NULL,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_founding_invite_token_hash UNIQUE (token_hash)
);

-- No máximo UM convite aberto por inscrito. Ver cabeçalho: expirado
-- continua "aberto" para este índice.
CREATE UNIQUE INDEX IF NOT EXISTS uk_founding_invite_open
    ON tb_founding_invite (waitlist_id)
    WHERE converted_at IS NULL AND invalidated_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_founding_invite_waitlist_id
    ON tb_founding_invite (waitlist_id);

ALTER TABLE tb_assessoria
    ADD COLUMN IF NOT EXISTS founding              BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS founding_converted_at TIMESTAMPTZ;

ALTER TABLE tb_signup_provisioning
    ADD COLUMN IF NOT EXISTS origin    VARCHAR(30) NOT NULL DEFAULT 'PUBLIC_SIGNUP',
    ADD COLUMN IF NOT EXISTS invite_id UUID REFERENCES tb_founding_invite(id) ON DELETE SET NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_signup_provisioning_origin'
    ) THEN
        ALTER TABLE tb_signup_provisioning
            ADD CONSTRAINT ck_signup_provisioning_origin
            CHECK (origin IN ('PUBLIC_SIGNUP', 'FOUNDING_INVITE'));
    END IF;
END $$;

-- Sustenta a contagem de tentativas por convite; parcial porque o cadastro
-- público (a maioria) não tem convite.
CREATE INDEX IF NOT EXISTS idx_signup_provisioning_invite_id
    ON tb_signup_provisioning (invite_id)
    WHERE invite_id IS NOT NULL;

COMMENT ON TABLE tb_founding_invite IS
    'Convite de assessoria fundadora emitido pelo ADMIN a partir da waitlist. Guarda só o hash do token.';
COMMENT ON COLUMN tb_founding_invite.token_hash IS
    'SHA-256 hex do token entregue por e-mail. O token em claro nunca é persistido.';
COMMENT ON COLUMN tb_assessoria.founding IS
    'Assessoria fundadora (convertida por convite). Marca do grupo, sem efeito em comportamento por ora.';
COMMENT ON COLUMN tb_signup_provisioning.origin IS
    'PUBLIC_SIGNUP (cadastro aberto) ou FOUNDING_INVITE (aceite de convite).';

DO $$
BEGIN
    RAISE NOTICE '✅ V84 - tb_founding_invite criada; founding em tb_assessoria; origin/invite_id em tb_signup_provisioning';
END$$;
