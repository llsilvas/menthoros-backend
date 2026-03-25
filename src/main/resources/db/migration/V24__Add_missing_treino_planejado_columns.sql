-- V24: Adiciona coluna external_id ausente em tb_treino_planejado

ALTER TABLE tb_treino_planejado
    ADD COLUMN IF NOT EXISTS external_id VARCHAR(255);
