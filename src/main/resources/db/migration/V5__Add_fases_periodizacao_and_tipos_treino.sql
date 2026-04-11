-- =====================================================================
-- V5: Enums e constantes de domínio (Periodização, Tipos de Treino)
-- =====================================================================
-- Consolida: V2 (tipos), V3 (enums), V7 (fases)
-- =====================================================================

-- ========================================
-- 1. TABELA DE FASES DE PERIODIZAÇÃO
-- ========================================

CREATE TABLE IF NOT EXISTS tb_fase_periodizacao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fase VARCHAR(30) NOT NULL UNIQUE,
    nome_pt VARCHAR(100) NOT NULL,
    descricao VARCHAR(500),
    duracao_semanas_min INTEGER DEFAULT 2,
    duracao_semanas_max INTEGER DEFAULT 12,
    volume_relativo_pct DECIMAL(5,2) DEFAULT 100,
    intensidade_relativa_pct DECIMAL(5,2) DEFAULT 100,
    enfoque VARCHAR(200),
    proximo_fase VARCHAR(30),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Inserir fases padrão de periodização
INSERT INTO tb_fase_periodizacao (fase, nome_pt, descricao, duracao_semanas_min, duracao_semanas_max, volume_relativo_pct, intensidade_relativa_pct, enfoque, proximo_fase)
SELECT 'PREPARATORIA_GERAL', 'Preparatória Geral', 'Base aeróbia, reabilitação pós-férias', 4, 8, 120, 60, 'Volume alto, intensidade baixa', 'PREPARATORIA_ESPECIFICA'
WHERE NOT EXISTS (SELECT 1 FROM tb_fase_periodizacao WHERE fase = 'PREPARATORIA_GERAL');

INSERT INTO tb_fase_periodizacao (fase, nome_pt, descricao, duracao_semanas_min, duracao_semanas_max, volume_relativo_pct, intensidade_relativa_pct, enfoque, proximo_fase)
SELECT 'PREPARATORIA_ESPECIFICA', 'Preparatória Específica', 'Treinos específicos de prova, limiar', 3, 6, 100, 80, 'Especificidade de prova', 'COMPETICAO'
WHERE NOT EXISTS (SELECT 1 FROM tb_fase_periodizacao WHERE fase = 'PREPARATORIA_ESPECIFICA');

INSERT INTO tb_fase_periodizacao (fase, nome_pt, descricao, duracao_semanas_min, duracao_semanas_max, volume_relativo_pct, intensidade_relativa_pct, enfoque, proximo_fase)
SELECT 'COMPETICAO', 'Competição', 'Peak taper, máxima performance', 2, 4, 60, 120, 'Qualidade, speed, taper', 'TRANSICAO'
WHERE NOT EXISTS (SELECT 1 FROM tb_fase_periodizacao WHERE fase = 'COMPETICAO');

INSERT INTO tb_fase_periodizacao (fase, nome_pt, descricao, duracao_semanas_min, duracao_semanas_max, volume_relativo_pct, intensidade_relativa_pct, enfoque, proximo_fase)
SELECT 'TRANSICAO', 'Transição', 'Recuperação, variedade, reavaliação', 2, 4, 50, 40, 'Recuperação mental e física', 'PREPARATORIA_GERAL'
WHERE NOT EXISTS (SELECT 1 FROM tb_fase_periodizacao WHERE fase = 'TRANSICAO');

CREATE INDEX IF NOT EXISTS idx_fase_periodizacao_fase ON tb_fase_periodizacao(fase);

COMMENT ON TABLE tb_fase_periodizacao IS 'Fases clássicas de periodização de Bompa adaptadas para corrida';

-- ========================================
-- 2. TABELA DE TIPOS DE TREINO
-- ========================================

CREATE TABLE IF NOT EXISTS tb_tipo_treino (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_treino VARCHAR(30) NOT NULL UNIQUE,
    nome_pt VARCHAR(100) NOT NULL,
    descricao VARCHAR(500),
    zona_fc_min DECIMAL(3,2),
    zona_fc_max DECIMAL(3,2),
    intensidade_padrao VARCHAR(20),
    duracao_tipica_min INTEGER,
    duracao_tipica_max INTEGER,
    nota_ia TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Inserir tipos de treino comuns
INSERT INTO tb_tipo_treino (tipo_treino, nome_pt, descricao, zona_fc_min, zona_fc_max, intensidade_padrao, duracao_tipica_min, duracao_tipica_max, nota_ia)
SELECT 'RECUPERACAO', 'Recuperação', 'Treino muito leve para recuperação ativa', 0.50, 0.65, 'Z1', 30, 60, 'Use após treinos intensos ou em dias de descanso ativo'
WHERE NOT EXISTS (SELECT 1 FROM tb_tipo_treino WHERE tipo_treino = 'RECUPERACAO');

INSERT INTO tb_tipo_treino (tipo_treino, nome_pt, descricao, zona_fc_min, zona_fc_max, intensidade_padrao, duracao_tipica_min, duracao_tipica_max, nota_ia)
SELECT 'BASE', 'Base Aeróbia', 'Treino aeróbio contínuo, conversível em moderado', 0.65, 0.75, 'Z2', 45, 120, 'Maioria dos treinos deve ser neste tipo'
WHERE NOT EXISTS (SELECT 1 FROM tb_tipo_treino WHERE tipo_treino = 'BASE');

INSERT INTO tb_tipo_treino (tipo_treino, nome_pt, descricao, zona_fc_min, zona_fc_max, intensidade_padrao, duracao_tipica_min, duracao_tipica_max, nota_ia)
SELECT 'LONGO', 'Treino Longo', 'Long run/long ride - volume progressivo', 0.65, 0.80, 'Z2-Z3', 60, 180, 'Aumentar gradualmente respeitando +10% por semana'
WHERE NOT EXISTS (SELECT 1 FROM tb_tipo_treino WHERE tipo_treino = 'LONGO');

INSERT INTO tb_tipo_treino (tipo_treino, nome_pt, descricao, zona_fc_min, zona_fc_max, intensidade_padrao, duracao_tipica_min, duracao_tipica_max, nota_ia)
SELECT 'LIMIAR', 'Treino Limiar', 'Treino no limiar anaeróbico (~FTP/LT rate)', 0.85, 0.95, 'Z4', 20, 60, 'Importante para adaptação de lactato'
WHERE NOT EXISTS (SELECT 1 FROM tb_tipo_treino WHERE tipo_treino = 'LIMIAR');

INSERT INTO tb_tipo_treino (tipo_treino, nome_pt, descricao, zona_fc_min, zona_fc_max, intensidade_padrao, duracao_tipica_min, duracao_tipica_max, nota_ia)
SELECT 'INTERVALO', 'Intervalo', 'Treino de intervalo com recuperação', 0.90, 1.00, 'Z5', 20, 45, 'Incluir recuperação entre repetições. Máx 1x/semana para iniciantes'
WHERE NOT EXISTS (SELECT 1 FROM tb_tipo_treino WHERE tipo_treino = 'INTERVALO');

INSERT INTO tb_tipo_treino (tipo_treino, nome_pt, descricao, zona_fc_min, zona_fc_max, intensidade_padrao, duracao_tipica_min, duracao_tipica_max, nota_ia)
SELECT 'SPRINTE', 'Sprinte/Max Potência', 'Esforço máximo para força e velocidade', 0.95, 1.05, 'Z5+', 5, 30, 'Cuidado com lesões. Necessário aquecimento completo'
WHERE NOT EXISTS (SELECT 1 FROM tb_tipo_treino WHERE tipo_treino = 'SPRINTE');

INSERT INTO tb_tipo_treino (tipo_treino, nome_pt, descricao, zona_fc_min, zona_fc_max, intensidade_padrao, duracao_tipica_min, duracao_tipica_max, nota_ia)
SELECT 'DESCANSO', 'Descanso Completo', 'Dia sem treino - recuperação passiva', 0.0, 0.40, 'Z0', 0, 0, 'Importante para adaptação e prevenção de overtraining'
WHERE NOT EXISTS (SELECT 1 FROM tb_tipo_treino WHERE tipo_treino = 'DESCANSO');

CREATE INDEX IF NOT EXISTS idx_tipo_treino_tipo ON tb_tipo_treino(tipo_treino);

COMMENT ON TABLE tb_tipo_treino IS 'Tipos de treino padrões com zonas de FC e duração típica';
COMMENT ON COLUMN tb_tipo_treino.zona_fc_min IS 'Frequência cardíaca mínima como percentual da FC máxima';
COMMENT ON COLUMN tb_tipo_treino.zona_fc_max IS 'Frequência cardíaca máxima como percentual da FC máxima';

-- ========================================
-- Finalização
-- ========================================

DO $$
BEGIN
    RAISE NOTICE '✅ V5 - Fases de periodização e tipos de treino criados';
    RAISE NOTICE '   - tb_fase_periodizacao (4 fases: preparatória, competição, transição)';
    RAISE NOTICE '   - tb_tipo_treino (7 tipos: recuperação, base, longo, limiar, intervalo, sprinte, descanso)';
    RAISE NOTICE '   - Referências para IA ao gerar planos';
END$$;