-- =====================================================================
-- V33: Adiciona keycloak_organization_id em tb_assessoria para a
--      modelagem de tenant via Keycloak Organizations (substitui Groups)
-- =====================================================================

ALTER TABLE tb_assessoria
    ADD COLUMN IF NOT EXISTS keycloak_organization_id VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uk_assessoria_keycloak_org
    ON tb_assessoria(keycloak_organization_id)
    WHERE keycloak_organization_id IS NOT NULL;

DO $$
BEGIN
    RAISE NOTICE '✅ V33 - keycloak_organization_id adicionado em tb_assessoria com sucesso';
END$$;
