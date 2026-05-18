# Implementation Plan: implement-update-treino-realizado

## Resumo

| Métrica | Valor |
|---|---|
| **Change** | implement-update-treino-realizado |
| **Objetivo** | Implementar stub `updateTreino` + endpoint PUT + ativar loop AI para treinos Strava |
| **Story Points (core)** | 10 SP |
| **Story Points (Strava melhoria)** | 7 SP — bloqueado por análise de rate limit |
| **Complexidade** | Baixa — sem schema changes, sem breaking changes |
| **Ondas de execução** | 4 ondas sequenciais |
| **Dependências críticas** | Repositório → Service → Controller+Testes → Validação |

---

## Completion Status

| Tarefa | Status | Concluído em | Notas |
|---|---|---|---|
| 1. Repositório | Pendente | — | — |
| 2. Service updateTreino | Pendente | — | — |
| 3. Controller PUT | Pendente | — | — |
| 4. Testes de integração | Pendente | — | — |
| 5. Validação final | Pendente | — | — |
| 6. Strava perceived_exertion | Bloqueado | — | Aguarda dados de rate limit em produção |

---

## Ordem de Execução (topologicamente ordenada)

| # | Grupo | Tarefas | SP | Risco | Dependência | Status |
|---|---|---|---|---|---|---|
| 1 | Repositório | 1.1–1.2 | 1 | Baixo | Nenhuma | Pendente |
| 2 | Service | 2.1–2.3 | 3 | Baixo | Grupo 1 | Pendente |
| 3 | Controller | 3.1–3.3 | 2 | Baixo | Grupo 2 | Pendente |
| 4 | Testes integração | 4.1–4.6 | 3 | Baixo | Grupo 2 | Pendente |
| 5 | Validação final | 5.1–5.3 | 1 | Baixo | Grupos 3+4 | Pendente |
| 6 | Strava RPE auto | 6.1–6.7 | 7 | Médio | Bloqueado* | Bloqueado |

*Grupo 6 requer monitoramento de rate limit em produção antes de implementar.*

---

## Estratégia de Execução Paralela

```
Onda 1 (1 SP)  ── [Repositório: 1.1–1.2] ─────────────────────────────────────────
Onda 2 (3 SP)  ──────────────── [Service: 2.1–2.3] ───────────────────────────────
Onda 3 (5 SP)  ───────────────────────────── [Controller: 3.1–3.3] ──┐
                                              [Testes: 4.1–4.6]       ┤ (paralelo)
Onda 4 (1 SP)  ──────────────────────────────────────────────────────── [Validação: 5.1–5.3]

Onda 5 (futuro) ── BLOQUEADO ── [Strava RPE: 6.1–6.7 após análise de rate limit]
```

Controller (Onda 3a) e Testes (Onda 3b) podem rodar em paralelo pois dependem apenas do Service. Em trabalho solo, implementar Controller antes dos Testes para garantir compilação.

---

## Arquivos Afetados por Onda

| Onda | Arquivo | Ação |
|---|---|---|
| 1 | `repository/TreinoRealizadoRepository.java` | Adicionar `findByIdAndTenantId` |
| 2 | `services/TreinoService.java` | Ajustar assinatura: retorno `TreinoRealizadoOutputDto` |
| 2 | `services/impl/TreinoServiceImpl.java` | Implementar `updateTreino` |
| 3 | `controller/TreinoRealizadoController.java` | Adicionar endpoint `PUT /api/v1/treinos/realizados/{id}` |
| 4 | `test/.../UpdateTreinoIntegrationTest.java` | Criar (novo arquivo) |

---

## Detalhes de Implementação por Onda

### Onda 1 — Repositório (1 SP)

**Arquivo:** `repository/TreinoRealizadoRepository.java`

```java
Optional<TreinoRealizado> findByIdAndTenantId(UUID id, UUID tenantId);
```

Critérios de aceite:
- [ ] Método derivado compila sem erros
- [ ] `./mvnw clean compile` passa

---

### Onda 2 — Service (3 SP)

**Arquivo:** `services/TreinoService.java`
- Alterar assinatura: `TreinoRealizado updateTreino(...)` → `TreinoRealizadoOutputDto updateTreino(...)`

**Arquivo:** `services/impl/TreinoServiceImpl.java`

Lógica de negócio:
```
updateTreino(id, dto):
  1. tenantId = TenantContext.getRequiredTenantId()
  2. treino = findByIdAndTenantId(id, tenantId)
             → DomainNotFoundException se vazio
  3. aplicar campos mutáveis do dto na entidade
  4. salvo = repository.save(treino)
  5. if salvo.getPercepcaoEsforco() != null:
       eventPublisher.publishEvent(new TreinoRegistradoEvent(id, tenantId))
  6. return treinoMapper.toOutputDto(salvo)
```

Campos mutáveis a aplicar: `percepcaoEsforco`, `feedbackAtleta`, `qualidadeSonoNoiteAnterior`, `nivelEstresse`, `fcMedia`, `fcMax`, `cadenciaMedia`, `potenciaMedia`, `velocidadeMedia`, `distanciaKm`, `duracaoMin`, `ritmoMedio`, `elevacaoGanhoMetros`, `elevacaoPerdaMetros`, `descricao`, `observacao`, `zonaAlvo`, `ritmoAlvo`, `status`

Campos imutáveis (NÃO aplicar): `atletaId`, `planoSemanalId`, `treinoPlanejadoId`, `dataTreino`, `diaSemana`, `tipoTreino`, `fonteDados`, `externalId`

JavaDoc obrigatório:
```
Idempotent: NO
Side Effects: Database update + conditional TreinoRegistradoEvent
Tenant-aware: YES
```

Critérios de aceite:
- [ ] Retorna `TreinoRealizadoOutputDto` corretamente mapeado
- [ ] Publica evento somente quando `percepcaoEsforco != null`
- [ ] Lança `DomainNotFoundException` para ID inexistente ou de outro tenant
- [ ] `./mvnw clean compile` passa

---

### Onda 3a — Controller (2 SP)

**Arquivo:** `controller/TreinoRealizadoController.java`

```java
@PutMapping("/realizados/{id}")
@PreAuthorize("isAuthenticated()")
@RequireTenant
@Operation(summary = "Atualizar treino realizado")
@ApiResponses(...)
public ResponseEntity<TreinoRealizadoOutputDto> updateTreino(
    @PathVariable UUID id,
    @Valid @RequestBody TreinoRealizadoInputDto dto) {
    return ResponseEntity.ok(treinoService.updateTreino(id, dto));
}
```

Critérios de aceite:
- [ ] Endpoint documentado com `@Tag`, `@Operation`, `@ApiResponses` (200, 400, 403, 404)
- [ ] `@RequireTenant` presente (obrigatório — usa `TenantContext` no service)
- [ ] `./mvnw clean compile` passa

---

### Onda 3b — Testes de Integração (3 SP)

**Arquivo:** `test/java/.../UpdateTreinoIntegrationTest.java`

| Teste | Verifica |
|---|---|
| `updateTreino_persistsMutableFields` | Campos observacionais são salvos corretamente |
| `updateTreino_withRpe_publishesEvent` | `percepcaoEsforco != null` → `TreinoRegistradoEvent` publicado |
| `updateTreino_withoutRpe_doesNotPublishEvent` | `percepcaoEsforco == null` → nenhum evento |
| `updateTreino_wrongTenant_throwsNotFound` | Outro tenant → `DomainNotFoundException` |
| `updateTreino_unknownId_throwsNotFound` | UUID inválido → `DomainNotFoundException` |

Mecanismo de verificação de evento: `@RecordApplicationEvents` + `ApplicationEvents` (Spring Boot Test 2.4+). Alternativa: `@SpyBean ApplicationEventPublisher` se `@RecordApplicationEvents` não estiver disponível.

Critérios de aceite:
- [ ] 5 testes passando
- [ ] `./mvnw clean test` passa com 0 falhas

---

### Onda 4 — Validação Final (1 SP)

Critérios de aceite:
- [ ] `./mvnw clean test` — 0 falhas, 0 erros
- [ ] Campos imutáveis confirmados não sobrescritos (verificação manual via log/debug)
- [ ] `tasks.md` atualizado com todos os itens marcados `[x]`

---

## Riscos e Mitigações

| Risco | Probabilidade | Mitigação |
|---|---|---|
| `TreinoService` tem outras implementações além de `TreinoServiceImpl` — alterar assinatura quebraria | Baixa | Verificar com `grep -r "implements TreinoService"` antes de alterar |
| `@RecordApplicationEvents` indisponível na versão do Spring Boot Test em uso | Baixa | Fallback: `@SpyBean ApplicationEventPublisher` com `verify(publisher).publishEvent(any(TreinoRegistradoEvent.class))` |
| Rate limit Strava ao habilitar Onda 5 (melhoria futura) | Média | Task 6.6 exige logging de `X-RateLimit-Usage` em produção antes de habilitar |

---

## Artefatos OpenSpec

| Artefato | Caminho |
|---|---|
| Proposal | `openspec/changes/implement-update-treino-realizado/proposal.md` |
| Design | `openspec/changes/implement-update-treino-realizado/design.md` |
| Tasks | `openspec/changes/implement-update-treino-realizado/tasks.md` |
| Este documento | `openspec/changes/implement-update-treino-realizado/implementation-plan.md` |

---

## Próximo Passo

Executar `/openspec-apply-change implement-update-treino-realizado` para iniciar a implementação a partir da Onda 1.
