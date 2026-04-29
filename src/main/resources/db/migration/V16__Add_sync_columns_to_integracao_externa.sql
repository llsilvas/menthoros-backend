-- Add sync tracking columns to tb_integracao_externa for manual Strava sync
ALTER TABLE tb_integracao_externa ADD COLUMN IF NOT EXISTS sync_activity_count INT DEFAULT 0;
ALTER TABLE tb_integracao_externa ADD COLUMN IF NOT EXISTS last_sync_error VARCHAR(500);

-- Add comment for documentation
COMMENT ON COLUMN tb_integracao_externa.sync_activity_count IS 'Quantidade de atividades importadas na última sincronização';
COMMENT ON COLUMN tb_integracao_externa.last_sync_error IS 'Mensagem de erro da última sincronização, se houver';
