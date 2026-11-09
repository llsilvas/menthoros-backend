-- =====================================================================
-- V115: Flag de esteira do treino realizado (add-activity-treadmill-flag, D1, D6)
--
-- true so com evidencia positiva: .fit com SessionMesg.subSport = TREADMILL ou intervals.icu
-- type = "Treadmill". Treino anterior a esta migration fica false — o .fit nao e retido, entao
-- nao ha backfill. Default constante no PostgreSQL 11+ nao reescreve a tabela.
--
-- Aditiva. Rollback: a versao anterior ignora a coluna; remove-la e um DROP e passa pelo gate
-- de confirmacao.
-- =====================================================================

ALTER TABLE tb_treino_realizado
    ADD COLUMN IF NOT EXISTS esteira BOOLEAN NOT NULL DEFAULT false;
