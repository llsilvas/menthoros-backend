-- Cria tabela de etapas realizadas (detalhamento opcional de treinos realizados)
-- Treinos existentes sem etapas continuam válidos (relação é opcional do lado do pai)

CREATE TABLE IF NOT EXISTS tb_etapa_realizada (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    treino_realizado_id UUID NOT NULL REFERENCES tb_treino_realizado(id) ON DELETE CASCADE,
    etapa_planejada_id UUID REFERENCES tb_etapa_treino(id) ON DELETE SET NULL,
    ordem INTEGER NOT NULL,
    tipo_etapa VARCHAR(50),
    descricao VARCHAR(500),
    duracao INTERVAL,
    distancia_km DECIMAL(10,3),
    fc_media INTEGER,
    fc_max INTEGER,
    pace_media INTERVAL,
    velocidade_media DECIMAL(5,2),
    percepcao_esforco INTEGER CHECK (percepcao_esforco BETWEEN 1 AND 10),
    cadencia_media INTEGER,
    potencia_media INTEGER,
    observacao VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_etapa_realizada_treino ON tb_etapa_realizada(treino_realizado_id);
CREATE INDEX IF NOT EXISTS idx_etapa_realizada_ordem ON tb_etapa_realizada(treino_realizado_id, ordem);

COMMENT ON TABLE tb_etapa_realizada IS 'Etapas detalhadas de treinos realizados (aquecimento, tiros, recuperação, etc)';
COMMENT ON COLUMN tb_etapa_realizada.etapa_planejada_id IS 'Referência à etapa planejada correspondente (permite comparação planejado vs realizado)';
COMMENT ON COLUMN tb_etapa_realizada.tipo_etapa IS 'AQUECIMENTO, PRINCIPAL, INTERVALADO, RECUPERACAO, DESAQUECIMENTO';
COMMENT ON COLUMN tb_etapa_realizada.percepcao_esforco IS 'RPE 1-10 específico desta etapa';
