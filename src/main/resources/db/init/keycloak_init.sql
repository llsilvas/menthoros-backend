-- keycloak_init.sql
-- Script para criar banco e usuário do Keycloak no MESMO PostgreSQL do Menthoros
-- Executado APÓS menthoros_mt_init.sql (02-keycloak.sql no dockerfile)

-- ════════════════════════════════════════════════════════════════════════════════
-- CRIAR BANCO KEYCLOAK (se não existir)
-- ════════════════════════════════════════════════════════════════════════════════

CREATE DATABASE keycloak
  WITH
  ENCODING 'UTF8'
  LC_COLLATE 'en_US.UTF-8'
  LC_CTYPE 'en_US.UTF-8'
  TEMPLATE template0;

-- ════════════════════════════════════════════════════════════════════════════════
-- COMENTÁRIO
-- ════════════════════════════════════════════════════════════════════════════════

COMMENT ON DATABASE keycloak IS 'Banco de dados para Keycloak (autenticação e autorização)';

-- ════════════════════════════════════════════════════════════════════════════════
-- CONFIGURAÇÕES DE ACESSO
-- ════════════════════════════════════════════════════════════════════════════════

-- Permitir que o usuário menthoros acesse o banco keycloak
GRANT ALL PRIVILEGES ON DATABASE keycloak TO menthoros;

-- ════════════════════════════════════════════════════════════════════════════════
-- PERMISSÕES PARA USO FUTURO (tabelas que Keycloak criará)
-- ════════════════════════════════════════════════════════════════════════════════

-- Conectar ao novo banco keycloak e configurar permissões
\c keycloak

-- Criar schema public se não existir
CREATE SCHEMA IF NOT EXISTS public;

-- Dar permissões para o usuário menthoros no schema public
GRANT ALL PRIVILEGES ON SCHEMA public TO menthoros;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO menthoros;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO menthoros;
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO menthoros;

-- ════════════════════════════════════════════════════════════════════════════════
-- RESULTADO
-- ════════════════════════════════════════════════════════════════════════════════

-- Keycloak agora pode:
-- 1. Conectar ao banco "keycloak" no container postgres-mt
-- 2. Criar suas tabelas automaticamente (auto-initialization)
-- 3. Menthoros pode ver os dados do Keycloak via:
--    psql -h localhost -p 5433 -U menthoros -d keycloak
