## 1. DTO e Configuração Assíncrona

- [ ] 1.1 Criar `StravaWebhookEventDto.java` com campos `objectType`, `aspectType`, `objectId`, `ownerId`, `eventTime`, `updates`
- [ ] 1.2 Configurar `ThreadPoolTaskExecutor` dedicado para webhooks e habilitar `@Async`

## 2. Webhook Service

- [ ] 2.1 Criar `StravaWebhookService.java` com `processCreateEvent`, `processUpdateEvent`, `processDeleteEvent`
- [ ] 2.2 Validar `ownerId` contra `IntegracaoExterna.externalAthleteId` antes de processar
- [ ] 2.3 Implementar `create` e `update` buscando atividade atualizada e aplicando sync específico
- [ ] 2.4 Implementar `delete` marcando `TreinoRealizado.statusSincronizacao = CANCELADO`

## 3. Webhook Controller

- [ ] 3.1 Criar `StravaWebhookController.java` com `GET /api/strava/webhook` e `POST /api/strava/webhook`
- [ ] 3.2 Implementar validação de `hub.verify_token` com HTTP 403 para token inválido
- [ ] 3.3 Garantir resposta do POST em menos de 500ms, delegando processamento assíncrono

## 4. Testes e Segurança

- [ ] 4.1 Criar `StravaWebhookServiceTest.java` cobrindo owner inválido, create/update/delete e eventos desconhecidos
- [ ] 4.2 Ajustar `SecurityConfig` para permitir acesso público somente ao webhook
