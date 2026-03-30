## 1. DTOs

- [x] 1.1 Criar `ProvaInputDto` com campos obrigatórios (`nomeProva`, `dataProva`, `distancia`) e opcionais validados com anotações Jakarta Validation
- [x] 1.2 Criar `ProvaOutputDto` com todos os campos de resposta da entidade `Prova`

## 2. Mapper

- [x] 2.1 Criar interface `ProvaMapper` com `@Mapper(componentModel = "spring")` mapeando `Prova` ↔ `ProvaInputDto` e `Prova` → `ProvaOutputDto`

## 3. Service

- [x] 3.1 Criar interface `ProvaService` com métodos: `criarProva`, `listarProvas`, `buscarProvaPorId`, `atualizarProva`, `deletarProva`
- [x] 3.2 Criar `ProvaServiceImpl` com `@Service @RequiredArgsConstructor` injetando `ProvaRepository`, `AtletaRepository` e `ProvaMapper`
- [x] 3.3 Implementar método `criarProva`: buscar atleta pelo `atletaId`, validar tenant via `TenantContext.getRequiredTenantId()`, criar e salvar a `Prova`
- [x] 3.4 Implementar método `listarProvas`: buscar atleta, validar tenant, usar `findByAtletaOrderByDataProvaAsc`
- [x] 3.5 Implementar método `buscarProvaPorId`: buscar atleta, validar tenant, buscar prova e verificar que pertence ao atleta
- [x] 3.6 Implementar método `atualizarProva`: buscar atleta, validar tenant, buscar prova, mapear campos do DTO e salvar
- [x] 3.7 Implementar método `deletarProva`: buscar atleta, validar tenant, buscar prova, chamar `repository.delete(prova)`

## 4. Controller

- [x] 4.1 Criar `ProvaController` com `@RestController @RequestMapping("/atleta/{atletaId}/provas")` e anotações OpenAPI (`@Tag`)
- [x] 4.2 Implementar `POST /` → `criarProva` retornando HTTP 201
- [x] 4.3 Implementar `GET /` → `listarProvas` retornando HTTP 200 com lista
- [x] 4.4 Implementar `GET /{provaId}` → `buscarProvaPorId` retornando HTTP 200
- [x] 4.5 Implementar `PUT /{provaId}` → `atualizarProva` retornando HTTP 200
- [x] 4.6 Implementar `DELETE /{provaId}` → `deletarProva` retornando HTTP 204

## 5. Testes Unitários

- [x] 5.1 Criar `ProvaServiceImplTest` cobrindo criação, listagem, busca, atualização e deleção com mocks do repositório
- [x] 5.2 Testar cenário de atleta de outro tenant (deve lançar `ResourceNotFoundException`)
- [x] 5.3 Testar cenário de prova não pertencente ao atleta (deve lançar `ResourceNotFoundException`)
