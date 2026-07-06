-- =====================================================================
-- V52: cria tb_batch_plan_job e o índice único de plano ativo por semana
--
-- tb_batch_plan_job persiste os jobs de geração assíncrona de planos em
-- lote (POST /coach/planos/gerar-lote → 202 + polling). Contadores
-- gerados/erros são incrementados atomicamente por atleta; resultado
-- (JSONB) só é preenchido no estado terminal.
--
-- O índice único parcial uk_plano_semanal_atleta_semana_ativo é a guarda
-- autoritativa contra planos duplicados na mesma semana quando dois lotes
-- concorrentes processam o mesmo atleta (corrida TOCTOU que o exists-check
-- de aplicação não fecha). REJEITADO é excluído — um plano rejeitado não
-- bloqueia a geração de um novo.
--
-- PRÉ-CONDIÇÃO: não pode haver duplicatas ativas pré-existentes, senão o
-- CREATE UNIQUE INDEX falha (fail-safe — aborta o deploy sem estado
-- parcial). Verificar antes de aplicar:
--   SELECT atleta_id, semana_inicio,
--          COUNT(*) FILTER (WHERE review_status <> 'REJEITADO') AS ativos
--   FROM tb_plano_semanal
--   GROUP BY atleta_id, semana_inicio
--   HAVING COUNT(*) FILTER (WHERE review_status <> 'REJEITADO') > 1;
--
-- Rollback:
--   DROP INDEX IF EXISTS uk_plano_semanal_atleta_semana_ativo;
--   DROP TABLE IF EXISTS tb_batch_plan_job;
-- Feature aditiva — nenhum dado existente é alterado ou removido.
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_batch_plan_job (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDENTE',
    total_atletas   INT NOT NULL,
    gerados         INT NOT NULL DEFAULT 0,
    erros           INT NOT NULL DEFAULT 0,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    concluido_em    TIMESTAMPTZ,
    resultado       JSONB,
    CONSTRAINT chk_batch_job_status CHECK (
        status IN ('PENDENTE', 'EM_PROGRESSO', 'CONCLUIDO', 'CONCLUIDO_COM_ERROS')
    )
);

CREATE INDEX IF NOT EXISTS idx_batch_plan_job_tenant ON tb_batch_plan_job(tenant_id);

-- Guarda autoritativa contra planos duplicados (lotes concorrentes, mesmo atleta):
CREATE UNIQUE INDEX IF NOT EXISTS uk_plano_semanal_atleta_semana_ativo
    ON tb_plano_semanal (atleta_id, semana_inicio)
    WHERE review_status <> 'REJEITADO';

DO $$
BEGIN
    RAISE NOTICE '✅ V52 - tb_batch_plan_job + índice único de plano ativo criados';
END$$;
