## 1. Dependências e Configuração

- [x] 1.1 Adicionar `spring-boot-starter-oauth2-client` e `spring-boot-starter-webflux` ao `pom.xml`
- [x] 1.2 Adicionar configuração `app.strava` ao `application.yml` (client-id, client-secret, redirect-uri, authorization-uri, token-uri, api-base-url, webhook-verify-token)
- [x] 1.3 Adicionar variáveis `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, `STRAVA_REDIRECT_URI`, `STRAVA_WEBHOOK_TOKEN` ao `.env` de exemplo
- [x] 1.4 Criar `StravaProperties.java` em `config/` com `@ConfigurationProperties(prefix = "app.strava")`
- [x] 1.5 Configurar bean `WebClient` no `StravaProperties` ou em classe de configuração dedicada

## 2. Modelo de Dados — Entidade e Migrations

- [x] 2.1 Criar entidade `IntegracaoExterna.java` em `entity/` com campos: `id`, `atleta` (FK), `plataforma` (enum `FonteDados`), `externalAthleteId`, `accessToken`, `refreshToken`, `tokenExpiraEm`, `scopes`, `ativo`, `ultimaSincronizacao`, `tenantId`, `createdAt`, `updatedAt`
- [x] 2.2 Criar migration `V26__Create_integracao_externa_table.sql` com tabela `tb_integracao_externa`, constraint UNIQUE em `(atleta_id, plataforma)` e índices
- [x] 2.3 Criar migration `V27__Add_strava_fields_to_treino_realizado.sql` adicionando: `status_sincronizacao`, `sincronizado_em`, `url_externo`, `metadados_sincronizacao`, `elapsed_time_seg`, `suffer_score`, `device_name`, `gear_name`
- [x] 2.4 Criar migration `V28__Add_strava_fields_to_etapa_realizada.sql` adicionando: `split_index`, `elevacao_ganho_metros`, `elevacao_perda_metros`
- [x] 2.5 Adicionar os novos campos de `V27` na entidade `TreinoRealizado.java`
- [x] 2.6 Adicionar os novos campos de `V28` na entidade `EtapaRealizada.java`

## 3. Repositories

- [x] 3.1 Criar `IntegracaoExternaRepository.java` com: `findByAtletaIdAndPlataforma(UUID, FonteDados)`, `findByExternalAthleteIdAndPlataforma(String, FonteDados)`, `existsByAtletaIdAndPlataforma(UUID, FonteDados)`

## 4. DTOs Strava

- [x] 4.1 Criar `StravaTokenResponse.java` em `dto/strava/` com campos: `tokenType`, `expiresAt` (Unix), `expiresIn`, `refreshToken`, `accessToken`, `athlete`
- [x] 4.2 Criar `StravaAthleteDto.java` com campos: `id`, `username`, `firstname`, `lastname`, `city`, `country`, `sex`, `profile`
- [x] 4.3 Criar `StravaActivityDto.java` com campos: `id`, `name`, `sportType`, `startDateLocal`, `distance`, `movingTime`, `elapsedTime`, `totalElevationGain`, `averageSpeed`, `averageHeartrate`, `maxHeartrate`, `hasHeartrate`, `sufferScore`, `perceivedExertion`, `description`, `manual`, `workoutType`, `averageCadence`, `deviceName`, `gear`, `splitsMetric`
- [x] 4.4 Criar `StravaSplitDto.java` (lap) com campos: `lapIndex`, `distance`, `elapsedTime`, `movingTime`, `averageSpeed`, `averageHeartrate`, `maxHeartrate`, `averageCadence`, `averageWatts`, `elevationDifference`
- [x] 4.5 Criar `StravaGearDto.java` com campos: `id`, `name`
- [x] 4.6 Criar `StravaWebhookEventDto.java` com campos: `objectType`, `aspectType`, `objectId`, `ownerId`, `eventTime`, `updates`

## 5. Capability: strava-oauth

- [x] 5.1 Criar `StravaOAuthService.java` com métodos: `getAuthorizationUrl(UUID atletaId)`, `exchangeCodeForToken(String code, Atleta atleta)`, `refreshAccessToken(IntegracaoExterna integracao)`, `getValidToken(UUID atletaId)`, `isConnected(UUID atletaId)`, `disconnect(UUID atletaId)`
- [x] 5.2 Implementar lógica de verificação de expiração de token em `getValidToken` (5 minutos de margem)
- [x] 5.3 Implementar desativação de integração (`ativo = false`, limpeza de tokens) em `disconnect`
- [x] 5.4 Criar `StravaAuthController.java` com endpoints: `GET /api/strava/auth`, `GET /api/strava/callback`, `GET /api/strava/status/{atletaId}`, `DELETE /api/strava/disconnect/{atletaId}`
- [x] 5.5 Adicionar anotações OpenAPI (`@Tag`, `@Operation`) no controller
- [x] 5.6 Garantir que `tenant_id` do `TenantContext` é associado ao `IntegracaoExterna` salvo

## 6. Capability: strava-activity-sync

- [x] 6.1 Criar `StravaActivityService.java` com método `fetchActivities(String accessToken, Instant after, int page)` chamando `GET /activities` da API Strava com paginação
- [x] 6.2 Implementar `fetchActivityLaps(String accessToken, Long activityId)` chamando `GET /activities/{id}/laps`
- [x] 6.3 Implementar `mapToTreinoRealizado(StravaActivityDto, Atleta)` com todas as conversões de unidade: metros→km, m/s→km/h, segundos→Duration, inferência de `TipoTreino`
- [x] 6.4 Implementar `mapToEtapaRealizada(StravaSplitDto)` com conversão de cadência (×2), distância e elevação por split
- [x] 6.5 Implementar `syncActivities(UUID atletaId)` orquestrando: obter token válido → buscar atividades desde `ultima_sincronizacao` → mapear → deduplicar por `externalId` → salvar → atualizar `ultima_sincronizacao`
- [x] 6.6 Implementar verificação e respeito ao rate limit Strava via header `X-RateLimit-Remaining`
- [x] 6.7 Criar `StravaActivityController.java` com endpoint `POST /api/strava/sync/{atletaId}`
- [x] 6.8 Garantir isolamento multi-tenancy: atleta do sync MUST pertencer ao tenant do usuário autenticado

## 7. Capability: strava-webhooks

- [x] 7.1 Criar `StravaWebhookService.java` com métodos: `processCreateEvent(Long objectId, Long ownerId)`, `processUpdateEvent(Long objectId, Long ownerId)`, `processDeleteEvent(Long objectId, Long ownerId)`
- [x] 7.2 Anotar métodos de processamento com `@Async` e configurar `ThreadPoolTaskExecutor` para processamento assíncrono de webhooks
- [x] 7.3 Implementar validação de `ownerId` contra `IntegracaoExterna.externalAthleteId` antes de processar evento
- [x] 7.4 Implementar processamento de evento `create`: buscar atividade na API Strava e chamar `syncActivities` para a atividade específica
- [x] 7.5 Implementar processamento de evento `update`: buscar atividade atualizada e atualizar `TreinoRealizado` existente
- [x] 7.6 Implementar processamento de evento `delete`: marcar `TreinoRealizado.statusSincronizacao = CANCELADO` sem deletar registro
- [x] 7.7 Criar `StravaWebhookController.java` com endpoints: `GET /api/strava/webhook` (validação de subscription com hub.challenge), `POST /api/strava/webhook` (receber eventos)
- [x] 7.8 Implementar validação do `hub.verify_token` no endpoint GET, respondendo HTTP 403 em caso de token inválido
- [x] 7.9 Garantir que `POST /api/strava/webhook` responde HTTP 200 em menos de 500ms antes de delegar ao processamento assíncrono

## 8. Testes Unitários

- [x] 8.1 Criar `StravaOAuthServiceTest.java` cobrindo: geração de URL, troca de código, refresh de token, detecção de expiração, desconexão
- [x] 8.2 Criar `StravaActivityServiceTest.java` cobrindo: mapeamento de unidades, inferência de TipoTreino, deduplicação por externalId, mapeamento de laps
- [x] 8.3 Criar `StravaWebhookServiceTest.java` cobrindo: validação de ownerId, processamento de create/update/delete, descarte de eventos desconhecidos

## 9. Segurança e Documentação

- [x] 9.1 Verificar que endpoints `/api/strava/**` estão protegidos por autenticação OAuth2 (exceto `/api/strava/webhook` que recebe chamadas do Strava sem JWT)
- [x] 9.2 Configurar Spring Security para permitir acesso não autenticado somente em `GET /api/strava/webhook` e `POST /api/strava/webhook`
- [x] 9.3 Adicionar `application-test.yml` com stub/mock das propriedades Strava para testes
- [x] 9.4 Documentar variáveis de ambiente necessárias no README ou `DOCKER_QUICKSTART.md`
