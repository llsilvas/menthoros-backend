## Why

A entidade `Prova` já existe no domínio e no banco de dados, mas não há endpoints REST expostos para gerenciá-la. Sem um controller, os clientes não conseguem cadastrar, consultar, atualizar ou remover provas de um atleta, bloqueando fluxos essenciais de planejamento de temporada.

## What Changes

- Criar `ProvaController` com endpoints CRUD completos para provas de um atleta
- Criar `ProvaInputDto` e `ProvaOutputDto` para separar entrada/saída da API
- Criar `ProvaMapper` (MapStruct) para converter entre entidade e DTOs
- Criar `ProvaService` (interface) e `ProvaServiceImpl` (implementação) com lógica de negócio
- Criar `ProvaRepository` (Spring Data JPA) para acesso a dados

## Capabilities

### New Capabilities

- `prova-crud`: CRUD completo de provas de atleta via REST, com isolamento multi-tenancy por tenant_id

### Modified Capabilities

<!-- Nenhuma capability existente tem seus requisitos alterados -->

## Impact

- **Novos arquivos:** `ProvaController`, `ProvaService`, `ProvaServiceImpl`, `ProvaRepository`, `ProvaInputDto`, `ProvaOutputDto`, `ProvaMapper`
- **Entidade existente:** `Prova.java` — sem modificações
- **Enums existentes:** `TipoProva`, `DistanciaProva`, `ProvaStatus` — sem modificações
- **Segurança:** Endpoints protegidos via OAuth2/JWT com extração de `tenant_id` pelo `JwtTenantFilter`
- **Cache:** Listagens de provas por atleta podem usar `@Cacheable` via Caffeine
- **Multi-tenancy:** Todas as queries devem filtrar por `tenant_id` do `TenantContext`
