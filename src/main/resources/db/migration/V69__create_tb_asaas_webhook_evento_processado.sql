-- =====================================================================
-- V69: Cria tb_asaas_webhook_evento_processado (assessoria-billing-asaas)
--
-- Controle de idempotencia do webhook do Asaas: o Asaas entrega eventos
-- at-least-once (ate 5 reenvios em caso de erro 4xx/5xx). Antes de aplicar
-- a transicao de estado, o servico grava o evento_id aqui; reenvio do
-- mesmo evento e respondido 200 sem reprocessar (design.md Decisao 4, CA10).
--
-- evento_id = id do evento do Asaas (ou payment.id para eventos de pagamento).
--
-- Rollback: DROP TABLE IF EXISTS tb_asaas_webhook_evento_processado;
-- Feature aditiva pura -- sem impacto em dado existente; reversao segura.
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_asaas_webhook_evento_processado (
    evento_id      VARCHAR(100) PRIMARY KEY,
    tipo_evento    VARCHAR(50),
    processado_em  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
    RAISE NOTICE '✅ V69 - tb_asaas_webhook_evento_processado criada com sucesso';
END$$;
