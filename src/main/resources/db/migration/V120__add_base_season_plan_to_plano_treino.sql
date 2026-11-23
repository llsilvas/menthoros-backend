-- =====================================================================
-- V120: Versão-base do ajuste da temporada (add-season-plan-adjust-v2)
--
-- base_season_plan_id: a temporada ACTIVE da qual um rascunho de nova versão (PROPOSED com
--   version = base.version + 1) foi copiado. Nulo na v1 e em todo PlanoTreino anterior. Na aprovação,
--   o rascunho só vira ACTIVE se a base ainda for a ACTIVE do atleta (senão 409).
--   ON DELETE SET NULL: a base nunca é apagada pelo fluxo (vira ARCHIVED), mas um DELETE de ADMIN não
--   pode levar a versão seguinte junto.
--
-- Aditiva, sem backfill. Rollback (V nova): remover o índice e a coluna.
-- =====================================================================

ALTER TABLE tb_plano_treino
    ADD COLUMN IF NOT EXISTS base_season_plan_id UUID
        CONSTRAINT fk_plano_treino_base_season_plan REFERENCES tb_plano_treino (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_plano_treino_base_season_plan
    ON tb_plano_treino (base_season_plan_id);

DO $$
BEGIN
    RAISE NOTICE 'V120 - base_season_plan_id em tb_plano_treino';
END$$;
