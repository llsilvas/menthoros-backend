-- =====================================================================
-- V121: Volume de partida da temporada (add-season-plan-version-diff)
--
-- initial_volume_km: volume semanal de partida que o motor usou na geração (média das últimas
--   semanas ou volume do onboarding). O recálculo do ajuste parte dele; o rascunho o copia da ativa.
--   Nulo em toda temporada anterior: o flag season-plan.enabled está desligado em produção, então só
--   há dados de dev, e a tela pede o valor ao treinador quando ele falta.
--
-- Aditiva, sem backfill. Rollback (V nova): remover a coluna.
-- =====================================================================

ALTER TABLE tb_plano_treino
    ADD COLUMN IF NOT EXISTS initial_volume_km NUMERIC(6, 1);

DO $$
BEGIN
    RAISE NOTICE 'V121 - initial_volume_km em tb_plano_treino';
END$$;
