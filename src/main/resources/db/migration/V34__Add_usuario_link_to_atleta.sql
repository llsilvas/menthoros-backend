-- =====================================================================
-- V34: Adiciona usuario_id em tb_atleta — vínculo Usuario<->Atleta.
--      Nullable: o Atleta é cadastrado pelo coach antes de existir a
--      conta; o vínculo é efetivado no aceite do convite.
-- =====================================================================

ALTER TABLE tb_atleta
    ADD COLUMN IF NOT EXISTS usuario_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'tb_atleta' AND constraint_name = 'fk_atleta_usuario'
    ) THEN
        ALTER TABLE tb_atleta
            ADD CONSTRAINT fk_atleta_usuario
            FOREIGN KEY (usuario_id) REFERENCES tb_usuario(id) ON DELETE SET NULL;
    END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_atleta_usuario ON tb_atleta(usuario_id);

DO $$
BEGIN
    RAISE NOTICE '✅ V34 - usuario_id (vínculo Usuario<->Atleta) adicionado em tb_atleta';
END$$;
