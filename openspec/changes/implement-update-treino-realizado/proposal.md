# Proposal: implement-update-treino-realizado

## Status

Proposed

## Why

`TreinoService.updateTreino()` é um stub que retorna `null`. Qualquer chamada ao endpoint `PUT /api/v1/treinos/realizados/{id}` (quando existir) ou chamada interna para atualizar um treino falha silenciosamente.

Consequência prática: o loop de análise AI está quebrado para treinos sincronizados via Strava. O Strava preenche apenas `sufferScore` (score algorítmico), nunca `percepcaoEsforco` (RPE subjetivo 1-10). O `WorkoutAnalysisListener` usa `percepcaoEsforco == null` como gate de idempotência — logo, nenhum treino vindo do Strava dispara análise.

O atleta/coach precisam conseguir:
1. Adicionar RPE a um treino já existente (Strava ou manual)
2. Corrigir métricas fisiológicas pós-sincronização
3. Adicionar feedback subjetivo (sono, estresse, comentário)

Sem `updateTreino` implementado, nenhum desses cenários funciona.

## What Changes

- Implementa `TreinoServiceImpl.updateTreino()`: carrega entidade com isolamento de tenant, aplica campos mutáveis do DTO, persiste, e publica `TreinoRegistradoEvent` quando `percepcaoEsforco` não for nulo após a atualização
- Adiciona endpoint `PUT /api/v1/treinos/realizados/{id}` em `TreinoRealizadoController`
- Adiciona teste de integração `UpdateTreinoIntegrationTest` cobrindo: update básico, gate de RPE → evento, isolamento de tenant

## Capabilities

### Modified Capabilities

- `treino-realizado`: Adiciona operação de atualização parcial de treino realizado. Campos estruturais (atletaId, planoSemanalId, treinoPlanejadoId, dataTreino, tipoTreino) permanecem imutáveis após criação; apenas campos observacionais e de feedback são atualizáveis.

## Impact

- **Arquivos afetados:** `TreinoServiceImpl.java`, `TreinoRealizadoController.java`, `TreinoService.java` (se assinatura precisar ser ajustada)
- **Sem breaking changes:** Endpoint novo, nenhum comportamento existente alterado
- **Efeito colateral intencional:** Treinos Strava que recebam RPE via update passarão a disparar `TreinoRegistradoEvent` e terão análise AI gerada assincronamente pelo listener existente
- **Sem novas dependências Maven**
- **Sem novas migrações Flyway** (campos já existem na entidade e tabela)

## Melhoria Identificada (fora do escopo imediato)

O sync Strava atual usa o endpoint de lista `/athlete/activities` que não retorna `perceived_exertion` — campo disponível apenas no detalhe `/activities/{id}`. O código já tenta ler o campo (linha 375 de `StravaActivityServiceImpl`) mas recebe `null`. Buscar o detalhe por atividade dobraria as chamadas à API e pode consumir o budget de rate limit do Strava (100 req/15min, 1000/dia por token). Tarefa 6 no `tasks.md` documenta a análise necessária antes de implementar.
