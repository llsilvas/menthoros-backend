## 1. Activity Service

- [ ] 1.1 Criar `StravaActivityService.java` com método `fetchActivities(String accessToken, Instant after, int page)` chamando API Strava com paginação
- [ ] 1.2 Implementar `fetchActivityLaps(String accessToken, Long activityId)` chamando `GET /activities/{id}/laps`
- [ ] 1.3 Implementar `mapToTreinoRealizado(StravaActivityDto, Atleta)` com conversões de unidade e inferência de `TipoTreino`
- [ ] 1.4 Implementar `mapToEtapaRealizada(StravaSplitDto)` com conversão de cadência, distância e elevação
- [ ] 1.5 Implementar `syncActivities(UUID atletaId)` com deduplicação por `externalId` e update de `ultima_sincronizacao`
- [ ] 1.6 Implementar verificação de rate limit via header `X-RateLimit-Remaining`
- [ ] 1.7 Implementar estratégia de `metodoCalculoTss` por fonte de dados (`FC` quando houver FC, fallback `PACE` quando não houver FC)
- [ ] 1.8 Persistir metadado de completude mínima da importação (ex.: presença de FC, cadência, pace) em `metadados_sincronizacao`

## 2. Controller

- [ ] 2.1 Criar `StravaActivityController.java` com endpoint `POST /api/strava/sync/{atletaId}`
- [ ] 2.2 Garantir isolamento multi-tenant: atleta deve pertencer ao tenant autenticado
- [ ] 2.3 Retornar resumo de sync (importados, atualizados, ignorados por tipo/rate-limit)

## 3. Testes

- [ ] 3.1 Criar `StravaActivityServiceTest.java` cobrindo mapeamento, inferência de tipo, deduplicação e laps
- [ ] 3.2 Adicionar teste para eleição automática de `metodoCalculoTss` (`FC` vs `PACE`)
- [ ] 3.3 Adicionar teste de parada por rate limit com atualização correta de `ultima_sincronizacao`

## 4. Critérios de Aceite

- [ ] 4.1 Sync manual não cria duplicata para `externalId` já existente do mesmo atleta
- [ ] 4.2 Laps importados preenchem `split_index`, cadência corrigida (`x2`) e campos de elevação
- [ ] 4.3 `metodoCalculoTss` é definido automaticamente conforme disponibilidade de dados
- [ ] 4.4 Sync de atleta fora do tenant retorna `404`

## 5. Review Gate (OpenSpec)

- [ ] 5.1 Executar `openspec status --change "strava-activity-sync" --json` e confirmar artifacts `done`
- [ ] 5.2 Executar `openspec instructions apply --change "strava-activity-sync" --json` e revisar tasks pendentes
- [ ] 5.3 Registrar resultado da revisão no PR antes de merge
