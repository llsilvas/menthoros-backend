-- Migration para adicionar novos campos da Fase 2 na tabela tb_plano_metadados
-- Data: 2025-10-06
-- Descrição: Adiciona campos para status geral, recomendação de treino e fase de periodização
-- Relacionado: Melhorias na geração de alertas e integração com LLM

-- Adiciona coluna status_geral
ALTER TABLE tb_plano_metadados
ADD COLUMN IF NOT EXISTS status_geral VARCHAR(50);

-- Adiciona coluna recomendacao_treino
ALTER TABLE tb_plano_metadados
ADD COLUMN IF NOT EXISTS recomendacao_treino TEXT;

-- Adiciona coluna fase_periodizacao
ALTER TABLE tb_plano_metadados
ADD COLUMN IF NOT EXISTS fase_periodizacao VARCHAR(30);

-- Cria índice para melhorar performance de queries por status geral
CREATE INDEX IF NOT EXISTS idx_metadados_status_geral ON tb_plano_metadados(status_geral);

-- Cria índice para melhorar performance de queries por fase de periodização
CREATE INDEX IF NOT EXISTS idx_metadados_fase_periodizacao ON tb_plano_metadados(fase_periodizacao);

-- Comentários nas colunas
COMMENT ON COLUMN tb_plano_metadados.status_geral IS 'Status geral calculado automaticamente: FADIGA CRÍTICA, FORMA IDEAL, PROGRESSÃO RÁPIDA, etc.';
COMMENT ON COLUMN tb_plano_metadados.recomendacao_treino IS 'Recomendação gerada automaticamente baseada nos alertas ativos';
COMMENT ON COLUMN tb_plano_metadados.fase_periodizacao IS 'Fase de periodização: BASE, BUILD, ESPECIFICO, TAPER, SEMANA_PROVA, POS_PROVA, DESENVOLVIMENTO_GERAL';

-- Atualiza registros existentes com valores padrão
UPDATE tb_plano_metadados
SET status_geral = 'COLETANDO DADOS'
WHERE status_geral IS NULL;

UPDATE tb_plano_metadados
SET fase_periodizacao = 'DESENVOLVIMENTO_GERAL'
WHERE fase_periodizacao IS NULL;