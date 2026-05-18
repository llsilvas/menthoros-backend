# Design: implement-update-treino-realizado

## Campos Mutáveis vs. Imutáveis

Após criação, o treino realizado tem dois tipos de dados:

**Imutáveis (estruturais — identidade do treino):**
- `atletaId`, `planoSemanalId`, `treinoPlanejadoId`
- `dataTreino`, `diaSemana`, `tipoTreino`
- `fonteDados`, `externalId` (origem do dado não muda)

**Mutáveis (observacionais/feedback — podem ser corrigidos ou adicionados depois):**
- `percepcaoEsforco` (RPE) — gate da análise AI
- `feedbackAtleta`, `qualidadeSonoNoiteAnterior`, `nivelEstresse`
- `fcMedia`, `fcMax`, `cadenciaMedia`, `potenciaMedia`, `velocidadeMedia`
- `distanciaKm`, `duracaoMin`, `ritmoMedio`
- `elevacaoGanhoMetros`, `elevacaoPerdaMetros`
- `descricao`, `observacao`, `zonaAlvo`, `ritmoAlvo`
- `status`
- `etapasRealizadas`

O DTO `TreinoRealizadoInputDto` já existe e cobre todos esses campos. Não será criado um DTO separado para update — o mesmo DTO é reutilizado, ignorando campos imutáveis presentes nele.

## Isolamento de Tenant

O método `updateTreino` deve:
1. Resolver `tenantId` via `TenantContext.getRequiredTenantId()`
2. Buscar o treino por `id` AND `tenantId` via query de repositório
3. Lançar `DomainNotFoundException` se não encontrado (404 no GlobalExceptionHandler)

Isso impede que um tenant atualize treinos de outro tenant mesmo que adivinhe o UUID.

## Publicação de Evento

`TreinoRegistradoEvent` é publicado após save se `percepcaoEsforco != null` na entidade pós-atualização.

Rationale: o listener `WorkoutAnalysisListener` já tem gate de idempotência (pula se existe `AnaliseWorkout` com status `COMPLETED`). Logo, publicar o evento sempre que RPE estiver presente é seguro — análise só dispara uma vez por treino. Não há custo em publicar o evento múltiplas vezes para o mesmo treino.

```
updateTreino(id, dto)
  → load TreinoRealizado (tenant-checked)
  → apply mutable fields
  → save
  → if entity.percepcaoEsforco != null:
       publish TreinoRegistradoEvent(id, tenantId)
  → return saved entity
```

## Assinatura do Controller

```
PUT /api/v1/treinos/realizados/{id}
Content-Type: application/json

Body: TreinoRealizadoInputDto (campos imutáveis são ignorados no service)

Response 200: TreinoRealizadoOutputDto
Response 404: treino não encontrado ou não pertence ao tenant
Response 400: dados de entrada inválidos
Response 403: não autenticado / sem permissão
```

O controller converte o retorno usando `TreinoMapper.toOutputDto()`, igual ao padrão dos outros endpoints.

## Ajuste na Interface TreinoService

O método atual retorna `TreinoRealizado`. O controller precisa retornar `TreinoRealizadoOutputDto`. Para manter consistência com `lancarTreino`, que já retorna `TreinoRealizadoOutputDto`, ajustamos a assinatura da interface e do service para retornar `TreinoRealizadoOutputDto` diretamente.

```java
TreinoRealizadoOutputDto updateTreino(UUID id, TreinoRealizadoInputDto dto);
```

## Teste de Integração

`UpdateTreinoIntegrationTest extends AbstractIntegrationTest` deve cobrir:

1. **Update básico** — campos observacionais são persistidos corretamente
2. **Gate RPE → evento** — update com `percepcaoEsforco != null` publica evento (verificado via `ApplicationEvents` ou spy do `ApplicationEventPublisher`)
3. **RPE null não publica evento** — update sem RPE não aciona análise
4. **Tenant isolation** — outro tenant não consegue atualizar o treino (404)
5. **Treino inexistente** — 404 para UUID inválido

## Repositório — Query Tenant-Aware

Se `TreinoRealizadoRepository` não tiver método `findByIdAndTenantId`, adicionar:

```java
Optional<TreinoRealizado> findByIdAndTenantId(UUID id, UUID tenantId);
```

Alternativa via `@Query` JPQL se necessário, mas o derived query é suficiente aqui.
