## Context

Este change assume OAuth já funcional na branch base para obtenção de token válido.

## Goals

- Importar atividades Strava desde `ultima_sincronizacao`
- Deduplicar por `externalId` por atleta
- Importar laps e preencher `EtapaRealizada`
- Respeitar tenant e rate limit

## Non-Goals

- Webhooks em tempo real
- Registro e handshake de webhook

## Decisions

### D1: Deduplicação por atleta + externalId

Usar busca por `externalId` e `atletaId` para evitar duplicatas no mesmo tenant.

### D2: Conversões explícitas de unidade

- metros -> km
- m/s -> km/h
- segundos -> `Duration`
- cadência Strava (half-cadence) -> cadência total (`x2`)

### D3: Sync incremental

Sincronizar a partir de `ultima_sincronizacao` com fallback para janela padrão inicial.

### D4: Rate limit defensivo

Interromper loop quando `X-RateLimit-Remaining` chegar a zero e registrar ponto de retomada.
