-- =====================================================================
-- Rollback de fix-sync-cursor-data-loss (V98) — passo 2 de 2
--
-- Passo 1: reverter o PR (o binario volta a usar ultima_sincronizacao como cursor do pull).
-- Passo 2: rodar ESTE script ANTES do primeiro ciclo de pull do codigo antigo (os schedulers de
-- pull rodam 1 min depois do boot e a cada 2 h — rode com o app parado, ou logo antes do deploy).
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
