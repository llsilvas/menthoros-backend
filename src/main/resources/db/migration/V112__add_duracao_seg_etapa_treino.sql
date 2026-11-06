-- =====================================================================
-- V112: Duracao da etapa em segundos (add-etapa-duracao-segundos, D1)
--
-- duracao_min continua existindo e e derivado (round, sem piso) de duracao_seg quando o segundo
-- chega; os leitores em minutos (TSS, normalizador, prompts) nao mudam. NULL = etapa gravada em
-- minutos, como ate aqui (IA e clientes antigos). Sem backfill: nao ha de onde tirar o segundo.
--
-- Rollback: apagar a coluna duracao_seg de tb_etapa_treino; nenhum dado a preservar, porque
-- duracao_min continua gravado.
-- =====================================================================

ALTER TABLE tb_etapa_treino
    ADD COLUMN IF NOT EXISTS duracao_seg INTEGER NULL;
