-- =====================================================================
-- V106: Eixo que vai ao relogio por etapa (coach-zonas-pace-fc-prescricao, D6)
--
-- NULL = legado: o push e /me/treinos/hoje continuam derivando o alvo (FC vence, pace assume).
-- NENHUM explicito vence texto presente: o treinador escolheu "sem objetivo".
--
-- Rollback: ALTER TABLE tb_etapa_treino DROP COLUMN IF EXISTS alvo_primario;
-- =====================================================================

ALTER TABLE tb_etapa_treino
    ADD COLUMN IF NOT EXISTS alvo_primario VARCHAR(10) NULL
        CONSTRAINT ck_etapa_treino_alvo_primario CHECK (alvo_primario IN ('PACE', 'FC', 'NENHUM'));
