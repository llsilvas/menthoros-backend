-- Adds audit trail columns (created_at, updated_at, created_by, updated_by)
-- to tb_atleta so that Atleta extends AuditableEntity correctly.
ALTER TABLE tb_atleta
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) DEFAULT 'migration',
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255) DEFAULT 'migration';
