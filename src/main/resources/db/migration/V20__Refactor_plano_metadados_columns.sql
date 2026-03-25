-- V20: Alinha tb_plano_metadados com a entidade PlanoMetaDados
-- Renomeia colunas antigas e adiciona colunas ausentes

-- =============================================
-- PARTE 1: Renomear colunas com nomes antigos
-- =============================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_plano_metadados' AND column_name = 'ctl')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_plano_metadados' AND column_name = 'ctl_atual') THEN
        ALTER TABLE tb_plano_metadados RENAME COLUMN ctl TO ctl_atual;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_plano_metadados' AND column_name = 'atl')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_plano_metadados' AND column_name = 'atl_atual') THEN
        ALTER TABLE tb_plano_metadados RENAME COLUMN atl TO atl_atual;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_plano_metadados' AND column_name = 'tsb')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_plano_metadados' AND column_name = 'tsb_atual') THEN
        ALTER TABLE tb_plano_metadados RENAME COLUMN tsb TO tsb_atual;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_plano_metadados' AND column_name = 'volume_semanal_anterior')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_plano_metadados' AND column_name = 'volume_semanal_medio') THEN
        ALTER TABLE tb_plano_metadados RENAME COLUMN volume_semanal_anterior TO volume_semanal_medio;
    END IF;
END $$;

-- =============================================
-- PARTE 2: Adicionar colunas ausentes
-- =============================================

ALTER TABLE tb_plano_metadados
    ADD COLUMN IF NOT EXISTS data_ultima_atualizacao       DATE,
    ADD COLUMN IF NOT EXISTS ctl_atual                     DOUBLE PRECISION          DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS atl_atual                     DOUBLE PRECISION          DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS tsb_atual                     DOUBLE PRECISION          DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS ramp_rate_atual               DOUBLE PRECISION          DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS volume_semanal_medio          DECIMAL(10, 2),
    ADD COLUMN IF NOT EXISTS volume_planejado              DECIMAL(10, 2),
    ADD COLUMN IF NOT EXISTS tss_semanal_medio             INTEGER,
    ADD COLUMN IF NOT EXISTS treinos_por_semana_medio      DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS dias_consecutivos_treino      INTEGER                   DEFAULT 0,
    ADD COLUMN IF NOT EXISTS dias_desde_ultimo_descanso    INTEGER                   DEFAULT 0,
    ADD COLUMN IF NOT EXISTS semanas_progressao_continua   INTEGER                   DEFAULT 0,
    ADD COLUMN IF NOT EXISTS alerta_sobrecarga             BOOLEAN                   DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS alerta_ramp_alto              BOOLEAN                   DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS alerta_dias_consecutivos      BOOLEAN                   DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS alerta_necessita_descanso     BOOLEAN                   DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS mensagem_alerta               TEXT,
    ADD COLUMN IF NOT EXISTS embedding                     vector(1536);
