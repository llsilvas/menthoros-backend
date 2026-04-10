-- menthoros_mt_init.sql
-- Script de inicializacao do database principal "menthoros"
-- Executado automaticamente na primeira inicializacao do container PostgreSQL

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
-- CRIAR SCHEMA PUBLIC
-- ════════════════════════════════════════════════════════════════════════════════

CREATE SCHEMA IF NOT EXISTS public;

-- ════════════════════════════════════════════════════════════════════════════════
-- COMENTÁRIOS DE DOCUMENTAÇÃO
-- ════════════════════════════════════════════════════════════════════════════════

COMMENT ON SCHEMA public IS 'Schema compartilhado apenas para extensoes e objetos globais';

-- ════════════════════════════════════════════════════════════════════════════════
-- CRIAR USUÁRIO ESPECÍFICO PARA APLICAÇÃO (se não existir)
-- ════════════════════════════════════════════════════════════════════════════════

-- O usuário já foi criado no docker-compose.yml
-- Mas garantir que tem permissões corretas
DO $$
BEGIN
    -- Dar permissoes ao usuario menthoros no database principal
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

-- Revogar permissoes publicas (apenas usuarios especificos terao acesso)
REVOKE ALL ON SCHEMA public FROM PUBLIC;

-- Permitir apenas menthoros acessar
GRANT USAGE, CREATE ON SCHEMA public TO menthoros;

ALTER DEFAULT PRIVILEGES FOR USER menthoros IN SCHEMA public
GRANT ALL PRIVILEGES ON TABLES TO menthoros;

ALTER DEFAULT PRIVILEGES FOR USER menthoros IN SCHEMA public
GRANT ALL PRIVILEGES ON SEQUENCES TO menthoros;

ALTER DEFAULT PRIVILEGES FOR USER menthoros IN SCHEMA public
GRANT ALL PRIVILEGES ON FUNCTIONS TO menthoros;

-- ════════════════════════════════════════════════════════════════════════════════
-- COMENTÁRIO FINAL
-- ════════════════════════════════════════════════════════════════════════════════

-- Database "menthoros" pronto para a aplicacao no schema public.
