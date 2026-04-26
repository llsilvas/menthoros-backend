## Why

Separar a sincronização de atividades em um change próprio permite evoluir mapeamentos e deduplicação sem acoplamento ao fluxo OAuth ou webhook.

## What Changes

- Serviço de sincronização (`StravaActivityService`) para buscar atividades e laps
- Mapeamento de `StravaActivityDto` para `TreinoRealizado`
- Mapeamento de `StravaSplitDto` para `EtapaRealizada`
- Endpoint manual de sync (`POST /api/strava/sync/{atletaId}`)

## Impact

- API: `POST /api/strava/sync/{atletaId}`
- Entidades: `TreinoRealizado`, `EtapaRealizada`
- Repositórios: deduplicação por `externalId`
