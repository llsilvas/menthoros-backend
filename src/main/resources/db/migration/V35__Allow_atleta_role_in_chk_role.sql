-- =====================================================================
-- V35: Inclui ATLETA no CHECK chk_role de tb_usuario (a role ATLETA
--      foi adicionada ao enum UserRole)
-- =====================================================================

ALTER TABLE tb_usuario DROP CONSTRAINT IF EXISTS chk_role;

ALTER TABLE tb_usuario
    ADD CONSTRAINT chk_role
    CHECK (role IN ('ADMIN', 'TECNICO', 'ATLETA', 'VISUALIZADOR'));

DO $$
BEGIN
    RAISE NOTICE '✅ V35 - chk_role atualizado para incluir ATLETA';
END$$;
