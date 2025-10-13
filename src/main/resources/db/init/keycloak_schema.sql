-- src/main/resources/db/init/keycloak_schema.sql
-- Cria o database separado para o Keycloak no mesmo servidor PostgreSQL

-- Cria o banco de dados do Keycloak
CREATE DATABASE keycloak
    WITH
    OWNER = menthoros
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

COMMENT ON DATABASE keycloak IS 'Database do Keycloak para autenticação e autorização';

-- Concede permissões
GRANT ALL PRIVILEGES ON DATABASE keycloak TO menthoros;