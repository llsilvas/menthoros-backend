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

## Mandatory Workflow (OpenSpec-first)

Never start implementation directly in code.

1. Identify active change in `menthoros-product/openspec/changes/<change-id>`.
2. Read in order:
   - `proposal.md`
   - `design.md` (if present)
   - `tasks.md`
   - affected `specs/**/spec.md`
3. Execute one `tasks.md` item at a time.
4. If behavior changes, update OpenSpec in the same work.
5. Keep changes minimal and in-scope.

## Coding Rules (Backend)

- Keep layered architecture boundaries:
  - `controller`: transport/http only.
  - `service`: business rules and orchestration.
  - `repository`: persistence access only.
- Validate request DTOs with Bean Validation (`@Valid`, `@NotNull`, etc).
- Keep API contracts stable unless explicitly required by the active change.
- For domain or persistence changes, prefer adding code over risky refactors.
- Do not introduce new dependencies without clear technical justification.

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
- Every public method must have `@Operation(summary = "...")`.
- Every public method must have `@ApiResponses` listing all possible HTTP status codes.
- Use `@Parameter` for path/query parameters that need description.

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

## Multi-tenancy and Security Guardrails

- Never bypass tenant isolation rules.
- Never remove tenant-aware filters/checks in request flow.
- Preserve authN/authZ behavior unless change scope explicitly requires update.
- Do not hardcode secrets or credentials.

## Database and Migration Rules

- All schema changes must go through Flyway (`src/main/resources/db/migration`).
- Never edit an already applied migration; create a new versioned migration.
- Keep migration names deterministic and descriptive.

## Testing and Validation

Run from `apps/menthoros-backend`.

Required before delivery:

```bash
./mvnw clean test
```

Useful checks when needed:

```bash
./mvnw test
./mvnw verify
./mvnw clean compile
```

If containerized dependencies are required for the task:

```bash
docker compose config
docker compose up -d
```

## Definition of Done (Backend Task)

A backend task is done only if:

1. Implementation matches the active OpenSpec change scope.
2. Corresponding `tasks.md` item is updated.
3. API/spec docs are updated when behavior changed.
4. `./mvnw clean test` passes.
5. No intentional out-of-scope modifications were introduced.

## Delivery Checklist

When finishing a backend task, report:

1. Change-id and completed task.
2. Files changed in backend.
3. Validation commands executed and results.
4. Risks, assumptions, or follow-up items.

Last reviewed on: 2026-05-14
