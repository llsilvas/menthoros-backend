-- =====================================================================
-- V98: Cursor exclusivo do pull + registro de pull + descarte registrado
-- (fix-sync-cursor-data-loss, design D1/D5/D6/D7)
-- ultima_sincronizacao era ao mesmo tempo "ultima atividade de sync" (exibida ao coach) e o cursor do
-- pull; push, webhook e sync manual gravavam now() nela e o pull pulava janelas inteiras. O cursor
-- passa a ter coluna propria, escrita so pelos schedulers de pull.
-- Aditiva: rollback = reverter o binario + script docs/rollback/fix-sync-cursor-data-loss.sql
-- (devolve pull_cursor para ultima_sincronizacao antes do primeiro ciclo do codigo antigo).
-- =====================================================================

ALTER TABLE tb_integracao_externa ADD COLUMN IF NOT EXISTS pull_cursor TIMESTAMPTZ;

-- Herda o cursor atual, que pode ja ter sido adiantado: perda antiga nao e recuperada aqui.
-- Integracao sem ultima_sincronizacao fica nula e ganha o horizonte inicial no primeiro ciclo.
UPDATE tb_integracao_externa SET pull_cursor = ultima_sincronizacao WHERE pull_cursor IS NULL;

CREATE TABLE IF NOT EXISTS tb_sync_pull_log (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL,                                     -- solto (regra do projeto)
    atleta_id      UUID NOT NULL REFERENCES tb_atleta(id) ON DELETE CASCADE,
    plataforma     VARCHAR(50) NOT NULL,
    executado_em   TIMESTAMPTZ NOT NULL,
    resultado      VARCHAR(20) NOT NULL,
    erro_categoria VARCHAR(40),
    insercoes      INTEGER NOT NULL DEFAULT 0,
    ignoradas      INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_sync_pull_log_resultado CHECK (resultado IN ('COMPLETO', 'PARCIAL', 'FALHA'))
);

CREATE INDEX IF NOT EXISTS idx_sync_pull_log_executado_em ON tb_sync_pull_log(executado_em);
CREATE INDEX IF NOT EXISTS idx_sync_pull_log_tenant_atleta
    ON tb_sync_pull_log(tenant_id, atleta_id, executado_em);

-- Atividade que o pull decidiu nao importar: sai da selecao em vez de ser rebuscada todo ciclo
-- (consumindo o teto por ciclo e travando o backlog atras dela).
CREATE TABLE IF NOT EXISTS tb_sync_atividade_descartada (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL,                                      -- solto (regra do projeto)
    atleta_id     UUID NOT NULL REFERENCES tb_atleta(id) ON DELETE CASCADE,
    plataforma    VARCHAR(50) NOT NULL,
    external_id   VARCHAR(100) NOT NULL,
    motivo        VARCHAR(40) NOT NULL,                               -- PERMANENTE | FALHA_RECORRENTE
    tentativas    INTEGER NOT NULL DEFAULT 0,
    descartada_em TIMESTAMPTZ,                                        -- nulo = ainda em tentativa
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sync_descartada UNIQUE (atleta_id, plataforma, external_id)
);

CREATE INDEX IF NOT EXISTS idx_sync_descartada_atualizado_em ON tb_sync_atividade_descartada(atualizado_em);
