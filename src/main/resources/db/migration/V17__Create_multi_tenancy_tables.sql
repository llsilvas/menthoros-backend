-- src/main/resources/db/migration/V7__Create_multi_tenancy_tables.sql

-- =============================================
-- TABELA: tb_assessoria (Tenant)
-- =============================================
CREATE TABLE IF NOT EXISTS tb_assessoria
(
    id                              UUID PRIMARY KEY             DEFAULT gen_random_uuid(),
    nome                            VARCHAR(200)        NOT NULL,
    dominio                         VARCHAR(100) UNIQUE NOT NULL,
    razao_social                    VARCHAR(200),
    cnpj                            VARCHAR(18) UNIQUE,
    email_contato                   VARCHAR(100),
    telefone                        VARCHAR(20),

    -- Endereço
    logradouro                      VARCHAR(200),
    numero                          VARCHAR(10),
    complemento                     VARCHAR(100),
    bairro                          VARCHAR(100),
    cidade                          VARCHAR(100),
    estado                          VARCHAR(2),
    cep                             VARCHAR(9),

    -- Configurações
    logo_url                        VARCHAR(500),
    cor_primaria                    VARCHAR(7)                   DEFAULT '#6366F1',
    cor_secundaria                  VARCHAR(7)                   DEFAULT '#EC4899',
    max_atletas                     INTEGER,
    max_tecnicos                    INTEGER,

    -- Plano e cobrança
    plano                           VARCHAR(20)         NOT NULL DEFAULT 'BASIC',
    data_assinatura                 TIMESTAMP,
    data_expiracao                  TIMESTAMP,
    trial                           BOOLEAN             NOT NULL DEFAULT FALSE,
    data_fim_trial                  TIMESTAMP,

    -- Feature flags
    feature_ia_avancada             BOOLEAN                      DEFAULT FALSE,
    feature_relatorios_customizados BOOLEAN                      DEFAULT FALSE,
    feature_integracao_strava       BOOLEAN                      DEFAULT TRUE,
    feature_api_externa             BOOLEAN                      DEFAULT FALSE,

    -- Controle
    ativo                           BOOLEAN             NOT NULL DEFAULT TRUE,
    created_at                      TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMP,

    CONSTRAINT chk_plano CHECK (plano IN ('BASIC', 'PRO', 'ENTERPRISE'))
);

CREATE INDEX IF NOT EXISTS idx_assessoria_dominio ON tb_assessoria (dominio);
CREATE INDEX IF NOT EXISTS idx_assessoria_ativo ON tb_assessoria (ativo);
CREATE INDEX IF NOT EXISTS idx_assessoria_cnpj ON tb_assessoria (cnpj);

-- =============================================
-- TABELA: tb_usuario
-- =============================================
CREATE TABLE IF NOT EXISTS tb_usuario
(
    id            UUID PRIMARY KEY             DEFAULT gen_random_uuid(),
    tenant_id     UUID                NOT NULL REFERENCES tb_assessoria (id) ON DELETE CASCADE,
    nome          VARCHAR(200)        NOT NULL,
    email         VARCHAR(100) UNIQUE NOT NULL,
    senha_hash    VARCHAR(255)        NOT NULL,
    avatar_url    VARCHAR(500),
    role          VARCHAR(20)         NOT NULL DEFAULT 'TECNICO',
    ativo         BOOLEAN             NOT NULL DEFAULT TRUE,
    ultimo_acesso TIMESTAMP,
    created_at    TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP,

    CONSTRAINT chk_role CHECK (role IN ('ADMIN', 'TECNICO', 'VISUALIZADOR'))
);

CREATE INDEX IF NOT EXISTS idx_usuario_email ON tb_usuario (email);
CREATE INDEX IF NOT EXISTS idx_usuario_tenant ON tb_usuario (tenant_id);
CREATE INDEX IF NOT EXISTS idx_usuario_tenant_role ON tb_usuario (tenant_id, role);

-- =============================================
-- ADICIONAR tenant_id NAS TABELAS EXISTENTES
-- =============================================

-- tb_atleta
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_atleta' AND column_name = 'tenant_id') THEN
        ALTER TABLE tb_atleta ADD COLUMN tenant_id UUID REFERENCES tb_assessoria (id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_atleta_tenant ON tb_atleta (tenant_id);
CREATE INDEX IF NOT EXISTS idx_atleta_tenant_ativo ON tb_atleta (tenant_id, ativo);

-- tb_treino_realizado
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_treino_realizado' AND column_name = 'tenant_id') THEN
        ALTER TABLE tb_treino_realizado ADD COLUMN tenant_id UUID REFERENCES tb_assessoria (id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_treino_realizado_tenant ON tb_treino_realizado (tenant_id);

-- tb_treino_planejado
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_treino_planejado' AND column_name = 'tenant_id') THEN
        ALTER TABLE tb_treino_planejado ADD COLUMN tenant_id UUID REFERENCES tb_assessoria (id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_treino_planejado_tenant ON tb_treino_planejado (tenant_id);

-- tb_plano_semanal
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_plano_semanal' AND column_name = 'tenant_id') THEN
        ALTER TABLE tb_plano_semanal ADD COLUMN tenant_id UUID REFERENCES tb_assessoria (id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_plano_semanal_tenant ON tb_plano_semanal (tenant_id);

-- tb_plano_metadados
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_plano_metadados' AND column_name = 'tenant_id') THEN
        ALTER TABLE tb_plano_metadados ADD COLUMN tenant_id UUID REFERENCES tb_assessoria (id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_plano_metadados_tenant ON tb_plano_metadados (tenant_id);

-- tb_prova
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_prova' AND column_name = 'tenant_id') THEN
        ALTER TABLE tb_prova ADD COLUMN tenant_id UUID REFERENCES tb_assessoria (id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_prova_tenant ON tb_prova (tenant_id);

-- tb_metricas_diarias (tabela pode não existir em todos os ambientes)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'tb_metricas_diarias' AND table_schema = 'public') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_metricas_diarias' AND column_name = 'tenant_id') THEN
            ALTER TABLE tb_metricas_diarias ADD COLUMN tenant_id UUID REFERENCES tb_assessoria (id) ON DELETE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE tablename = 'tb_metricas_diarias' AND indexname = 'idx_metricas_diarias_tenant') THEN
            CREATE INDEX idx_metricas_diarias_tenant ON tb_metricas_diarias (tenant_id);
        END IF;
    END IF;
END $$;

-- =============================================
-- CRIAR ASSESSORIA PADRÃO (para dados existentes)
-- =============================================
INSERT INTO tb_assessoria (id,
                           nome,
                           dominio,
                           plano,
                           ativo,
                           trial,
                           data_fim_trial)
SELECT gen_random_uuid(),
       'Menthoros Default',
       'default',
       'ENTERPRISE',
       TRUE,
       FALSE,
       NULL
WHERE NOT EXISTS (SELECT 1 FROM tb_assessoria WHERE dominio = 'default');

-- =============================================
-- ATUALIZAR REGISTROS EXISTENTES
-- =============================================
UPDATE tb_atleta
SET tenant_id = (SELECT id FROM tb_assessoria WHERE dominio = 'default' LIMIT 1)
WHERE tenant_id IS NULL;

UPDATE tb_treino_realizado
SET tenant_id = (SELECT id FROM tb_assessoria WHERE dominio = 'default' LIMIT 1)
WHERE tenant_id IS NULL;

UPDATE tb_treino_planejado
SET tenant_id = (SELECT id FROM tb_assessoria WHERE dominio = 'default' LIMIT 1)
WHERE tenant_id IS NULL;

UPDATE tb_plano_semanal
SET tenant_id = (SELECT id FROM tb_assessoria WHERE dominio = 'default' LIMIT 1)
WHERE tenant_id IS NULL;

UPDATE tb_plano_metadados
SET tenant_id = (SELECT id FROM tb_assessoria WHERE dominio = 'default' LIMIT 1)
WHERE tenant_id IS NULL;

UPDATE tb_prova
SET tenant_id = (SELECT id FROM tb_assessoria WHERE dominio = 'default' LIMIT 1)
WHERE tenant_id IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'tb_metricas_diarias' AND table_schema = 'public') THEN
        UPDATE tb_metricas_diarias
        SET tenant_id = (SELECT id FROM tb_assessoria WHERE dominio = 'default' LIMIT 1)
        WHERE tenant_id IS NULL;
    END IF;
END $$;

-- =============================================
-- TORNAR tenant_id OBRIGATÓRIO
-- =============================================
DO $$
BEGIN
    -- tb_atleta
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'tb_atleta' AND column_name = 'tenant_id' AND is_nullable = 'YES') THEN
        ALTER TABLE tb_atleta ALTER COLUMN tenant_id SET NOT NULL;
    END IF;

    -- tb_treino_realizado
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'tb_treino_realizado' AND column_name = 'tenant_id' AND is_nullable = 'YES') THEN
        ALTER TABLE tb_treino_realizado ALTER COLUMN tenant_id SET NOT NULL;
    END IF;

    -- tb_treino_planejado
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'tb_treino_planejado' AND column_name = 'tenant_id' AND is_nullable = 'YES') THEN
        ALTER TABLE tb_treino_planejado ALTER COLUMN tenant_id SET NOT NULL;
    END IF;

    -- tb_plano_semanal
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'tb_plano_semanal' AND column_name = 'tenant_id' AND is_nullable = 'YES') THEN
        ALTER TABLE tb_plano_semanal ALTER COLUMN tenant_id SET NOT NULL;
    END IF;

    -- tb_plano_metadados
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'tb_plano_metadados' AND column_name = 'tenant_id' AND is_nullable = 'YES') THEN
        ALTER TABLE tb_plano_metadados ALTER COLUMN tenant_id SET NOT NULL;
    END IF;

    -- tb_prova
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'tb_prova' AND column_name = 'tenant_id' AND is_nullable = 'YES') THEN
        ALTER TABLE tb_prova ALTER COLUMN tenant_id SET NOT NULL;
    END IF;

    -- tb_metricas_diarias
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'tb_metricas_diarias' AND column_name = 'tenant_id' AND is_nullable = 'YES') THEN
        ALTER TABLE tb_metricas_diarias ALTER COLUMN tenant_id SET NOT NULL;
    END IF;
END $$;