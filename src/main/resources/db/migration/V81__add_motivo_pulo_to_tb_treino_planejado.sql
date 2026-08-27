-- =====================================================================
-- V81: pulo do treino de hoje pelo atleta (athlete-training-loop, D4)
-- =====================================================================
--
-- "Não vou conseguir hoje" NÃO cria status novo: o planejado vai a PERDIDO,
-- que a aderência e o encerramento da semana já tratam. O que é novo é o
-- MOTIVO (opcional, lista curta) e o CARIMBO de quando o atleta pulou —
-- é o que distingue "pulou e avisou" de "simplesmente não fez".
--
-- Aditiva e nula: linhas existentes ficam como estão. Os dois campos são
-- limpos quando um realizado vincula o planejado (reversão do pulo).

ALTER TABLE tb_treino_planejado
    ADD COLUMN IF NOT EXISTS motivo_pulo VARCHAR(20) NULL,
    ADD COLUMN IF NOT EXISTS pulado_em   TIMESTAMP   NULL;

DO $$
BEGIN
    RAISE NOTICE '✅ V81 - colunas motivo_pulo e pulado_em adicionadas em tb_treino_planejado';
END$$;
