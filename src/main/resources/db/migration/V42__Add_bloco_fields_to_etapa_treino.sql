-- =====================================================================
-- V42: Adiciona suporte a blocos repetidos nas etapas de treino
-- =====================================================================

ALTER TABLE tb_etapa_treino
    ADD COLUMN IF NOT EXISTS bloco_id         UUID,
    ADD COLUMN IF NOT EXISTS bloco_repeticoes INTEGER;

COMMENT ON COLUMN tb_etapa_treino.bloco_id IS
    'UUID compartilhado por todas as etapas que pertencem ao mesmo bloco repetido';

COMMENT ON COLUMN tb_etapa_treino.bloco_repeticoes IS
    'Número de repetições do bloco ao qual esta etapa pertence';

CREATE INDEX IF NOT EXISTS idx_etapa_treino_bloco_id ON tb_etapa_treino(bloco_id);

DO $$
BEGIN
    RAISE NOTICE '✅ V42 - bloco_id e bloco_repeticoes adicionados a tb_etapa_treino';
END$$;
