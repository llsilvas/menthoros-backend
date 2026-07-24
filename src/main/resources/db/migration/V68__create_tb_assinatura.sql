-- =====================================================================
-- V68: Cria tb_assinatura (assessoria-billing-asaas)
--
-- Contrato de cobranca B2B entre Assessoria e Menthoros no Asaas.
-- 1:1 com Assessoria (uk_assinatura_assessoria), sem historico local
-- (o Asaas e o sistema de registro de cobranca -- ver ADR-0004).
-- Sem tenant_id: Assinatura e cross-tenant por natureza (job de carencia
-- e webhook nao usam TenantContext; a assessoria e o vinculo).
--
-- status inclui PENDENTE (estado transitorio de criacao -- ancora local
-- gravada antes de confirmar o Asaas, estrategia de falha parcial da
-- design.md Decisao 9). asaas_customer_id/asaas_subscription_id ficam
-- NULL enquanto PENDENTE.
--
-- Rollback: DROP TABLE IF EXISTS tb_assinatura;
-- Feature aditiva pura -- sem impacto em dado existente; reversao segura.
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_assinatura (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    assessoria_id           UUID         NOT NULL REFERENCES tb_assessoria(id) ON DELETE CASCADE,
    asaas_customer_id       VARCHAR(50),
    asaas_subscription_id   VARCHAR(50),
    status                  VARCHAR(20)  NOT NULL
                            CHECK (status IN ('PENDENTE', 'ATIVA', 'INADIMPLENTE', 'SUSPENSA', 'CANCELADA')),
    data_proxima_cobranca   TIMESTAMPTZ,
    valor                   NUMERIC(10,2),
    overdue_desde           TIMESTAMPTZ,
    criado_em               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    atualizado_em           TIMESTAMPTZ,
    CONSTRAINT uk_assinatura_assessoria UNIQUE (assessoria_id),
    CONSTRAINT uk_assinatura_asaas_sub  UNIQUE (asaas_subscription_id)
);

-- Suporta a query do job de carencia: findByStatusAndOverdueDesdeBefore(INADIMPLENTE, ...)
CREATE INDEX IF NOT EXISTS idx_assinatura_status_overdue ON tb_assinatura(status, overdue_desde);

DO $$
BEGIN
    RAISE NOTICE '✅ V68 - tb_assinatura criada com sucesso';
END$$;
