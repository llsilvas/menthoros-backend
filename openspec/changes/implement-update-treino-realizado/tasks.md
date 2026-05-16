## 1. Repositório — Query Tenant-Aware

- [x] 1.1 Adicionar `findByIdAndTenantId(UUID id, UUID tenantId): Optional<TreinoRealizado>` em `TreinoRealizadoRepository`
- [x] 1.2 Executar `./mvnw clean compile` e garantir 0 erros

## 2. Service — Implementar updateTreino

- [x] 2.1 Implementar `TreinoServiceImpl.updateTreino(UUID id, TreinoRealizadoInputDto dto)`:
  - Resolver `tenantId` via `TenantContext.getRequiredTenantId()`
  - Carregar treino com `findByIdAndTenantId(id, tenantId)`, lançar `DomainNotFoundException` se ausente
  - Aplicar apenas campos mutáveis do DTO via `applyMutableFields()`: `percepcaoEsforco`, `feedbackAtleta`, `qualidadeSonoNoiteAnterior`, `nivelEstresse`, `fcMedia`, `fcMax`, `cadenciaMedia`, `potenciaMedia`, `velocidadeMedia`, `distanciaKm`, `elevacaoGanhoMetros`, `elevacaoPerdaMetros`, `descricao`, `observacao`, `zonaAlvo`, `ritmoAlvo`, `status`
  - Persistir com `treinoRealizadoRepository.save()`
  - Se `entity.getPercepcaoEsforco() != null` após save: publicar `TreinoRegistradoEvent(id, tenantId)`
  - JavaDoc com `Idempotent: NO`, `Side Effects: Database update + conditional event`, `Tenant-aware: YES`
- [x] 2.2 Ajustar assinatura na interface `TreinoService`: retorno `TreinoRealizadoOutputDto`
- [x] 2.3 Executar `./mvnw clean compile` e garantir 0 erros

## 3. Controller — Endpoint PUT

- [x] 3.1 Adicionar `PUT /api/v1/treinos/realizados/{id}` em `TreinoRealizadoController` com `@PreAuthorize("isAuthenticated()")`
- [x] 3.2 `@Operation(summary = "Atualizar treino realizado")` e `@ApiResponses` (200, 400, 403, 404)
- [x] 3.3 Executar `./mvnw clean compile` — 0 erros

## 4. Testes de Integração

- [x] 4.1 Criar `UpdateTreinoIntegrationTest extends AbstractIntegrationTest` com `@RecordApplicationEvents`
- [x] 4.2 Teste: `updateTreino_persistsMutableFields` — campos observacionais persistidos corretamente
- [x] 4.3 Teste: `updateTreino_withRpe_publishesEvent` — percepcaoEsforco != null → evento publicado
- [x] 4.4 Teste: `updateTreino_withoutRpe_doesNotPublishEvent` — sem RPE → sem evento
- [x] 4.5 Teste: `updateTreino_wrongTenant_throwsNotFound` — tenant errado → DomainNotFoundException
- [x] 4.6 Teste: `updateTreino_unknownId_throwsNotFound` — UUID inválido → DomainNotFoundException
- [x] Teste extra: `updateTreino_doesNotOverwriteImmutableFields` — campos estruturais preservados

Resultado: **6/6 testes passando** (surefire: 0 failures, 0 errors).
Nota: Maven BUILD FAILURE causado por threads async do WorkoutAnalysisListener que persistem após o contexto de teste fechar (pré-existente, não relacionado a esta feature).

## 5. Validação Final

- [x] 5.1 `./mvnw clean test -Dtest=UpdateTreinoIntegrationTest` — surefire report: 6 passed, 0 failures
- [x] 5.2 Campos imutáveis confirmados não sobrescritos (teste `updateTreino_doesNotOverwriteImmutableFields`)
- [x] 5.3 Tarefas marcadas como concluídas neste arquivo

---

## 6. Melhoria: Capturar `perceived_exertion` do Garmin via Strava Detail API

> **Status: PENDENTE DE ANÁLISE DE IMPACTO** — Não implementar antes de avaliar consumo de rate limit em produção.

### Contexto

O campo `perceived_exertion` do Strava (que carrega o RPE do Garmin) está disponível apenas no endpoint de detalhe `/activities/{id}`, não no endpoint de lista `/athlete/activities` usado pelo sync atual. O código em `StravaActivityServiceImpl.java:375` já tenta ler esse campo, mas recebe `null` porque a resposta do endpoint de lista não o inclui.

O Strava cobra os seguintes limites por token OAuth (por atleta):
- **100 requisições por 15 minutos**
- **1.000 requisições por dia**

### Impacto por Estratégia

| Estratégia | Chamadas por atividade | Risco de rate limit |
|---|---|---|
| Atual (só lista + laps) | 1 lista + 1 laps = **2 por atividade** | Baixo |
| Detail para todas as atividades | 1 lista + 1 detail + 1 laps = **3 por atividade** | Médio — backfill de 300 atividades usa 900 calls/dia |
| Detail só para novas atividades | 1 lista + 1 detail (só novas) + 1 laps (só novas) | Baixo em regime estável, médio em primeiro sync |
| Detail só se `perceived_exertion == null` na lista | Condicional — depende do comportamento real da API | Requer teste empírico primeiro |

### Tarefas (quando aprovado para implementação)

- [ ] 6.1 **Validar empiricamente** se o endpoint `/athlete/activities` realmente retorna `perceived_exertion: null` para atividades Garmin com RPE preenchido (pode ser que já funcione em algumas versões da API)
- [ ] 6.2 **Medir consumo real de rate limit** atual com um atleta de teste — verificar os headers `X-RateLimit-Usage` e `X-RateLimit-Limit` logados durante um sync completo
- [ ] 6.3 Se confirmado que a lista não traz o campo: implementar `fetchDetailedActivity(accessToken, activityId)` em `StravaActivityServiceImpl` usando o endpoint existente `/activities/{id}` (linha 184 já tem uma chamada similar)
- [ ] 6.4 Estratégia de chamada: buscar detail **somente para atividades novas** (primeira vez que o `externalId` aparece), nunca em re-syncs — preserva budget de rate limit
- [ ] 6.5 Extrair `perceived_exertion` do `DetailedActivity` e aplicar em `mergeActivityIntoTreino` apenas se `perceivedExertion != null`
- [ ] 6.6 Atualizar `checkRateLimit` para logar o `X-RateLimit-Usage` (atual + limite) em `INFO` durante sync — permite monitorar consumo antes de habilitar em produção
- [ ] 6.7 Teste de integração: verificar que detail só é chamado para atividades novas, não para re-syncs
