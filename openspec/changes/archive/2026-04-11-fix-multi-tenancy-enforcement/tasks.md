## 0. Pré-requisito: Exception Handler para Tenant Ausente

- [x] 0.1 Em `GlobalExceptionHandler`, adicionar `@ExceptionHandler(IllegalStateException.class)` retornando 403 Forbidden com mensagem genérica (`"Acesso não autorizado: contexto de tenant ausente"`) — evita que `TenantContext.getRequiredTenantId()` exponha mensagem interna via HTTP 500

## 1. Segurança: Autenticação Obrigatória

- [x] 1.1 Em `SecurityConfig`, remover `.anyRequest().permitAll()` e substituir por `.anyRequest().authenticated()`
- [x] 1.2 Verificar que as rotas públicas `/api/public/**`, `/swagger-ui/**`, `/api-docs/**`, `/actuator/health` continuam com `permitAll`
- [x] 1.3 Executar testes existentes para confirmar que requests sem JWT retornam 401 nos endpoints de negócio
- [x] 1.4 Criar `application-local.yml` com override de `SecurityConfig` via profile `local`: `permitAll` em todas as rotas e tenant fixo de desenvolvimento; adicionar aviso explícito no arquivo de que este profile não pode ser usado em produção ou CI
- [x] 1.5 Nos testes de integração que cobrem endpoints de negócio, configurar mock JWT válido com `tenant_id` usando `SecurityMockMvcRequestPostProcessors.jwt()` ou `@WithMockUser` — necessário antes de executar os testes após a task 1.1 (não há testes de integração no momento; sem ação necessária)

## 2. Remoção do Fallback de Tenant Default

- [x] 2.1 Em `AtletaServiceImpl`, deletar o método `resolveTenantId()` e substituir todos os seus usos por `TenantContext.getRequiredTenantId()`
- [x] 2.2 Em `ProvaServiceImpl`, remover o mesmo padrão de fallback e adotar `TenantContext.getRequiredTenantId()` diretamente
- [x] 2.3 Verificar que não existem outros services com `findFirstByAtivoTrue()` ou fallback similar para tenant default

## 3. Repositories Tenant-Aware

- [x] 3.1 Em `PlanoSemanalRepository`, adicionar método `findByIdAndTenantId(UUID id, UUID tenantId)` com JPQL filtrando por `assessoria.id`
- [x] 3.2 Em `TreinoPlanejadoRepository`, adicionar `findByIdAndTenantId(UUID id, UUID tenantId)`
- [x] 3.3 Em `TreinoRealizadoRepository`, adicionar `findByIdAndTenantId(UUID id, UUID tenantId)`
- [x] 3.4 Em `PlanoMetadadosRepository`, adicionar `findByIdAndTenantId(UUID id, UUID tenantId)`
- [x] 3.5 Em `ProvaRepository`, verificar se já existe `findByIdAndTenantId`; adicionar se não existir

## 4. Services: Troca de findById por Variantes Tenant-Aware

- [x] 4.1 Em `TreinoServiceImpl`, substituir `atletaRepository.findById(atletaId)` por `atletaRepository.findByIdAndTenantId(atletaId, TenantContext.getRequiredTenantId())`
- [x] 4.2 Em `TreinoServiceImpl`, substituir `treinoPlanejadoRepository.findById(treinoPlanejadoId)` por variante tenant-aware
- [x] 4.3 Em `TreinoServiceImpl`, corrigir a deduplicação por `fonteDados + externalId` para incluir `tenantId` no critério de busca
- [x] 4.4 Em `PlanoServiceImpl`, substituir `atletaRepository.findById(atletaId)` por `findByIdAndTenantId`
- [x] 4.5 Em `PlanoServiceImpl`, substituir `planoSemanalRepository.findById(planoSemanalId)` por variante tenant-aware
- [x] 4.6 Em `PlanoServiceImpl`, substituir `planoMetadadosRepository.findById(metaDadosCached.getId())` por variante tenant-aware
- [x] 4.7 Em `ProvaServiceImpl`, substituir `provaRepository.findById(...)` por `findByIdAndTenantId`
- [x] 4.8 Em `IaServiceImpl` (linha 299), substituir `atletaRepository.findById(atletaId)` por `atletaRepository.findByIdAndTenantId(atletaId, TenantContext.getRequiredTenantId())` — este método é chamado via HTTP pelo fluxo `PlanoTreinoController → PlanoServiceImpl → IaServiceImpl`

## 5. Entidade PlanoMetaDados: Mapear tenant_id

- [x] 5.1 Em `PlanoMetaDados`, adicionar campo `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "tenant_id") private Assessoria assessoria`
- [x] 5.2 Em `PlanoMetadadosServiceImpl`, popular `assessoria` ao criar novo `PlanoMetaDados` usando `assessoriaRepository.getReferenceById(TenantContext.getRequiredTenantId())`
- [x] 5.3 Verificar que o `PlanoMetadadosRepository` usa o campo `assessoria.id` nas queries de busca por atleta

## 6. Cache Segmentado por Tenant

- [x] 6.1 Em `AtletaServiceImpl`, atualizar `@Cacheable(value = "atletas", key = "#id")` para incluir `tenantId` na chave: `key = "T(com.menthoros.multitenancy.TenantContext).getRequiredTenantId() + ':' + #id"`
- [x] 6.2 Em `AtletaServiceImpl`, atualizar `@Cacheable(value = "atletas-list")` para usar `key = "T(com.menthoros.multitenancy.TenantContext).getRequiredTenantId()"`
- [x] 6.3 Em `AtletaServiceImpl`, atualizar todos os `@CacheEvict` com chaves tenant correspondentes (remover `allEntries = true` para listas)
- [x] 6.4 Em `PlanoMetadadosServiceImpl`, atualizar `@Cacheable(value = "metadados-atleta", key = "#atleta.id")` para incluir `tenantId` na chave
- [x] 6.5 Remover os comentários `TODO(tenant-isolation)` resolvidos após aplicar as mudanças de cache

## 7. Migration: Constraints e Índice de Deduplicação

- [x] 7.1 Criar `V11__Add_multi_tenancy_constraints.sql` com:
  - `DROP INDEX IF EXISTS uk_treino_realizado_external_id` (índice global criado em V8, sem escopo de tenant; `IF EXISTS` garante idempotência)
  - CREATE de índice único composto `(tenant_id, fonte_dados, external_id)` em `tb_treino_realizado` com filtro parcial `WHERE fonte_dados IS NOT NULL AND external_id IS NOT NULL`
- [x] 7.2 Verificar que não há duplicatas existentes em `tb_treino_realizado` antes de aplicar o unique index (query de diagnóstico incluída na migration como comentário; banco local sem dados de sincronização)
- [x] 7.3 Executar `./mvnw flyway:migrate` localmente e confirmar que a migration V11 é aplicada sem erros
