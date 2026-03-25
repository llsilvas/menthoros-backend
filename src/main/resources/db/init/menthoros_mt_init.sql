-- menthoros_mt_init.sql
-- Script de inicialização do banco menthoros-multi
-- Executado automaticamente quando Docker criar o container postgres-mt
-- Este script cria extensões e schemas necessários para multi-tenancy

-- ════════════════════════════════════════════════════════════════════════════════
-- CRIAR EXTENSÕES
-- ════════════════════════════════════════════════════════════════════════════════

-- UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;

-- pgvector (para embeddings do OpenAI)
CREATE EXTENSION IF NOT EXISTS "vector" WITH SCHEMA public;

-- Full-text search em português
CREATE EXTENSION IF NOT EXISTS "pg_trgm" WITH SCHEMA public;

-- ════════════════════════════════════════════════════════════════════════════════
-- CRIAR SCHEMA PÚBLICO
-- ════════════════════════════════════════════════════════════════════════════════

-- O schema "public" já existe por padrão no PostgreSQL
-- Mas garantir que ele existe e tem as permissões corretas
CREATE SCHEMA IF NOT EXISTS public;

-- ════════════════════════════════════════════════════════════════════════════════
-- COMENTÁRIOS DE DOCUMENTAÇÃO
-- ════════════════════════════════════════════════════════════════════════════════

COMMENT ON SCHEMA public IS 'Schema padrão para aplicação Menthoros Multi-Tenancy';

-- ════════════════════════════════════════════════════════════════════════════════
-- CRIAR USUÁRIO ESPECÍFICO PARA APLICAÇÃO (se não existir)
-- ════════════════════════════════════════════════════════════════════════════════

-- O usuário já foi criado no docker-compose.yml
-- Mas garantir que tem permissões corretas
DO $$
BEGIN
    -- Dar permissões ao usuário menthoros
    GRANT ALL PRIVILEGES ON SCHEMA public TO menthoros;
    GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO menthoros;
    GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO menthoros;
    GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO menthoros;
EXCEPTION
    WHEN OTHERS THEN
        -- Usuário já existe ou erro de permissão, continuar
        NULL;
END
$$;

-- ════════════════════════════════════════════════════════════════════════════════
-- ÍNDICES PARA PERFORMANCE (será criado por Flyway normalmente)
-- ════════════════════════════════════════════════════════════════════════════════

-- Estes índices são criados por Flyway nas migrations
-- Mantém aqui apenas como referência do que será criado

-- CREATE INDEX IF NOT EXISTS idx_assessoria_dominio ON public.tb_assessoria(dominio);
-- CREATE INDEX IF NOT EXISTS idx_assessoria_keycloak_group ON public.tb_assessoria(keycloak_group_id);
-- CREATE INDEX IF NOT EXISTS idx_usuario_assessoria ON public.tb_usuario(assessoria_id);
-- CREATE INDEX IF NOT EXISTS idx_atleta_assessoria ON public.tb_atleta(assessoria_id);

-- ════════════════════════════════════════════════════════════════════════════════
-- CONFIGURAÇÕES DE SEGURANÇA
-- ════════════════════════════════════════════════════════════════════════════════

-- Revogar permissões públicas (apenas usuários específicos terão acesso)
REVOKE ALL ON SCHEMA public FROM PUBLIC;

-- Permitir apenas menthoros acessar
GRANT USAGE ON SCHEMA public TO menthoros;

-- ════════════════════════════════════════════════════════════════════════════════
-- COMENTÁRIO FINAL
-- ════════════════════════════════════════════════════════════════════════════════

-- Banco menthoros-multi está pronto para Flyway executar migrations
-- Flyway criará as tabelas e dados conforme definido em src/main/resources/db/migration/
