-- =====================================================================
-- V56: adiciona fonte_limiar_pace a tb_plano_metadados
--
-- Registra a proveniencia do paceLimiarEstimado calculado em
-- TsbServiceImpl.atualizarLimiareInferidos: PROVA_REGISTRADA quando uma
-- prova valida recente (5000-21097m, ultimos 90 dias) derivou o valor via
-- formula de Riegel, MEDIA_TREINOS quando veio da inferencia passiva por
-- quintil (comportamento pre-existente). Persistido no momento do calculo
-- para nao depender de recomputo no read (a prova pode sair da janela de
-- 90 dias entre o sync e a leitura pelo coach) -- ver design.md D6 da
-- change infer-threshold-from-race-result.
--
-- NULLABLE sem backfill: null = nunca calculado por esta logica,
-- comportamento pre-existente a esta change.
--
-- Rollback: ALTER TABLE tb_plano_metadados DROP COLUMN IF EXISTS fonte_limiar_pace;
-- Feature aditiva pura -- sem impacto em dado existente; reversao segura.
-- =====================================================================

ALTER TABLE tb_plano_metadados
    ADD COLUMN IF NOT EXISTS fonte_limiar_pace VARCHAR(20);

DO $$
BEGIN
    RAISE NOTICE '✅ V56 - coluna fonte_limiar_pace adicionada a tb_plano_metadados';
END$$;
