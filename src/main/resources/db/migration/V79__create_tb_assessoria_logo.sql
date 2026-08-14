-- =====================================================================
-- V79: tb_assessoria_logo — bytes da logo da assessoria
-- =====================================================================
--
-- POR QUE NO BANCO, E NAO EM OBJECT STORAGE:
-- o projeto não tem nenhuma infraestrutura de arquivos (sem S3/MinIO, sem
-- volume, sem multipart configurado). Decisão D1 da change: no MVP os bytes
-- ficam no Postgres. O contrato de leitura é uma rota do próprio produto
-- (GET /api/v1/assessorias/me/logo), então trocar por storage externo depois
-- não muda o cliente.
--
-- POR QUE UMA TABELA 1:1, E NAO UMA COLUNA EM tb_assessoria:
-- tb_assessoria é carregada em caminhos quentes. Um LOB na própria entidade
-- viaja em qualquer SELECT que o Hibernate gere, e @Basic(fetch = LAZY) sobre
-- LOB é frágil sem instrumentação de bytecode. Tabela separada torna o
-- carregamento acidental impossível por construção, em vez de depender de um
-- hint que pode ser ignorado.
--
-- SEM HISTORICO, POR DECISAO:
-- uma linha por assessoria (a PK é a própria FK). Substituir a logo é UPDATE,
-- não append — o teto de armazenamento é nº de assessorias × 2 MiB, e não
-- cresce com o número de trocas.
--
-- ETAG É DO CONTEUDO, não do instante: é o que permite responder 304 sem
-- reler os bytes e sobrevive a um restore que mude os timestamps.
--
-- Identificadores em INGLES (ADR-0007): tabela nova.

CREATE TABLE IF NOT EXISTS tb_assessoria_logo (
    assessoria_id UUID PRIMARY KEY REFERENCES tb_assessoria(id) ON DELETE CASCADE,
    content       BYTEA        NOT NULL,
    content_type  VARCHAR(40)  NOT NULL,
    size_bytes    INTEGER      NOT NULL,
    etag          VARCHAR(64)  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_assessoria_logo_size CHECK (size_bytes > 0 AND size_bytes <= 2097152)
);

DO $$
BEGIN
    RAISE NOTICE '✅ V79 - tb_assessoria_logo criada com sucesso';
END$$;
