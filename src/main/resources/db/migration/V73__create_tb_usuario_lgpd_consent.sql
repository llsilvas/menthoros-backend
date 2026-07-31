-- =====================================================================
-- V73: Cria tb_usuario_lgpd_consent (add-coach-lgpd-consent)
--
-- Registro APPEND-ONLY do consentimento LGPD do coach: uma linha por aceite,
-- versionada pela data de vigencia da Politica de Privacidade e dos Termos de
-- Uso. Nada e sobrescrito -- re-consentimento apos mudanca de versao cria uma
-- linha nova e preserva a anterior, que e o que sustenta a prova de qual texto
-- foi aceito e quando.
--
-- Sem coluna equivalente em tb_usuario (booleano/timestamp): "esta consentido?"
-- e DERIVADO da existencia de linha com as versoes vigentes. Guardar o flag
-- tambem em tb_usuario seria estado redundante, capaz de divergir da tabela.
--
-- uk_usuario_lgpd_consent_versoes nao e so integridade: e ela que torna o
-- insert idempotente e arbitra a corrida de dois aceites simultaneos, sem
-- precisar de update condicional na aplicacao.
--
-- tenant_id sem FK, conforme o Table Design Standard (gerido pela aplicacao).
--
-- Feature aditiva pura -- tb_usuario NAO e alterada, nenhum dado existente e
-- tocado; reversao segura.
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_usuario_lgpd_consent (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      UUID         NOT NULL REFERENCES tb_usuario(id) ON DELETE CASCADE,
    tenant_id       UUID         NOT NULL,
    policy_version  VARCHAR(20)  NOT NULL,
    terms_version   VARCHAR(20)  NOT NULL,
    consented_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_usuario_lgpd_consent_versoes
        UNIQUE (usuario_id, policy_version, terms_version)
);

CREATE INDEX IF NOT EXISTS idx_usuario_lgpd_consent_tenant_usuario
    ON tb_usuario_lgpd_consent(tenant_id, usuario_id);

DO $$
BEGIN
    RAISE NOTICE '✅ V73 - tb_usuario_lgpd_consent criada com sucesso';
END$$;
