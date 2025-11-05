-- src/main/resources/db/migration/V8__Create_keycloak_multi_tenancy.sql

-- =============================================
-- ADICIONAR CAMPOS KEYCLOAK NA tb_assessoria
-- =============================================
ALTER TABLE tb_assessoria
    ADD COLUMN IF NOT EXISTS keycloak_group_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS keycloak_realm VARCHAR(100) DEFAULT 'menthoros-app';

-- Adicionar constraint de unicidade separadamente (pode falhar se já existir)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tb_assessoria_keycloak_group_id_key') THEN
        ALTER TABLE tb_assessoria ADD CONSTRAINT tb_assessoria_keycloak_group_id_key UNIQUE (keycloak_group_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_assessoria_keycloak_group
    ON tb_assessoria (keycloak_group_id);

COMMENT ON COLUMN tb_assessoria.keycloak_group_id IS 'ID do grupo no Keycloak correspondente a esta assessoria';
COMMENT ON COLUMN tb_assessoria.keycloak_realm IS 'Nome do realm no Keycloak';

-- =============================================
-- MODIFICAR tb_usuario PARA INTEGRAÇÃO KEYCLOAK
-- =============================================

-- Remover coluna senha_hash (não mais necessária - Keycloak gerencia senhas)
ALTER TABLE tb_usuario DROP COLUMN IF EXISTS senha_hash;

-- Adicionar campos do Keycloak
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS keycloak_id VARCHAR(100);
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS sobrenome VARCHAR(200);
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS email_verificado BOOLEAN DEFAULT FALSE;
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS ultima_sinc TIMESTAMP;

-- Adicionar constraint de unicidade no keycloak_id (separadamente)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tb_usuario_keycloak_id_key') THEN
        ALTER TABLE tb_usuario ADD CONSTRAINT tb_usuario_keycloak_id_key UNIQUE (keycloak_id);
    END IF;
END $$;

-- Adicionar índices para performance
CREATE INDEX IF NOT EXISTS idx_usuario_keycloak_id ON tb_usuario (keycloak_id);
CREATE INDEX IF NOT EXISTS idx_usuario_tenant_ativo ON tb_usuario (tenant_id, ativo);

COMMENT ON TABLE tb_usuario IS 'Cache local de usuários sincronizados do Keycloak';
COMMENT ON COLUMN tb_usuario.id IS 'UUID do usuário - será sincronizado com subject (sub) do JWT do Keycloak';
COMMENT ON COLUMN tb_usuario.keycloak_id IS 'Subject (sub) do JWT - identificador único no Keycloak';
COMMENT ON COLUMN tb_usuario.ultima_sinc IS 'Data/hora da última sincronização com o Keycloak';

-- Trigger para updated_at (se ainda não existir)
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