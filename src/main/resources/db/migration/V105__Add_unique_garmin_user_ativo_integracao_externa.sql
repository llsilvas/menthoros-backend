-- =====================================================================
-- V105: Um userId Garmin ativo por vez (garmin-connection, D4)
--
-- O guard do callback ("userId ja vinculado a outro atleta") e check-then-act: dois callbacks
-- simultaneos passam juntos e gravam duas linhas ativas com o mesmo external_athlete_id, e o webhook
-- (que roteia so por userId) deixa de ter resposta unica. O indice parcial fecha a corrida no banco.
-- Linhas inativas ficam de fora: reconexao e troca de atleta continuam livres.
--
-- Rollback: DROP INDEX IF EXISTS ux_integracao_garmin_user_ativo;
-- =====================================================================

CREATE UNIQUE INDEX IF NOT EXISTS ux_integracao_garmin_user_ativo
    ON tb_integracao_externa(external_athlete_id)
    WHERE plataforma = 'GARMIN' AND ativo = TRUE AND external_athlete_id IS NOT NULL;

DO $$
BEGIN
    RAISE NOTICE '✅ V105 - indice unico parcial do userId Garmin ativo criado';
END$$;
