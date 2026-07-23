-- =====================================================================
-- V60: Cria tb_atividade_proveniencia_descartada (athlete-onboarding-baseline)
--
-- Auditoria append-only do dedup entre fontes do Activity Normalizer
-- (design.md Decisao 2): quando 2 atividades da mesma sessao real
-- chegam de fontes diferentes (ex.: Garmin + Strava), a fonte de menor
-- prioridade e descartada do registro ativo, mas seus dados ficam aqui
-- para nunca serem perdidos -- sem reintroduzir SourcedValue<T> (dropado
-- para v1) como tipo de campo no registro ativo.
--
-- Sem UPDATE/DELETE no fluxo normal. O insert aqui e o insert do
-- registro ativo em tb_treino_realizado devem acontecer na MESMA
-- transacao (ActivityDedupService) -- ver design.md Decisao 2.
--
-- Rollback: DROP TABLE IF EXISTS tb_atividade_proveniencia_descartada;
-- Feature aditiva pura -- sem impacto em dado existente; reversao segura.
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_atividade_proveniencia_descartada (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    atividade_id       UUID        NOT NULL REFERENCES tb_treino_realizado(id) ON DELETE CASCADE,
    tenant_id          UUID        NOT NULL,
    fonte_descartada   VARCHAR(50) NOT NULL,
    dados_descartados  JSONB       NOT NULL,
    motivo_descarte    VARCHAR(255),
    criado_em          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_proveniencia_descartada_atividade ON tb_atividade_proveniencia_descartada(atividade_id);
CREATE INDEX IF NOT EXISTS idx_proveniencia_descartada_tenant ON tb_atividade_proveniencia_descartada(tenant_id);

DO $$
BEGIN
    RAISE NOTICE '✅ V60 - tb_atividade_proveniencia_descartada criada com sucesso';
END$$;
