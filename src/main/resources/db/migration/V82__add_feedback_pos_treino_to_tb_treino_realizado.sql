-- =====================================================================
-- V82: feedback pós-treino do atleta (athlete-training-loop, D3)
-- =====================================================================
--
-- "Como foi?" reaproveita percepcao_esforco (RPE) e feedback_atleta (comentário),
-- campos que já existiam. O que é novo: sensações (lista fechada) e o carimbo que
-- define completude — feedback_registrado_em não nulo é o único critério; RPE
-- gravado sem carimbo (registro manual antigo) continua incompleto.
--
-- Aditiva e nula: sem backfill. Rollback: reverter o código; coluna e tabela ficam
-- inertes.

ALTER TABLE tb_treino_realizado
    ADD COLUMN IF NOT EXISTS feedback_registrado_em TIMESTAMP NULL;

CREATE TABLE IF NOT EXISTS tb_treino_realizado_sensacao (
    treino_realizado_id UUID NOT NULL REFERENCES tb_treino_realizado(id) ON DELETE CASCADE,
    sensacao VARCHAR(30) NOT NULL,
    PRIMARY KEY (treino_realizado_id, sensacao)
);

DO $$
BEGIN
    RAISE NOTICE '✅ V82 - feedback_registrado_em e tb_treino_realizado_sensacao adicionados';
END$$;
