## Context

O backend Menthoros usa um modelo de **shared schema com `tenant_id`** onde cada assessoria (tenant) é isolada pela coluna `tenant_id` nas tabelas de domínio. A infraestrutura de multi-tenancy já existe:

- `TenantContext` (InheritableThreadLocal) propaga o UUID do tenant na thread da request
- `JwtTenantFilter` extrai `tenant_id` do JWT e popula o contexto
- `AtletaRepository` já tem `findByIdAndTenantId` como método tenant-aware modelo

O problema: a camada de serviço ainda contém 3 lacunas críticas que precisam ser removidas antes de ir para produção:
1. Segurança HTTP com `.anyRequest().permitAll()` — requests sem JWT funcionam
2. Fallback `resolveTenantId()` em `AtletaServiceImpl` e `ProvaServiceImpl` — requests sem tenant context operam em dados de outro tenant
3. `findById` global ainda usado em `TreinoServiceImpl`, `PlanoServiceImpl` e `PlanoMetadadosServiceImpl`

Complementarmente: cache sem segmentação por tenant e `PlanoMetaDados` sem campo `tenant_id` mapeado.

## Goals / Non-Goals

**Goals:**
- Toda rota de negócio exige JWT válido com `tenant_id`
- Toda leitura e escrita de entidade tenant-scoped filtra por tenant no mesmo select
- Cache não pode ter hit entre tenants diferentes
- Entidade `PlanoMetaDados` alinhada ao schema (campo `tenant_id` mapeado)
- Constraints compostas de integridade no banco para evitar vínculos entre tenants

**Non-Goals:**
- Propagação de tenant em execução assíncrona (jobs, schedulers) — P1, fora deste escopo
- Row Level Security no PostgreSQL — backlog
- Sincronização admin de usuários via Keycloak — P2, fora deste escopo
- Testes automatizados de isolamento — tratados como tarefa separada nesta entrega
- Modificar `TenantContext` (InheritableThreadLocal já funciona para requests síncronas HTTP)

## Decisions

### D1: Enforçar autenticação em `SecurityConfig`

**Decisão:** trocar `.anyRequest().permitAll()` por `.anyRequest().authenticated()`.

Manter públicos apenas: `/api/public/**`, `/swagger-ui/**`, `/api-docs/**`, `/actuator/health`.

**Alternativas consideradas:**
- Manter `permitAll` com profile de dev — rejeitado porque o fallback de tenant continua presente; prefere-se controlar via profile separado se necessário, não no filtro de segurança

---

### D2: Remover `resolveTenantId()` e usar `TenantContext.getRequiredTenantId()` diretamente

**Decisão:** deletar o método `resolveTenantId()` de `AtletaServiceImpl` e `ProvaServiceImpl`. Toda resolução de tenant passa a ser `TenantContext.getRequiredTenantId()`, que lança `IllegalStateException` se não houver contexto.

**Alternativas consideradas:**
- Manter fallback controlado por profile — rejeitado; introduz risco de configuração incorreta em produção e dificulta testes de isolamento

---

### D3: Repositories tenant-aware com métodos explícitos

**Decisão:** adicionar métodos `findByIdAndTenantId(UUID id, UUID tenantId)` nos repositories que ainda não os têm (`PlanoSemanalRepository`, `TreinoPlanejadoRepository`, `TreinoRealizadoRepository`, `PlanoMetadadosRepository`, `ProvaRepository`). O padrão já existe em `AtletaRepository` e serve de modelo.

Services trocam `findById(id)` por `findByIdAndTenantId(id, TenantContext.getRequiredTenantId())`.

**Alternativas consideradas:**
- Filtro Hibernate global (`@Filter`) — considerado mas rejeitado nesta fase: aumenta risco de regressão em queries existentes e requer teste mais extenso; métodos explícitos são mais rastreáveis
- `findByIdBasic` e `findByIdForUpdate` existentes no `AtletaRepository` — mantidos apenas para uso interno de services que já validam ownership por outro meio (ex: fetch de atleta já validado para calcular TSB)

---

### D4: Chaves de cache segmentadas por `tenantId`

**Decisão:** trocar chaves de cache para incluir `tenantId` como prefixo do ID:

```java
// Antes
@Cacheable(value = "atletas", key = "#id")
@Cacheable(value = "atletas-list")
@Cacheable(value = "metadados-atleta", key = "#atleta.id")

// Depois
@Cacheable(value = "atletas", key = "T(com.menthoros.multitenancy.TenantContext).getRequiredTenantId() + ':' + #id")
@Cacheable(value = "atletas-list", key = "T(com.menthoros.multitenancy.TenantContext).getRequiredTenantId()")
@Cacheable(value = "metadados-atleta", key = "T(com.menthoros.multitenancy.TenantContext).getRequiredTenantId() + ':' + #atleta.id")
```

`CacheEvict` segue o mesmo padrão, substituindo `allEntries = true` em listas por chave tenant.

**Alternativas consideradas:**
- Prefixo global por tenant no `CacheManager` — mais elegante mas requer customização do `CaffeineCacheManager`; as anotações SpEL têm menos risco de efeito colateral

---

### D5: Mapear `tenant_id` em `PlanoMetaDados` como `@ManyToOne Assessoria`

**Decisão:** seguir o padrão de `Atleta`, `PlanoSemanal` e `Prova` — adicionar campo:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "tenant_id", nullable = false)
private Assessoria assessoria;
```

Atualizar `PlanoMetadadosServiceImpl` para popular `assessoria` ao criar metadados via `assessoriaRepository.getReferenceById(TenantContext.getRequiredTenantId())`.

**Alternativas consideradas:**
- UUID simples `tenant_id` sem relação JPA (como `MetricasDiarias`) — rejeitado; metadados têm consultas diretas por atleta que se beneficiam de join via assessoria, e o padrão dominante no domínio é `@ManyToOne`

---

### D6: Migration com constraints compostas e índice de deduplicação

**Decisão:** criar migration `V26__Add_multi_tenancy_constraints.sql` com:
- Índice único `(tenant_id, fonte_dados, external_id)` em `tb_treino_realizado` para evitar colisão de IDs externos entre tenants
- Constraints compostas em tabelas críticas para impedir vínculos entre tenants diferentes

A migration é aditiva — não altera colunas existentes.

## Risks / Trade-offs

**[Risco] Quebra de integração com frontend sem JWT** → Mitigação: a mudança no `SecurityConfig` deve ser coordenada com o time de frontend. Se necessário, adicionar profile `dev` com `permitAll` temporariamente, mas nunca como default.

**[Risco] `IllegalStateException` em paths não mapeados** → Mitigação: após ativar `.authenticated()`, qualquer path que retornar 200 sem JWT se tornará 401 — revisar endpoints públicos antes de mergear.

**[Risco] Invalidação de cache ampla durante rollout** → Não é problema: o cache é Caffeine local (in-memory), sem estado persistido entre deploys.

**[Risco] Migration V26 em banco com dados existentes** → Mitigação: constraints compostas são aditivas; verificar que `tb_treino_realizado` não tem duplicatas `(tenant_id, fonte_dados, external_id)` antes de aplicar o unique index.

**[Trade-off] Chave SpEL no `@Cacheable` é verbosa** → Aceito; alternativa de `CacheManager` customizado tem mais surface area de efeito colateral nesta fase.

## Migration Plan

1. Aplicar em branch `develop` com PR único para os 3 grupos de mudança (segurança, persistência, cache/entidades)
2. Executar testes unitários e de integração existentes após cada grupo
3. Em caso de falha na migration V26 (duplicatas), executar query de diagnóstico antes de re-aplicar:
   ```sql
   SELECT tenant_id, fonte_dados, external_id, COUNT(*)
   FROM tb_treino_realizado
   WHERE fonte_dados IS NOT NULL AND external_id IS NOT NULL
   GROUP BY tenant_id, fonte_dados, external_id
   HAVING COUNT(*) > 1;
   ```
4. Rollback: reverter PR — não há DDL destrutivo

## Open Questions

- **Modo dev sem Keycloak:** há necessidade de executar localmente sem JWT? Se sim, definir profile `local` que mantenha `permitAll` e `resolveTenantId()`, documentar que não pode ir para produção.
- **`findByIdForUpdate` em `AtletaRepository`:** usado em fluxo de lock otimista — avaliar se precisa de versão tenant-aware ou se o contexto de posse já é garantido antes dessa chamada.
