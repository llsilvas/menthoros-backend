-- =====================================================================
-- V38: Índices compostos para suporte ao perfil do atleta no coach
-- =====================================================================

CREATE INDEX IF NOT EXISTS idx_plano_semanal_tenant_atleta
    ON tb_plano_semanal(tenant_id, atleta_id);

CREATE INDEX IF NOT EXISTS idx_treino_planejado_tenant_atleta_data
    ON tb_treino_planejado(tenant_id, atleta_id, data_treino);

DO $$
BEGIN
    RAISE NOTICE 'V38 - Índices compostos de perfil de atleta criados com sucesso';
END$$;
