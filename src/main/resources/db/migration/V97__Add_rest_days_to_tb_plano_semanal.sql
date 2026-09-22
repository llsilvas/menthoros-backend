-- =====================================================================
-- V97: Dias de descanso prescritos pela IA (add-descanso-explicito-por-fadiga)
-- Fora de tb_treino_planejado de proposito: descanso nao e treino a cumprir — dentro daquela
-- tabela ele seria marcado PERDIDO no encerramento da semana e entraria na aderencia.
-- Aditiva e opcional — planos anteriores ficam com NULL (lidos como lista vazia).
-- =====================================================================

ALTER TABLE tb_plano_semanal
    ADD COLUMN IF NOT EXISTS rest_days JSONB;

COMMENT ON COLUMN tb_plano_semanal.rest_days IS
    'Dias de descanso prescritos: [{"dayOfWeek":"QUINTA","reason":"check-in de hoje: DESCANSAR"}]';

DO $$
BEGIN
    RAISE NOTICE '✅ V97 - rest_days adicionada a tb_plano_semanal';
END$$;
