-- =====================================================================
-- V109: Edição de treino em plano aprovado (coach-editar-treino-aprovado, D3/D7)
--
-- Aditiva. edited_after_approval_at nasce nula, sem backfill: nenhum treino foi editado
-- depois de aprovado antes desta change.
-- O registro guarda o treino antes/depois (dado de saúde quando o motivo é PAIN_INJURY):
-- some em cascata com o treino, que some em cascata com o atleta (LGPD).
--
-- Rollback: DROP TABLE IF EXISTS tb_planned_workout_post_approval_edit;
--   ALTER TABLE tb_treino_planejado DROP COLUMN IF EXISTS edited_after_approval_at;
-- =====================================================================

ALTER TABLE tb_treino_planejado
    ADD COLUMN IF NOT EXISTS edited_after_approval_at TIMESTAMPTZ NULL;

CREATE TABLE IF NOT EXISTS tb_planned_workout_post_approval_edit (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL,
    planned_workout_id UUID NOT NULL REFERENCES tb_treino_planejado(id) ON DELETE CASCADE,
    weekly_plan_id     UUID NOT NULL,
    edited_by          VARCHAR(255) NOT NULL,
    edited_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reason             VARCHAR(20) NULL
        CONSTRAINT ck_planned_workout_post_approval_edit_reason
            CHECK (reason IN ('PAIN_INJURY', 'SCHEDULE', 'LOAD', 'OTHER')),
    snapshot_before    JSONB NOT NULL,
    snapshot_after     JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_planned_workout_post_approval_edit_tenant_workout
    ON tb_planned_workout_post_approval_edit(tenant_id, planned_workout_id);

DO $$
BEGIN
    RAISE NOTICE 'V109 - tb_planned_workout_post_approval_edit criada com sucesso';
END$$;
