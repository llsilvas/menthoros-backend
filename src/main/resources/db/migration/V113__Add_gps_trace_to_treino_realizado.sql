-- =====================================================================
-- V113: Trajeto GPS do treino realizado (add-activity-gps-trace, D2)
--
-- JSONB {v, n, polyline, tSeg, fc}: polyline codificada (Google, precisao 5) do trajeto ja
-- simplificado (<= 400 pontos) com arrays paralelos de tempo relativo e FC. NULL = sem GPS
-- (esteira, indoor, manual) ou treino anterior a esta migration — o .fit nao e retido, entao
-- nao ha backfill (D5).
--
-- Aditiva e sem backfill. Rollback: a versao anterior ignora a coluna; remove-la e um DROP e
-- passa pelo gate de confirmacao.
-- =====================================================================

ALTER TABLE tb_treino_realizado
    ADD COLUMN IF NOT EXISTS gps_trace JSONB NULL;
