# add-assessoria-onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar o cadastro de assessoria (tenant) com provisionamento no Keycloak via Organizations, a role `ATLETA`, o vínculo `Usuario`↔`Atleta` e o onboarding de atleta por convite — destravando os shells de atleta e coach.

**Architecture:** Camadas Spring Boot (controller → service → repository) com isolamento multi-tenant via `TenantContext`. A integração com o Keycloak Admin Client fica atrás de uma interface-gateway (`KeycloakOrganizationGateway`), permitindo TDD do service com a dependência mockada; o adapter real (admin-client v25.0.3, já no `pom.xml`) e a configuração do realm são infra, verificadas por boot/integração. O vínculo `Usuario`↔`Atleta` é uma coluna FK em `tb_atleta` (`usuario_id`, nullable até o aceite do convite).

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Security OAuth2 Resource Server, Spring Data JPA, PostgreSQL + Flyway, Keycloak Admin Client 25.0.3, MapStruct 1.6.3 (não usado aqui — mappers desta change são `@Component` com null-check), JUnit 5 + Mockito + AssertJ.

**Escopo e fases:** Esta change cobre múltiplos subsistemas. As Fases 1–4 são código TDD-ável. As Fases 5–6 são **infra de Keycloak e runbook de migração** (config de realm + integração viva do admin-client), verificadas por boot/integração/manual — não por testes unitários. O usuário optou por manter #0 inteira em um único plano.

**Branch & convenções:** trabalhar em `apps/menthoros-backend`. Seguir `apps/menthoros-backend/CLAUDE.md` (Controller/DTO/Service/Mapper/Migration Standards). Commits Conventional Commits em PT-BR, sem referência ao modelo. Base package: `br.com.menthoros.backend`.

---

## File Structure

**Criar:**
- `entity/` — modificar `Atleta.java` (campo `usuario`), `Assessoria.java` (campo `keycloakOrganizationId`).
- `enums/UserRole.java` — adicionar `ATLETA`.
- `dto/input/AssessoriaInputDto.java`, `dto/output/AssessoriaOutputDto.java` — records.
- `mapper/AssessoriaMapper.java` — `@Component` com null-check.
- `services/KeycloakOrganizationGateway.java` (interface) + `services/impl/KeycloakOrganizationGatewayImpl.java` (adapter admin-client, infra).
- `services/AssessoriaService.java` (interface) + `services/impl/AssessoriaServiceImpl.java`.
- `controller/AssessoriaController.java` — `POST /api/admin/assessorias`.
- `exception/DominioJaExisteException.java` (ou reusar `DuplicateResourceException`).
- `services/AtletaService.java` / `services/impl/AtletaServiceImpl.java` — método `gerarConvite`.
- `controller/AtletaController.java` — `POST /api/v1/atletas/{id}/convite`.
- `services/impl/UsuarioSyncServiceImpl.java` — vínculo ATLETA↔Atleta.
- `repository/AssessoriaRepository.java` — `existsByDominio`; `repository/AtletaRepository.java` — `findByEmailAndAssessoria_Id`, `findByUsuario_IdAndAssessoria_Id`.
- `db/migration/V33__Add_keycloak_organization_id_to_assessoria.sql`, `db/migration/V34__Add_usuario_link_to_atleta.sql`.
- Testes em `src/test/java/.../` espelhando os pacotes.

**Config (Fase 5):** realm `menthoros-app` (Organizations + attribute mapper + client role `ATLETA`), `security/JwtTenantFilter.java` (validar claim de Organizations).

---

## Setup (uma vez, antes de qualquer código)

- [ ] **S1: Atualizar develop e criar branch**

Run:
```bash
cd /Users/leandrosilva/dev/workspace/menthoros-workspace/apps/menthoros-backend
git checkout develop && git pull origin develop
git checkout -b feature/add-assessoria-onboarding
```
Expected: branch `feature/add-assessoria-onboarding` criada a partir de `develop` atualizado.

- [ ] **S2: Baseline verde**

Run: `./mvnw clean test`
Expected: BUILD SUCCESS (suíte atual passando antes de começar).

---

## Fase 1 — Fundação de modelo (role, migrations, entidades)

### Task 1: Adicionar role ATLETA ao enum e ao mapeamento de roles

**Files:**
- Modify: `src/main/java/br/com/menthoros/backend/enums/UserRole.java`
- Modify: `src/main/java/br/com/menthoros/backend/services/impl/UsuarioSyncServiceImpl.java:136-147` (`mapToUserRole`)
- Test: `src/test/java/br/com/menthoros/backend/services/impl/UsuarioSyncServiceImplRoleTest.java`

- [ ] **Step 1: Escrever o teste do mapeamento de role (falha)**

Como `mapToUserRole` é privado, testar via método público `syncUsuarioFromJwt`. Criar o teste com um JWT mock contendo `realm_access.roles=["ATLETA"]` e verificar `usuario.getRole() == UserRole.ATLETA`.

```java
package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioSyncServiceImplRoleTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AssessoriaRepository assessoriaRepository;
    @Mock private AtletaRepository atletaRepository;

    @InjectMocks private UsuarioSyncServiceImpl service;

    private Jwt jwtComRole(String role) {
        return Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("email", "atleta@teste.com")
                .claim("given_name", "Ana")
                .claim("family_name", "Atleta")
                .claim("email_verified", true)
                .claim("realm_access", Map.of("roles", List.of(role)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    @Test
    void deveMapearRoleAtleta() {
        UUID tenantId = UUID.randomUUID();
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        when(assessoriaRepository.getReferenceById(tenantId)).thenReturn(assessoria);
        when(usuarioRepository.findByKeycloakId(any())).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario usuario = service.syncUsuarioFromJwt(jwtComRole("ATLETA"), tenantId);

        assertThat(usuario.getRole()).isEqualTo(UserRole.ATLETA);
    }
}
```

> Nota: ajustar os stubs (`assessoriaRepository.getReferenceById` / `createNewUsuario`) ao que `UsuarioSyncServiceImpl.createNewUsuario` realmente chama — ler o método antes e alinhar os mocks. Se `createNewUsuario` usar `assessoriaRepository.findById`, trocar o stub para `findById(tenantId)).thenReturn(Optional.of(assessoria))`.

- [ ] **Step 2: Rodar o teste e ver falhar**

Run: `./mvnw -Dtest=UsuarioSyncServiceImplRoleTest test`
Expected: FAIL — `getRole()` retorna `VISUALIZADOR` (ATLETA cai no default).

- [ ] **Step 3: Adicionar ATLETA ao enum**

Em `enums/UserRole.java`, adicionar o valor após `VISUALIZADOR`:
```java
    VISUALIZADOR,

    /**
     * Atleta
     * - Acessa apenas os próprios dados (perfil, plano, progresso, mensagens).
     * - Não gerencia usuários, atletas ou configurações.
     */
    ATLETA
}
```

- [ ] **Step 4: Mapear ATLETA em `mapToUserRole`**

Em `UsuarioSyncServiceImpl.mapToUserRole`, inserir antes do `else` final (mantendo prioridade ADMIN > TECNICO > ATLETA > VISUALIZADOR):
```java
        } else if (roles.contains("ATLETA")) {
            return UserRole.ATLETA;
        } else if (roles.contains("VISUALIZADOR")) {
```

- [ ] **Step 5: Rodar o teste e ver passar**

Run: `./mvnw -Dtest=UsuarioSyncServiceImplRoleTest test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/menthoros/backend/enums/UserRole.java \
        src/main/java/br/com/menthoros/backend/services/impl/UsuarioSyncServiceImpl.java \
        src/test/java/br/com/menthoros/backend/services/impl/UsuarioSyncServiceImplRoleTest.java
git commit -m "feat(auth): adicionar role ATLETA e mapeá-la no sync de usuário"
```

---

### Task 2: Migration V33 — keycloak_organization_id em tb_assessoria + campo na entidade

**Files:**
- Create: `src/main/resources/db/migration/V33__Add_keycloak_organization_id_to_assessoria.sql`
- Modify: `src/main/java/br/com/menthoros/backend/entity/Assessoria.java`

- [ ] **Step 1: Criar a migration V33**

```sql
-- =====================================================================
-- V33: Adiciona keycloak_organization_id em tb_assessoria para a
--      modelagem de tenant via Keycloak Organizations (substitui Groups)
-- =====================================================================

ALTER TABLE tb_assessoria
    ADD COLUMN IF NOT EXISTS keycloak_organization_id VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uk_assessoria_keycloak_org
    ON tb_assessoria(keycloak_organization_id)
    WHERE keycloak_organization_id IS NOT NULL;

DO $$
BEGIN
    RAISE NOTICE '✅ V33 - keycloak_organization_id adicionado em tb_assessoria com sucesso';
END$$;
```

- [ ] **Step 2: Adicionar o campo na entidade Assessoria**

Após `keycloakRealm` em `entity/Assessoria.java`:
```java
    @Column(name = "keycloak_organization_id", unique = true, length = 100)
    private String keycloakOrganizationId;
```

- [ ] **Step 3: Compilar e validar a migration via boot de teste**

Run: `./mvnw -Dtest=*ApplicationTests test` (ou o teste de contexto existente que sobe Flyway via Testcontainers)
Expected: contexto sobe; Flyway aplica V33 sem erro. Se não houver teste de contexto, rodar `./mvnw clean compile` e validar o SQL na subida local depois.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/migration/V33__Add_keycloak_organization_id_to_assessoria.sql \
        src/main/java/br/com/menthoros/backend/entity/Assessoria.java
git commit -m "chore(db): V33 adicionar keycloak_organization_id em tb_assessoria"
```

---

### Task 3: Migration V34 — usuario_id em tb_atleta + vínculo na entidade

**Files:**
- Create: `src/main/resources/db/migration/V34__Add_usuario_link_to_atleta.sql`
- Modify: `src/main/java/br/com/menthoros/backend/entity/Atleta.java`

- [ ] **Step 1: Criar a migration V34**

```sql
-- =====================================================================
-- V34: Adiciona usuario_id em tb_atleta — vínculo Usuario<->Atleta.
--      Nullable: o Atleta é cadastrado pelo coach antes de existir a
--      conta; o vínculo é efetivado no aceite do convite.
-- =====================================================================

ALTER TABLE tb_atleta
    ADD COLUMN IF NOT EXISTS usuario_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_atleta_usuario'
    ) THEN
        ALTER TABLE tb_atleta
            ADD CONSTRAINT fk_atleta_usuario
            FOREIGN KEY (usuario_id) REFERENCES tb_usuario(id) ON DELETE SET NULL;
    END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_atleta_usuario ON tb_atleta(usuario_id);

DO $$
BEGIN
    RAISE NOTICE '✅ V34 - usuario_id (vínculo Usuario<->Atleta) adicionado em tb_atleta';
END$$;
```

- [ ] **Step 2: Adicionar o ManyToOne na entidade Atleta**

Junto do mapeamento de `assessoria` em `entity/Atleta.java` (lazy, nullable):
```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
```
(Import `br.com.menthoros.backend.entity.Usuario` se necessário — mesmo pacote, não precisa.)

- [ ] **Step 3: Compilar**

Run: `./mvnw clean compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/migration/V34__Add_usuario_link_to_atleta.sql \
        src/main/java/br/com/menthoros/backend/entity/Atleta.java
git commit -m "chore(db): V34 adicionar vínculo Usuario<->Atleta (usuario_id) em tb_atleta"
```

---

## Fase 2 — Gateway do Keycloak (seam para TDD)

### Task 4: Definir a interface KeycloakOrganizationGateway

**Files:**
- Create: `src/main/java/br/com/menthoros/backend/services/KeycloakOrganizationGateway.java`

- [ ] **Step 1: Criar a interface (sem teste — é contrato)**

```java
package br.com.menthoros.backend.services;

import java.util.UUID;

/**
 * Abstração da integração com o Keycloak para Organizations e convites.
 * O service de domínio depende desta interface (mockável em testes);
 * o adapter real usa o Keycloak Admin Client (infra).
 */
public interface KeycloakOrganizationGateway {

    /**
     * Cria uma Organization no realm para a assessoria e injeta o atributo tenant_id.
     *
     * @param nome      nome da assessoria
     * @param dominio   domínio (alias) da Organization
     * @param tenantId  id do tenant a projetar no claim tenant_id
     * @return o id da Organization criada no Keycloak
     */
    String criarOrganization(String nome, String dominio, UUID tenantId);

    /**
     * Gera/reenvia um convite de atleta vinculado à Organization da assessoria.
     *
     * @param keycloakOrganizationId id da Organization da assessoria
     * @param email                  email do atleta convidado
     * @param atletaId               id do Atleta (para correlação no aceite)
     */
    void enviarConviteAtleta(String keycloakOrganizationId, String email, UUID atletaId);
}
```

- [ ] **Step 2: Compilar**

Run: `./mvnw clean compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/br/com/menthoros/backend/services/KeycloakOrganizationGateway.java
git commit -m "feat(keycloak): definir interface KeycloakOrganizationGateway"
```

> O **adapter real** (`KeycloakOrganizationGatewayImpl` com `KeycloakBuilder`/`OrganizationsResource`) é implementado na Fase 5 (infra). Até lá, os services são testados com o gateway mockado.

---

## Fase 3 — Cadastro de assessoria

### Task 5: DTOs de assessoria

**Files:**
- Create: `src/main/java/br/com/menthoros/backend/dto/input/AssessoriaInputDto.java`
- Create: `src/main/java/br/com/menthoros/backend/dto/output/AssessoriaOutputDto.java`

- [ ] **Step 1: Criar o AssessoriaInputDto (record + Bean Validation)**

```java
package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.enums.PlanoAssessoria;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados de entrada para cadastro de uma assessoria (tenant)")
public record AssessoriaInputDto(

        @Schema(description = "Nome da assessoria", example = "Corridas da Serra", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 200, message = "Nome deve ter no máximo 200 caracteres")
        String nome,

        @Schema(description = "Domínio único da assessoria", example = "corridasserra", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Domínio é obrigatório")
        @Size(max = 100, message = "Domínio deve ter no máximo 100 caracteres")
        String dominio,

        @Schema(description = "Plano contratado", example = "PRO", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Plano é obrigatório")
        PlanoAssessoria plano,

        @Schema(description = "Email de contato", example = "contato@corridasserra.com")
        @Size(max = 100)
        String emailContato,

        @Schema(description = "Limite de atletas", example = "100")
        @Positive(message = "maxAtletas deve ser positivo")
        Integer maxAtletas,

        @Schema(description = "Limite de técnicos", example = "10")
        @Positive(message = "maxTecnicos deve ser positivo")
        Integer maxTecnicos
) {}
```

- [ ] **Step 2: Criar o AssessoriaOutputDto (record + NON_NULL)**

```java
package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.PlanoAssessoria;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Dados de saída de uma assessoria")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssessoriaOutputDto(

        @Schema(description = "Identificador único da assessoria")
        UUID id,

        @Schema(description = "Nome da assessoria", example = "Corridas da Serra")
        String nome,

        @Schema(description = "Domínio único", example = "corridasserra")
        String dominio,

        @Schema(description = "Plano contratado", example = "PRO")
        PlanoAssessoria plano,

        @Schema(description = "Id da Organization no Keycloak")
        String keycloakOrganizationId,

        @Schema(description = "Limite de atletas", example = "100")
        Integer maxAtletas,

        @Schema(description = "Limite de técnicos", example = "10")
        Integer maxTecnicos,

        @Schema(description = "Indica se a assessoria está ativa", example = "true")
        boolean ativo,

        @Schema(description = "Data de criação")
        LocalDateTime createdAt
) {}
```

- [ ] **Step 3: Compilar**

Run: `./mvnw clean compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/br/com/menthoros/backend/dto/input/AssessoriaInputDto.java \
        src/main/java/br/com/menthoros/backend/dto/output/AssessoriaOutputDto.java
git commit -m "feat(assessoria): adicionar AssessoriaInputDto e AssessoriaOutputDto"
```

---

### Task 6: AssessoriaMapper (@Component, null-check)

**Files:**
- Create: `src/main/java/br/com/menthoros/backend/mapper/AssessoriaMapper.java`
- Test: `src/test/java/br/com/menthoros/backend/mapper/AssessoriaMapperTest.java`

- [ ] **Step 1: Escrever o teste do mapper (falha)**

```java
package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.input.AssessoriaInputDto;
import br.com.menthoros.backend.dto.output.AssessoriaOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssessoriaMapperTest {

    private final AssessoriaMapper mapper = new AssessoriaMapper();

    @Test
    void toEntityMapeiaCamposBasicos() {
        AssessoriaInputDto dto = new AssessoriaInputDto(
                "Corridas da Serra", "corridasserra", PlanoAssessoria.PRO,
                "contato@cs.com", 100, 10);

        Assessoria entity = mapper.toEntity(dto);

        assertThat(entity.getNome()).isEqualTo("Corridas da Serra");
        assertThat(entity.getDominio()).isEqualTo("corridasserra");
        assertThat(entity.getPlano()).isEqualTo(PlanoAssessoria.PRO);
        assertThat(entity.getMaxAtletas()).isEqualTo(100);
    }

    @Test
    void toEntityRejeitaNull() {
        assertThatThrownBy(() -> mapper.toEntity(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toOutputDtoMapeiaCampos() {
        Assessoria e = new Assessoria();
        e.setId(UUID.randomUUID());
        e.setNome("Corridas da Serra");
        e.setDominio("corridasserra");
        e.setPlano(PlanoAssessoria.PRO);
        e.setKeycloakOrganizationId("org-123");
        e.setMaxAtletas(100);
        e.setMaxTecnicos(10);
        e.setAtivo(true);
        e.setCreatedAt(LocalDateTime.now());

        AssessoriaOutputDto dto = mapper.toOutputDto(e);

        assertThat(dto.id()).isEqualTo(e.getId());
        assertThat(dto.keycloakOrganizationId()).isEqualTo("org-123");
        assertThat(dto.ativo()).isTrue();
    }

    @Test
    void toOutputDtoRejeitaNull() {
        assertThatThrownBy(() -> mapper.toOutputDto(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw -Dtest=AssessoriaMapperTest test`
Expected: FAIL — `AssessoriaMapper` não existe (erro de compilação).

- [ ] **Step 3: Implementar o mapper**

```java
package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.input.AssessoriaInputDto;
import br.com.menthoros.backend.dto.output.AssessoriaOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import org.springframework.stereotype.Component;

/**
 * Conversão entre AssessoriaInputDto/OutputDto e a entidade Assessoria.
 */
@Component
public class AssessoriaMapper {

    public Assessoria toEntity(AssessoriaInputDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("AssessoriaInputDto cannot be null");
        }
        Assessoria entity = new Assessoria();
        entity.setNome(dto.nome());
        entity.setDominio(dto.dominio());
        entity.setPlano(dto.plano());
        entity.setEmailContato(dto.emailContato());
        entity.setMaxAtletas(dto.maxAtletas());
        entity.setMaxTecnicos(dto.maxTecnicos());
        return entity;
    }

    public AssessoriaOutputDto toOutputDto(Assessoria entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Assessoria entity cannot be null");
        }
        return new AssessoriaOutputDto(
                entity.getId(),
                entity.getNome(),
                entity.getDominio(),
                entity.getPlano(),
                entity.getKeycloakOrganizationId(),
                entity.getMaxAtletas(),
                entity.getMaxTecnicos(),
                Boolean.TRUE.equals(entity.getAtivo()),
                entity.getCreatedAt()
        );
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./mvnw -Dtest=AssessoriaMapperTest test`
Expected: PASS (4 testes).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/menthoros/backend/mapper/AssessoriaMapper.java \
        src/test/java/br/com/menthoros/backend/mapper/AssessoriaMapperTest.java
git commit -m "feat(assessoria): adicionar AssessoriaMapper com null-check"
```

---

### Task 7: Repository — existsByDominio

**Files:**
- Modify: `src/main/java/br/com/menthoros/backend/repository/AssessoriaRepository.java`

- [ ] **Step 1: Adicionar os métodos de query (derivados — sem teste unitário)**

Adicionar ao `AssessoriaRepository`:
```java
    /**
     * Verifica se existe assessoria com o domínio informado.
     */
    boolean existsByDominio(String dominio);

    /**
     * Busca assessoria pelo domínio.
     */
    Optional<Assessoria> findByDominio(String dominio);
```

- [ ] **Step 2: Compilar**

Run: `./mvnw clean compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/br/com/menthoros/backend/repository/AssessoriaRepository.java
git commit -m "feat(assessoria): query existsByDominio/findByDominio no repository"
```

---

### Task 8: AssessoriaService — criarAssessoria (TDD com gateway mockado)

**Files:**
- Create: `src/main/java/br/com/menthoros/backend/services/AssessoriaService.java`
- Create: `src/main/java/br/com/menthoros/backend/services/impl/AssessoriaServiceImpl.java`
- Test: `src/test/java/br/com/menthoros/backend/services/impl/AssessoriaServiceImplTest.java`

- [ ] **Step 1: Escrever os testes do service (falha)**

```java
package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.AssessoriaInputDto;
import br.com.menthoros.backend.dto.output.AssessoriaOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.exception.DuplicateResourceException;
import br.com.menthoros.backend.mapper.AssessoriaMapper;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.services.KeycloakOrganizationGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssessoriaServiceImplTest {

    @Mock private AssessoriaRepository assessoriaRepository;
    @Mock private AssessoriaMapper assessoriaMapper;
    @Mock private KeycloakOrganizationGateway keycloakGateway;

    @InjectMocks private AssessoriaServiceImpl service;

    private AssessoriaInputDto inputValido() {
        return new AssessoriaInputDto("Corridas da Serra", "corridasserra",
                PlanoAssessoria.PRO, "contato@cs.com", 100, 10);
    }

    @Test
    void criaAssessoriaEOrganizationEPersisteOrgId() {
        AssessoriaInputDto input = inputValido();
        Assessoria nova = new Assessoria();
        nova.setNome(input.nome());
        nova.setDominio(input.dominio());

        when(assessoriaRepository.existsByDominio("corridasserra")).thenReturn(false);
        when(assessoriaMapper.toEntity(input)).thenReturn(nova);
        when(assessoriaRepository.save(any(Assessoria.class))).thenAnswer(inv -> {
            Assessoria a = inv.getArgument(0);
            if (a.getId() == null) a.setId(UUID.randomUUID());
            return a;
        });
        when(keycloakGateway.criarOrganization(eq("Corridas da Serra"), eq("corridasserra"), any(UUID.class)))
                .thenReturn("org-123");
        when(assessoriaMapper.toOutputDto(any(Assessoria.class))).thenAnswer(inv -> {
            Assessoria a = inv.getArgument(0);
            return new AssessoriaOutputDto(a.getId(), a.getNome(), a.getDominio(),
                    PlanoAssessoria.PRO, a.getKeycloakOrganizationId(), 100, 10, true, null);
        });

        AssessoriaOutputDto out = service.criarAssessoria(input);

        assertThat(out.keycloakOrganizationId()).isEqualTo("org-123");
        verify(keycloakGateway).criarOrganization(eq("Corridas da Serra"), eq("corridasserra"), any(UUID.class));
        // persistência em duas etapas: cria entidade e depois grava o org id
        verify(assessoriaRepository, times(2)).save(any(Assessoria.class));
        ArgumentCaptor<Assessoria> captor = ArgumentCaptor.forClass(Assessoria.class);
        verify(assessoriaRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getKeycloakOrganizationId()).isEqualTo("org-123");
    }

    @Test
    void rejeitaDominioDuplicado() {
        AssessoriaInputDto input = inputValido();
        when(assessoriaRepository.existsByDominio("corridasserra")).thenReturn(true);

        assertThatThrownBy(() -> service.criarAssessoria(input))
                .isInstanceOf(DuplicateResourceException.class);

        verify(keycloakGateway, never()).criarOrganization(any(), any(), any());
        verify(assessoriaRepository, never()).save(any());
    }
}
```

> Antes de implementar, confirmar o construtor de `DuplicateResourceException` (lido em `exception/DuplicateResourceException.java`). Se não aceitar `String`, usar a exceção que aceitar ou criar `DominioJaExisteException extends RuntimeException` + handler 409 no `GlobalExceptionHandler` (Task 8b).

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw -Dtest=AssessoriaServiceImplTest test`
Expected: FAIL — `AssessoriaService`/`AssessoriaServiceImpl` não existem.

- [ ] **Step 3: Criar a interface**

```java
package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.AssessoriaInputDto;
import br.com.menthoros.backend.dto.output.AssessoriaOutputDto;

public interface AssessoriaService {
    AssessoriaOutputDto criarAssessoria(AssessoriaInputDto input);
}
```

- [ ] **Step 4: Implementar o service**

```java
package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.AssessoriaInputDto;
import br.com.menthoros.backend.dto.output.AssessoriaOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.exception.DuplicateResourceException;
import br.com.menthoros.backend.mapper.AssessoriaMapper;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.services.AssessoriaService;
import br.com.menthoros.backend.services.KeycloakOrganizationGateway;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssessoriaServiceImpl implements AssessoriaService {

    private final AssessoriaRepository assessoriaRepository;
    private final AssessoriaMapper assessoriaMapper;
    private final KeycloakOrganizationGateway keycloakGateway;

    /**
     * Cria uma assessoria (tenant) e a Organization correspondente no Keycloak.
     *
     * Idempotent: NO — cria nova entidade e Organization a cada chamada.
     * Side Effects: Database insert (Assessoria) + criação de Organization no Keycloak.
     * Tenant-aware: N/A — operação administrativa (provisiona um novo tenant).
     *
     * @param input dados da assessoria
     * @return AssessoriaOutputDto persistida (com keycloakOrganizationId)
     * @throws DuplicateResourceException se o domínio já existir
     */
    @Override
    @Transactional
    public AssessoriaOutputDto criarAssessoria(AssessoriaInputDto input) {
        if (input == null || input.dominio() == null || input.dominio().isBlank()) {
            throw new IllegalArgumentException("Domínio é obrigatório");
        }
        log.info("Criando assessoria: nome={}, dominio={}", input.nome(), input.dominio());

        if (assessoriaRepository.existsByDominio(input.dominio())) {
            throw new DuplicateResourceException("Domínio já existe: " + input.dominio());
        }

        Assessoria entity = assessoriaMapper.toEntity(input);
        entity = assessoriaRepository.save(entity);

        String orgId = keycloakGateway.criarOrganization(entity.getNome(), entity.getDominio(), entity.getId());
        entity.setKeycloakOrganizationId(orgId);
        entity = assessoriaRepository.save(entity);

        log.info("Assessoria criada: id={}, organizationId={}", entity.getId(), orgId);
        return assessoriaMapper.toOutputDto(entity);
    }
}
```

> Se `Assessoria.createdAt`/`ativo`/`plano` forem NOT NULL e não houver `@PrePersist`, setar defaults antes do primeiro `save` (ler a entidade — V2 define `created_at NOT NULL DEFAULT CURRENT_TIMESTAMP` e `ativo DEFAULT TRUE` no banco, mas a entidade pode precisar de `entity.setPlano(input.plano())` já feito no mapper; garantir `createdAt`/`ativo` se a coluna exigir e o DEFAULT do banco não cobrir o INSERT do JPA). Ajustar no mapper/service conforme a entidade real.

- [ ] **Step 5: Rodar e ver passar**

Run: `./mvnw -Dtest=AssessoriaServiceImplTest test`
Expected: PASS (2 testes).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/menthoros/backend/services/AssessoriaService.java \
        src/main/java/br/com/menthoros/backend/services/impl/AssessoriaServiceImpl.java \
        src/test/java/br/com/menthoros/backend/services/impl/AssessoriaServiceImplTest.java
git commit -m "feat(assessoria): criar assessoria + Organization no Keycloak (service)"
```

---

### Task 9: AssessoriaController — POST /api/admin/assessorias

**Files:**
- Create: `src/main/java/br/com/menthoros/backend/controller/AssessoriaController.java`

> `/api/admin/**` exige autenticação e role ADMIN. Confirmar que não está em `publicPaths`. Não usar `@RequireTenant` (operação administrativa cria tenant, não opera dentro de um).

- [ ] **Step 1: Implementar o controller**

```java
package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.AssessoriaInputDto;
import br.com.menthoros.backend.dto.output.AssessoriaOutputDto;
import br.com.menthoros.backend.services.AssessoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/assessorias")
@RequiredArgsConstructor
@Tag(name = "Assessorias", description = "Cadastro administrativo de assessorias (tenants)")
public class AssessoriaController {

    private final AssessoriaService assessoriaService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cadastrar assessoria", description = "Cria uma assessoria (tenant) e a Organization no Keycloak")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Assessoria criada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AssessoriaOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado (requer ADMIN)", content = @Content),
            @ApiResponse(responseCode = "409", description = "Domínio já existe", content = @Content)
    })
    public ResponseEntity<AssessoriaOutputDto> criarAssessoria(@Valid @RequestBody AssessoriaInputDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assessoriaService.criarAssessoria(dto));
    }
}
```

- [ ] **Step 2: Compilar e rodar a suíte da change**

Run: `./mvnw -Dtest=AssessoriaServiceImplTest,AssessoriaMapperTest test`
Expected: PASS. (O controller é fino; a lógica já é coberta no service. Teste de controller — MockMvc/autorização — é opcional e pode ser adicionado na change `complete-authorization-controllers`.)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/br/com/menthoros/backend/controller/AssessoriaController.java
git commit -m "feat(assessoria): endpoint POST /api/admin/assessorias"
```

---

## Fase 4 — Convite de atleta e vínculo Usuario↔Atleta

### Task 10: Repository do Atleta — buscas por email e por usuário

**Files:**
- Modify: `src/main/java/br/com/menthoros/backend/repository/AtletaRepository.java`

- [ ] **Step 1: Adicionar os métodos de query**

```java
    /**
     * Busca um atleta por email dentro do tenant (usado no aceite do convite).
     */
    Optional<Atleta> findByEmailAndAssessoria_Id(String email, UUID tenantId);

    /**
     * Resolve o atleta vinculado a um usuário dentro do tenant.
     */
    Optional<Atleta> findByUsuario_IdAndAssessoria_Id(UUID usuarioId, UUID tenantId);
```
(Garantir imports `java.util.Optional` e `java.util.UUID` já presentes.)

- [ ] **Step 2: Compilar**

Run: `./mvnw clean compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/br/com/menthoros/backend/repository/AtletaRepository.java
git commit -m "feat(atleta): queries findByEmail/findByUsuario no repository"
```

---

### Task 11: AtletaService.gerarConvite (TDD)

**Files:**
- Modify: `src/main/java/br/com/menthoros/backend/services/AtletaService.java`
- Modify: `src/main/java/br/com/menthoros/backend/services/impl/AtletaServiceImpl.java`
- Test: `src/test/java/br/com/menthoros/backend/services/impl/AtletaServiceImplConviteTest.java`

> Antes: ler `AtletaServiceImpl` para os nomes reais dos campos injetados e o repositório (`atletaRepository.findByIdAndTenantId`). O gateway será um novo campo injetado — adicioná-lo ao construtor (via `@RequiredArgsConstructor`, basta declarar o `private final`).

- [ ] **Step 1: Escrever os testes do convite (falha)**

```java
package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.services.KeycloakOrganizationGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AtletaServiceImplConviteTest {

    @Mock private AtletaRepository atletaRepository;
    @Mock private KeycloakOrganizationGateway keycloakGateway;
    // demais @Mock conforme o construtor real de AtletaServiceImpl (AssessoriaRepository,
    // AtletaMapper, PlanoMetadadosRepository, TsbService) — declarar todos para o @InjectMocks.

    private AtletaServiceImpl service; // instanciar via construtor no @BeforeEach com os mocks

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        // service = new AtletaServiceImpl(atletaRepository, assessoriaRepository, atletaMapper,
        //         planoMetadadosRepository, tsbService, keycloakGateway);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void gerarConviteEnviaConviteParaAtletaDoTenant() {
        UUID atletaId = UUID.randomUUID();
        Assessoria assessoria = new Assessoria();
        assessoria.setKeycloakOrganizationId("org-123");
        Atleta atleta = new Atleta();
        atleta.setId(atletaId);
        atleta.setEmail("ana@teste.com");
        atleta.setAssessoria(assessoria);

        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));

        service.gerarConvite(atletaId);

        verify(keycloakGateway).enviarConviteAtleta(eq("org-123"), eq("ana@teste.com"), eq(atletaId));
    }

    @Test
    void gerarConviteRejeitaAtletaDeOutroTenant() {
        UUID atletaId = UUID.randomUUID();
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.gerarConvite(atletaId))
                .isInstanceOf(DomainNotFoundException.class);

        verify(keycloakGateway, never()).enviarConviteAtleta(any(), any(), any());
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw -Dtest=AtletaServiceImplConviteTest test`
Expected: FAIL — método `gerarConvite` não existe / gateway não injetado.

- [ ] **Step 3: Declarar o método na interface**

Em `services/AtletaService.java`, adicionar:
```java
    void gerarConvite(UUID atletaId);
```

- [ ] **Step 4: Implementar em AtletaServiceImpl**

Adicionar o campo injetado (junto aos demais `private final`):
```java
    private final KeycloakOrganizationGateway keycloakOrganizationGateway;
```
E o método:
```java
    /**
     * Gera (ou reenvia) um convite para o atleta acessar o sistema.
     *
     * Idempotent: YES — reenviar o convite não cria Atleta/Usuario adicionais.
     * Side Effects: External API call (Keycloak — convite/Organization).
     * Tenant-aware: YES — usa TenantContext.getRequiredTenantId().
     *
     * @param atletaId id do atleta a convidar
     * @throws DomainNotFoundException se o atleta não pertencer ao tenant atual
     */
    @Override
    public void gerarConvite(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        log.info("Gerando convite para atleta {} (tenant {})", atletaId, tenantId);

        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado: " + atletaId));

        if (atleta.getEmail() == null || atleta.getEmail().isBlank()) {
            throw new DomainRuleViolationException("Atleta sem email não pode ser convidado");
        }

        String orgId = atleta.getAssessoria().getKeycloakOrganizationId();
        keycloakOrganizationGateway.enviarConviteAtleta(orgId, atleta.getEmail(), atletaId);

        log.info("Convite enviado para atleta {} (email {})", atletaId, atleta.getEmail());
    }
```
(Importar `DomainNotFoundException` e `DomainRuleViolationException` de `br.com.menthoros.backend.exception`.)

- [ ] **Step 5: Rodar e ver passar**

Run: `./mvnw -Dtest=AtletaServiceImplConviteTest test`
Expected: PASS (2 testes). Rodar também `./mvnw -Dtest=AtletaServiceImplTest test` para garantir que a injeção extra não quebrou os testes existentes (eles podem precisar do novo mock no construtor — atualizar `AtletaServiceImplTest` se usar `@InjectMocks` Mockito injeta o mock automaticamente; se instanciar manualmente, adicionar o arg).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/menthoros/backend/services/AtletaService.java \
        src/main/java/br/com/menthoros/backend/services/impl/AtletaServiceImpl.java \
        src/test/java/br/com/menthoros/backend/services/impl/AtletaServiceImplConviteTest.java
git commit -m "feat(atleta): gerar/reenviar convite de atleta via Keycloak"
```

---

### Task 12: AtletaController — POST /api/v1/atletas/{id}/convite

**Files:**
- Modify: `src/main/java/br/com/menthoros/backend/controller/AtletaController.java`

> Ler o `AtletaController` antes para casar com o estilo de injeção (manual ou `@RequiredArgsConstructor`) e o `@Tag` existente.

- [ ] **Step 1: Adicionar o endpoint**

```java
    @PostMapping("/{id}/convite")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Convidar atleta", description = "Gera ou reenvia o convite de acesso para o atleta")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Convite gerado/reenviado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado", content = @Content),
            @ApiResponse(responseCode = "422", description = "Atleta sem email", content = @Content)
    })
    public ResponseEntity<Void> convidarAtleta(
            @Parameter(description = "ID do atleta") @PathVariable UUID id) {
        atletaService.gerarConvite(id);
        return ResponseEntity.accepted().build();
    }
```
(Garantir imports: `PostMapping`, `PathVariable`, `Parameter`, `RequireTenant`, `ResponseEntity`, Swagger annotations.)

- [ ] **Step 2: Compilar**

Run: `./mvnw clean compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/br/com/menthoros/backend/controller/AtletaController.java
git commit -m "feat(atleta): endpoint POST /api/v1/atletas/{id}/convite"
```

---

### Task 13: Vincular Usuario↔Atleta no sync do JWT (TDD)

**Files:**
- Modify: `src/main/java/br/com/menthoros/backend/services/impl/UsuarioSyncServiceImpl.java`
- Test: `src/test/java/br/com/menthoros/backend/services/impl/UsuarioSyncServiceImplLinkTest.java`

> O `UsuarioSyncServiceImpl` ganha dependência de `AtletaRepository` (já mockado no teste de role). Quando o usuário sincronizado tiver role `ATLETA` e existir um `Atleta` do mesmo tenant com o mesmo email ainda sem vínculo, preencher `atleta.setUsuario(usuario)` e salvar.

- [ ] **Step 1: Escrever o teste do vínculo (falha)**

```java
package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioSyncServiceImplLinkTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AssessoriaRepository assessoriaRepository;
    @Mock private AtletaRepository atletaRepository;

    @InjectMocks private UsuarioSyncServiceImpl service;

    private Jwt jwtAtleta(String email) {
        return Jwt.withTokenValue("t").header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("email", email).claim("given_name", "Ana").claim("family_name", "Atleta")
                .claim("email_verified", true)
                .claim("realm_access", Map.of("roles", List.of("ATLETA")))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }

    @Test
    void vinculaAtletaQuandoRoleAtletaEEmailBate() {
        UUID tenantId = UUID.randomUUID();
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        when(assessoriaRepository.findById(tenantId)).thenReturn(Optional.of(assessoria));
        when(usuarioRepository.findByKeycloakId(any())).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            return u;
        });

        Atleta atletaSemVinculo = new Atleta();
        atletaSemVinculo.setEmail("ana@teste.com");
        when(atletaRepository.findByEmailAndAssessoria_Id("ana@teste.com", tenantId))
                .thenReturn(Optional.of(atletaSemVinculo));

        service.syncUsuarioFromJwt(jwtAtleta("ana@teste.com"), tenantId);

        ArgumentCaptor<Atleta> captor = ArgumentCaptor.forClass(Atleta.class);
        verify(atletaRepository).save(captor.capture());
        assertThat(captor.getValue().getUsuario()).isNotNull();
    }

    @Test
    void naoVinculaQuandoAtletaJaTemUsuario() {
        UUID tenantId = UUID.randomUUID();
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        when(assessoriaRepository.findById(tenantId)).thenReturn(Optional.of(assessoria));
        when(usuarioRepository.findByKeycloakId(any())).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Atleta jaVinculado = new Atleta();
        jaVinculado.setEmail("ana@teste.com");
        jaVinculado.setUsuario(new Usuario());
        when(atletaRepository.findByEmailAndAssessoria_Id("ana@teste.com", tenantId))
                .thenReturn(Optional.of(jaVinculado));

        service.syncUsuarioFromJwt(jwtAtleta("ana@teste.com"), tenantId);

        verify(atletaRepository, never()).save(any());
    }
}
```

> Ajustar os stubs (`assessoriaRepository.findById` vs `getReferenceById`) ao que `createNewUsuario` usa de fato.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw -Dtest=UsuarioSyncServiceImplLinkTest test`
Expected: FAIL — o vínculo ainda não é feito (`atletaRepository.save` nunca chamado / dependência ausente).

- [ ] **Step 3: Implementar o vínculo**

Adicionar `AtletaRepository` ao `UsuarioSyncServiceImpl` (campo `private final` — `@RequiredArgsConstructor` injeta) e, ao final de `syncUsuarioFromJwt`, antes do `return usuario;`:
```java
        if (usuario.getRole() == UserRole.ATLETA) {
            vincularAtletaSeNecessario(usuario, tenantId);
        }
```
E o método auxiliar:
```java
    /**
     * Efetiva o vínculo Usuario<->Atleta no primeiro acesso de um ATLETA:
     * localiza, no tenant, o Atleta com o mesmo email ainda sem conta vinculada.
     */
    private void vincularAtletaSeNecessario(Usuario usuario, UUID tenantId) {
        if (usuario.getEmail() == null) {
            return;
        }
        atletaRepository.findByEmailAndAssessoria_Id(usuario.getEmail(), tenantId)
                .filter(atleta -> atleta.getUsuario() == null)
                .ifPresent(atleta -> {
                    atleta.setUsuario(usuario);
                    atletaRepository.save(atleta);
                    log.info("Atleta {} vinculado ao usuário {} (email {})",
                            atleta.getId(), usuario.getId(), usuario.getEmail());
                });
    }
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./mvnw -Dtest=UsuarioSyncServiceImplLinkTest,UsuarioSyncServiceImplRoleTest test`
Expected: PASS. (Ambos os testes do sync verdes; o de role pode precisar do novo mock `atletaRepository` no `@InjectMocks` — já declarado.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/menthoros/backend/services/impl/UsuarioSyncServiceImpl.java \
        src/test/java/br/com/menthoros/backend/services/impl/UsuarioSyncServiceImplLinkTest.java
git commit -m "feat(auth): vincular Usuario<->Atleta no primeiro acesso de ATLETA"
```

---

### Task 14: Suíte completa da change

- [ ] **Step 1: Rodar a suíte inteira**

Run: `./mvnw clean test`
Expected: BUILD SUCCESS, sem falhas/erros. Corrigir produção (nunca o teste) se algo quebrar.

- [ ] **Step 2: Checagens dos AI-Generated guardrails do CLAUDE.md**

Run:
```bash
grep -r "public class.*OutputDto\|public class.*InputDto" src/main/java/br/com/menthoros/backend/dto/ || echo "OK: DTOs são records"
grep -rn "@Autowired.*Repository" src/main/java/br/com/menthoros/backend/controller/ || echo "OK: controllers sem Repository"
```
Expected: ambos os "OK".

---

## Fase 5 — Infra Keycloak (Organizations) — NÃO é TDD unitário

> Estas tarefas configuram o realm e implementam o adapter vivo do gateway. Verificação por boot/integração e checagem manual de token, não por testes unitários.

### Task 15: Adapter real KeycloakOrganizationGatewayImpl

**Files:**
- Create: `src/main/java/br/com/menthoros/backend/services/impl/KeycloakOrganizationGatewayImpl.java`
- Config: propriedades do admin client em `application.yml` (server-url, realm, client-id, client-secret) sob um prefixo dedicado (ex.: `keycloak.admin.*`).

- [ ] **Step 1: Implementar o adapter usando keycloak-admin-client (v25.0.3)**

Usar `org.keycloak.admin.client.Keycloak` (`KeycloakBuilder`) e a API de Organizations do realm (`realm.organizations()`), criando a Organization com `name=nome`, um domínio (`dominio`) e o atributo `tenant_id=[tenantId]`; para convite, usar a API de membros/convites de Organization. Anotar `@Service`. Ler a doc da versão 25.0.3 do admin-client para os nomes exatos de `OrganizationRepresentation`/`OrganizationsResource` (a API de Organizations evolui entre versões — validar contra o jar resolvido).

> Decisão de design (do `design.md`): o atributo `tenant_id` da Organization é projetado no token como `organization.<org>.tenant_id` — formato que `JwtTenantFilter.extractTenantId` já resolve.

- [ ] **Step 2: Verificação de boot**

Run: `./mvnw -Dtest=*ApplicationTests test` (contexto sobe com o novo bean) — ou subir local com `docker compose up -d` + `./mvnw spring-boot:run` e checar logs de inicialização.
Expected: contexto sobe sem erro de bean ausente; o gateway real substitui qualquer stub.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/br/com/menthoros/backend/services/impl/KeycloakOrganizationGatewayImpl.java \
        src/main/resources/application.yml
git commit -m "feat(keycloak): adapter de Organizations/convite via admin-client"
```

### Task 16: Configuração do realm menthoros-app

- [ ] **Step 1: Habilitar Organizations no realm** (Realm Settings → Organizations: On) no Keycloak 26.x do `docker-compose`.
- [ ] **Step 2: Criar o client role `ATLETA`** no client/realm conforme o mapeamento de `realm_access.roles`.
- [ ] **Step 3: Configurar o protocol/attribute mapper** que injeta `tenant_id` da Organization no token no formato `organization.<org>.tenant_id` (ou claim direta `tenant_id`).
- [ ] **Step 4: Verificação manual** — emitir um token de teste e confirmar que `JwtTenantFilter` resolve o `tenant_id` (logar e bater um endpoint tenant-aware com o token). Se o shape divergir de `organization.<org>.tenant_id`, ajustar `extractTenantId` (Task 17).

### Task 17 (condicional): Ajuste do JwtTenantFilter ao claim real de Organizations

**Files:**
- Modify (se necessário): `src/main/java/br/com/menthoros/backend/security/JwtTenantFilter.java:97-141`

- [ ] **Step 1:** Se o claim emitido pelo KC 26 divergir do mapa `organization.<org>.tenant_id` já suportado, escrever um teste unitário do filtro com um `Jwt` no shape real e ajustar `extractTenantId` para resolvê-lo (mantendo compatibilidade com a claim direta `tenant_id`). Caso contrário, registrar "sem mudança necessária".

---

## Fase 6 — Runbook de migração Groups → Organizations

### Task 18: Documentar o runbook (sem código de produção)

**Files:**
- Create: `src/main/resources/db/migration/V33__RUNBOOK.md` (ou `docs/`): procedimento manual/idempotente.

- [ ] **Step 1:** Documentar: (1) habilitar Organizations; (2) para cada `Assessoria` com `keycloakGroupId`, criar a Organization equivalente, migrar membros, setar atributo `tenant_id`, gravar `keycloakOrganizationId`; (3) criar Organization `default` com o mesmo `tenant_id` (claim direta `tenant_id` permanece válida na janela de transição); (4) após validação, depreciar `keycloakGroupId`. Compatibilidade: enquanto ambos os mappers emitirem `tenant_id`, o filtro resolve qualquer um — migração incremental, sem downtime.

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/db/migration/V33__RUNBOOK.md
git commit -m "docs(keycloak): runbook de migração Groups->Organizations"
```

---

## Atualização do OpenSpec

- [ ] **Marcar tasks concluídas** em `menthoros-product/openspec/changes/add-assessoria-onboarding/tasks.md` conforme a execução avança (itens 1.x, 3.x, 4.x; 2.x e 5.x ficam pendentes até a infra de Keycloak ser concluída em ambiente).

---

## Verification (end-to-end)

1. **Unit/build:** `./mvnw clean test` → BUILD SUCCESS (todos os novos testes verdes: role mapping, mapper, assessoria service, convite, vínculo).
2. **Migrations:** subir local (`docker compose up -d` + app) e confirmar nos logs `✅ V33` e `✅ V34`; conferir colunas `tb_assessoria.keycloak_organization_id` e `tb_atleta.usuario_id` no Postgres.
3. **Cadastro de assessoria (com Keycloak):** autenticado como ADMIN, `POST /api/admin/assessorias` com `AssessoriaInputDto` válido → 201 + `keycloakOrganizationId` preenchido; repetir o mesmo `dominio` → 409.
4. **Convite + vínculo:** cadastrar um `Atleta` com email; `POST /api/v1/atletas/{id}/convite` → 202; aceitar o convite (primeiro login como ATLETA) e confirmar que `tb_atleta.usuario_id` foi preenchido; chamar um endpoint `me/*` (entregue em #1) e ver o `atletaId` resolver.
5. **Isolamento:** convite para atleta de outro tenant → 404; token sem `tenant_id` → 401/403.

---

## Self-Review (cobertura do spec `assessoria-onboarding/spec.md`)

- **Req “Cadastro de assessoria cria tenant e Organization”** → Tasks 5–9 (DTOs, mapper, service com gateway, controller; 201/400/403/409). ✓
- **Req “tenant_id via Keycloak Organizations”** → Tasks 15–17 (adapter, realm config, filtro) + design.md. ✓ (infra)
- **Req “Role ATLETA e vínculo Usuario↔Atleta”** → Tasks 1, 3, 13 (enum, migration V34, vínculo no sync). ✓
- **Req “Onboarding de atleta por convite”** → Tasks 4, 10, 11, 12, 13 (gateway, queries, service, endpoint, vínculo idempotente; 202/404/422). ✓

**Type/assinatura consistentes:** `KeycloakOrganizationGateway.criarOrganization(String,String,UUID)` e `enviarConviteAtleta(String,String,UUID)` usados de forma idêntica no service, no convite e nos testes; `AtletaRepository.findByEmailAndAssessoria_Id` / `findByUsuario_IdAndAssessoria_Id` consistentes entre Task 10 e 13; `Atleta.usuario` (getter `getUsuario`/setter `setUsuario`) consistente entre entidade (Task 3), vínculo (Task 13) e resolução futura de `/me` (#1).
