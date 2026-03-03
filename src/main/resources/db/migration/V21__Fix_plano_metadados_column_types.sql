-- V21: Corrige tipos de colunas em tb_plano_metadados
-- ctl_atual, atl_atual, tsb_atual foram renomeadas de numeric(10,3) para double precision

ALTER TABLE tb_plano_metadados
    ALTER COLUMN ctl_atual   TYPE DOUBLE PRECISION USING ctl_atual::DOUBLE PRECISION,
    ALTER COLUMN atl_atual   TYPE DOUBLE PRECISION USING atl_atual::DOUBLE PRECISION,
    ALTER COLUMN tsb_atual   TYPE DOUBLE PRECISION USING tsb_atual::DOUBLE PRECISION;
