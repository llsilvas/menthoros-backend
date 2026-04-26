## 1. Activity Service

- [ ] 1.1 Criar `StravaActivityService.java` com método `fetchActivities(String accessToken, Instant after, int page)` chamando API Strava com paginação
- [ ] 1.2 Implementar `fetchActivityLaps(String accessToken, Long activityId)` chamando `GET /activities/{id}/laps`
- [ ] 1.3 Implementar `mapToTreinoRealizado(StravaActivityDto, Atleta)` com conversões de unidade e inferência de `TipoTreino`
- [ ] 1.4 Implementar `mapToEtapaRealizada(StravaSplitDto)` com conversão de cadência, distância e elevação
- [ ] 1.5 Implementar `syncActivities(UUID atletaId)` com deduplicação por `externalId` e update de `ultima_sincronizacao`
- [ ] 1.6 Implementar verificação de rate limit via header `X-RateLimit-Remaining`

## 2. Controller

- [ ] 2.1 Criar `StravaActivityController.java` com endpoint `POST /api/strava/sync/{atletaId}`
- [ ] 2.2 Garantir isolamento multi-tenant: atleta deve pertencer ao tenant autenticado

## 3. Testes

- [ ] 3.1 Criar `StravaActivityServiceTest.java` cobrindo mapeamento, inferência de tipo, deduplicação e laps
