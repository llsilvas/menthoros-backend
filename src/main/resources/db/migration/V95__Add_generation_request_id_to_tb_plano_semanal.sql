-- =====================================================================
-- V95: Liga o plano persistido as chamadas LLM que o geraram (add-plan-generation-ledger, D4)
-- Escrita no mesmo save que ja persiste o plano; a ligacao chamada <-> plano e por join
-- em generation_request_id (nada e copiado entre as tabelas).
-- Aditiva e opcional — planos anteriores ficam com NULL.
-- =====================================================================

ALTER TABLE tb_plano_semanal
    ADD COLUMN IF NOT EXISTS generation_request_id UUID;

CREATE INDEX IF NOT EXISTS idx_plano_semanal_generation_request
    ON tb_plano_semanal(generation_request_id);

COMMENT ON COLUMN tb_plano_semanal.generation_request_id IS
    'Requisicao de geracao que produziu este plano — join com tb_llm_call.generation_request_id';

DO $$
BEGIN
    RAISE NOTICE '✅ V95 - generation_request_id adicionada a tb_plano_semanal';
END$$;
