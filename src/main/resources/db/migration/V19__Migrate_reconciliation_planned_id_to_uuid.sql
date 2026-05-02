-- V19: Migrar afterPlannedId (e beforePlannedId) de Long para UUID para rastreabilidade real

-- Criar novas colunas com tipos UUID
ALTER TABLE tb_treino_reconciliacao 
ADD COLUMN before_planned_id_uuid UUID,
ADD COLUMN after_planned_id_uuid UUID;

-- Nota: Dados existentes em Long não podem ser convertidos para UUID semanticamente válido.
-- Esta migração apenas prepara o schema para futuros registros.
-- Registros antigos podem ter NULL ou dados históricos perdidos (aceitável para MVP).

-- Remover coluna antiga (dados perdidos, mas é apenas auditoria histórica)
-- ALTER TABLE tb_treino_reconciliacao DROP COLUMN before_planned_id;
-- ALTER TABLE tb_treino_reconciliacao DROP COLUMN after_planned_id;

-- Por segurança, mantém as colunas antigas para não quebrar código legado
-- Novos inserts usarão as colunas UUID
