-- =====================================================================
-- V2: Adiciona suporte multi-tenancy (Keycloak + Assessoria)
-- =====================================================================
-- Consolida: V17 (tb_assessoria, tb_usuario), V18 (Keycloak fields)
-- =====================================================================

-- ========================================
-- 1. ASSESSORIA (Tenant)
-- ========================================

CREATE TABLE IF NOT EXISTS tb_assessoria (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(200) NOT NULL,
    dominio VARCHAR(100) UNIQUE NOT NULL,
    razao_social VARCHAR(200),
    cnpj VARCHAR(18) UNIQUE,
    email_contato VARCHAR(100),
    telefone VARCHAR(20),
    -- Endereço
    logradouro VARCHAR(200),
    numero VARCHAR(10),
    complemento VARCHAR(100),
    bairro VARCHAR(100),
    cidade VARCHAR(100),
    estado VARCHAR(2),
    cep VARCHAR(9),
    -- Marca (branding)
    logo_url VARCHAR(500),
    cor_primaria VARCHAR(7) DEFAULT '#6366F1',
    cor_secundaria VARCHAR(7) DEFAULT '#EC4899',
    -- Limites
    max_atletas INTEGER,
    max_tecnicos INTEGER,
    -- Plano
    plano VARCHAR(20) NOT NULL DEFAULT 'BASIC',
    data_assinatura TIMESTAMP,
    data_expiracao TIMESTAMP,
    trial BOOLEAN NOT NULL DEFAULT FALSE,
    data_fim_trial TIMESTAMP,
    -- Feature flags
    feature_ia_avancada BOOLEAN DEFAULT FALSE,
    feature_relatorios_customizados BOOLEAN DEFAULT FALSE,
    feature_integracao_strava BOOLEAN DEFAULT TRUE,
    feature_api_externa BOOLEAN DEFAULT FALSE,
    -- Keycloak integration (V18)
    keycloak_group_id VARCHAR(100) UNIQUE,
    keycloak_realm VARCHAR(100) DEFAULT 'menthoros-app',
    -- Status
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_plano CHECK (plano IN ('BASIC', 'PRO', 'ENTERPRISE'))
);

CREATE INDEX IF NOT EXISTS idx_assessoria_dominio ON tb_assessoria(dominio);
CREATE INDEX IF NOT EXISTS idx_assessoria_ativo ON tb_assessoria(ativo);
CREATE INDEX IF NOT EXISTS idx_assessoria_cnpj ON tb_assessoria(cnpj);
CREATE INDEX IF NOT EXISTS idx_assessoria_keycloak_group ON tb_assessoria(keycloak_group_id);

COMMENT ON TABLE tb_assessoria IS 'Representa uma organização (assessoria/treinador) - tenant root';
COMMENT ON COLUMN tb_assessoria.keycloak_group_id IS 'ID do grupo no Keycloak correspondente a esta assessoria (isolamento de tenants)';
COMMENT ON COLUMN tb_assessoria.keycloak_realm IS 'Nome do realm Keycloak onde usuários desta assessoria são gerenciados';

-- ========================================
-- 2. USUÁRIO (sincronizado com Keycloak)
-- ========================================

CREATE TABLE IF NOT EXISTS tb_usuario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tb_assessoria(id) ON DELETE CASCADE,
    -- Dados pessoais
    nome VARCHAR(200) NOT NULL,
    sobrenome VARCHAR(200),
    email VARCHAR(100) UNIQUE NOT NULL,
    avatar_url VARCHAR(500),
    -- Keycloak integration (V18)
    keycloak_id VARCHAR(100) UNIQUE,
    email_verificado BOOLEAN DEFAULT FALSE,
    -- Permissões
    role VARCHAR(20) NOT NULL DEFAULT 'TECNICO',
    -- Auditoria
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    ultimo_acesso TIMESTAMP,
    ultima_sinc TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_role CHECK (role IN ('ADMIN', 'TECNICO', 'VISUALIZADOR'))
);

CREATE INDEX IF NOT EXISTS idx_usuario_email ON tb_usuario(email);
CREATE INDEX IF NOT EXISTS idx_usuario_tenant ON tb_usuario(tenant_id);
CREATE INDEX IF NOT EXISTS idx_usuario_tenant_ativo ON tb_usuario(tenant_id, ativo);
CREATE INDEX IF NOT EXISTS idx_usuario_keycloak_id ON tb_usuario(keycloak_id);

COMMENT ON TABLE tb_usuario IS 'Cache local de usuários sincronizados do Keycloak';
COMMENT ON COLUMN tb_usuario.keycloak_id IS 'Subject (sub) do JWT - identificador único no Keycloak para este usuário';
COMMENT ON COLUMN tb_usuario.ultima_sinc IS 'Timestamp da última sincronização com Keycloak (para detectar desatualizações)';

-- Trigger para atualizar updated_at
CREATE OR REPLACE FUNCTION update_usuario_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_usuario_updated_at ON tb_usuario;
CREATE TRIGGER trigger_usuario_updated_at
    BEFORE UPDATE ON tb_usuario
    FOR EACH ROW
    EXECUTE FUNCTION update_usuario_updated_at();

-- ========================================
-- 3. ADICIONAR tenant_id NAS TABELAS EXISTENTES
-- ========================================

-- tb_atleta
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_atleta' AND column_name = 'tenant_id') THEN
        ALTER TABLE tb_atleta ADD COLUMN tenant_id UUID REFERENCES tb_assessoria(id) ON DELETE CASCADE;
        CREATE INDEX idx_atleta_tenant ON tb_atleta(tenant_id);
        CREATE INDEX idx_atleta_tenant_ativo ON tb_atleta(tenant_id, ativo);
    END IF;
END $$;

-- tb_prova
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_prova' AND column_name = 'tenant_id') THEN
        ALTER TABLE tb_prova ADD COLUMN tenant_id UUID REFERENCES tb_assessoria(id) ON DELETE CASCADE;
        CREATE INDEX idx_prova_tenant ON tb_prova(tenant_id);
    END IF;
END $$;

-- tb_plano_metadados
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_plano_metadados' AND column_name = 'tenant_id') THEN
        ALTER TABLE tb_plano_metadados ADD COLUMN tenant_id UUID REFERENCES tb_assessoria(id) ON DELETE CASCADE;
        CREATE INDEX idx_plano_metadados_tenant ON tb_plano_metadados(tenant_id);
    END IF;
END $$;

-- tb_plano_treino
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_plano_treino' AND column_name = 'tenant_id') THEN
        ALTER TABLE tb_plano_treino ADD COLUMN tenant_id UUID REFERENCES tb_assessoria(id) ON DELETE CASCADE;
        CREATE INDEX idx_plano_treino_tenant ON tb_plano_treino(tenant_id);
    END IF;
END $$;

-- tb_plano_semanal
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_plano_semanal' AND column_name = 'tenant_id') THEN
        ALTER TABLE tb_plano_semanal ADD COLUMN tenant_id UUID REFERENCES tb_assessoria(id) ON DELETE CASCADE;
        CREATE INDEX idx_plano_semanal_tenant ON tb_plano_semanal(tenant_id);
    END IF;
END $$;

-- tb_treino_planejado
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_treino_planejado' AND column_name = 'tenant_id') THEN
        ALTER TABLE tb_treino_planejado ADD COLUMN tenant_id UUID REFERENCES tb_assessoria(id) ON DELETE CASCADE;
        CREATE INDEX idx_treino_planejado_tenant ON tb_treino_planejado(tenant_id);
    END IF;
END $$;

-- tb_treino_realizado
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_treino_realizado' AND column_name = 'tenant_id') THEN
        ALTER TABLE tb_treino_realizado ADD COLUMN tenant_id UUID REFERENCES tb_assessoria(id) ON DELETE CASCADE;
        CREATE INDEX idx_treino_realizado_tenant ON tb_treino_realizado(tenant_id);
    END IF;
END $$;

-- tb_metricas_diarias
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_metricas_diarias' AND column_name = 'tenant_id') THEN
        ALTER TABLE tb_metricas_diarias ADD COLUMN tenant_id UUID REFERENCES tb_assessoria(id) ON DELETE CASCADE;
        CREATE INDEX idx_metricas_diarias_tenant ON tb_metricas_diarias(tenant_id);
    END IF;
END $$;

-- ========================================
-- 4. INSERIR ASSESSORIA PADRÃO
-- ========================================

INSERT INTO tb_assessoria (id, nome, dominio, plano, ativo, trial, keycloak_realm)
SELECT gen_random_uuid(), 'Menthoros Default', 'default', 'ENTERPRISE', TRUE, FALSE, 'menthoros-app'
WHERE NOT EXISTS (SELECT 1 FROM tb_assessoria WHERE dominio = 'default');

-- ========================================
-- 5. ATUALIZAR REGISTROS EXISTENTES COM TENANT
-- ========================================

DO $$
DECLARE
    default_tenant_id UUID;
BEGIN
    SELECT id INTO default_tenant_id FROM tb_assessoria WHERE dominio = 'default' LIMIT 1;
    
    IF default_tenant_id IS NOT NULL THEN
        UPDATE tb_atleta SET tenant_id = default_tenant_id WHERE tenant_id IS NULL;
        UPDATE tb_prova SET tenant_id = default_tenant_id WHERE tenant_id IS NULL;
        UPDATE tb_plano_metadados SET tenant_id = default_tenant_id WHERE tenant_id IS NULL;
        UPDATE tb_plano_treino SET tenant_id = default_tenant_id WHERE tenant_id IS NULL;
        UPDATE tb_plano_semanal SET tenant_id = default_tenant_id WHERE tenant_id IS NULL;
        UPDATE tb_treino_planejado SET tenant_id = default_tenant_id WHERE tenant_id IS NULL;
        UPDATE tb_treino_realizado SET tenant_id = default_tenant_id WHERE tenant_id IS NULL;
        UPDATE tb_metricas_diarias SET tenant_id = default_tenant_id WHERE tenant_id IS NULL;
    END IF;
END $$;

-- ========================================
-- 6. TORNAR tenant_id NOT NULL
-- ========================================

DO $$
BEGIN
    -- Verificar cada tabela antes de alterar
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_atleta' AND column_name = 'tenant_id' AND is_nullable = 'YES') THEN
        ALTER TABLE tb_atleta ALTER COLUMN tenant_id SET NOT NULL;
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_prova' AND column_name = 'tenant_id' AND is_nullable = 'YES') THEN
        ALTER TABLE tb_prova ALTER COLUMN tenant_id SET NOT NULL;
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_plano_metadados' AND column_name = 'tenant_id' AND is_nullable = 'YES') THEN
        ALTER TABLE tb_plano_metadados ALTER COLUMN tenant_id SET NOT NULL;
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_plano_treino' AND column_name = 'tenant_id' AND is_nullable = 'YES') THEN
        ALTER TABLE tb_plano_treino ALTER COLUMN tenant_id SET NOT NULL;
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_plano_semanal' AND column_name = 'tenant_id' AND is_nullable = 'YES') THEN
        ALTER TABLE tb_plano_semanal ALTER COLUMN tenant_id SET NOT NULL;
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_treino_planejado' AND column_name = 'tenant_id' AND is_nullable = 'YES') THEN
        ALTER TABLE tb_treino_planejado ALTER COLUMN tenant_id SET NOT NULL;
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_treino_realizado' AND column_name = 'tenant_id' AND is_nullable = 'YES') THEN
        ALTER TABLE tb_treino_realizado ALTER COLUMN tenant_id SET NOT NULL;
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_metricas_diarias' AND column_name = 'tenant_id' AND is_nullable = 'YES') THEN
        ALTER TABLE tb_metricas_diarias ALTER COLUMN tenant_id SET NOT NULL;
    END IF;
END $$;

-- ========================================
-- Finalização
-- ========================================

DO $$
BEGIN
    RAISE NOTICE '✅ V2 - Multi-tenancy implementado';
    RAISE NOTICE '   - tb_assessoria (Keycloak realms)';
    RAISE NOTICE '   - tb_usuario (sincronizados com Keycloak)';
    RAISE NOTICE '   - tenant_id adicionado em 8 tabelas';
    RAISE NOTICE '   - Assessoria default criada';
END$$;