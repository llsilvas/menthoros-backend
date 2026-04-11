-- =====================================================================
-- V3: Adiciona tabelas de constants e otimizações de CTL/ATL
-- =====================================================================
-- Consolida: V5 (constantes de tempo), V20 (cálculos de métricas)
-- =====================================================================

-- ========================================
-- 1. TABELA DE CONSTANTES POR NÍVEL (V5)
-- ========================================

CREATE TABLE IF NOT EXISTS tb_nivel_experiencia_constants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nivel_experiencia VARCHAR(20) NOT NULL UNIQUE,
    ctl_time_constant INTEGER NOT NULL DEFAULT 42,
    atl_time_constant INTEGER NOT NULL DEFAULT 7,
    ramp_rate_maxima DOUBLE PRECISION NOT NULL DEFAULT 1.5,
    volume_semanal_recomendado INTEGER NOT NULL DEFAULT 50,
    descricao VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ctl_time CHECK (ctl_time_constant > 0 AND ctl_time_constant <= 100),
    CONSTRAINT chk_atl_time CHECK (atl_time_constant > 0 AND atl_time_constant <= 20),
    CONSTRAINT chk_ramp_rate CHECK (ramp_rate_maxima > 0 AND ramp_rate_maxima <= 2.5)
);

-- Inserir valores padrão (V5)
INSERT INTO tb_nivel_experiencia_constants (nivel_experiencia, ctl_time_constant, atl_time_constant, ramp_rate_maxima, volume_semanal_recomendado, descricao)
SELECT 'INICIANTE', 42, 7, 1.2, 40, 'Atleta em primeiros 12 meses de treinamento'
WHERE NOT EXISTS (SELECT 1 FROM tb_nivel_experiencia_constants WHERE nivel_experiencia = 'INICIANTE');

INSERT INTO tb_nivel_experiencia_constants (nivel_experiencia, ctl_time_constant, atl_time_constant, ramp_rate_maxima, volume_semanal_recomendado, descricao)
SELECT 'INTERMEDIARIO', 42, 7, 1.5, 60, 'Atleta com 1-3 anos de treinamento consistente'
WHERE NOT EXISTS (SELECT 1 FROM tb_nivel_experiencia_constants WHERE nivel_experiencia = 'INTERMEDIARIO');

INSERT INTO tb_nivel_experiencia_constants (nivel_experiencia, ctl_time_constant, atl_time_constant, ramp_rate_maxima, volume_semanal_recomendado, descricao)
SELECT 'AVANCADO', 42, 7, 1.8, 80, 'Atleta com 3+ anos e resultados competitivos comprovados'
WHERE NOT EXISTS (SELECT 1 FROM tb_nivel_experiencia_constants WHERE nivel_experiencia = 'AVANCADO');

INSERT INTO tb_nivel_experiencia_constants (nivel_experiencia, ctl_time_constant, atl_time_constant, ramp_rate_maxima, volume_semanal_recomendado, descricao)
SELECT 'ELITE', 42, 7, 2.0, 100, 'Atleta profissional ou com histórico de resultados élite'
WHERE NOT EXISTS (SELECT 1 FROM tb_nivel_experiencia_constants WHERE nivel_experiencia = 'ELITE');

CREATE INDEX IF NOT EXISTS idx_nivel_constants_nivel ON tb_nivel_experiencia_constants(nivel_experiencia);

COMMENT ON TABLE tb_nivel_experiencia_constants IS 'Constantes de treinamento por nível de experiência (CTL, ATL, ramp rate)';
COMMENT ON COLUMN tb_nivel_experiencia_constants.ctl_time_constant IS 'Número de dias para calcular CTL (Chronic Training Load). Padrão: 42 dias.';
COMMENT ON COLUMN tb_nivel_experiencia_constants.atl_time_constant IS 'Número de dias para calcular ATL (Acute Training Load). Padrão: 7 dias.';
COMMENT ON COLUMN tb_nivel_experiencia_constants.ramp_rate_maxima IS 'Taxa máxima segura de aumento semanal de TSS (%). Valores: 1.0-2.0.';

-- ========================================
-- 2. ADICIONAR CAMPO DE CONSTANTES PERSONALIZADAS EM tb_atleta
-- ========================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tb_atleta' AND column_name = 'ctl_time_constant') THEN
        ALTER TABLE tb_atleta
            ADD COLUMN ctl_time_constant INTEGER DEFAULT NULL,
            ADD COLUMN atl_time_constant INTEGER DEFAULT NULL;
        
        COMMENT ON COLUMN tb_atleta.ctl_time_constant IS 'Constante CTL personalizada para este atleta (dias). NULL = usar valor padrão do nível_experiencia';
        COMMENT ON COLUMN tb_atleta.atl_time_constant IS 'Constante ATL personalizada para este atleta (dias). NULL = usar valor padrão do nível_experiencia';
        
        CREATE INDEX IF NOT EXISTS idx_atleta_custom_constants 
            ON tb_atleta(ctl_time_constant, atl_time_constant) 
            WHERE ctl_time_constant IS NOT NULL OR atl_time_constant IS NOT NULL;
    END IF;
END $$;

-- ========================================
-- 3. TABELA DE FAIXAS TSB (para alertas e recomendações - V20)
-- ========================================

CREATE TABLE IF NOT EXISTS tb_faixa_tsb (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tb_assessoria(id) ON DELETE CASCADE,
    faixa_min DOUBLE PRECISION NOT NULL,
    faixa_max DOUBLE PRECISION NOT NULL,
    status VARCHAR(30) NOT NULL,
    cor_hex VARCHAR(7) DEFAULT '#808080',
    recomendacao VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_faixa_tsb_tenant_range UNIQUE (tenant_id, faixa_min, faixa_max),
    CONSTRAINT chk_faixa_min_max CHECK (faixa_min < faixa_max)
);

-- Inserir faixas padrão (V20)
DO $$
DECLARE
    default_tenant_id UUID;
BEGIN
    SELECT id INTO default_tenant_id FROM tb_assessoria WHERE dominio = 'default' LIMIT 1;
    
    IF default_tenant_id IS NOT NULL THEN
        INSERT INTO tb_faixa_tsb (tenant_id, faixa_min, faixa_max, status, cor_hex, recomendacao)
        SELECT default_tenant_id, -100, -35, 'SUPERCOMPENSACAO', '#00AA00', 'Atleta super compensado. Pronto para prova ou teste máximo.'
        WHERE NOT EXISTS (SELECT 1 FROM tb_faixa_tsb WHERE tenant_id = default_tenant_id AND status = 'SUPERCOMPENSACAO');
        
        INSERT INTO tb_faixa_tsb (tenant_id, faixa_min, faixa_max, status, cor_hex, recomendacao)
        SELECT default_tenant_id, -35, -10, 'EXCELENTE', '#00FF00', 'Condição excelente. Ótimo momento para competir.'
        WHERE NOT EXISTS (SELECT 1 FROM tb_faixa_tsb WHERE tenant_id = default_tenant_id AND status = 'EXCELENTE');
        
        INSERT INTO tb_faixa_tsb (tenant_id, faixa_min, faixa_max, status, cor_hex, recomendacao)
        SELECT default_tenant_id, -10, 5, 'BOM', '#FFFF00', 'Condição boa. Treinos moderados possíveis.'
        WHERE NOT EXISTS (SELECT 1 FROM tb_faixa_tsb WHERE tenant_id = default_tenant_id AND status = 'BOM');
        
        INSERT INTO tb_faixa_tsb (tenant_id, faixa_min, faixa_max, status, cor_hex, recomendacao)
        SELECT default_tenant_id, 5, 25, 'NEUTRO', '#FFAA00', 'Neuralizado. Treinos normais planejados.'
        WHERE NOT EXISTS (SELECT 1 FROM tb_faixa_tsb WHERE tenant_id = default_tenant_id AND status = 'NEUTRO');
        
        INSERT INTO tb_faixa_tsb (tenant_id, faixa_min, faixa_max, status, cor_hex, recomendacao)
        SELECT default_tenant_id, 25, 50, 'FADIGA', '#FF5500', 'Começando a acumular fadiga. Reduzir volume.'
        WHERE NOT EXISTS (SELECT 1 FROM tb_faixa_tsb WHERE tenant_id = default_tenant_id AND status = 'FADIGA');
        
        INSERT INTO tb_faixa_tsb (tenant_id, faixa_min, faixa_max, status, cor_hex, recomendacao)
        SELECT default_tenant_id, 50, 200, 'SOBRECARGA', '#FF0000', 'ALERTA: Sobrecargado. Necessário descanso urgente!'
        WHERE NOT EXISTS (SELECT 1 FROM tb_faixa_tsb WHERE tenant_id = default_tenant_id AND status = 'SOBRECARGA');
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_faixa_tsb_tenant ON tb_faixa_tsb(tenant_id);

COMMENT ON TABLE tb_faixa_tsb IS 'Faixas de TSB customizáveis por tenant para alertas e recomendações';

-- ========================================
-- Finalização
-- ========================================

DO $$
BEGIN
    RAISE NOTICE '✅ V3 - Constants e configurações de métricas criadas';
    RAISE NOTICE '   - tb_nivel_experiencia_constants (CTL/ATL padrões)';
    RAISE NOTICE '   - Campos ctl/atl_time_constant adicionados em tb_atleta';
    RAISE NOTICE '   - tb_faixa_tsb (faixas customizáveis por tenant)';
END$$;