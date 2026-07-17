-- =====================================================================
-- V57: adiciona tipo_plano_atleta e data_vencimento_plano a tb_atleta
--
-- Registra o plano de cobranca do atleta com a assessoria (periodicidade
-- + data de vencimento), preenchido manualmente pelo treinador. Distinto
-- de PlanoAssessoria (plano SaaS da assessoria com a Menthoros) e de
-- PlanoMetaDados (metadados de plano de treino) -- ver design.md D1 da
-- change add-athlete-billing-plan.
--
-- O status de vencimento (EM_DIA/PROXIMO_VENCIMENTO/VENCIDO) e derivado
-- em tempo de leitura a partir de data_vencimento_plano -- nunca
-- persistido (design.md D2).
--
-- NULLABLE sem backfill: null = atleta sem dados de cobranca cadastrados,
-- comportamento pre-existente a esta change.
--
-- Rollback: ALTER TABLE tb_atleta DROP COLUMN IF EXISTS tipo_plano_atleta;
--           ALTER TABLE tb_atleta DROP COLUMN IF EXISTS data_vencimento_plano;
-- Feature aditiva pura -- sem impacto em dado existente; reversao segura.
-- =====================================================================

ALTER TABLE tb_atleta
    ADD COLUMN IF NOT EXISTS tipo_plano_atleta VARCHAR(20);

ALTER TABLE tb_atleta
    ADD COLUMN IF NOT EXISTS data_vencimento_plano DATE;

DO $$
BEGIN
    RAISE NOTICE '✅ V57 - colunas tipo_plano_atleta e data_vencimento_plano adicionadas a tb_atleta';
END$$;
