-- =====================================================================
-- V78: concorrência otimista em tb_assessoria (assessoria-settings-ui)
-- =====================================================================
--
-- POR QUE AGORA:
-- até aqui ninguém editava a assessoria pela aplicação — só o POST admin a
-- criava. O PATCH desta change abre o primeiro caminho de escrita, e com ele
-- a possibilidade de lost update entre duas abas do mesmo coach.
--
-- DEFAULT 0 NAS LINHAS EXISTENTES:
-- não há como derivar uma versão histórica, e não é preciso: o contador só
-- precisa ser monotônico a partir de agora. Quem ler antes do primeiro PATCH
-- recebe 0 e o primeiro conflito real já é detectado.
--
-- Migração não-destrutiva: apenas ADD COLUMN, sem DROP e sem reescrita de dado.

ALTER TABLE tb_assessoria
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
    RAISE NOTICE '✅ V78 - coluna version adicionada em tb_assessoria';
END$$;
