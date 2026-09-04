-- =====================================================================
-- V86: bloco do atleta na análise pós-treino (analise-ia-treino-atleta, D1)
-- =====================================================================
--
-- A análise que só o coach via ganha um retorno em linguagem de atleta, gerado
-- numa segunda chamada de LLM (skill athlete-workout-motivation). Os quatro textos
-- ficam nulos quando a chamada falha, o AthleteMessageValidator bloqueia o conteúdo
-- (atleta_bloqueado_motivo registra por quê) ou a análise é anterior a esta change.
-- atleta_primeira_visualizacao_em deduplica a métrica de visualização (uma por análise,
-- carimbada na primeira resposta 200 COMPLETED ao dono).
--
-- Aditiva e nula: sem backfill. Rollback: reverter o código; colunas ficam inertes.

ALTER TABLE tb_analise_workout
    ADD COLUMN IF NOT EXISTS atleta_reconhecimento TEXT NULL,
    ADD COLUMN IF NOT EXISTS atleta_como_foi TEXT NULL,
    ADD COLUMN IF NOT EXISTS atleta_esforco TEXT NULL,
    ADD COLUMN IF NOT EXISTS atleta_proximo_treino TEXT NULL,
    ADD COLUMN IF NOT EXISTS atleta_bloqueado_motivo VARCHAR(40) NULL,
    ADD COLUMN IF NOT EXISTS atleta_primeira_visualizacao_em TIMESTAMP NULL;

DO $$
BEGIN
    RAISE NOTICE '✅ V86 - colunas do bloco do atleta adicionadas a tb_analise_workout';
END$$;
