-- =====================================================================
-- V4: Índices de performance e otimizações
-- =====================================================================
-- Consolida: V3 (índices iniciais), V6 (índices fisiologia), V11 (índices tipos)
-- =====================================================================

-- ========================================
-- 1. ÍNDICES COMPOSTOS PARA FILTROS COMUNS
-- ========================================

-- Atleta: tenant + status (filtro mais comum)
CREATE INDEX IF NOT EXISTS idx_atleta_tenant_ativo_nivel 
    ON tb_atleta(tenant_id, ativo, nivel_experiencia);

-- Prova: tenant + data (ordenar por data)
CREATE INDEX IF NOT EXISTS idx_prova_tenant_data_status 
    ON tb_prova(tenant_id, data_prova DESC, status_prova);

-- Plano Semanal: tenant + data (queries de semana)
CREATE INDEX IF NOT EXISTS idx_plano_semanal_tenant_semana 
    ON tb_plano_semanal(tenant_id, semana_inicio, semana_fim);

-- Treino Planejado: tenant + data + status
CREATE INDEX IF NOT EXISTS idx_treino_planejado_tenant_data_status 
    ON tb_treino_planejado(tenant_id, data_treino, status_treino);

-- Treino Realizado: tenant + data (queries de histórico)
CREATE INDEX IF NOT EXISTS idx_treino_realizado_tenant_data_tipo 
    ON tb_treino_realizado(tenant_id, data_treino DESC, tipo_treino);

-- Métricas Diárias: tenant + data (queries de ranges)
CREATE INDEX IF NOT EXISTS idx_metricas_diarias_tenant_data_range 
    ON tb_metricas_diarias(tenant_id, data DESC);

-- ========================================
-- 2. ÍNDICES PARA BUSCAS FULL-TEXT (elasticsearch future)
-- ========================================

-- Atleta: nome para busca
CREATE INDEX IF NOT EXISTS idx_atleta_nome_gin 
    ON tb_atleta USING GIN (to_tsvector('portuguese', nome));

-- Plano Treino: descrição para busca
CREATE INDEX IF NOT EXISTS idx_plano_treino_descricao_gin 
    ON tb_plano_treino USING GIN (to_tsvector('portuguese', descricao));

-- ========================================
-- 3. ÍNDICES PARA CAMPOS FISIOLÓGICOS (adicionados em V6)
-- ========================================

-- Criados condicionalmente pois as colunas são adicionadas em V6
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_atleta' AND column_name = 'vo2max_estimado') THEN
        CREATE INDEX IF NOT EXISTS idx_atleta_vo2max ON tb_atleta(vo2max_estimado) WHERE vo2max_estimado IS NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_atleta' AND column_name = 'fc_maxima') THEN
        CREATE INDEX IF NOT EXISTS idx_atleta_fc_maxima ON tb_atleta(fc_maxima) WHERE fc_maxima IS NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_atleta' AND column_name = 'pace_limiar') THEN
        CREATE INDEX IF NOT EXISTS idx_atleta_pace_limiar ON tb_atleta(pace_limiar) WHERE pace_limiar IS NOT NULL;
    END IF;
END$$;

-- ========================================
-- 4. ÍNDICES PARA SINCRONIZAÇÃO (V22)
-- ========================================

-- Treino Planejado: buscar por sincronização pendente
CREATE INDEX IF NOT EXISTS idx_treino_planejado_sync_pending 
    ON tb_treino_planejado(tenant_id, status_sincronizacao) 
    WHERE status_sincronizacao IN ('PENDENTE', 'ERRO');

-- Treino Realizado: buscar por sincronização pendente
CREATE INDEX IF NOT EXISTS idx_treino_realizado_sync_pending 
    ON tb_treino_realizado(tenant_id, status_sincronizacao) 
    WHERE status_sincronizacao IN ('PENDENTE', 'ERRO');

-- Treino Realizado: buscar por external_id (ex: Strava)
CREATE INDEX IF NOT EXISTS idx_treino_realizado_external_id 
    ON tb_treino_realizado(external_id) WHERE external_id IS NOT NULL;

-- ========================================
-- 5. ÍNDICES PARA ENUMS E STATUS (V11)
-- ========================================

-- Plano Treino: buscar por status
CREATE INDEX IF NOT EXISTS idx_plano_treino_status 
    ON tb_plano_treino(status);

-- Plano Semanal: buscar por status
CREATE INDEX IF NOT EXISTS idx_plano_semanal_status 
    ON tb_plano_semanal(status);

-- Etapa Treino: buscar por tipo
CREATE INDEX IF NOT EXISTS idx_etapa_treino_tipo
    ON tb_etapa_treino(tipo_etapa);

-- Etapa Realizada: buscar por tipo
CREATE INDEX IF NOT EXISTS idx_etapa_realizada_tipo_etapa 
    ON tb_etapa_realizada(tipo_etapa);

-- ========================================
-- 6. ÍNDICES PARA QUERIES DE RANGE (V10, V19)
-- ========================================

-- Treino Realizado: intensidade real (para queries de zona)
CREATE INDEX IF NOT EXISTS idx_treino_realizado_intensidade 
    ON tb_treino_realizado(intensidade_real) WHERE intensidade_real IS NOT NULL;

-- Treino Realizado: potência (para análise de power)
CREATE INDEX IF NOT EXISTS idx_treino_realizado_potencia 
    ON tb_treino_realizado(potencia_media) WHERE potencia_media IS NOT NULL;

-- Treino Realizado: FC média (para análise de frequência cardíaca)
CREATE INDEX IF NOT EXISTS idx_treino_realizado_fc 
    ON tb_treino_realizado(fc_media) WHERE fc_media IS NOT NULL;

-- Métricas Diárias: TSB para análise de forma
CREATE INDEX IF NOT EXISTS idx_metricas_diarias_tsb
    ON tb_metricas_diarias(tsb) WHERE tsb IS NOT NULL;

-- Métricas Diárias: CTL para análise de carga crônica
CREATE INDEX IF NOT EXISTS idx_metricas_diarias_ctl 
    ON tb_metricas_diarias(ctl) WHERE ctl IS NOT NULL;

-- ========================================
-- 7. ÍNDICES PARA RELACIONAMENTOS
-- ========================================

-- Etapa Treino: buscar etapas de um treino planejado em ordem
CREATE INDEX IF NOT EXISTS idx_etapa_treino_planejado_ordem 
    ON tb_etapa_treino(treino_planejado_id, ordem);

-- Etapa Realizada: buscar etapas de um treino realizado em ordem
CREATE INDEX IF NOT EXISTS idx_etapa_realizada_realizado_ordem 
    ON tb_etapa_realizada(treino_realizado_id, ordem);

-- Plano Semanal: buscar semanas de um plano de treino
CREATE INDEX IF NOT EXISTS idx_plano_semanal_plano_treino 
    ON tb_plano_semanal(plano_treino_id);

-- Treino Planejado: buscar treinos de uma semana
CREATE INDEX IF NOT EXISTS idx_treino_planejado_semana 
    ON tb_treino_planejado(plano_semanal_id);

-- ========================================
-- Finalização
-- ========================================

DO $$
BEGIN
    RAISE NOTICE '✅ V4 - Índices e otimizações de performance criados';
    RAISE NOTICE '   - 30+ índices para queries comuns';
    RAISE NOTICE '   - Índices compostos (tenant + campo)';
    RAISE NOTICE '   - Índices PARTIAL para campos nullable';
    RAISE NOTICE '   - Full-text search preparado (tsvector)';
    RAISE NOTICE '   - Execute VACUUM ANALYZE manualmente após a migration';
END$$;