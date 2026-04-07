-- V25: Adiciona coluna external_id em tb_treino_realizado (para deduplicação de importações)

ALTER TABLE tb_treino_realizado
    ADD COLUMN IF NOT EXISTS external_id VARCHAR(255);

-- Remove duplicatas mantendo apenas o registro mais antigo (menor criado_em) por external_id
DELETE FROM tb_treino_realizado
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY external_id
                   ORDER BY criado_em ASC, id ASC
               ) AS rn
        FROM tb_treino_realizado
        WHERE external_id IS NOT NULL
    ) ranked
    WHERE rn > 1
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_treino_realizado_external_id
    ON tb_treino_realizado (external_id)
    WHERE external_id IS NOT NULL;

COMMENT ON COLUMN tb_treino_realizado.external_id IS 'Identificador externo para deduplicação (ex: importação do Garmin, Strava, etc)';
