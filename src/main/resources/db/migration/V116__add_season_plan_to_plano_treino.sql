-- =====================================================================
-- V116: Plano de temporada sobre tb_plano_treino
--       (add-season-plan-model-integrity, ticket 1 do épico add-season-plan-macrocycle)
--
-- 1) Colunas novas (identificadores em inglês, ADR-0007), todas nulas: PlanoTreino legado
--    continua com season_status nulo e fora do índice parcial. Sem backfill.
-- 2) FKs que apagavam a temporada em cascata:
--    tb_plano_treino.prova_id          CASCADE -> RESTRICT (excluir prova com temporada vira 422 na aplicação)
--    tb_plano_semanal.plano_treino_id  CASCADE -> SET NULL (plano semanal aprovado sobrevive à temporada)
--    As FKs da V1 não têm nome; o nome gerado é buscado no catálogo.
-- 3) No máximo uma temporada ACTIVE por atleta, garantido no banco.
--
-- Rollback (V nova): recriar as duas FKs com ON DELETE CASCADE, remover o índice
--   uk_plano_treino_active_season, a check ck_plano_treino_season_status e as colunas novas.
--   Nenhuma coluna existente muda, então não há perda de dado legado.
-- =====================================================================

ALTER TABLE tb_plano_treino
    ADD COLUMN IF NOT EXISTS total_weeks        INTEGER,
    ADD COLUMN IF NOT EXISTS version            INTEGER,
    ADD COLUMN IF NOT EXISTS season_status      VARCHAR(20),
    ADD COLUMN IF NOT EXISTS version_reason     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS initial_ctl        DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS peak_volume_km     DECIMAL(10,3),
    ADD COLUMN IF NOT EXISTS max_long_run_km    DECIMAL(10,3),
    ADD COLUMN IF NOT EXISTS short_preparation  BOOLEAN,
    ADD COLUMN IF NOT EXISTS approved_by        UUID,
    ADD COLUMN IF NOT EXISTS approved_at        TIMESTAMPTZ;

ALTER TABLE tb_plano_treino
    ADD CONSTRAINT ck_plano_treino_season_status
        CHECK (season_status IN ('PROPOSED', 'ACTIVE', 'ARCHIVED', 'COMPLETED'));

DO $$
DECLARE
    fk_name TEXT;
BEGIN
    SELECT c.conname INTO fk_name
      FROM pg_constraint c
      JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
     WHERE c.contype = 'f'
       AND c.conrelid = 'tb_plano_treino'::regclass
       AND c.confrelid = 'tb_prova'::regclass
       AND a.attname = 'prova_id';
    IF fk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE tb_plano_treino DROP CONSTRAINT %I', fk_name);
    END IF;

    SELECT c.conname INTO fk_name
      FROM pg_constraint c
      JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
     WHERE c.contype = 'f'
       AND c.conrelid = 'tb_plano_semanal'::regclass
       AND c.confrelid = 'tb_plano_treino'::regclass
       AND a.attname = 'plano_treino_id';
    IF fk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE tb_plano_semanal DROP CONSTRAINT %I', fk_name);
    END IF;
END $$;

ALTER TABLE tb_plano_treino
    ADD CONSTRAINT fk_plano_treino_prova
        FOREIGN KEY (prova_id) REFERENCES tb_prova(id) ON DELETE RESTRICT;

ALTER TABLE tb_plano_semanal
    ADD CONSTRAINT fk_plano_semanal_plano_treino
        FOREIGN KEY (plano_treino_id) REFERENCES tb_plano_treino(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_plano_treino_active_season
    ON tb_plano_treino (atleta_id)
    WHERE season_status = 'ACTIVE';

DO $$
BEGIN
    RAISE NOTICE 'V116 - plano de temporada em tb_plano_treino aplicado';
END$$;
