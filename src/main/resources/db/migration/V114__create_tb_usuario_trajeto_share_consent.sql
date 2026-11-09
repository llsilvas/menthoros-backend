-- =====================================================================
-- V114: Cria tb_usuario_trajeto_share_consent (lgpd-trajeto-cards-compartilhamento)
--
-- Consentimento específico do atleta (LGPD Art. 11, I) para gerar imagens de
-- treino com o trajeto GPS completo, inclusive início e fim.
--
-- APPEND-ONLY DE EVENTOS: concessão e revogação são linhas (evento CONCEDIDO ou
-- REVOGADO). Nada é atualizado ou apagado -- o histórico é a prova de qual
-- versão da Política foi confirmada, quando, e de quando foi revogada.
-- O estado atual é o último evento do usuário no tenant, ordenado por `seq`
-- (sequência do banco), não por `registrado_em`: com mais de uma instância da
-- aplicação, relógios diferentes poderiam inverter uma revogação e uma concessão
-- próximas. `registrado_em` fica como dado da prova, não como critério de ordem.
--
-- Sem unique: revogar e conceder de novo na mesma versão são eventos legítimos.
-- A idempotência (não gravar evento igual ao estado atual) é do service.
--
-- tenant_id sem FK, conforme o Table Design Standard; faz parte do filtro pelo
-- mesmo motivo da V73 -- o consentimento é por assessoria.
--
-- Feature aditiva pura: nenhuma tabela existente é alterada; reversão segura.
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_usuario_trajeto_share_consent (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    seq             BIGINT       GENERATED ALWAYS AS IDENTITY,
    usuario_id      UUID         NOT NULL REFERENCES tb_usuario(id) ON DELETE CASCADE,
    tenant_id       UUID         NOT NULL,
    policy_version  VARCHAR(20)  NOT NULL,
    evento          VARCHAR(10)  NOT NULL CHECK (evento IN ('CONCEDIDO', 'REVOGADO')),
    registrado_em   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trajeto_share_consent_usuario
    ON tb_usuario_trajeto_share_consent(tenant_id, usuario_id, seq DESC);
