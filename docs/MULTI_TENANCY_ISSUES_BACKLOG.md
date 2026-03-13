# 🎯 BACKLOG: IMPLEMENTAÇÃO MULTI-TENANCY

**Projeto:** Menthoros - Multi-Tenancy
**Branch:** feature/multi-tenancy
**Data Criação:** 2025-11-04
**Última Atualização:** 2025-11-04

---

## 📊 VISÃO GERAL

### Status Atual
- ✅ **Infraestrutura Core:** 100% (TenantContext, JwtFilter, SecurityConfig)
- ✅ **Sincronização Keycloak:** 100% (UsuarioSyncService)
- ✅ **Modelo de Dados:** 95% (Migrações SQL completas)
- ❌ **Isolamento de Dados:** 20% (Repositories não filtram por tenant)
- ❌ **Hibernate Filters:** 0% (Dependência manual de queries)
- ❌ **Testes:** 0% (Sem testes de isolamento)

### Riscos Identificados
🚨 **CRÍTICO:** Vazamento de dados entre tenants (AtletaRepository e outros)
🔶 **ALTO:** Falta de Hibernate Filters (isolamento manual é frágil)
🔶 **ALTO:** Sem testes automatizados de isolamento

### Estimativa Total
- **Sprint 1 (Crítico):** 2-3 dias
- **Sprint 2 (Hibernate Filters):** 2-3 dias
- **Sprint 3 (Entity Listener):** 1 dia
- **Sprint 4 (Testes):** 2-3 dias
- **TOTAL:** 7-10 dias úteis

---

## 🚨 SPRINT 1: SEGURANÇA CRÍTICA (URGENTE)
**Objetivo:** Corrigir vazamento de dados nos repositories
**Prazo:** 2-3 dias
**Prioridade:** 🔴 CRÍTICA

### 1.1 [ISSUE-001] 🚨 Corrigir AtletaRepository

**Prioridade:** 🔴 P0 - CRÍTICA
**Estimativa:** 4 horas
**Responsável:** Backend Team
**Labels:** `security`, `critical`, `bug`

#### Descrição
AtletaRepository NÃO filtra por tenant_id, retornando atletas de TODOS os tenants. Isso é um **vazamento crítico de dados**.

#### Problema Atual
```java
// ❌ CÓDIGO ATUAL (INCORRETO):
@Query("select atl from Atleta atl where atl.ativo = 'ATIVO' order by atl.nome ASC")
List<Atleta> findAllAtletasWithBasicInfo();
```

#### Solução Esperada
```java
// ✅ CÓDIGO CORRETO:
@Query("select atl from Atleta atl where atl.assessoria.id = :tenantId AND atl.ativo = 'ATIVO' order by atl.nome ASC")
List<Atleta> findAllAtletasWithBasicInfo(@Param("tenantId") UUID tenantId);
```

#### Arquivos Impactados
- `/src/main/java/com/menthoros/repository/AtletaRepository.java`

#### Métodos a Corrigir
1. `findAllAtletasWithBasicInfo()` ← Mais usado
2. `findAllAtletas()`
3. `findAllAtletasWithDias()`
4. `findAllAtletasWithProvas()`
5. `findByIdBasic(UUID id)` ← Validar que ID pertence ao tenant

#### Critérios de Aceitação
- [ ] Todos os métodos recebem parâmetro `@Param("tenantId") UUID tenantId`
- [ ] Todas as queries filtram por `atl.assessoria.id = :tenantId`
- [ ] Método `findByIdBasic` valida ownership do atleta
- [ ] Testes unitários criados para cada método
- [ ] Code review aprovado

#### Testes de Verificação
```java
@Test
public void deveRetornarApenasAtletasDoTenant() {
    UUID tenant1 = criarTenant("Assessoria A");
    UUID tenant2 = criarTenant("Assessoria B");

    criarAtleta("João", tenant1);
    criarAtleta("Maria", tenant2);

    TenantContext.setTenantId(tenant1);
    List<Atleta> atletas = atletaRepository.findAllAtletasWithBasicInfo(tenant1);

    assertEquals(1, atletas.size());
    assertEquals("João", atletas.get(0).getNome());
}
```

---

### 1.2 [ISSUE-002] 🚨 Corrigir AtletaServiceImpl

**Prioridade:** 🔴 P0 - CRÍTICA
**Estimativa:** 2 horas
**Responsável:** Backend Team
**Labels:** `security`, `critical`, `bug`

#### Descrição
AtletaServiceImpl não passa `tenantId` para os repositories, causando retorno de dados de todos os tenants.

#### Problema Atual
```java
// ❌ CÓDIGO ATUAL (INCORRETO):
@Override
public List<AtletaOutputDto> getAllAtletas() {
    List<Atleta> allAtletas = atletaRepository.findAllAtletasWithBasicInfo();
    return atletaMapper.toOutputDtoList(allAtletas);
}
```

#### Solução Esperada
```java
// ✅ CÓDIGO CORRETO:
@Override
public List<AtletaOutputDto> getAllAtletas() {
    UUID tenantId = TenantContext.getRequiredTenantId();
    List<Atleta> allAtletas = atletaRepository.findAllAtletasWithBasicInfo(tenantId);
    return atletaMapper.toOutputDtoList(allAtletas);
}
```

#### Arquivos Impactados
- `/src/main/java/com/menthoros/services/impl/AtletaServiceImpl.java`

#### Métodos a Corrigir
1. `getAllAtletas()`
2. `getAtletaById(UUID id)` ← Validar ownership
3. `createAtleta(AtletaInputDto dto)` ← Setar tenant automaticamente
4. `updateAtleta(UUID id, AtletaInputDto dto)` ← Validar ownership
5. Todos os outros métodos que fazem queries

#### Critérios de Aceitação
- [ ] Todos os métodos usam `TenantContext.getRequiredTenantId()`
- [ ] Operações de escrita validam ownership antes de salvar
- [ ] Operações de leitura retornam apenas dados do tenant
- [ ] Lança `AccessDeniedException` se tentar acessar recurso de outro tenant
- [ ] Testes de integração criados

---

### 1.3 [ISSUE-003] 🔴 Auditar Todos os Repositories

**Prioridade:** 🔴 P0 - CRÍTICA
**Estimativa:** 6-8 horas
**Responsável:** Backend Team
**Labels:** `security`, `audit`, `critical`

#### Descrição
Revisar TODOS os repositories do sistema e garantir que queries filtram por tenant_id.

#### Repositories a Auditar

##### Alta Prioridade (dados sensíveis):
1. ✅ `UsuarioRepository` - JÁ IMPLEMENTADO CORRETAMENTE
2. ❌ `AtletaRepository` - [ISSUE-001]
3. ❌ `TreinoPlanejadoRepository`
4. ❌ `TreinoRealizadoRepository`
5. ❌ `ProvaRepository`
6. ❌ `PlanoSemanalRepository`
7. ❌ `PlanoMetaDadosRepository`
8. ❌ `MetricasDiariasRepository`

##### Média Prioridade:
9. ❌ `PlanoTreinoRepository`
10. ⚠️ `AssessoriaRepository` - Validar se está correto

#### Checklist por Repository
Para cada repository, verificar:
- [ ] Todas as queries customizadas (`@Query`) filtram por tenant_id
- [ ] Métodos herdados de `JpaRepository` são seguros (usar Hibernate Filter depois)
- [ ] Métodos `findById` validam ownership
- [ ] Métodos de escrita validam ownership antes de salvar/deletar
- [ ] Documentação atualizada com exemplos de uso seguro

#### Template de Correção
```java
// ANTES (INSEGURO):
@Query("SELECT t FROM TreinoPlanejado t WHERE t.status = :status")
List<TreinoPlanejado> findByStatus(@Param("status") StatusTreino status);

// DEPOIS (SEGURO):
@Query("SELECT t FROM TreinoPlanejado t WHERE t.planoSemanal.planoTreino.atleta.assessoria.id = :tenantId AND t.status = :status")
List<TreinoPlanejado> findByTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") StatusTreino status);
```

#### Critérios de Aceitação
- [ ] Todos os 10 repositories auditados
- [ ] Planilha de auditoria preenchida (ver template abaixo)
- [ ] Issues individuais criadas para cada repository com problemas
- [ ] Prioridades definidas para correção
- [ ] Plano de testes definido

#### Template de Planilha de Auditoria

| Repository | Total Métodos | Seguros | Inseguros | Criticidade | Issue |
|------------|---------------|---------|-----------|-------------|-------|
| AtletaRepository | 5 | 0 | 5 | 🔴 CRÍTICA | ISSUE-001 |
| TreinoPlanejadoRepository | ? | ? | ? | ? | ? |
| ... | ... | ... | ... | ... | ... |

---

### 1.4 [ISSUE-004] 🔴 Implementar Validação de Ownership nos Controllers

**Prioridade:** 🔴 P1 - ALTA
**Estimativa:** 4 horas
**Responsável:** Backend Team
**Labels:** `security`, `enhancement`

#### Descrição
Adicionar validação de ownership nos controllers para garantir que usuários não possam acessar recursos de outros tenants mesmo que passem IDs válidos.

#### Exemplo de Vulnerabilidade
```bash
# Usuário do Tenant A tenta acessar atleta do Tenant B:
GET /api/atletas/123e4567-e89b-12d3-a456-426614174001  # ID válido de outro tenant
# Sem validação: retorna dados do Tenant B ❌
# Com validação: retorna HTTP 403 Forbidden ✅
```

#### Implementação

##### Opção 1: Validação Manual nos Services (Imediata)
```java
@Override
public AtletaOutputDto getAtletaById(UUID id) {
    UUID tenantId = TenantContext.getRequiredTenantId();

    Atleta atleta = atletaRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado"));

    // Validar ownership
    if (!atleta.getAssessoria().getId().equals(tenantId)) {
        throw new AccessDeniedException("Acesso negado: recurso pertence a outro tenant");
    }

    return atletaMapper.toOutputDto(atleta);
}
```

##### Opção 2: Annotation Customizada (Médio Prazo)
```java
@PreAuthorize("@tenantSecurityService.hasAccessToAtleta(#id)")
@GetMapping("/{id}")
public ResponseEntity<AtletaOutputDto> getAtleta(@PathVariable UUID id) {
    // ...
}
```

#### Arquivos Impactados
- `/src/main/java/com/menthoros/controller/*Controller.java`
- `/src/main/java/com/menthoros/services/impl/*ServiceImpl.java`

#### Endpoints a Proteger
1. `GET /api/atletas/{id}`
2. `PUT /api/atletas/{id}`
3. `DELETE /api/atletas/{id}`
4. `GET /api/planos/{id}`
5. `PUT /api/planos/{id}`
6. Todos os endpoints que recebem IDs de recursos

#### Critérios de Aceitação
- [ ] Todos os endpoints com `{id}` validam ownership
- [ ] Lança `AccessDeniedException` (HTTP 403) para acesso negado
- [ ] Lança `ResourceNotFoundException` (HTTP 404) se recurso não existe
- [ ] Logs de tentativa de acesso não autorizado
- [ ] Testes de segurança criados

#### Testes de Segurança
```java
@Test
public void naoDevePermitirAcessoAAtletaDeOutroTenant() {
    UUID tenant1 = criarTenant("Assessoria A");
    UUID tenant2 = criarTenant("Assessoria B");

    UUID atletaId = criarAtleta("João", tenant2).getId();

    // Simular login como usuário do Tenant 1
    TenantContext.setTenantId(tenant1);

    // Tentar acessar atleta do Tenant 2
    assertThrows(AccessDeniedException.class, () -> {
        atletaService.getAtletaById(atletaId);
    });
}
```

---

### 1.5 [ISSUE-005] 🔴 Criar Exception Handler para Multi-Tenancy

**Prioridade:** 🔴 P1 - ALTA
**Estimativa:** 2 horas
**Responsável:** Backend Team
**Labels:** `security`, `enhancement`

#### Descrição
Criar handler global para exceções relacionadas a multi-tenancy com respostas padronizadas e logs de segurança.

#### Implementação
```java
@ControllerAdvice
public class MultiTenancyExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY_AUDIT");

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {

        UUID tenantId = TenantContext.getTenantId();
        String username = SecurityContextHolder.getContext()
            .getAuthentication().getName();

        // Log de tentativa de acesso não autorizado
        securityLog.warn("ACESSO NEGADO - User: {}, Tenant: {}, Path: {}, Message: {}",
            username, tenantId, request.getRequestURI(), ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.FORBIDDEN.value())
            .error("Forbidden")
            .message("Você não tem permissão para acessar este recurso")
            .path(request.getRequestURI())
            .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTenantNotFound(
            TenantNotFoundException ex,
            HttpServletRequest request) {

        securityLog.error("TENANT NÃO ENCONTRADO - Message: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.FORBIDDEN.value())
            .error("Forbidden")
            .message("Tenant não encontrado ou inválido")
            .path(request.getRequestURI())
            .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
}
```

#### Arquivos a Criar
- `/src/main/java/com/menthoros/exception/MultiTenancyExceptionHandler.java`
- `/src/main/java/com/menthoros/exception/AccessDeniedException.java`
- `/src/main/java/com/menthoros/exception/TenantNotFoundException.java`
- `/src/main/java/com/menthoros/dto/ErrorResponse.java`

#### Critérios de Aceitação
- [ ] Handler criado e registrado
- [ ] Logs de segurança separados (SECURITY_AUDIT logger)
- [ ] Respostas padronizadas (ErrorResponse DTO)
- [ ] Não vaza informações sensíveis nas mensagens de erro
- [ ] Testes unitários criados

---

## 🔧 SPRINT 2: HIBERNATE FILTERS (ALTA PRIORIDADE)
**Objetivo:** Implementar isolamento automático por tenant usando Hibernate Filters
**Prazo:** 2-3 dias
**Prioridade:** 🟠 ALTA

### 2.1 [ISSUE-006] 🟠 Criar Interface TenantAware

**Prioridade:** 🟠 P2 - ALTA
**Estimativa:** 1 hora
**Responsável:** Backend Team
**Labels:** `enhancement`, `architecture`

#### Descrição
Criar interface marker para identificar entidades que devem ser filtradas por tenant.

#### Implementação
```java
package com.menthoros.entity;

/**
 * Interface marker para entidades multi-tenant.
 * Entidades que implementam esta interface serão automaticamente
 * filtradas pelo Hibernate Filter "tenantFilter".
 */
public interface TenantAware {

    /**
     * Retorna a assessoria (tenant) dona desta entidade.
     */
    Assessoria getAssessoria();

    /**
     * Define a assessoria (tenant) dona desta entidade.
     */
    void setAssessoria(Assessoria assessoria);
}
```

#### Arquivos a Criar
- `/src/main/java/com/menthoros/entity/TenantAware.java`

#### Entidades a Atualizar
Fazer todas implementarem `TenantAware`:
- [ ] `Atleta`
- [ ] `Usuario`
- [ ] `TreinoPlanejado`
- [ ] `TreinoRealizado`
- [ ] `Prova`
- [ ] `PlanoSemanal`
- [ ] `PlanoTreino`
- [ ] `PlanoMetaDados`
- [ ] `MetricasDiarias`

#### Exemplo
```java
@Entity
@Table(name = "tb_atleta")
public class Atleta implements TenantAware {

    // ... campos existentes ...

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Assessoria assessoria;

    @Override
    public Assessoria getAssessoria() {
        return assessoria;
    }

    @Override
    public void setAssessoria(Assessoria assessoria) {
        this.assessoria = assessoria;
    }
}
```

#### Critérios de Aceitação
- [ ] Interface `TenantAware` criada
- [ ] Todas as entidades multi-tenant implementam a interface
- [ ] Compilação sem erros
- [ ] Documentação JavaDoc completa

---

### 2.2 [ISSUE-007] 🟠 Adicionar @FilterDef e @Filter nas Entidades

**Prioridade:** 🟠 P2 - ALTA
**Estimativa:** 3 horas
**Responsável:** Backend Team
**Labels:** `enhancement`, `hibernate`

#### Descrição
Adicionar anotações Hibernate Filter em todas as entidades multi-tenant para filtrar automaticamente por tenant_id.

#### Implementação

##### Passo 1: Adicionar @FilterDef e @Filter
```java
@Entity
@Table(name = "tb_atleta")
@FilterDef(
    name = "tenantFilter",
    parameters = @ParamDef(name = "tenantId", type = UUID.class)
)
@Filter(
    name = "tenantFilter",
    condition = "tenant_id = :tenantId"
)
public class Atleta implements TenantAware {
    // ...
}
```

##### Passo 2: Para entidades com relacionamento indireto
```java
@Entity
@Table(name = "tb_treino_planejado")
@FilterDef(
    name = "tenantFilter",
    parameters = @ParamDef(name = "tenantId", type = UUID.class)
)
@Filter(
    name = "tenantFilter",
    condition = "EXISTS (SELECT 1 FROM tb_atleta a WHERE a.id = atleta_id AND a.tenant_id = :tenantId)"
)
public class TreinoPlanejado implements TenantAware {
    // ...
}
```

#### Arquivos a Atualizar
- [ ] `Atleta.java`
- [ ] `Usuario.java`
- [ ] `TreinoPlanejado.java`
- [ ] `TreinoRealizado.java`
- [ ] `Prova.java`
- [ ] `PlanoSemanal.java`
- [ ] `PlanoTreino.java`
- [ ] `PlanoMetaDados.java`
- [ ] `MetricasDiarias.java`

#### Mapeamento de Condições

| Entidade | Coluna tenant_id | Condição |
|----------|------------------|----------|
| Atleta | tenant_id (direto) | `tenant_id = :tenantId` |
| Usuario | tenant_id (direto) | `tenant_id = :tenantId` |
| TreinoPlanejado | tenant_id (direto) | `tenant_id = :tenantId` |
| TreinoRealizado | tenant_id (direto) | `tenant_id = :tenantId` |
| Prova | tenant_id (via atleta_id) | `EXISTS (SELECT 1 FROM tb_atleta a WHERE a.id = atleta_id AND a.tenant_id = :tenantId)` |

#### Critérios de Aceitação
- [ ] Todas as entidades têm `@FilterDef` e `@Filter`
- [ ] Condições SQL testadas manualmente no PostgreSQL
- [ ] Compilação sem erros
- [ ] Testes unitários passando

---

### 2.3 [ISSUE-008] 🟠 Criar TenantHibernateFilterInterceptor

**Prioridade:** 🟠 P2 - ALTA
**Estimativa:** 4 horas
**Responsável:** Backend Team
**Labels:** `enhancement`, `hibernate`, `architecture`

#### Descrição
Criar interceptor para ativar automaticamente o Hibernate Filter em todas as requisições.

#### Implementação
```java
package com.menthoros.multitenancy;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;

/**
 * Interceptor que ativa o Hibernate Filter "tenantFilter" automaticamente
 * em todas as requisições, usando o tenant_id do TenantContext.
 *
 * IMPORTANTE: Este interceptor deve ser executado DEPOIS do JwtTenantFilter,
 * que é responsável por popular o TenantContext.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantHibernateFilterInterceptor implements HandlerInterceptor {

    private final EntityManager entityManager;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {

        UUID tenantId = TenantContext.getTenantId();

        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);

            // Ativar filtro Hibernate
            session.enableFilter("tenantFilter")
                   .setParameter("tenantId", tenantId);

            log.debug("Hibernate Filter 'tenantFilter' ativado para tenant: {}", tenantId);
        } else {
            log.warn("TenantContext vazio - Hibernate Filter NÃO ativado");
        }

        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {

        // Limpar filtro após request (por segurança)
        try {
            Session session = entityManager.unwrap(Session.class);
            session.disableFilter("tenantFilter");
            log.debug("Hibernate Filter 'tenantFilter' desativado");
        } catch (Exception e) {
            log.error("Erro ao desativar Hibernate Filter", e);
        }
    }
}
```

#### Arquivos a Criar
- `/src/main/java/com/menthoros/multitenancy/TenantHibernateFilterInterceptor.java`

#### Arquivos a Atualizar
- `/src/main/java/com/menthoros/config/WebMvcConfig.java` (registrar interceptor)

#### Configuração WebMvc
```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private TenantHibernateFilterInterceptor tenantHibernateFilterInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantHibernateFilterInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/public/**", "/actuator/**");
    }
}
```

#### Critérios de Aceitação
- [ ] Interceptor criado e funcional
- [ ] Registrado corretamente no WebMvcConfig
- [ ] Executa DEPOIS do JwtTenantFilter
- [ ] Ativa filtro apenas se TenantContext tem tenant
- [ ] Desativa filtro após request
- [ ] Logs de debug implementados
- [ ] Testes de integração passando

#### Testes de Verificação
```java
@Test
public void deveAtivarHibernateFilterAutomaticamente() {
    UUID tenantId = criarTenant("Assessoria A");
    criarAtleta("João", tenantId);
    criarAtleta("Maria", criarTenant("Assessoria B"));

    // Simular request com tenant A
    TenantContext.setTenantId(tenantId);

    // Query SEM filtro manual - Hibernate Filter deve filtrar automaticamente
    List<Atleta> atletas = entityManager
        .createQuery("SELECT a FROM Atleta a", Atleta.class)
        .getResultList();

    // Deve retornar apenas atleta do tenant A
    assertEquals(1, atletas.size());
    assertEquals("João", atletas.get(0).getNome());
}
```

---

### 2.4 [ISSUE-009] 🟠 Atualizar Repositories para Remover Filtros Manuais

**Prioridade:** 🟠 P3 - MÉDIA
**Estimativa:** 3 horas
**Responsável:** Backend Team
**Labels:** `refactor`, `cleanup`

#### Descrição
Com Hibernate Filters ativados, os repositories não precisam mais filtrar manualmente por tenant_id. Simplificar queries.

#### Antes (com filtro manual)
```java
@Query("SELECT u FROM Usuario u WHERE u.assessoria.id = :tenantId AND u.ativo = true")
List<Usuario> findByTenantIdAndAtivoTrue(@Param("tenantId") UUID tenantId);
```

#### Depois (Hibernate Filter automático)
```java
// Filtro por tenant acontece automaticamente!
@Query("SELECT u FROM Usuario u WHERE u.ativo = true")
List<Usuario> findByAtivoTrue();

// OU simplesmente usar métodos do JpaRepository:
List<Usuario> findByAtivoTrue();
```

#### Estratégia de Refatoração
1. **NÃO deletar queries antigas imediatamente** (manter como fallback)
2. Criar novos métodos sem filtro manual
3. Adicionar testes de integração
4. Migrar código para usar novos métodos
5. Após 1-2 sprints estáveis, deletar métodos antigos

#### Exemplo de Migração
```java
// FASE 1: Manter ambos os métodos
@Query("SELECT u FROM Usuario u WHERE u.assessoria.id = :tenantId AND u.ativo = true")
@Deprecated(since = "v2.0", forRemoval = true)
List<Usuario> findByTenantIdAndAtivoTrue_OLD(@Param("tenantId") UUID tenantId);

List<Usuario> findByAtivoTrue(); // Novo método

// FASE 2 (após validação): Deletar método antigo
```

#### Critérios de Aceitação
- [ ] Novos métodos criados sem filtro manual
- [ ] Testes comparando resultado dos 2 métodos (devem ser iguais)
- [ ] Código migrado para usar novos métodos
- [ ] Métodos antigos marcados como @Deprecated
- [ ] Plano de remoção definido

---

## 🤖 SPRINT 3: ENTITY LISTENER (MÉDIA PRIORIDADE)
**Objetivo:** Automatizar configuração de tenant ao criar entidades
**Prazo:** 1 dia
**Prioridade:** 🟡 MÉDIA

### 3.1 [ISSUE-010] 🟡 Criar TenantEntityListener

**Prioridade:** 🟡 P4 - MÉDIA
**Estimativa:** 3 horas
**Responsável:** Backend Team
**Labels:** `enhancement`, `automation`

#### Descrição
Criar EntityListener para setar automaticamente `tenant_id` ao persistir novas entidades, evitando esquecimento manual.

#### Implementação
```java
package com.menthoros.multitenancy;

import com.menthoros.entity.TenantAware;
import com.menthoros.entity.Assessoria;
import jakarta.persistence.PrePersist;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * EntityListener que configura automaticamente o tenant_id em entidades
 * TenantAware antes de persisti-las no banco.
 *
 * IMPORTANTE: Este listener previne erros humanos ao esquecer de
 * setar o tenant, mas NÃO substitui validações de segurança!
 */
@Slf4j
@Component
public class TenantEntityListener {

    @PrePersist
    public void setTenantOnCreate(Object entity) {
        if (!(entity instanceof TenantAware)) {
            return; // Não é entidade multi-tenant
        }

        TenantAware tenantEntity = (TenantAware) entity;

        // Se já tem tenant configurado, não sobrescrever
        if (tenantEntity.getAssessoria() != null) {
            log.debug("Entidade {} já tem tenant configurado: {}",
                entity.getClass().getSimpleName(),
                tenantEntity.getAssessoria().getId());
            return;
        }

        // Obter tenant do contexto
        UUID tenantId = TenantContext.getTenantId();

        if (tenantId == null) {
            String error = String.format(
                "ERRO CRÍTICO: Tentativa de persistir %s sem tenant_id no TenantContext!",
                entity.getClass().getSimpleName());
            log.error(error);
            throw new IllegalStateException(error);
        }

        // Configurar tenant
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        tenantEntity.setAssessoria(assessoria);

        log.info("Tenant {} configurado automaticamente em {}",
            tenantId, entity.getClass().getSimpleName());
    }
}
```

#### Arquivos a Criar
- `/src/main/java/com/menthoros/multitenancy/TenantEntityListener.java`

#### Arquivos a Atualizar
Adicionar `@EntityListeners` em todas as entidades TenantAware:
```java
@Entity
@EntityListeners(TenantEntityListener.class)
public class Atleta implements TenantAware {
    // ...
}
```

#### Critérios de Aceitação
- [ ] Listener criado e funcional
- [ ] Adicionado em todas as entidades TenantAware
- [ ] Não sobrescreve tenant se já configurado
- [ ] Lança exceção se TenantContext vazio
- [ ] Logs informativos implementados
- [ ] Testes unitários criados

#### Testes
```java
@Test
public void deveConfigurarTenantAutomaticamente() {
    UUID tenantId = criarTenant("Assessoria A");
    TenantContext.setTenantId(tenantId);

    Atleta atleta = new Atleta();
    atleta.setNome("João");
    // NÃO setar assessoria manualmente

    Atleta saved = atletaRepository.save(atleta);

    // EntityListener deve ter configurado automaticamente
    assertNotNull(saved.getAssessoria());
    assertEquals(tenantId, saved.getAssessoria().getId());
}

@Test
public void deveLancarExcecaoSeContextoVazio() {
    TenantContext.clear(); // Simular contexto vazio

    Atleta atleta = new Atleta();
    atleta.setNome("João");

    assertThrows(IllegalStateException.class, () -> {
        atletaRepository.save(atleta);
    });
}
```

---

### 3.2 [ISSUE-011] 🟡 Adicionar Validação de Tenant em Update/Delete

**Prioridade:** 🟡 P4 - MÉDIA
**Estimativa:** 2 horas
**Responsável:** Backend Team
**Labels:** `enhancement`, `security`

#### Descrição
Adicionar validação no EntityListener para impedir que entidades sejam atualizadas/deletadas se não pertencerem ao tenant atual.

#### Implementação
```java
@Component
public class TenantEntityListener {

    // ... método setTenantOnCreate ...

    @PreUpdate
    @PreRemove
    public void validateTenantOnModify(Object entity) {
        if (!(entity instanceof TenantAware)) {
            return;
        }

        TenantAware tenantEntity = (TenantAware) entity;
        UUID entityTenantId = tenantEntity.getAssessoria().getId();
        UUID currentTenantId = TenantContext.getTenantId();

        if (!entityTenantId.equals(currentTenantId)) {
            String error = String.format(
                "VIOLAÇÃO DE SEGURANÇA: Tentativa de modificar %s (tenant: %s) usando contexto tenant: %s",
                entity.getClass().getSimpleName(), entityTenantId, currentTenantId);
            log.error(error);
            throw new AccessDeniedException(error);
        }
    }
}
```

#### Critérios de Aceitação
- [ ] Validação implementada em @PreUpdate e @PreRemove
- [ ] Lança AccessDeniedException se tenants diferentes
- [ ] Logs de segurança implementados
- [ ] Testes de segurança criados

---

## 🧪 SPRINT 4: TESTES (ALTA PRIORIDADE)
**Objetivo:** Criar suite completa de testes de isolamento
**Prazo:** 2-3 dias
**Prioridade:** 🟠 ALTA

### 4.1 [ISSUE-012] 🟠 Criar Testes de Isolamento de Dados

**Prioridade:** 🟠 P2 - ALTA
**Estimativa:** 6 horas
**Responsável:** QA + Backend Team
**Labels:** `testing`, `security`

#### Descrição
Criar suite de testes automatizados para garantir que dados não vazam entre tenants.

#### Estrutura de Testes
```
src/test/java/com/menthoros/multitenancy/
├── isolation/
│   ├── AtletaIsolationTest.java
│   ├── TreinoIsolationTest.java
│   ├── UsuarioIsolationTest.java
│   └── ...
├── security/
│   ├── TenantAccessControlTest.java
│   ├── CrossTenantAttackTest.java
│   └── ...
└── integration/
    ├── MultiTenancyIntegrationTest.java
    └── HibernateFilterTest.java
```

#### Template de Teste de Isolamento
```java
@SpringBootTest
@Transactional
@Rollback
public class AtletaIsolationTest {

    @Autowired
    private AtletaRepository atletaRepository;

    @Autowired
    private AssessoriaRepository assessoriaRepository;

    private Assessoria tenantA;
    private Assessoria tenantB;

    @BeforeEach
    public void setup() {
        tenantA = criarAssessoria("Assessoria A", "assessoria-a.com");
        tenantB = criarAssessoria("Assessoria B", "assessoria-b.com");
    }

    @Test
    @DisplayName("Deve retornar apenas atletas do tenant A quando contexto = tenant A")
    public void deveRetornarApenasAtletasDoTenantA() {
        // Arrange
        Atleta joao = criarAtleta("João", tenantA);
        Atleta maria = criarAtleta("Maria", tenantB);

        // Act
        TenantContext.setTenantId(tenantA.getId());
        List<Atleta> atletas = atletaRepository.findAll();

        // Assert
        assertEquals(1, atletas.size());
        assertEquals("João", atletas.get(0).getNome());
        assertEquals(tenantA.getId(), atletas.get(0).getAssessoria().getId());
    }

    @Test
    @DisplayName("Não deve permitir buscar atleta de outro tenant por ID")
    public void naoDevePermitirBuscarAtletaDeOutroTenantPorId() {
        // Arrange
        Atleta maria = criarAtleta("Maria", tenantB);

        // Act - Tentar buscar com contexto do tenant A
        TenantContext.setTenantId(tenantA.getId());
        Optional<Atleta> result = atletaRepository.findById(maria.getId());

        // Assert
        assertTrue(result.isEmpty(), "Não deve encontrar atleta de outro tenant");
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar atualizar atleta de outro tenant")
    public void deveLancarExcecaoAoTentarAtualizarAtletaDeOutroTenant() {
        // Arrange
        Atleta maria = criarAtleta("Maria", tenantB);

        // Act & Assert
        TenantContext.setTenantId(tenantA.getId());
        maria.setNome("Maria Silva");

        assertThrows(AccessDeniedException.class, () -> {
            atletaRepository.save(maria);
        });
    }

    // Helper methods
    private Assessoria criarAssessoria(String nome, String dominio) {
        Assessoria assessoria = Assessoria.builder()
            .nome(nome)
            .dominio(dominio)
            .plano(PlanoAssessoria.BASIC)
            .ativo(true)
            .build();
        return assessoriaRepository.save(assessoria);
    }

    private Atleta criarAtleta(String nome, Assessoria assessoria) {
        Atleta atleta = Atleta.builder()
            .nome(nome)
            .assessoria(assessoria)
            .objetivo("Correr 10k")
            .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
            .ativo(AtletaStatus.ATIVO)
            .build();
        return atletaRepository.save(atleta);
    }
}
```

#### Cenários de Teste Obrigatórios
Por entidade (Atleta, Usuario, Treino, etc.):
- [ ] `findAll()` retorna apenas dados do tenant atual
- [ ] `findById()` não encontra recursos de outros tenants
- [ ] `save()` atribui tenant automaticamente em CREATE
- [ ] `save()` impede UPDATE de recursos de outros tenants
- [ ] `delete()` impede DELETE de recursos de outros tenants
- [ ] Queries customizadas respeitam isolamento
- [ ] Relacionamentos (@ManyToOne, @OneToMany) respeitam isolamento

#### Critérios de Aceitação
- [ ] Mínimo 5 testes por entidade principal
- [ ] 100% dos cenários obrigatórios cobertos
- [ ] Todos os testes passando
- [ ] Coverage mínimo de 80% nas classes de multi-tenancy
- [ ] Relatório de testes gerado

---

### 4.2 [ISSUE-013] 🟠 Criar Testes de Segurança (Penetration Testing)

**Prioridade:** 🟠 P2 - ALTA
**Estimativa:** 4 horas
**Responsável:** Security + Backend Team
**Labels:** `testing`, `security`, `pentest`

#### Descrição
Criar testes que simulam ataques de usuários mal-intencionados tentando acessar dados de outros tenants.

#### Cenários de Ataque a Testar

##### 1. Tenant ID Guessing
```java
@Test
@DisplayName("Não deve permitir acessar recurso adivinhando UUID de outro tenant")
public void naoDevePermitirTenantIdGuessing() {
    // Simular: Usuário do Tenant A descobriu UUID de atleta do Tenant B
    UUID atletaDoTenantB = criarAtletaEmOutroTenant();

    autenticarComoUsuarioDoTenantA();

    assertThrows(AccessDeniedException.class, () -> {
        atletaService.getAtletaById(atletaDoTenantB);
    });
}
```

##### 2. JWT Tampering
```java
@Test
@DisplayName("Deve rejeitar JWT com tenant_id manipulado")
public void deveRejeitarJwtComTenantIdManipulado() {
    String jwtValido = gerarJwtParaTenantA();
    String jwtManipulado = trocarTenantIdNoJwt(jwtValido, tenantB.getId());

    ResponseEntity<?> response = fazerRequestComJwt(jwtManipulado);

    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
}
```

##### 3. SQL Injection via Tenant Context
```java
@Test
@DisplayName("Não deve permitir SQL injection via tenant_id malicioso")
public void naoDevePermitirSqlInjectionViaTenantId() {
    String tenantIdMalicioso = "' OR '1'='1"; // Tentativa de SQL injection

    assertThrows(IllegalArgumentException.class, () -> {
        TenantContext.setTenantId(UUID.fromString(tenantIdMalicioso));
    });
}
```

##### 4. Race Condition Attack
```java
@Test
@DisplayName("Não deve permitir race condition entre requests de tenants diferentes")
public void naoDevePermitirRaceConditionEntreRequests() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);

    // Thread 1: Request do Tenant A
    Future<List<Atleta>> futureA = executor.submit(() -> {
        TenantContext.setTenantId(tenantA.getId());
        return atletaRepository.findAll();
    });

    // Thread 2: Request do Tenant B (simultâneo)
    Future<List<Atleta>> futureB = executor.submit(() -> {
        TenantContext.setTenantId(tenantB.getId());
        return atletaRepository.findAll();
    });

    List<Atleta> atletasA = futureA.get();
    List<Atleta> atletasB = futureB.get();

    // Verificar que não houve cross-contamination
    assertTrue(atletasA.stream().allMatch(a -> a.getAssessoria().getId().equals(tenantA.getId())));
    assertTrue(atletasB.stream().allMatch(a -> a.getAssessoria().getId().equals(tenantB.getId())));
}
```

#### Critérios de Aceitação
- [ ] Todos os 4 cenários de ataque testados
- [ ] Sistema defende contra ataques com sucesso
- [ ] Logs de segurança gerados para tentativas de ataque
- [ ] Relatório de pentest gerado

---

### 4.3 [ISSUE-014] 🟡 Criar Testes de Performance de Multi-Tenancy

**Prioridade:** 🟡 P4 - MÉDIA
**Estimativa:** 4 horas
**Responsável:** Performance Team
**Labels:** `testing`, `performance`

#### Descrição
Validar que Hibernate Filters não degradam significativamente a performance.

#### Métricas a Medir
1. **Tempo de query com filtro manual vs Hibernate Filter**
2. **Overhead de ativação do filtro por request**
3. **Memory usage com múltiplos tenants**
4. **Throughput máximo do sistema**

#### Implementação
```java
@SpringBootTest
public class MultiTenancyPerformanceTest {

    @Test
    @DisplayName("Hibernate Filter não deve degradar performance em mais de 5%")
    public void hibernateFilterNaoDeveDegra darPerformance() {
        // Setup: 10 tenants, 100 atletas cada
        criarDadosDePerformance();

        // Teste 1: Query manual com filtro
        long startManual = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            List<Atleta> atletas = atletaRepository.findByTenantIdAndAtivoTrue(tenantId);
        }
        long tempoManual = System.currentTimeMillis() - startManual;

        // Teste 2: Query com Hibernate Filter
        long startHibernate = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            TenantContext.setTenantId(tenantId);
            List<Atleta> atletas = atletaRepository.findByAtivoTrue();
        }
        long tempoHibernate = System.currentTimeMillis() - startHibernate;

        // Assert: Diferença deve ser < 5%
        double overhead = ((double) tempoHibernate - tempoManual) / tempoManual * 100;
        assertTrue(overhead < 5.0, String.format("Overhead: %.2f%%", overhead));
    }
}
```

#### Critérios de Aceitação
- [ ] Performance degradation < 5%
- [ ] Memory overhead < 10%
- [ ] Throughput mantém 95% do baseline
- [ ] Relatório de performance gerado

---

## 📚 SPRINT 5: DOCUMENTAÇÃO E MELHORIAS (BAIXA PRIORIDADE)
**Objetivo:** Documentar, criar ferramentas de gestão e melhorias UX
**Prazo:** 3-5 dias
**Prioridade:** 🟢 BAIXA

### 5.1 [ISSUE-015] 🟢 Implementar KeycloakAdminService

**Prioridade:** 🟢 P5 - BAIXA
**Estimativa:** 6 horas
**Responsável:** Backend Team
**Labels:** `feature`, `keycloak`

#### Descrição
Implementar service para gerenciar assessorias (tenants) no Keycloak Admin API.

#### Funcionalidades
- Criar Group no Keycloak ao criar Assessoria
- Adicionar usuários ao Group
- Remover usuários do Group
- Atualizar Group (nome, atributos)
- Sincronização bidirecional

#### Arquivos a Criar
- `/src/main/java/com/menthoros/services/KeycloakAdminService.java`
- `/src/main/java/com/menthoros/config/KeycloakAdminConfig.java`

---

### 5.2 [ISSUE-016] 🟢 Criar AssessoriaController (CRUD)

**Prioridade:** 🟢 P5 - BAIXA
**Estimativa:** 4 horas
**Responsável:** Backend Team
**Labels:** `feature`, `api`

#### Endpoints
- `POST /api/assessorias` - Criar nova assessoria
- `GET /api/assessorias/{id}` - Buscar assessoria
- `PUT /api/assessorias/{id}` - Atualizar assessoria
- `DELETE /api/assessorias/{id}` - Desativar assessoria
- `GET /api/assessorias/me` - Dados da assessoria do usuário logado

---

### 5.3 [ISSUE-017] 🟢 Criar Dashboard de Multi-Tenancy

**Prioridade:** 🟢 P6 - BAIXA
**Estimativa:** 8 horas
**Responsável:** Frontend Team
**Labels:** `feature`, `ui`

#### Funcionalidades
- Lista de assessorias (apenas para ADMIN)
- Estatísticas por assessoria (atletas, treinos, usuários)
- Gestão de usuários da assessoria
- Feature flags por assessoria

---

## 📋 CHECKLIST DE VALIDAÇÃO FINAL

Antes de considerar multi-tenancy completo:

### Segurança
- [ ] Todos os repositories filtram por tenant
- [ ] Hibernate Filters ativados e testados
- [ ] Testes de isolamento passando (100%)
- [ ] Pentest executado e vulnerabilidades corrigidas
- [ ] Logs de segurança implementados
- [ ] Code review de segurança aprovado

### Funcionalidade
- [ ] CRUD de assessorias funcionando
- [ ] Sincronização com Keycloak bidirecional
- [ ] Feature flags funcionando
- [ ] Limites de plano sendo validados

### Performance
- [ ] Performance degradation < 5%
- [ ] Load testing executado
- [ ] Queries otimizadas (EXPLAIN ANALYZE)

### Documentação
- [ ] README atualizado
- [ ] Guia de desenvolvimento atualizado
- [ ] Swagger/OpenAPI documentado
- [ ] Diagramas de arquitetura atualizados

### Testes
- [ ] Coverage > 80%
- [ ] Testes de integração passando
- [ ] Testes E2E passando
- [ ] Testes de regressão passando

---

## 🔗 REFERÊNCIAS

- [MULTI_TENANCY_IMPLEMENTATION_ROADMAP.md](./MULTI_TENANCY_IMPLEMENTATION_ROADMAP.md)
- [MULTI_TENANCY_INTEGRATION_GUIDE.md](./MULTI_TENANCY_INTEGRATION_GUIDE.md)
- [Hibernate Filters Documentation](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#filters)
- [Spring Security Multi-Tenancy](https://docs.spring.io/spring-security/reference/servlet/authorization/architecture.html)

---

## 📞 CONTATO

**Tech Lead:** [Nome]
**Security Team:** [Email]
**Dúvidas:** Abrir issue no GitHub com label `question`

---

**Última Atualização:** 2025-11-04
**Versão do Documento:** 1.0
