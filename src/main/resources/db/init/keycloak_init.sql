-- keycloak_init.sql
-- Cria o database usado pelo Keycloak no mesmo servidor PostgreSQL da aplicacao.
-- Executado na primeira inicializacao do volume do Postgres, apos o init do Menthoros.

SELECT 'CREATE DATABASE keycloak'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'keycloak')
\gexec

COMMENT ON DATABASE keycloak IS 'Banco de dados para o Keycloak';

GRANT ALL PRIVILEGES ON DATABASE keycloak TO menthoros;

\connect keycloak

CREATE SCHEMA IF NOT EXISTS public;

GRANT ALL ON SCHEMA public TO menthoros;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO menthoros;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO menthoros;
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO menthoros;

ALTER DEFAULT PRIVILEGES FOR USER menthoros IN SCHEMA public
GRANT ALL PRIVILEGES ON TABLES TO menthoros;

ALTER DEFAULT PRIVILEGES FOR USER menthoros IN SCHEMA public
GRANT ALL PRIVILEGES ON SEQUENCES TO menthoros;

ALTER DEFAULT PRIVILEGES FOR USER menthoros IN SCHEMA public
GRANT ALL PRIVILEGES ON FUNCTIONS TO menthoros;
