## 1. DTO e Configuração Assíncrona

- [ ] 1.1 Criar `StravaWebhookEventDto.java` com campos `objectType`, `aspectType`, `objectId`, `ownerId`, `eventTime`, `updates`
- [ ] 1.2 Configurar `ThreadPoolTaskExecutor` dedicado para webhooks e habilitar `@Async`
- [ ] 1.3 Implementar enfileiramento com prioridade (mínimo viável: prioridade por `aspect_type` e/ou atleta com prova próxima)

## 2. Webhook Service

- [ ] 2.1 Criar `StravaWebhookService.java` com `processCreateEvent`, `processUpdateEvent`, `processDeleteEvent`
- [ ] 2.2 Validar `ownerId` contra `IntegracaoExterna.externalAthleteId` antes de processar
- [ ] 2.3 Implementar `create` e `update` buscando atividade atualizada e aplicando sync específico
- [ ] 2.4 Implementar `delete` marcando `TreinoRealizado.statusSincronizacao = CANCELADO`
- [ ] 2.5 Implementar tratamento de falha assíncrona com política de retry/reprocessamento mínimo
- [ ] 2.6 Logar eventos descartados com motivo explícito (owner inválido, tipo não suportado, payload inválido)

## 3. Webhook Controller

- [ ] 3.1 Criar `StravaWebhookController.java` com `GET /api/strava/webhook` e `POST /api/strava/webhook`
- [ ] 3.2 Implementar validação de `hub.verify_token` com HTTP 403 para token inválido
- [ ] 3.3 Garantir resposta do POST em menos de 500ms, delegando processamento assíncrono
- [ ] 3.4 Garantir idempotência básica no processamento de eventos duplicados

## 4. Testes e Segurança

- [ ] 4.1 Criar `StravaWebhookServiceTest.java` cobrindo owner inválido, create/update/delete e eventos desconhecidos
- [ ] 4.2 Ajustar `SecurityConfig` para permitir acesso público somente ao webhook
- [ ] 4.3 Adicionar teste para prioridade de fila e teste de retry em falha assíncrona

## 5. Critérios de Aceite

- [ ] 5.1 `GET /api/strava/webhook` responde challenge quando token válido e `403` quando inválido
- [ ] 5.2 `POST /api/strava/webhook` retorna `200` rapidamente e processa evento em background
- [ ] 5.3 Eventos de `owner_id` desconhecido são descartados sem quebrar endpoint
- [ ] 5.4 Evento `delete` marca treino como `CANCELADO` sem exclusão física

## 6. Review Gate (OpenSpec)

- [ ] 6.1 Executar `openspec status --change "strava-webhooks" --json` e confirmar artifacts `done`
- [ ] 6.2 Executar `openspec instructions apply --change "strava-webhooks" --json` e revisar tasks pendentes
- [ ] 6.3 Registrar resultado da revisão no PR antes de merge
