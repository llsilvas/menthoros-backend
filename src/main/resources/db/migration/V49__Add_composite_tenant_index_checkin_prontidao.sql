-- =====================================================================
-- V49: Adiciona índice composto liderado por tenant_id em tb_checkin_prontidao,
-- alinhando com o padrão obrigatório do projeto (ex.: V38__Add_composite_indexes_athlete_profile.sql)
-- de índice (tenant_id, ...) em toda tabela tenant-scoped.
-- =====================================================================

CREATE INDEX IF NOT EXISTS idx_checkin_prontidao_tenant_atleta_data
    ON tb_checkin_prontidao(tenant_id, atleta_id, data);

DO $$
BEGIN
    RAISE NOTICE '✅ V49 - indice composto (tenant_id, atleta_id, data) criado em tb_checkin_prontidao';
END$$;
