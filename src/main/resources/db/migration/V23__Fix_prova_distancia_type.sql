-- V23: Corrige tipo da coluna distancia em tb_prova
-- DistanciaProva sem @Enumerated → Hibernate usa ORDINAL → SMALLINT
-- V22 criou como VARCHAR(50) — converter para SMALLINT

ALTER TABLE tb_prova DROP COLUMN distancia;
ALTER TABLE tb_prova ADD COLUMN distancia SMALLINT;
