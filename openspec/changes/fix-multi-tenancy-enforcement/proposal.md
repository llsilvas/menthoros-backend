## Why

O backend já possui a infraestrutura base de multi-tenancy (TenantContext, JwtTenantFilter, coluna `tenant_id` nas tabelas), mas o isolamento entre tenants ainda depende de disciplina manual e tem lacunas críticas: autenticação não é obrigatória, existem fallbacks para tenant default, repositories expõem `findById` global, cache não é segmentado por tenant e a entidade `PlanoMetaDados` não mapeia o campo `tenant_id`. Sem essas correções, há risco real de leitura e escrita cruzada entre tenants em produção.

## What Changes

- Fechar `SecurityConfig` para exigir JWT válido em todas as rotas de negócio (remover `.anyRequest().permitAll()`)
- Remover fallback para tenant default em `AtletaServiceImpl` e `ProvaServiceImpl`; substituir `resolveTenantId()` por `TenantContext.getRequiredTenantId()`
- Adicionar métodos tenant-aware (`findByIdAndTenantId`, `existsByIdAndTenantId`, `deleteByIdAndTenantId`) nos repositories de domínio e remover uso de `findById` global em fluxos HTTP
- Segmentar chaves de cache por `tenantId` em `AtletaServiceImpl` e `PlanoMetadadosServiceImpl`; ajustar invalidações para escopo por tenant
- Mapear campo `tenant_id` em `PlanoMetaDados` com relação `@ManyToOne Assessoria` (padrão das demais entidades) e garantir persistência correta na criação de metadados
- Adicionar migration com constraints compostas e índice único `(tenant_id, fonte_dados, external_id)` em `tb_treino_realizado`

## Capabilities

### New Capabilities

- `multi-tenancy-enforcement`: Garantias transversais de isolamento por tenant: autenticação obrigatória, acesso a dados filtrado por tenant, cache segmentado e entidades alinhadas ao schema.

### Modified Capabilities

<!-- Nenhuma capability existente tem mudança de requisitos funcionais. As alterações são de enforcement de segurança e integridade. -->

## Impact

**Segurança / Autenticação:**
- `SecurityConfig` — remoção do `permitAll()` global; manutenção de rotas públicas para health check e Swagger

**Services:**
- `AtletaServiceImpl` — remoção de `resolveTenantId()`, uso de `TenantContext.getRequiredTenantId()`, chaves de cache com tenant
- `ProvaServiceImpl` — remoção de fallback, uso de `findByIdAndTenantId` para prova
- `TreinoServiceImpl` — troca de `findById` por variantes tenant-aware para atleta, treino planejado e deduplicação com tenant
- `PlanoServiceImpl` — troca de `findById` por variantes tenant-aware para atleta, plano semanal e metadados
- `PlanoMetadadosServiceImpl` — chave de cache com tenant; persistência de `tenant_id` ao criar metadados

**Repositories:**
- `AtletaRepository`, `PlanoSemanalRepository`, `TreinoPlanejadoRepository`, `TreinoRealizadoRepository`, `PlanoMetadadosRepository`, `ProvaRepository` — adição de métodos tenant-aware

**Entities:**
- `PlanoMetaDados` — adição de campo `Assessoria assessoria` e `tenantId` para alinhamento com schema

**Database:**
- Nova migration com constraints compostas de tenant em tabelas críticas e índice único `(tenant_id, fonte_dados, external_id)`
