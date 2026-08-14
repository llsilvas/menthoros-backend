-- =====================================================================
-- V77: Marca o dono da assessoria em tb_usuario (assessoria-settings-ui)
-- =====================================================================
--
-- POR QUE UMA COLUNA, E NÃO UM VALOR NOVO EM `role`:
-- `role` guarda um único valor e é lida por countByTenantIdAndRoleAndAtivoTrue
-- (a contagem de técnicos do plano, com max_tecnicos = 1 no BASIC), por
-- isTecnico() e por podeEscrever(). Resolver o dono como PROPRIETARIO o tiraria
-- das três. Ele continua TECNICO; a propriedade vive aqui.
--
-- FONTE DA VERDADE É O KEYCLOAK:
-- esta coluna é espelho da role PROPRIETARIO, reescrita a cada sincronização de
-- JWT. Um UPDATE manual aqui sobrevive só até o próximo login do usuário.
--
-- DEFAULT false É DELIBERADO:
-- ninguém vira dono por migração de schema. A atribuição dos coaches existentes
-- é feita no backfill (task 0.4), que decide caso a caso e registra as exceções.

ALTER TABLE tb_usuario
    ADD COLUMN IF NOT EXISTS owner BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_usuario_tenant_owner
    ON tb_usuario(tenant_id, owner);

DO $$
BEGIN
    RAISE NOTICE '✅ V77 - coluna owner adicionada em tb_usuario';
END$$;
