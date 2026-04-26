## Context

Este change assume sync manual já disponível na base para reutilizar mapeamentos e atualização de atividades.

## Goals

- Validar subscription handshake do Strava
- Responder webhook rapidamente e processar de forma assíncrona
- Processar eventos `create`, `update`, `delete` com validação por `owner_id`

## Non-Goals

- Implementar fila externa de mensageria
- Garantias de exactly-once

## Decisions

### D1: POST responde imediatamente

`POST /api/strava/webhook` retorna HTTP 200 antes da lógica de negócio para evitar retries agressivos do Strava.

### D2: Async com executor dedicado

Usar `@Async` com `ThreadPoolTaskExecutor` próprio para isolar webhook do restante da aplicação.

### D3: Validação por owner

Só processar evento quando `owner_id` corresponder a `IntegracaoExterna.externalAthleteId` ativo.

### D4: Delete lógico

Evento `delete` não remove registro: marca `statusSincronizacao = CANCELADO`.
