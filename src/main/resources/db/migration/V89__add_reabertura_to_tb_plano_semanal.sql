-- =====================================================================
-- V89: reabertura de revisão por prova (prova-no-plano-semanal, D4)
-- =====================================================================
--
-- Quando uma prova entra ou sai de uma semana já aprovada, o plano volta a AGUARDANDO_REVISAO
-- em vez de ficar sinalizado por fora — o coach precisa ver e reaprovar a semana que mudou de
-- conteúdo. motivo_reabertura e reaberto_em registram por quê e quando; aprovar ou rejeitar
-- limpa os dois.
--
-- Aditiva; rollback: reverter o código, colunas ficam inertes.

ALTER TABLE tb_plano_semanal
    ADD COLUMN IF NOT EXISTS motivo_reabertura VARCHAR(20) NULL,
    ADD COLUMN IF NOT EXISTS reaberto_em TIMESTAMP NULL;

DO $$
BEGIN
    RAISE NOTICE '✅ V89 - motivo_reabertura e reaberto_em adicionadas a tb_plano_semanal';
END$$;
