-- V22: Add timezone field to tb_atleta for reconciliation scheduling
-- Default: America/Sao_Paulo (Brazilian timezone)
-- Used for normalizing activity dates in the matching engine

ALTER TABLE tb_atleta
ADD COLUMN timezone VARCHAR(50) DEFAULT 'America/Sao_Paulo';

-- Index for future queries by timezone (if needed for batch operations)
CREATE INDEX idx_atleta_timezone ON tb_atleta(timezone);
