-- =====================================================================
-- V9: Adiciona suporte a alertas na tb_plano_metadados e cria VIEWs
-- =====================================================================
-- Consolida: V20 (alertas), V21 (views), V23 (views de análise)
-- =====================================================================

-- ========================================
-- 1. GARANTIR CAMPOS DE ALERTA EM tb_plano_metadados
-- ========================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_plano_metadados' AND column_name = 'alerta_sobrecarga') THEN
        ALTER TABLE tb_plano_metadados
            ADD COLUMN alerta_sobrecarga BOOLEAN DEFAULT FALSE,
            ADD COLUMN alerta_ramp_alto BOOLEAN DEFAULT FALSE,
            ADD COLUMN alerta_dias_consecutivos BOOLEAN DEFAULT FALSE,
            ADD COLUMN alerta_necessita_descanso BOOLEAN DEFAULT FALSE,
            ADD COLUMN mensagem_alerta TEXT;
    END IF;
END $$;

-- ========================================
-- 2. CRIAR VIEW MATERIALIZADA: agregações diárias
-- ========================================

CREATE MATERIALIZED VIEW IF NOT EXISTS v_metricas_diarias_agregadas AS
SELECT 
    md.id,
    md.tenant_id,
    md.atleta_id,
    md.data,
    md.tss,
    md.ctl,
    md.atl,
    md.tsb,
    md.ramp_rate,
    md.fatigue_ratio,
    md.forma_percentual,
    md.treinos_realizados,
    md.volume_km,
    md.foi_dia_descanso,
    CASE 
        WHEN md.tsb < -35 THEN 'SUPERCOMPENSACAO'
        WHEN md.tsb BETWEEN -35 AND -10 THEN 'EXCELENTE'
        WHEN md.tsb BETWEEN -10 AND 5 THEN 'BOM'
        WHEN md.tsb BETWEEN 5 AND 25 THEN 'NEUTRO'
        WHEN md.tsb BETWEEN 25 AND 50 THEN 'FADIGA'
        ELSE 'SOBRECARGA'
    END as status_tsb,
    CASE 
        WHEN md.ramp_rate > 1.5 THEN TRUE 
        ELSE FALSE 
    END as alerta_ramp_alto,
    md.created_at,
    md.updated_at
FROM tb_metricas_diarias md;

CREATE INDEX idx_v_metricas_agregadas_tenant_data 
    ON v_metricas_diarias_agregadas(tenant_id, data DESC);

CREATE INDEX idx_v_metricas_agregadas_status_tsb 
    ON v_metricas_diarias_agregadas(status_tsb);

COMMENT ON MATERIALIZED VIEW v_metricas_diarias_agregadas IS 'Agregação de métricas diárias com classificação de status TSB para dashboards';

-- ========================================
-- 3. CRIAR VIEW: resumo semanal por atleta
-- ========================================

CREATE OR REPLACE VIEW v_resumo_semanal_atleta AS
SELECT 
    ps.id as plano_semanal_id,
    ps.tenant_id,
    ps.atleta_id,
    ps.semana,
    ps.semana_inicio,
    ps.semana_fim,
    COUNT(DISTINCT tp.id) as total_treinos,
    COALESCE(SUM(tp.distancia_km), 0) as volume_km_planejado,
    COALESCE(ps.volume_realizado_km, 0) as volume_km_realizado,
    ps.status,
    COALESCE(AVG(tp.tss_planejado), 0) as tss_medio,
    ps.versao,
    ps.created_at
FROM tb_plano_semanal ps
LEFT JOIN tb_treino_planejado tp ON tp.plano_semanal_id = ps.id
GROUP BY ps.id, ps.tenant_id, ps.atleta_id, ps.semana, ps.semana_inicio, ps.semana_fim, ps.status, ps.versao, ps.created_at;

COMMENT ON VIEW v_resumo_semanal_atleta IS 'Resumo semanal com contagem de treinos e volumes para cada atleta';

-- ========================================
-- 4. CRIAR VIEW: histórico de provas completadas
-- ========================================

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

-- ========================================
-- 5. CRIAR VIEW: análise de tendência de forma
-- ========================================

CREATE OR REPLACE VIEW v_tendencia_forma_atlas AS
SELECT 
    a.id as atleta_id,
    a.tenant_id,
    a.nome,
    COUNT(DISTINCT md.data) as dias_rastreados,
    ROUND(AVG(md.tsb)::numeric, 2) as tsb_medio,
    ROUND(AVG(md.ctl)::numeric, 2) as ctl_medio,
    ROUND(AVG(md.atl)::numeric, 2) as atl_medio,
    MIN(md.data) as data_primeiro_registro,
    MAX(md.data) as data_ultimo_registro,
    ROUND((MAX(md.ctl) - MIN(md.ctl))::numeric, 2) as ctl_variacao,
    ROUND((MAX(md.tsb) - MIN(md.tsb))::numeric, 2) as tsb_variacao
FROM tb_atleta a
LEFT JOIN tb_metricas_diarias md ON md.atleta_id = a.id
WHERE a.ativo = 'ATIVO'
GROUP BY a.id, a.tenant_id, a.nome;

COMMENT ON VIEW v_tendencia_forma_atlas IS 'Tendência de forma por atleta com variações de CTL/TSB';

-- ========================================
-- 6. RECRIAR MATERIALIZED VIEW COM REFRESH
-- ========================================

REFRESH MATERIALIZED VIEW v_metricas_diarias_agregadas;

-- ========================================
-- Finalização
-- ========================================

DO $$
BEGIN
    RAISE NOTICE '✅ V9 - Alertas e VIEWs de análise criadas';
    RAISE NOTICE '   - Campos de alerta adicionados em tb_plano_metadados';
    RAISE NOTICE '   - 4 VIEWs para análises e dashboards';
    RAISE NOTICE '   - 1 MATERIALIZED VIEW para agregações de performance';
    RAISE NOTICE '   - Índices em VIEWs materializadas para queries rápidas';
END$$;