-- =====================================================================
-- V94: Ledger de chamadas LLM (add-plan-generation-ledger, design D1/D2/D6/D10/D13)
-- Uma linha por chamada LOGICA ao modelo, em toda rota. As colunas de enriquecimento
-- (generation_request_id, atleta_id, attempt, prompt_*, schema_version, violations,
-- response_json) so sao preenchidas pela rota `plano`. Sem o texto do prompt, nunca.
-- Feature aditiva — nenhum dado existente e alterado. Rollback: DROP TABLE tb_llm_call.
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_llm_call (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID,                       -- solto (regra do projeto); NULL so sem tenant conhecido
    atleta_id             UUID REFERENCES tb_atleta(id) ON DELETE SET NULL,
    generation_request_id UUID,                       -- agrupa as tentativas de uma geracao de plano
    route                 VARCHAR(20) NOT NULL,
    model                 VARCHAR(80) NOT NULL,
    input_tokens          BIGINT,
    output_tokens         BIGINT,
    cache_read_tokens     BIGINT,
    cache_write_tokens    BIGINT,
    cost_usd              NUMERIC(12,10),             -- 10 casas: precisao sub-centavo do CostTrackingAdvisor
    latency_ms            INTEGER NOT NULL,
    attempt               INTEGER,                    -- 1..N dentro da requisicao (so rota plano)
    prompt_version        VARCHAR(20),
    prompt_hash           VARCHAR(64),
    schema_version        VARCHAR(20),
    result                VARCHAR(30) NOT NULL,
    request_outcome       VARCHAR(30),                -- desfecho da requisicao, na ultima chamada (D6)
    transport_retries     INTEGER,                    -- retries HTTP dentro da mesma chamada logica (D13)
    violations            JSONB,                      -- [{key, mensagem}]
    response_json         JSONB,                      -- resposta bruta, nome do atleta redigido (D7); purgada apos 90 dias (D8)
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_llm_call_result CHECK (
        result IN ('PENDING', 'SUCCESS', 'VALIDATION_REJECTED', 'PARSE_ERROR', 'LLM_ERROR', 'TIMEOUT')
    ),
    CONSTRAINT chk_llm_call_request_outcome CHECK (
        request_outcome IS NULL
        OR request_outcome IN ('PERSISTED', 'CONFLICT', 'REJECTED_POST_LLM', 'PERSIST_ERROR')
    )
);

CREATE INDEX IF NOT EXISTS idx_llm_call_tenant_created ON tb_llm_call(tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_llm_call_generation_request ON tb_llm_call(generation_request_id);

COMMENT ON TABLE tb_llm_call IS
    'Ledger de chamadas LLM: custo, latencia, versao do prompt e resultado por chamada. Observabilidade tecnica, nao evento de dominio.';
COMMENT ON COLUMN tb_llm_call.response_json IS
    'Resposta bruta do modelo (rota plano), com o nome do atleta redigido. Dado sensivel: anulada apos 90 dias e na exclusao do atleta.';

DO $$
BEGIN
    RAISE NOTICE '✅ V94 - tb_llm_call criada com sucesso';
END$$;
