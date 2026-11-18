-- =====================================================================
-- V118: Índices da geração da temporada (add-season-plan-generate-tracer)
--
-- idx_plano_treino_tenant_prova: as consultas de temporada filtram por (tenant_id, prova_id)
--   (archiveOpenSeasons, existsReferencingRace).
-- uk_plano_treino_proposed_season: no máximo uma temporada PROPOSED por atleta, como a V116
--   faz para ACTIVE. Fecha o duplo clique concorrente no "Gerar temporada" (vira 409).
--
-- Rollback (V nova): remover os dois índices; nenhum dado é afetado.
-- =====================================================================

CREATE INDEX IF NOT EXISTS idx_plano_treino_tenant_prova
    ON tb_plano_treino (tenant_id, prova_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_plano_treino_proposed_season
    ON tb_plano_treino (atleta_id)
    WHERE season_status = 'PROPOSED';

DO $$
BEGIN
    RAISE NOTICE 'V118 - índices da geração da temporada aplicados';
END$$;
