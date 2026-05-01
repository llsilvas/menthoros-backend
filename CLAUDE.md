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

Last reviewed on: 2026-04-30
