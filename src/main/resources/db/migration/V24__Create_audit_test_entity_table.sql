-- Creates the audit_test_entity table required by AuditConfigTest.
-- AuditConfigTest defines a static inner @Entity that is picked up by Hibernate
-- entity scanning in every @SpringBootTest context. Schema validation fails
-- unless this table is present in the integration DB.
-- The test's own context uses H2 with ddl-auto=create-drop, so no data is ever
-- stored here outside of AuditConfigTest itself.
CREATE TABLE IF NOT EXISTS audit_test_entity (
    id          UUID         NOT NULL,
    name        VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    CONSTRAINT pk_audit_test_entity PRIMARY KEY (id)
);
