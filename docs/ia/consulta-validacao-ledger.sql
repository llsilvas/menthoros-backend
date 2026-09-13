-- =====================================================================
-- Consulta de validação do ledger de chamadas LLM (add-plan-generation-ledger, task 7.2)
--
-- Rodar contra o banco de DEV (ambiente `develop` no Railway), depois do deploy desta
-- change e de pelo menos um dia de geração real de planos — antes disso as tabelas
-- estão vazias e a consulta não tem o que mostrar.
--
-- Objetivo: custo, p50/p95 de latência, taxa de retry, PENDING residual, distribuição
-- de request_outcome e taxa de REJEITADO do coach, agrupados por tenant e por
-- prompt_version — é o painel que vai aprovar/reprovar as fases seguintes (F1, F4, F5).
-- =====================================================================

-- 1) Custo, latência (p50/p95) e volume, por tenant e prompt_version — só rota `plano`
SELECT
    a.nome                                                      AS assessoria,
    c.prompt_version,
    c.schema_version,
    count(*) FILTER (WHERE c.route = 'plano')                   AS chamadas_plano,
    count(*) FILTER (WHERE c.result = 'PENDING')                AS pending_residual,
    round(sum(c.cost_usd) FILTER (WHERE c.route = 'plano'), 4)  AS custo_usd_total,
    percentile_cont(0.5) WITHIN GROUP (ORDER BY c.latency_ms)
        FILTER (WHERE c.route = 'plano')                        AS latencia_p50_ms,
    percentile_cont(0.95) WITHIN GROUP (ORDER BY c.latency_ms)
        FILTER (WHERE c.route = 'plano')                        AS latencia_p95_ms
FROM tb_llm_call c
LEFT JOIN tb_assessoria a ON a.id = c.tenant_id
WHERE c.created_at > now() - interval '14 days'
GROUP BY a.nome, c.prompt_version, c.schema_version
ORDER BY chamadas_plano DESC;

-- 2) Taxa de retry por Requisição de geração (attempt máximo > 1 = houve retry)
WITH por_requisicao AS (
    SELECT generation_request_id, max(attempt) AS tentativas, max(created_at) AS ultima_em
    FROM tb_llm_call
    WHERE route = 'plano' AND generation_request_id IS NOT NULL
    GROUP BY generation_request_id
)
SELECT
    date_trunc('day', ultima_em)                                       AS dia,
    count(*)                                                           AS requisicoes,
    count(*) FILTER (WHERE tentativas > 1)                             AS com_retry,
    round(100.0 * count(*) FILTER (WHERE tentativas > 1) / count(*), 1) AS pct_retry
FROM por_requisicao
GROUP BY 1
ORDER BY 1 DESC;

-- 3) Distribuição de request_outcome (desfecho da requisição) por prompt_version
SELECT
    c.prompt_version,
    c.request_outcome,
    count(DISTINCT c.generation_request_id) AS requisicoes
FROM tb_llm_call c
WHERE c.route = 'plano' AND c.request_outcome IS NOT NULL
GROUP BY c.prompt_version, c.request_outcome
ORDER BY c.prompt_version, requisicoes DESC;

-- 4) Taxa de REJEITADO do coach por prompt_version (join com o veredito real do plano)
SELECT
    c.prompt_version,
    count(DISTINCT p.id)                                              AS planos,
    count(DISTINCT p.id) FILTER (WHERE p.review_status = 'REJEITADO') AS rejeitados,
    round(100.0 * count(DISTINCT p.id) FILTER (WHERE p.review_status = 'REJEITADO')
        / NULLIF(count(DISTINCT p.id), 0), 1)                         AS pct_rejeitado
FROM tb_plano_semanal p
JOIN tb_llm_call c ON c.generation_request_id = p.generation_request_id
WHERE p.generation_request_id IS NOT NULL
GROUP BY c.prompt_version
ORDER BY planos DESC;

-- 5) Gate de cache da Fase 1 (system-user-prompt-split, CA6): mediana de
--    cached_tokens/prompt_tokens nas chamadas 2..N de um lote (mesmo tenant, sequência)
--    — só relevante depois que a Fase 1 estiver no ar; deixado pronto aqui.
-- SELECT tenant_id, percentile_cont(0.5) WITHIN GROUP (ORDER BY cache_read_tokens::numeric / NULLIF(input_tokens + cache_read_tokens, 0))
-- FROM tb_llm_call WHERE route = 'plano' AND attempt = 1 GROUP BY tenant_id;
