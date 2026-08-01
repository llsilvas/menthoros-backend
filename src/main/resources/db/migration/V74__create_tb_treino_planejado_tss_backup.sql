-- =====================================================================
-- V74: Snapshot do tss_planejado antes da correcao do BUG-CONF-001
--      (fix-tss-planejado-divergente)
--
-- O TSS de treino planejado era calculado com min x RPE^2 / 90 enquanto o
-- realizado usava h x IF^2 x 100 — duas formulas para a mesma grandeza,
-- divergindo de 2,4x a 6x conforme o RPE. A correcao unifica as duas, e o
-- recalculo das linhas existentes muda numeros que coach e atleta JA VIRAM na
-- tela (TreinoEditDialog, buildWeeklyPlan, DetalheTreinoDialog).
--
-- Esta tabela existe para que a operacao seja auditavel e reversivel: guarda o
-- valor anterior de cada linha tocada. Sem ela, reverter exigiria recomputar a
-- formula antiga — reconstrucao, nao reversao.
--
-- Por que nao separar as escalas por status_treino, como se cogitou antes:
-- status e mutavel (PENDENTE vira REALIZADO/PERDIDO em producao), entao o
-- criterio se dissolveria sozinho e os status executados passariam a misturar
-- as duas escalas sem marcacao. Observado na pratica: a contagem de PENDENTE
-- caiu de 38 para 36 em poucas horas durante a propria implementacao.
--
-- Tabela de auditoria pontual, nao de dominio: nao tem entidade JPA de
-- escrita alem do recalculador, e pode ser removida quando a janela de
-- reversao fechar.
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_treino_planejado_tss_backup (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    treino_planejado_id  UUID         NOT NULL REFERENCES tb_treino_planejado(id) ON DELETE CASCADE,
    tss_planejado_antes  INTEGER      NOT NULL,
    migrado_em           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_treino_planejado_tss_backup_treino UNIQUE (treino_planejado_id)
);

CREATE INDEX IF NOT EXISTS idx_treino_planejado_tss_backup_treino
    ON tb_treino_planejado_tss_backup(treino_planejado_id);

DO $$
BEGIN
    RAISE NOTICE '✅ V74 - tb_treino_planejado_tss_backup criada com sucesso';
END$$;
