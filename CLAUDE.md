# Menthoros Backend Instructions

## Scope

This file applies to `apps/menthoros-backend`.
Use it as the backend execution guide for coding tasks.

## Instruction Priority

When instructions conflict, follow this order:

1. Repository root `AGENTS.md`.
2. Repository root `CLAUDE.md`.
3. This file (`apps/menthoros-backend/CLAUDE.md`).
4. Active OpenSpec change instructions.
5. Existing code conventions in this module.

## Backend Context

- Stack: Java 21, Spring Boot 3.5.x, Spring Data JPA, Spring Security OAuth2 Resource Server.
- Data: PostgreSQL + Flyway migrations.
- Integration: Keycloak (JWT), OpenAI via Spring AI.
- Build/Test: Maven Wrapper (`./mvnw`).

## Mandatory Workflow

Segue o fluxo **OpenSpec-first** e as diretrizes de **branch/commit** definidos no
`CLAUDE.md` da raiz (seções "Mandatory Workflow (OpenSpec-first)" e "Diretrizes de
Git"). A raiz é a fonte canônica — não duplicar o fluxo aqui.

Específico deste módulo:
- Branch no repo `apps/menthoros-backend`.
- Validar antes de entregar: `./mvnw clean verify` (ver "Testing and Validation" — `test` não roda os `*IT`).

## Coding Rules (Backend)

- Keep layered architecture boundaries:
  - `controller`: transport/http only.
  - `service`: business rules and orchestration.
  - `repository`: persistence access only.
- Validate request DTOs with Bean Validation (`@Valid`, `@NotNull`, etc).
- Keep API contracts stable unless explicitly required by the active change.
- For domain or persistence changes, prefer adding code over risky refactors.
- Do not introduce new dependencies without clear technical justification.

## Identifier Language (decisão 2026-07-25, ver ADR-0007)

**Código novo — campos, enums, DTOs, colunas, tipos — nasce em inglês.** O idioma de resposta,
comentários, JavaDoc e mensagens de commit permanece PT-BR (inalterado, ver `CLAUDE.md` da raiz).

Campo legado em português encontrado numa entidade que a change **já está modificando por outro
motivo** é normalizado junto, com migration de rename e PR coordenado nos repos afetados. Não abrir
change só para renomear, e não sair caçando campo PT em código que a task não toca — vale a regra de
escopo já existente ("stay within task scope").

O glossário (`CONTEXT.md`) permanece em PT-BR, com o identificador do código entre parênteses.

Consequência aceita: a base fica bilíngue por tempo indefinido — entidades tocadas com frequência
convergem rápido, entidades estáveis ficam em PT. É deliberado; o custo de uma renomeação global
(369 campos, migrations de rename em todas as tabelas, contrato do front refeito) não se paga.

## Controller Standards

Rules enforced across all controllers. Violations must be corrected in the same PR that introduces new controller code.

### Layered Architecture (mandatory)
- Controllers must NOT inject `Repository` beans — only `Service` interfaces.
- Controllers must NOT inject concrete service implementations (e.g. `PlanoServiceImpl`) — only the interface.
- Business logic belongs in the service layer, not in controller methods.

### Exception Handling (mandatory)
- Controllers must NOT have try/catch blocks for HTTP error mapping.
- All exception-to-HTTP-status mappings live in `GlobalExceptionHandler` (`@RestControllerAdvice`).
- When adding a new custom exception, add a corresponding `@ExceptionHandler` method in `GlobalExceptionHandler` in the same commit.

### URL Convention (mandatory)
- All endpoints use prefix `/api/v1/` followed by plural resource name.
- Example: `/api/v1/atletas`, `/api/v1/treinos`, `/api/v1/planos`.
- Strava integration endpoints: `/api/v1/strava/**`.

### Response Types (mandatory)
- All controller methods return `ResponseEntity<XxxOutputDto>`, `ResponseEntity<List<XxxOutputDto>>`, `ResponseEntity<Page<XxxOutputDto>>`, or `ResponseEntity<Void>`.
- Raw `Map<String, Object>` returns are NOT allowed — create a typed DTO record instead.

### Swagger / OpenAPI Documentation (mandatory)
- Every controller class must have `@Tag(name = "...", description = "...")`.
  - **`name` must be ASCII kebab-case** (e.g. `coach-dashboard`, `race-projection`), sem acento/espaço:
    o gerador de cliente do front (`openapi-typescript-codegen`) deriva o nome da classe de serviço do
    `name` do tag — nomes PT-BR com acento geram serviços corrompidos (`AnLiseDeTreinoService`).
    Coloque o texto PT-BR rico no `description` (a Swagger UI mostra ambos).
- Every public method must have `@Operation(summary = "...")`.
- Every public method must have `@ApiResponses` listing all possible HTTP status codes.
- Use `@Parameter` for path/query parameters that need description.
- **Collection endpoints (`List<>`) must declare `array` in the `200` response** — use
  `@ArraySchema` (`content = @Content(array = @ArraySchema(schema = @Schema(implementation = X.class)))`)
  **or omit the schema override entirely** and let springdoc infer the array from the `List<>` return
  type (see `CoachDashboardController.getRoster`). A bare `schema = @Schema(implementation = X.class)`
  on a list endpoint generates a **single-object** type in the front client, breaking `.map`/`.slice`
  at runtime.

### HTTP Semantics (mandatory)
- GET: read-only, no side effects.
- POST: create or trigger action (including operations that mutate state like recalculate/sync).
- PUT: full update of existing resource.
- PATCH: partial update of existing resource.
- DELETE: remove resource (return 204 No Content).

### Dependency Injection (recommended)
- Prefer `@RequiredArgsConstructor` (Lombok) for constructor injection.
- All injected fields declared `private final`.

### Tenant Resolution (mandatory)
- Use `TenantContext.getRequiredTenantId()` to resolve tenant inside controller methods.
- Do NOT read `@RequestHeader("X-Tenant-ID")` manually in controllers — this bypasses the tenant filter.
- Tenant isolation is enforced in layers: `JwtTenantFilter` populates `TenantContext` per request,
  `getRequiredTenantId()` fails fast when absent, and repository queries are tenant-scoped.
- **`@RequireTenant` is a METHOD-level annotation** (`@Target(METHOD)`), not class-level. It is
  handled by `TenantValidationAspect` and validates that a **resource-ID parameter** (by
  `resourceParamIndex`, default `0`) belongs to the current tenant. Apply it to handler methods that
  receive a resource `UUID`:
  ```java
  @GetMapping("/{id}")
  @RequireTenant(resourceParamIndex = 0)  // ✅ validates that {id} belongs to the current tenant
  public ResponseEntity<AtletaOutputDto> getAtleta(@PathVariable UUID id) { ... }
  ```
  Do NOT place it on a method with no resource-ID parameter: the aspect throws
  `IllegalArgumentException` when `resourceParamIndex >= args.length`. Self-resolving endpoints
  (e.g. `GET /me`, which resolves the caller from the JWT `sub`) must NOT use `@RequireTenant`; they
  rely on `TenantContext` + tenant-scoped queries, and should document the omission in a comment
  (see `StatusController` for the public-endpoint variant).

## Mapper Standards

Rules for conversion between entities, DTOs, and domain objects.

### Nullability Annotations (mandatory)
- Use `@Nullable` from **`org.jspecify.annotations`** — not `javax.annotation` or `jakarta.annotation`.
- JSpecify is the project's nullability library; mixing annotation sources breaks static analysis consistency.
- Apply `@Nullable` on return types and parameters that legitimately return/accept null; omit on `@NonNull` paths.

### Null Handling (mandatory)
- Every mapper method MUST validate null inputs explicitly.
- Throw `IllegalArgumentException` with a clear message if null is detected.
- Do NOT silently return null or allow NPE.

**Correct:**
```java
public AtletaOutputDto toOutputDto(Atleta entity) {
    if (entity == null) {
        throw new IllegalArgumentException("Atleta entity cannot be null");
    }
    return new AtletaOutputDto(
        entity.getId(),
        entity.getNome(),
        entity.getPesoKg(),
        entity.getCriadoEm(),
        entity.getAtualizadoEm()
    );
}
```

**Incorrect:**
```java
public AtletaOutputDto toOutputDto(Atleta entity) {
    // ❌ What if entity is null? NPE later!
    return new AtletaOutputDto(
        entity.getId(),
        entity.getNome(),
        entity.getPesoKg()
    );
}
```

### Record Conversion
- Use constructor-based conversion (not setters — records don't have them).
- Preserve field names exactly as defined in records.
- If conversion logic is complex, extract to private helper methods.

## DTO & Records Standards

Rules for Data Transfer Objects. All new DTOs must follow these patterns.

### Use Records (mandatory, Java 17+)
- ALL DTOs must be `public record` declarations, not classes.
- Records are immutable by default, provide auto-generated `equals()`, `hashCode()`, `toString()`, and accessors.
- Never use `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor` (Lombok annotations) on DTOs.

**Correct:**
```java
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Athlete data for API response")
public record AtletaOutputDto(
    @Schema(description = "Unique athlete ID")
    UUID id,
    
    @NotBlank(message = "Name is required")
    @Size(max = 100)
    String nome,
    
    @Positive(message = "Weight must be positive")
    BigDecimal pesoKg
) {}
```

**Incorrect:**
```java
@Data  // ❌ Generates mutable setters
@NoArgsConstructor
@AllArgsConstructor
public class AtletaOutputDto {
    private UUID id;
    private String nome;
    private BigDecimal pesoKg;
}
```

### Input vs Output DTOs
- **Input DTOs** (in `dto/input/`): Contain Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Size`, etc.).
  - Example: `AtletaInputDto` with `@Valid` validation.
- **Output DTOs** (in `dto/output/`): Used for responses, include `@JsonInclude(NON_NULL)` to exclude null fields.
  - Example: `AtletaOutputDto`, `PlanoSemanalOutputDto`.

### Nested Records (when needed)
Use nested record declarations for related DTOs:
```java
public record ResumoSemanalTreinoDto(
    UUID atletaId,
    String nomeAtleta,
    Resumo resumo
) {
    public record Resumo(
        Integer totalTreinos,
        Double volumeTotalKm,
        String ultimoTreino
    ) {}
}
```

### Swagger Documentation (mandatory)
- Every record must have `@Schema(description = "...")` on the class.
- Every field should have `@Schema(description = "...", example = "...")` for API documentation.

### Record Accessor Names
- Records auto-generate accessors using field name directly (e.g., `id()`, `nome()`, `pesoKg()`).
- Do NOT call Lombok-style getters (e.g., `getId()`) — use record field accessors.
- When refactoring classes to records, update all `getFieldName()` calls to `fieldName()`.

### Type Safety
- Always declare generic types fully. Avoid raw types.
- Example: `List<ProvaOutputDto>` (✓) not `List` (✗).
- Use bounded generics for reusable components.

### Immutability Guarantee
- Records are `final` and all fields are `final` by default.
- Never attempt to reassign record fields (compile error, which is good).
- Collections in records should be defensively copied if they come from untrusted sources (rare in DTOs).

## Service Standards

Rules for service layer classes that contain business logic.

### Idempotency & Side Effects Documentation (mandatory)
- Every public method MUST document whether it is idempotent and what side effects it has.
- This documentation prevents IA from generating unsafe retry logic or state corruption.

**Format:** Add this JavaDoc to every public method:

```java
/**
 * Brief description of what this method does.
 * 
 * **Idempotent:** YES/NO
 *   - YES: Safe to call multiple times without unexpected state changes
 *   - NO: Calling multiple times produces different results or corrupts state
 * 
 * **Side Effects:** NONE / Database mutation / External API call / etc.
 * 
 * **Tenant-aware:** YES/NO
 *   - Uses TenantContext.getRequiredTenantId() or validates tenant parameter
 * 
 * @param atletaId the athlete ID
 * @return result of operation
 * @throws EntityNotFoundException if athlete not found
 */
public void recalcularMetricasAtleta(UUID atletaId) { ... }
```

**Examples:**

```java
/**
 * Idempotent: YES — Read-only operation, no state changes.
 * Side Effects: NONE
 * Tenant-aware: YES
 */
@Transactional(readOnly = true)
public AtletaOutputDto getAtletaById(UUID id) { ... }

/**
 * Idempotent: NO — Creates new entity each time.
 * Side Effects: Database insert (new entity created)
 * Tenant-aware: YES
 */
public Atleta createAtleta(AtletaInputDto input) { ... }

/**
 * Idempotent: YES — Deleting twice is safe (already deleted).
 * Side Effects: Database update (soft delete)
 * Tenant-aware: YES
 */
public void deleteAtleta(UUID id) { ... }

/**
 * Idempotent: NO — Updates metrics each time, data changes.
 * Side Effects: Database update (multiple fields)
 * Tenant-aware: YES
 */
public void recalcularMetricasAtleta(UUID atletaId) { ... }
```

### Input Validation (mandatory)
- Services MUST NOT trust input DTOs — even though they have `@Valid` on controller side.
- Validate again in service layer for critical business rules.

```java
public Atleta createAtleta(AtletaInputDto input) {
    // Defensive validation (beyond DTO annotations)
    if (input.nome() == null || input.nome().isBlank()) {
        throw new InvalidArgumentException("Nome cannot be blank");
    }
    if (input.pesoKg() != null && input.pesoKg().compareTo(BigDecimal.ZERO) <= 0) {
        throw new InvalidArgumentException("Peso must be positive");
    }
    // ... rest of logic
}
```

### Catching `DataIntegrityViolationException` requires a NON-`@Transactional` method (mandatory)

The idempotent-write idiom — try to insert, catch the unique-constraint violation, treat it as
success — **silently breaks** when the method is `@Transactional`. Spring marks the transaction
rollback-only the moment the constraint fires. The `catch` swallows the exception, the method
returns normally, and then the commit fails *outside* the try block: the caller gets a 500 on
exactly the path the contract promises is safe to retry.

Rule: a method whose contract is "insert-or-ignore" must **not** carry `@Transactional`. Let each
repository call run in its own transaction, so a failed insert rolls back alone and the catch is
meaningful. Document the omission in the JavaDoc — otherwise the next person "fixes" it by adding
the annotation back.

```java
/**
 * Idempotent: YES — reaplicar o mesmo consentimento é no-op.
 * Side Effects: Database insert (append-only)
 *
 * Deliberadamente SEM @Transactional: o catch de DataIntegrityViolationException só funciona fora
 * de uma transação — dentro dela o commit falharia depois do catch, devolvendo 500 num caminho
 * contratualmente idempotente.
 */
public void registerConsent(...) {
    try {
        repository.save(entity);
    } catch (DataIntegrityViolationException e) {
        log.debug("Consentimento já registrado, ignorando: {}", e.getMessage());
    }
}
```

Precedent: `WaitlistServiceImpl`, `UsuarioServiceImpl.registerConsent`.

### Logging (mandatory)
- Log entry point and exit with relevant context (ID, tenant, operation result).
- Use structured logging with SLF4J + MDC when available.

```java
@Slf4j
public class AtletaService {
    public Atleta createAtleta(AtletaInputDto input) {
        log.info("Creating atleta: nome={}", input.nome());
        UUID tenantId = TenantContext.getRequiredTenantId();
        
        Atleta entity = mapper.toDomain(input);
        entity = repository.save(entity);
        
        log.info("Atleta created: id={}, tenantId={}", entity.getId(), tenantId);
        return entity;
    }
}
```

### Service Size & Decomposition (guideline)

A service that mixes orchestration, LLM/IO, schema building, validation and persistence in one class becomes untestable and unsafe to change. Watch for these smells and extract collaborators **when you are already touching such a class** (do not do opportunistic refactors out of scope):

- A single `*ServiceImpl` well over ~400 lines, or a method over ~80 lines.
- Distinct concerns living together: building a request payload / JSON schema, calling an external model, validating/normalizing the response, and persisting it — these are four collaborators, not one method.
- Pure transformation/validation logic (no Spring, no IO) that could be a `services/helper` validator or a `DomainSkill` (see **Skills Architecture Standards**).

Keep the `*ServiceImpl` as a thin orchestrator and move focused logic into `services/helper`, `services/prompt`, or `skills/`.

**Known debt — do not grow:** `IaServiceImpl` (~1500 lines: JSON-schema building + plan generation + FC/interval/load validation), `PlanoServiceImpl` (~740), `StravaActivityServiceImpl` (~650), `TsbServiceImpl` (~640). Decomposition of `IaServiceImpl` is tracked in OpenSpec change `refactor-iaservice-decomposition`.

### Partial-Failure Pattern for Aggregation Endpoints (mandatory when applicable)

When a service method assembles a response from multiple independent sub-queries (e.g. a profile endpoint that fetches PMC, aderência, plano, sinais, sugestões), use the **partial-failure pattern**: each sub-query runs inside a helper that catches non-critical exceptions, records the failed field in an `avisos` list, and returns a safe default (empty list or null) — so one failing query never brings down the whole response.

Reference implementation: `CoachAthleteProfileServiceImpl.buscarPerfil()` + `buscarLista()` / `buscarNullable()`.

```java
// Helper shape — keep in the service impl, private
private <T> List<T> buscarLista(String campo, List<String> avisos, Supplier<List<T>> fn) {
    try {
        return fn.get();
    } catch (DomainNotFoundException | IllegalStateException e) {
        throw e;  // domain errors always propagate
    } catch (Exception e) {
        log.warn("[perfil] erro ao buscar {}: {}", campo, e);
        avisos.add(campo);
        return List.of();
    }
}
```

Rules:
- **Re-throw** `DomainNotFoundException` and `IllegalStateException` — these signal a broken invariant, not a degraded response.
- **Swallow and record** infrastructure/transient exceptions (DB timeout, lazy-load outside TX, etc.) into `avisos`.
- **Return the `avisos` list** in the DTO (as `List<String>` or null when empty) so callers can detect partial results without guessing.
- Apply only to aggregation methods where sub-queries are genuinely independent. Do not use for single-resource lookups.

## Skills Architecture Standards

Rules for domain skills in `br.com.menthoros.backend.skills.*`.

### JPA entities must not cross into skill logic (mandatory)

Skills are domain components — they must not receive or depend on JPA entities (`@Entity` classes).

**Why:**
- JPA entities have lazy-loaded collections that throw `LazyInitializationException` outside an active transaction — skills are often invoked outside the transactional boundary that loaded the entity.
- Skills become coupled to the ORM lifecycle, breaking portability (batch, async, future module extraction).
- Unit testing the skill requires constructing a full JPA entity instead of a simple record.

**Rule:** The service layer that calls a skill is responsible for mapping JPA entities to the skill's input record types before invoking `skill.execute(input)`.

```java
// ✅ Correct — dedicated mapper converts entity to skill input record
// br.com.menthoros.backend.mapper.AthleteProfileMapper
@Component
public class AthleteProfileMapper {
    public AthleteProfile from(Atleta atleta) {
        if (atleta == null) throw new IllegalArgumentException("Atleta cannot be null");
        return new AthleteProfile(
            atleta.getId(),
            atleta.getNome(),
            atleta.getSobrenome(),
            atleta.getFcMaxima(),
            atleta.getFcLimiar(),
            atleta.getVo2maxEstimado(),
            atleta.getNivelExperiencia()
        );
    }
}

// Service injects mapper and calls skill
public RaceProjectionOutput generateProjection(UUID atletaId, ...) {
    Atleta atleta = atletaRepository.findById(atletaId)...;
    AthleteProfile profile = athleteProfileMapper.from(atleta);
    return raceProjectionSkill.execute(new RaceProjectionInput(profile, ...));
}

// ❌ Wrong — positional constructor call spread across callers (fragile, not reusable)
AthleteProfile profile = new AthleteProfile(
    atleta.getId(), atleta.getNome(), atleta.getSobrenome(), ...  // breaks silently if field order changes
);

// ❌ Wrong — JPA entity passed directly into skill
public RaceProjectionOutput generateProjection(Atleta atleta, ...) {
    return raceProjectionSkill.execute(new RaceProjectionInput(atleta, ...));
}
```

### Skill input/output types must be records (mandatory)

All types that form the skill's input and output contract (`*Input`, `*Output`, and their nested types) must be `public record` declarations — same rule as DTOs (see **DTO & Records Standards**).

### Skill input record fields should be minimal (mandatory)

Include only the fields the skill actually reads. Do not pass full entity graphs "just in case". If the skill needs 6 fields from `Atleta`, define a record with those 6 fields — nothing more.

### Skill Testing Standards (mandatory)

Skills are pure, deterministic domain logic — the easiest and most important code to unit-test. Reference tests: `DomainSkillContractTest`, `IntervalWorkoutAnalysisSkillTest`, `WeeklyDistributionSkillTest`, `RecoveryCargaSkillTest`.

- Every `DomainSkill` implementation has a dedicated `*SkillTest` following the patterns in **Test Standards** (JUnit 5, `@Nested`, Arrange-Act-Assert).
- Build inputs as the skill's **record types** — never construct a JPA entity in a skill test. Skills receive records by contract (see above), and tests must too.
- Skills are deterministic and have no injected collaborators: assert exact outputs (zones, paces, flags, messages) for representative inputs; do NOT mock anything inside a skill test.
- Cover the decision branches: eligibility yes/no, every `SkillResult` status, and boundary inputs — apply BVA / equivalence partitioning from **Maximizing Coverage**.
- The cross-cutting contract (every registered skill has a non-blank `skillKey`, `skillVersion`, `category`, and reports invalid input through `SkillResult` rather than throwing) is enforced by `DomainSkillContractTest`. A new skill registered as `@Component` is picked up automatically — do not write a bespoke contract test, just make the skill conform.

## Multi-tenancy and Security Guardrails

- Never bypass tenant isolation rules.
- Never remove tenant-aware filters/checks in request flow.
- Preserve authN/authZ behavior unless change scope explicitly requires update.
- Do not hardcode secrets or credentials.

## External Call Resilience

Every call that leaves the process (LLM via OpenAI/Anthropic, Keycloak, Strava, intervals.icu) must be defended against latency and cascading failure. **The strategy is decided in ADR-0008 — read it before changing anything here.**

Why this matters more than "one thread gets stuck": a hung call blocks a real flow (plan
generation, analysis) and, before `refactor-llm-call-outside-transaction` (2026-09-01), it also
held a **database connection** — plan generation was `@Transactional` around the LLM call, and the
batch pinned 4 of the 5 pool connections. That coupling is gone for plan generation: the LLM call
now runs **outside any transaction** (loader → LLM → persister), throttled by
`LlmConcurrencyLimiter` (global + per-tenant cap + interactive reserve). The `@Async` listeners
(`WorkoutAnalysisListener`, `WeeklyFocusNarrativeService`) still hold a connection during their LLM
calls — tracked in the `refactor-async-llm-listeners-outside-transaction` change.

Current state (verified 2026-07-26): connect/read timeouts on Keycloak (`KeycloakAdminRestClientConfig`, 5s/10s — the reference) and intervals.icu (`IntervalsIcuWebClientConfig`, same values). Gaps being closed by `add-external-call-resilience`: no timeout on LLM calls or on the Strava `WebClient`; no latency instrumentation at all (`CostTrackingAdvisor` has only `Counter`, no `Timer`).

Standards for new or modified external integrations:

- **Timeouts are mandatory.** Every external client sets both a connect and a read/response timeout — no call may block indefinitely. For plain REST clients, copy the Keycloak reference (5s/10s) **in code**; do not externalize to `application.yml` — nobody tunes those per environment.
- **LLM timeouts are per route, not per provider.** In Spring AI the timeout lives in the `ChatModel`'s HTTP client, and there are only two (OpenAI, Anthropic) — but `SIMPLE` (1k tokens) and `PLANO` (12k tokens) are the *same* provider. A provider-level timeout would couple the pair with the widest latency gap. Values belong in `app.llm.routing.*.timeout`, next to `model`/`temperature`/`maxTokens`.
- **Never retry a timeout.** Retry 5xx and 429 — they fail fast and cheap, so a second attempt costs almost nothing. A timeout fails *slowly by definition*: retrying pays the worst case twice, precisely when the system is already under pressure. Cap attempts, use backoff, and never blindly retry a non-idempotent write.
- **Bound the total, not just each attempt.** Where a retry loop wraps a slow call, the worst case is `timeout × attempts`. Check the elapsed budget before starting another attempt (see `PlanoResilienceService`).
- **No circuit breaker, by decision (ADR-0008).** Do NOT add Resilience4j or any circuit-breaker library — the re-evaluation trigger is recorded in the ADR. Where calls repeat in bursts (batch processing), stop after N consecutive failures instead: same useful behavior, no new dependency, no thresholds that can mask real errors.
- **Expose metrics.** Timeouts, retries and call latency surface through the existing Micrometer/Prometheus registry, tagged by route.

## E-mail transacional (desde 2026-08-28)

O backend envia e-mail próprio — antes, todo e-mail saía do Keycloak (verify-email, convite de
atleta), e o Keycloak continua com os dele. O que motivou: o convite das assessorias fundadoras
precisa de e-mail **antes** de existir usuário ou organização no Keycloak.

- **Porta única:** `services/email/EmailSender`. Nunca `JavaMailSender` direto num serviço.
- **Duas implementações, por profile.** `SmtpEmailSender` (Spring Mail sobre o SMTP do Resend,
  porta 2587/STARTTLS — 465 e 587 são bloqueadas no Railway) **só em `cloud` e `dev`**;
  `FileEmailSender` (grava `.eml` em `app.email.outbox-dir`, default `target/outbox`) em todo o
  resto — inclusive **sem profile**, que é como os `@SpringBootTest` com H2 sobem (a primeira
  versão, `!local & !test & !integration`, derrubou 15 desses no CI). **Nunca um sender que loga o corpo:** a mensagem pode carregar um
  link com segredo, e log agrega e vai para ferramentas externas.
- **No `cloud`, faltar `SMTP_HOST` derruba o startup** — de propósito (`application-cloud.yml` sem
  default). Degradar para arquivo/log na nuvem colocaria o link em lugar errado. No `dev` (docker
  local) há defaults e o envio falha em runtime (502), não no boot.
- **Config:** credenciais em `spring.mail.*` lidas de `SMTP_*` (mesmos valores das `KC_SMTP_*` do
  Keycloak, por referência no Railway); remetente e outbox em `app.email.*` (`EmailProperties`).
- **Templates** em `resources/templates/email/<nome>.html` + `.txt`, renderizados por
  `EmailTemplateRenderer` (`{{chave}}`, escape de HTML no `.html`, placeholder sem valor é erro). Sem
  Thymeleaf/Freemarker — a dependência é só o `spring-boot-starter-mail`.
- **Falha de envio** é `EmailDeliveryException` → 502; a mensagem nunca carrega o corpo. Quem chama
  decide o que persiste antes/depois do envio (ver `FoundingInviteServiceImpl`: envia **fora** de
  transação e só então marca `sent_at`).
- **Segredos em mensagem** (token de convite): `EmailMessage.toString()` é mascarado; testes
  afirmam que o token não aparece em log (`FileEmailSenderTest`, `FoundingInviteServiceImplTest`).

## Database and Migration Rules

- All schema changes must go through Flyway (`src/main/resources/db/migration`).
- Never edit an already applied migration; create a new versioned migration.
- Keep migration names deterministic and descriptive.

### Table Design Standards (mandatory)

Before proposing or writing any `CREATE TABLE`, read the existing migrations to understand and follow the established patterns. All new tables must conform to:

**Naming**
- Prefix: `tb_` + snake_case (e.g. `tb_race_projection_snapshot`).
- Column names: snake_case throughout.

**Primary Key**
- Always `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`.
- Never use `BIGSERIAL`, `SERIAL`, or `AUTO_INCREMENT`.

**Foreign Keys**
- Type: `UUID [NOT NULL] REFERENCES tb_xxx(id) ON DELETE CASCADE` (or `ON DELETE SET NULL` for optional).
- `tenant_id UUID NOT NULL` — no FK constraint, managed by the application layer.

**Timestamps**
- Use `TIMESTAMPTZ NOT NULL DEFAULT NOW()` for creation timestamp.
- Use `TIMESTAMPTZ` (nullable) for optional event timestamps (e.g. `reviewed_at`, `synced_at`).
- Do not mix `TIMESTAMP` (without timezone) in new tables.

**Constraints**
- Always name constraints explicitly: `CONSTRAINT uk_<table>_<cols> UNIQUE (...)`.
- Use `CHECK` constraints inline on the column when the rule is simple.

**Indexes**
- `CREATE INDEX IF NOT EXISTS idx_<table>_<column> ON tb_xxx(col);`
- Add a composite index on `(tenant_id, <main_lookup_column>)` for all tenant-scoped tables.

**Migration file structure**
```sql
-- =====================================================================
-- Vxx: Short description of what this migration does
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_xxx ( ... );

CREATE INDEX IF NOT EXISTS idx_xxx_col ON tb_xxx(col);

DO $$
BEGIN
    RAISE NOTICE '✅ Vxx - tb_xxx criada com sucesso';
END$$;
```

**Version number**: always check the latest file in `db/migration/` and increment by 1 (`V26` → `V27`).

## Testing and Validation

Run from `apps/menthoros-backend`.

Inner loop (fast — Surefire only, `*Test`):

```bash
./mvnw clean test
```

Required before delivery — **`verify` is the gate, not `test`**. It is the only command that runs
the `*IT` integration tests (see *Test class suffix* under **Test Standards**); `test` alone leaves
eleven classes unexecuted:

```bash
./mvnw clean verify
```

Useful checks when needed:

```bash
./mvnw test -Dtest=XxxTest
./mvnw clean compile
```

If containerized dependencies are required for the task:

```bash
docker compose config
docker compose up -d
```

## Test Standards

Rules for unit tests. All new tests must follow these patterns. Reference implementation: `TreinoServiceImplTest`, `PlanoServiceImplTest`.

### Nested Structure (mandatory)

- Group test cases by the method under test using `@Nested` inner classes — one nested class per public method.
- The nested class name is the method under test in PascalCase (e.g. `marcarTreinoPerdido` → `class MarcarTreinoPerdido`).
- Annotate each `@Nested` class with `@DisplayName("<methodName>")`; inner `@Test` methods describe ONLY the scenario, not the method name.
- This produces a readable hierarchical output (`MyServiceTest$MethodName > scenario`).

```java
@ExtendWith(MockitoExtension.class)
class TreinoServiceImplTest {

    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @InjectMocks private TreinoServiceImpl treinoService;

    private UUID tenantId;

    @BeforeEach
    void setUpTenant() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDownTenant() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("marcarTreinoPerdido")
    class MarcarTreinoPerdido {

        @Test
        @DisplayName("rejeita treino já REALIZADO")
        void rejeitaRealizado() { ... }
    }
}
```

- Mocks (`@Mock`), the subject (`@InjectMocks`), shared state, lifecycle hooks (`@BeforeEach`/`@AfterEach`), and helper methods live in the OUTER class — nested classes inherit them. Do NOT redeclare mocks inside `@Nested` classes.

### Framework & Naming (mandatory)

- JUnit 5 (`org.junit.jupiter`) + Mockito only. Use `@ExtendWith(MockitoExtension.class)`.
- Test class name: `<ClassUnderTest>Test` (e.g. `TreinoServiceImplTest`).
- `@DisplayName` text in PT-BR, describing the observable behavior (e.g. "publica evento quando percepcaoEsforco está presente").
- Method names in lowerCamelCase summarizing the scenario (e.g. `publicaEventoComPercepcao`).

### Structure of a Test (mandatory)

- Follow Arrange-Act-Assert (or Given-When-Then). Keep the three phases visually separated.
- One behavior per test method — do not assert unrelated outcomes in the same test.
- Tests must be independent and order-agnostic; never share mutable state between tests through fields populated in a previous test.

### Tenant-aware Services (mandatory)

- Any service that calls `TenantContext.getRequiredTenantId()` MUST set the tenant in `@BeforeEach` and clear it in `@AfterEach`:
  ```java
  @BeforeEach void setUp() { tenantId = UUID.randomUUID(); TenantContext.setTenantId(tenantId); }
  @AfterEach  void tearDown() { TenantContext.clear(); }
  ```
- Failing to `clear()` leaks tenant state into other tests in the same JVM.

### Mockito Best Practices (mandatory)

- Default strict stubbing (`MockitoExtension`) is required — do NOT switch to `LENIENT` to silence warnings. An `UnnecessaryStubbingException` means the stub is dead code: remove it.
- Mock only direct collaborators (repositories, mappers, other services, `ApplicationEventPublisher`). Never mock the class under test.
- Do NOT mock records/DTOs/value objects — construct real instances (use a private helper for DTOs with many fields).
- Stub return values with `when(...).thenReturn(...)`; verify interactions with `verify(...)`, `verify(..., never())`, `verify(..., times(n))`.
- Use `ArgumentCaptor` to assert on the content of objects passed to collaborators (e.g. the payload of a published event).
- For static calls inside the method under test (e.g. `Hibernate.initialize(...)`), use `try (MockedStatic<...> m = mockStatic(...))` scoped to the act phase.

### Coverage Expectations (mandatory)

For each public service method, cover at minimum:
- The happy path (state mutated and/or collaborators invoked as expected).
- Not-found / invalid-input branches (assert the exact exception type, e.g. `DomainNotFoundException`, `DomainRuleViolationException`).
- Idempotency / no-op branches when the method documents them (assert NO persistence/side effect occurred).
- Conditional side effects (e.g. event published only when a field is present — test both presence and absence).

#### Maximizing Coverage (market best practices)

Aim for **branch (decision) coverage, not just line coverage** — every `if`/`else`, every `switch`/`case`, every ternary, every `catch`, and both outcomes of every boolean condition must be exercised. Apply the techniques below to get there without writing redundant tests.

- **Boundary Value Analysis (BVA):** for any numeric/range/length rule, test the exact edges and their neighbors — `min-1, min, min+1` and `max-1, max, max+1`, plus `0`, negative, and the largest realistic value. Off-by-one errors live at the boundary, not the middle.
- **Equivalence Partitioning:** pick one representative per input class (valid, invalid-too-low, invalid-too-high, null) instead of many values from the same class — keeps the suite small while covering each branch.
- **Null / empty / blank matrix:** for every nullable parameter and DTO field that drives a branch, test `null`, empty (`""`, `List.of()`), and blank (`"  "`) explicitly. The service layer re-validates input (see *Input Validation*) — assert that defensive validation fires.
- **Collection cardinality:** exercise `0`, `1`, and `N` elements, plus collections containing a `null` element when the code iterates/streams over them.
- **Full enum coverage:** when behavior branches on an enum (e.g. `TreinoExecucaoStatus`, `PlanoStatus`), cover every relevant value. Use `@ParameterizedTest` + `@EnumSource` so adding an enum constant later forces a test decision.
- **State-transition coverage:** for stateful entities, test every documented transition AND the illegal ones (e.g. `REALIZADO → PERDIDO` must throw; `PERDIDO → PERDIDO` is a no-op). One test per edge of the state machine.
- **Parameterized tests to scale inputs:** replace copy-pasted near-identical tests with `@ParameterizedTest` + `@ValueSource` / `@CsvSource` / `@MethodSource` / `@NullAndEmptySource`. This raises input coverage cheaply and keeps tests DRY.
- **Tenant isolation (negative):** assert that a cross-tenant id (entity exists but in another tenant) resolves to not-found — protects the multi-tenancy guardrail, not just the happy lookup.
- **Date/time & numeric edges:** test `null`-date defaulting to `LocalDate.now()`, week/month boundaries (`getResumoSemanal`), and `BigDecimal` scale/rounding and `Double` precision where money/volume/metrics are computed.
- **Exhaustive side-effect verification:** assert both that expected collaborators are called AND that nothing else happens — use `verify(mock, never())`, `verifyNoInteractions(mock)`, and `verifyNoMoreInteractions(mock)` to catch accidental extra writes/events.
- **Interaction order:** when ordering matters (persist before publishing an event, save parent before child), assert it with `InOrder`.
- **Argument precision:** prefer `eq(value)` over `any()` once the exact argument is known — a test that only checks `any()` passes even when the wrong value is sent.

#### Quality gate over the number

Coverage % is necessary but not sufficient — a line can be covered by a test with weak or no assertions.

- **No assertion-free tests.** Every test must assert observable behavior (return value, mutated state, or verified interaction). Calling the method "to cover the line" is forbidden.
- **Validate assertion strength with mutation testing** (PIT / `pitest`) on critical business logic: if a mutant survives, the lines are covered but under-asserted — add the missing assertion.
- **Don't chase 100% on trivial code** — getters/setters, `record` accessors, generated mappers, and pure DTOs don't need dedicated tests. Concentrate effort on `service`/business logic and on branches that encode domain rules.

### Test Layers (sliced vs integration)

Use the lightest test that covers the behavior (the `springboot-tdd` skill from everything-claude-code provides the templates):

- **Service / business rule:** Mockito unit test (`@ExtendWith(MockitoExtension.class)`, `@Nested`) — the dominant pattern in this module.
- **Controller:** `@WebMvcTest(XxxController.class)` + `MockMvc` + `@MockBean` on the service — validates route/status/JSON without booting the full context. Prefer it over a full `@SpringBootTest` for controller-only checks; it is now the dominant controller pattern (22+ slices).
- **Repository / JPA query:** `@DataJpaTest` + Testcontainers (wire via `@DynamicPropertySource`).
- **End-to-end integration:** `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` — reserve for flows that need the real context.

#### Test class suffix drives WHICH command runs the test (mandatory)

The suffix is not cosmetic — it selects the plugin, and therefore the Maven phase:

| Suffix | Plugin | Runs in | Command |
|---|---|---|---|
| `*Test`, `*Tests`, `*TestCase` | Surefire | `test` | `./mvnw test` |
| `*IT`, `*ITCase` | Failsafe | `integration-test` / `verify` | `./mvnw verify` |

Both are configured in `pom.xml` (Surefire at `:346`, Failsafe at `:361` — the latter also forces
`spring.profiles.active=test`). Use `*IT` for what it means: tests that boot the full context or
Testcontainers and are too slow for the inner loop.

**The trap:** `./mvnw clean test` stops at the `test` phase and never runs a single `*IT`. Eleven
`*IT` classes exist in this module and none of them execute under the inner-loop command. Anything
`*IT` is verified **only** by `./mvnw verify` — before delivering a change that touches integration
behavior, run `verify`, not just `test`, and say which one you ran (see **Testing and Validation**).

Do not "fix" an unexecuted `*IT` by renaming it to `*Test` — that drags a slow, container-dependent
test into every inner-loop run. Run the right command instead.

**Estado (verificado 2026-08-02, em `develop`):** `./mvnw clean verify` passa integralmente — 62
testes de integração, 0 falhas. Ele é gate utilizável; se falhar, é regressão sua, não ruído
herdado.

#### Autenticação em `*IT` de controller: JWT, nunca `@WithMockUser` (mandatory)

`@WithMockUser` produz um principal `User`. O `JwtTenantFilter` só popula o `TenantContext` quando o
principal é um `Jwt` — então com `@WithMockUser` o filtro vira no-op, `getRequiredTenantId()` lança e
**todo request responde 403**, independentemente da role. Some-se a isso que `ROLE_USER` não existe
no domínio (os papéis são `TECNICO`, `ATLETA`, `ADMIN`) e o teste falha por dois motivos ao mesmo
tempo, nenhum deles óbvio na mensagem.

Foi o que manteve `ManualReconciliationControllerIT` vermelho por 2,5 meses, deixando três endpoints
sem cobertura de contrato. Use o post-processor `jwt()` por request:

```java
private RequestPostProcessor jwtDe(UUID subject, UUID tenant, String papel) {
    return jwt()
            .authorities(new SimpleGrantedAuthority("ROLE_" + papel))
            .jwt(j -> j.subject(subject.toString()).claim("tenant_id", tenant.toString()));
}
```

Dois detalhes que fazem o teste passar **por acidente** se ignorados:

- **O subject tem de ser um UUID** e a linha de `Usuario` precisa existir. `createNewUsuario` faz
  `UUID.fromString(keycloakId)`; com subject não-UUID o sync cai no fail-safe do `JwtTenantFilter` e
  o teste fica verde exercitando um caminho degradado.
- **As authorities vão explícitas.** O post-processor `jwt()` não usa o `JwtAuthenticationConverter`
  da aplicação; o default dele mapeia scopes para `SCOPE_*`, e `@PreAuthorize` exige `ROLE_*`.

Não envie `X-Tenant-ID`: a produção resolve tenant pelo claim do JWT, nunca por header. Referências:
`ManualReconciliationControllerIT`, `OnboardingSensitiveDataAccessIT`.

#### `@WebMvcTest` auto-includes `HandlerInterceptor` and `WebMvcConfigurer` (mandatory)

The `@WebMvcTest` slice filter does not stop at controllers: it also picks up every
`HandlerInterceptor` and `WebMvcConfigurer` in the context. Declaring either as `@Component` drags
its whole dependency graph into **all** slices at once — a single `@Component` interceptor with a
service dependency broke 22 slices with 136 errors, none of them related to the interceptor's own
behavior.

Declare cross-cutting web components as `@Bean` in a plain `@Configuration` class instead — the
slice ignores them, and the interceptor is exercised where it belongs (its own unit test plus a
`@SpringBootTest`). Reference: `LgpdConsentInterceptor` and its configuration.

### Assertions

- Prefer **AssertJ** (`assertThat(...)`, `assertThatThrownBy(...)`) for readability — it is the dominant style in this module. For JSON in `MockMvc`, use `jsonPath`.
- JUnit assertions (`assertThrows`, `assertEquals`) remain acceptable in legacy tests; do not mix both styles within a single test.
- When asserting an exception message, check that it **contains** the snippet (`assertThat(ex).hasMessageContaining("...")`) rather than full-string equality, to stay resilient to wording tweaks.
- Helper factory methods (`criarAtleta`, `novoInput`, `outputStub`, ...) belong at the bottom of the outer class, after the `@Nested` blocks.

## Definition of Done (Backend Task)

A backend task is done only if:

1. Implementation matches the active OpenSpec change scope.
2. Corresponding `tasks.md` item is updated.
3. API/spec docs are updated when behavior changed.
4. `./mvnw clean verify` passes (`test` alone does not run the `*IT` suite).
5. No intentional out-of-scope modifications were introduced.

## Delivery Checklist

When finishing a backend task, report:

1. Change-id and completed task.
2. Files changed in backend.
3. Validation commands executed and results.
4. Risks, assumptions, or follow-up items.

## AI-Assisted Code Generation Guidelines

Guidelines for requesting and validating code generated by Claude or other AI models.

### Before Requesting AI-Generated Code

Provide the AI with this context:

```
You are generating code for the Menthoros Backend.

MANDATORY: Follow these rules from apps/menthoros-backend/CLAUDE.md:

1. **Controller Standards**
   - Inject only Service (never Repository)
   - All write endpoints (@PostMapping, @PutMapping, @DeleteMapping) have @PreAuthorize
   - All endpoints have @Operation + @ApiResponses
   - Use ResponseEntity<OutputDto> (never Map<String, Object>)
   - All DTOs are validated with @Valid

2. **DTO & Records Standards**
   - ALL DTOs must be records (not classes)
   - Input DTOs have validation annotations (@NotNull, @Size, etc.)
   - Output DTOs have @JsonInclude(NON_NULL)

3. **Service Standards**
   - EVERY public method documents: Idempotent: YES/NO
   - EVERY public method documents: Side Effects: NONE/Database/External API
   - EVERY public method documents: Tenant-aware: YES/NO
   - Validate inputs (don't trust DTOs)
   - Add logging for entry/exit

4. **Mapper Standards**
   - NULL CHECK: If input is null, throw IllegalArgumentException
   - Never allow silent null returns

5. **Tenant Resolution**
   - Use TenantContext.getRequiredTenantId() for tenant resolution
   - Never read @RequestHeader("X-Tenant-ID") directly
   - Mark controller with @RequireTenant annotation

Test generated code with: ./mvnw clean verify
```

### Validating AI-Generated Code

**Before merging, verify these patterns:**

```bash
# 1. Check for Repository injection in controllers (❌ BAD)
grep -r "@Autowired.*Repository\|private.*Repository" \
  src/main/java/br/com/menthoros/backend/controller/

# 2. Check for class-based DTOs (❌ BAD - should be records)
grep -r "public class.*OutputDto\|public class.*InputDto" \
  src/main/java/br/com/menthoros/backend/dto/

# 3. Check for missing @PreAuthorize on write endpoints (⚠️ WARNING)
grep -B2 "@PostMapping\|@PutMapping\|@DeleteMapping" \
  src/main/java/br/com/menthoros/backend/controller/*.java \
  | grep -v "@PreAuthorize"

# 4. Check for null-unsafe mappers (❌ BAD)
grep -A5 "public.*toOutputDto\|public.*toDomain" \
  src/main/java/br/com/menthoros/backend/mapper/*.java \
  | grep -v "if.*null\|throw.*Illegal"

# 5. Run full test suite, including the *IT integration tests (required for all generated code)
./mvnw clean verify
```

### Code Review Checklist for AI-Generated Code

Use this checklist when reviewing AI-generated code:

- [ ] **Controllers**
  - [ ] Only Service injected (no Repository)
  - [ ] All write endpoints (@POST/@PUT/@DELETE) have `@PreAuthorize`
  - [ ] Every method has `@Operation` + `@ApiResponses`
  - [ ] Returns `ResponseEntity<OutputDto>` (never raw types)
  - [ ] Input DTOs have `@Valid` annotation

- [ ] **Services**
  - [ ] Every public method has full JavaDoc with:
    - [ ] Brief description
    - [ ] `Idempotent: YES/NO`
    - [ ] `Side Effects: ...`
    - [ ] `Tenant-aware: YES/NO`
  - [ ] Validates inputs (not just DTOs)
  - [ ] Logs entry/exit with context

- [ ] **DTOs**
  - [ ] All are `record` (not `class`)
  - [ ] Input DTOs have validation annotations
  - [ ] Output DTOs have `@JsonInclude(NON_NULL)`
  - [ ] Every field has `@Schema(description = "...")`

- [ ] **Mappers**
  - [ ] Null checks on inputs (throws `IllegalArgumentException`)
  - [ ] No silent null returns

- [ ] **Tests**
  - [ ] `./mvnw clean verify` passes (100% success — inclui os `*IT`)
  - [ ] No test failures or errors

### Red Flags in AI-Generated Code

Reject code if it has:

```
❌ Controllers injecting Repository directly
❌ DTOs as mutable classes (not records)
❌ Missing @PreAuthorize on write endpoints
❌ Raw Map<String, Object> returns
❌ Try/catch blocks for HTTP error handling (should be GlobalExceptionHandler)
❌ Mappers without null checks
❌ Services without Idempotency documentation
❌ Services using TenantContext without @RequireTenant on controller
❌ Test failures or compilation errors
```

Last reviewed on: 2026-06-26

## Agent skills

A configuração dos skills de engenharia (`to-tickets`, `to-spec`, `triage`, `qa`, `wayfinder`,
`grill-with-docs`) é **transversal e vive na raiz do workspace** — `CLAUDE.md` da raiz, seção
"Agent skills", e `docs/agents/*.md` versionados em `menthoros-infra/workspace/`. O rastreador é
o **OpenSpec** em `menthoros-product/openspec/changes/`, não o GitHub Issues; os ADRs ficam em
`menthoros-product/adr/`. Não duplicar aqui: a cópia que existia neste repo apontava para GitHub
Issues e `docs/adr/` na raiz, e divergiu da configuração real em silêncio.
