-- Migration para adicionar coluna status_prova na tabela tb_prova
-- Data: 2025-10-03
-- Autor: Sistema

-- Adiciona a coluna status_prova com valor padrão PLANEJADA
ALTER TABLE tb_prova
ADD COLUMN IF NOT EXISTS status_prova VARCHAR(50) NOT NULL DEFAULT 'PLANEJADA';

-- Cria índice para melhorar performance de queries por status
CREATE INDEX IF NOT EXISTS idx_prova_status ON tb_prova(status_prova);

-- Atualiza provas já realizadas
UPDATE tb_prova
SET status_prova = 'REALIZADA'
WHERE foi_realizada = true;

-- Comentários na tabela
COMMENT ON COLUMN tb_prova.status_prova IS 'Status da prova: PLANEJADA, CONFIRMADA, REALIZADA, CANCELADA';