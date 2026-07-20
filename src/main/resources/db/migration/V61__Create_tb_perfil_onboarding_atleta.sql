-- =====================================================================
-- V61: Cria tb_perfil_onboarding_atleta (athlete-onboarding-baseline)
--
-- Design.md Decisao 10: dos 11 campos "obrigatorios" do formulario de
-- onboarding, 7 ja existem em tb_atleta (objetivo, nivel_experiencia,
-- dia [diasDisponiveis], historico_lesoes/tem_lesao/descricao_lesao/
-- data_ultima_lesao, volume_semanal_max) -- o onboarding escreve DIRETO
-- la, sem duplicar (evita duas fontes de verdade). Esta tabela guarda
-- so o estado que nao tem lugar em Atleta: o status do draft (CA8) e os
-- 5 campos genuinamente novos. dataProva NAO fica aqui -- vira uma
-- Prova real (Decisao 8).
--
-- Rollback: DROP TABLE IF EXISTS tb_perfil_onboarding_atleta;
-- Feature aditiva pura -- sem impacto em dado existente; reversao segura.
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_perfil_onboarding_atleta (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    atleta_id                   UUID        NOT NULL REFERENCES tb_atleta(id) ON DELETE CASCADE,
    tenant_id                   UUID        NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'RASCUNHO' CHECK (status IN ('RASCUNHO', 'COMPLETO')),
    maior_treino_recente_km     NUMERIC(6,2),
    duracao_disponivel_min      INTEGER,
    restricoes                  TEXT,
    modalidade                  VARCHAR(30),
    percepcao_condicionamento   VARCHAR(30),
    preenchido_por_coach        BOOLEAN     NOT NULL DEFAULT false,
    criado_em                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_perfil_onboarding_atleta_tenant UNIQUE (atleta_id, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_perfil_onboarding_tenant ON tb_perfil_onboarding_atleta(tenant_id);

DO $$
BEGIN
    RAISE NOTICE '✅ V61 - tb_perfil_onboarding_atleta criada com sucesso';
END$$;
