-- =====================================================================
-- V122: Evidência do ponto de partida da temporada (season-plan-start-volume)
--
-- initial_long_run_km: longão de partida (maior treino recente, ou o informado pelo treinador no
--   recálculo). O longão de cada semana é proporcional a ele.
-- proposed_volume_km: volume de partida que o motor propôs na geração; não muda depois, para comparar
--   com o initial_volume_km quando o treinador recalcula.
-- initial_volume_source: de onde veio o initial_volume_km (mediana das 8 semanas, onboarding ou treinador).
-- initial_volume_history: km por semana das 8 semanas usadas na mediana, como na geração.
--
-- Aditiva, nulas, sem backfill: temporadas anteriores ficam sem evidência e a tela omite.
-- Rollback (V nova): remover as colunas.
-- =====================================================================

ALTER TABLE tb_plano_treino
    ADD COLUMN IF NOT EXISTS initial_long_run_km NUMERIC(4, 1),
    ADD COLUMN IF NOT EXISTS proposed_volume_km NUMERIC(6, 1),
    ADD COLUMN IF NOT EXISTS initial_volume_source VARCHAR(16)
        CONSTRAINT ck_plano_treino_initial_volume_source
            CHECK (initial_volume_source IN ('MEDIAN', 'ONBOARDING', 'COACH')),
    ADD COLUMN IF NOT EXISTS initial_volume_history JSONB;

DO $$
BEGIN
    RAISE NOTICE 'V122 - evidência do ponto de partida em tb_plano_treino';
END$$;
