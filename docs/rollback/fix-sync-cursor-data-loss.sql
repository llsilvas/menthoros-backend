-- =====================================================================
-- Rollback de fix-sync-cursor-data-loss (V98) — ordem obrigatoria:
--
-- 1. PARAR todas as instancias do backend (o codigo novo ainda grava ultima_sincronizacao = now();
--    com ele rodando, o script e desfeito no ciclo seguinte).
-- 2. Rodar ESTE script, numa transacao, com acesso de DBA.
-- 3. Subir o binario ANTIGO (PR revertido). Os schedulers de pull rodam 1 min depois do boot e
--    passam a ler ultima_sincronizacao como cursor — por isso o passo 2 vem antes.
--
-- Por que existe: com esta change, os schedulers gravam ultima_sincronizacao = now() mesmo quando o
-- pull foi PARCIAL. O codigo antigo le esse campo como cursor e pularia a janela que ficou para tras.
-- pull_cursor guarda o progresso confirmado; o script o devolve ao campo antigo.
--
-- Efeito colateral aceito: o "ultimo sync" exibido ao coach recua ate o progresso confirmado.
-- Idempotente. Nao remove a coluna nem as tabelas da V98 (ficam sem uso; limpeza e outra migration).
-- Coberto por SyncPullCursorRollbackScriptTest.
-- =====================================================================

UPDATE tb_integracao_externa
   SET ultima_sincronizacao = pull_cursor
 WHERE pull_cursor IS NOT NULL
   AND (ultima_sincronizacao IS NULL OR pull_cursor < ultima_sincronizacao);
