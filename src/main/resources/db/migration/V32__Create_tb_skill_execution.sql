-- =====================================================================
-- V32: Cria tb_skill_execution para persistência de execuções de skills
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_skill_execution (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    skill_key               VARCHAR(100) NOT NULL,
    skill_version           VARCHAR(20)  NOT NULL,
    skill_category          VARCHAR(50),
    severity                VARCHAR(20)  NOT NULL,
    confidence              VARCHAR(20)  NOT NULL,
    payload_json            JSONB,
    evidence_json           JSONB,
    recommendations_json    JSONB,
    tenant_id               UUID         NOT NULL,
    atleta_id               UUID         REFERENCES tb_atleta(id) ON DELETE SET NULL,
    plano_semanal_id        UUID,
    treino_realizado_id     UUID,
    executed_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    data_referencia         DATE
);

CREATE INDEX IF NOT EXISTS idx_skill_exec_atleta
    ON tb_skill_execution(atleta_id, executed_at DESC);

CREATE INDEX IF NOT EXISTS idx_skill_exec_key
    ON tb_skill_execution(skill_key, skill_version);

CREATE INDEX IF NOT EXISTS idx_skill_exec_tenant
    ON tb_skill_execution(tenant_id);

DO $$
BEGIN
    RAISE NOTICE '✅ V32 - tb_skill_execution criada com sucesso';
END$$;
