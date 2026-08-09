-- =====================================================================
-- V75: tb_signup_provisioning — rastro do auto-cadastro de assessoria
--
-- POR QUE UMA TABELA, E NÃO UMA COLUNA EM tb_assessoria:
-- o cadastro começa ANTES de a assessoria existir e precisa sobreviver ao
-- caso em que ela nunca chega a ser criada. Estado pendurado na assessoria
-- não consegue registrar a tentativa que falhou no primeiro passo — que é
-- justamente a que precisa de rastro.
--
-- POR QUE tb_assessoria NÃO GANHA COLUNA DE ESTADO:
-- a compensação APAGA a Assessoria; é isso que devolve o slug ao pool.
-- Marcar a linha como falha manteria o `dominio` e prenderia o nome para
-- sempre pela UNIQUE tb_assessoria_dominio_key.
--
-- O SLUG NÃO GANHA CAMPO NEM ÍNDICE NOVO em tb_assessoria: a reserva é a
-- UNIQUE existente, e a corrida entre dois cadastros simultâneos resolve
-- nela — não em verificação prévia, que sempre tem janela.
--
-- Rollback:
--   DROP TABLE IF EXISTS tb_signup_provisioning;
-- Migração puramente aditiva — nenhuma tabela existente é alterada.
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_signup_provisioning (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Header Idempotency-Key. A UNIQUE é o que impede o duplo clique de criar
    -- duas assessorias: a segunda tentativa colide aqui, não no meio do fluxo.
    idempotency_key          VARCHAR(64)  NOT NULL,

    -- Hash do payload SEM a senha. Sem ele a idempotência não distingue
    -- "mesma requisição reenviada" (devolve o resultado) de "chave reusada com
    -- outro payload" (409). Só a chave não basta.
    request_hash             VARCHAR(64)  NOT NULL,

    -- Corpo devolvido na primeira execução, replicado no reenvio.
    -- NUNCA contém senha, access token ou refresh token.
    resultado                JSONB,

    -- Larguras espelham CoachSignupInputDto: nada além do que o DTO valida
    -- chega até aqui. `slug` acompanha tb_assessoria.dominio — varchar(100).
    email                    VARCHAR(180) NOT NULL,
    slug                     VARCHAR(100) NOT NULL,

    status                   VARCHAR(40)  NOT NULL,

    -- ON DELETE SET NULL porque a compensação APAGA a assessoria e não pode
    -- ser bloqueada pela FK, nem levar o rastro junto. O que permanece
    -- legível depois disso é slug + correlation_id + error_detail.
    assessoria_id            UUID         REFERENCES tb_assessoria(id) ON DELETE SET NULL,

    keycloak_organization_id VARCHAR(64),
    keycloak_user_id         VARCHAR(64),

    -- NOT NULL de propósito: quando a falha acontece antes da assessoria,
    -- não há tenant a registrar e este é o único fio que amarra o rastro
    -- ao log da requisição.
    correlation_id           VARCHAR(64)  NOT NULL,

    error_detail             TEXT,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ,

    CONSTRAINT uk_signup_provisioning_idempotency UNIQUE (idempotency_key)
);

-- CHECK em vez de convenção: um status escrito errado sumiria silenciosamente
-- da varredura de reconciliação, que é exatamente a consulta que não pode
-- perder linha.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_signup_provisioning_status'
    ) THEN
        ALTER TABLE tb_signup_provisioning
            ADD CONSTRAINT ck_signup_provisioning_status CHECK (status IN (
                'STARTED',
                'ASSESSORIA_CREATED',
                'ORGANIZATION_CREATED',
                'KEYCLOAK_USER_CREATED',
                'LOCAL_USER_CREATED',
                'VERIFICATION_EMAIL_SENT',
                'ACTIVE',
                'FAILED',
                'RECONCILIATION_REQUIRED'
            ));
    END IF;
END $$;

-- Parcial: a varredura operacional só procura o que precisa de intervenção
-- humana, e essas linhas são a minoria absoluta da tabela.
CREATE INDEX IF NOT EXISTS idx_signup_provisioning_reconciliacao
    ON tb_signup_provisioning (created_at)
    WHERE status = 'RECONCILIATION_REQUIRED';

-- Sustenta o limite anti-abuso por e-mail (~3/dia) e a investigação por conta.
CREATE INDEX IF NOT EXISTS idx_signup_provisioning_email_created
    ON tb_signup_provisioning (email, created_at);

COMMENT ON TABLE tb_signup_provisioning IS
    'Rastro do auto-cadastro público de assessoria. Sobrevive à compensação: a Assessoria é apagada, esta linha permanece.';
COMMENT ON COLUMN tb_signup_provisioning.resultado IS
    'Corpo devolvido na 1a execucao, replicado no reenvio idempotente. Nunca contem senha nem token.';
COMMENT ON COLUMN tb_signup_provisioning.request_hash IS
    'Hash do payload sem a senha. Distingue reenvio identico (200/201) de chave reusada com outro payload (409).';
