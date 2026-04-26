## Why

Separar webhooks em change dedicado permite tratar assíncrono, idempotência e validação de owner com menor risco operacional.

## What Changes

- `StravaWebhookEventDto` para payloads de evento
- Serviço `StravaWebhookService` para processar `create`, `update`, `delete`
- Controller `StravaWebhookController` para handshake e recebimento de eventos
- Executor assíncrono dedicado para webhook

## Impact

- APIs: `GET /api/strava/webhook`, `POST /api/strava/webhook`
- Segurança: webhook público com validação por `hub.verify_token`
- Persistência: atualização/cancelamento de `TreinoRealizado` por `externalId`
