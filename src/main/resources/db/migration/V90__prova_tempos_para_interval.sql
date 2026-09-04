-- =====================================================================
-- V90: tempo_objetivo e tempo_realizado viram interval (prova-no-plano-semanal, D6)
-- =====================================================================
--
-- Os dois campos são durações (o que o atleta pretende fazer / fez), não horários do dia — a
-- decisão original de usar TIME foi um erro de modelagem. Migram para INTERVAL, a mesma
-- convenção que Duration já usa em tb_treino_planejado/tb_treino_realizado (duracao_min via
-- INTERVAL_SECOND). O contrato JSON não muda: os DTOs continuam trafegando "HH:mm:ss" por um
-- serializer/deserializer dedicado (DurationHhMmSsSerializer/Deserializer), então o front não
-- precisa mudar.
--
-- v_historico_provas_completadas (V9) lê tempo_realizado — Postgres recusa ALTER COLUMN TYPE
-- numa coluna usada por view, então precisa cair e voltar (texto idêntico ao da V9; só o tipo
-- por trás muda).
--
-- Aditiva quanto ao dado (USING converte o valor existente); rollback: reverter o código e uma
-- migration nova reconverteria interval -> time.

DROP VIEW IF EXISTS v_historico_provas_completadas;

ALTER TABLE tb_prova
    ALTER COLUMN tempo_objetivo TYPE interval USING (tempo_objetivo - TIME '00:00:00'),
    ALTER COLUMN tempo_realizado TYPE interval USING (tempo_realizado - TIME '00:00:00');

CREATE OR REPLACE VIEW v_historico_provas_completadas AS
SELECT
    p.id,
    p.tenant_id,
    p.atleta_id,
    p.nome_prova,
    p.tipo_prova,
    p.data_prova,
    p.distancia_km,
    p.tempo_realizado,
    p.posicao_geral,
    p.posicao_categoria,
    p.tss_prova,
    p.percepcao_esforco_prova,
    p.feedback_prova,
    p.semanas_preparacao,
    p.created_at
FROM tb_prova p
WHERE p.foi_realizada = TRUE
ORDER BY p.data_prova DESC;

COMMENT ON VIEW v_historico_provas_completadas IS 'Histórico de provas realizadas com resultados completos para análise';

DO $$
BEGIN
    RAISE NOTICE '✅ V90 - tempo_objetivo e tempo_realizado de tb_prova convertidas para interval';
END$$;
