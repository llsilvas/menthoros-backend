# Runbook — Keycloak Organizations para `add-assessoria-onboarding` (Fase 5/6)

Este runbook cobre a infra de Keycloak que **não** é coberta por código testável: habilitar Organizations no realm, configurar o claim de `tenant_id`, criar a role `ATLETA`, implementar o adapter real e migrar do modelo de Groups.

## Estado atual (entregue por código)

- `UserRole.ATLETA` existe (enum + migration V35 atualizando `chk_role`).
- `tb_assessoria.keycloak_organization_id` (V33) e `tb_atleta.usuario_id` (V34, FK `Usuario`↔`Atleta`).
- Endpoints: `POST /api/admin/assessorias` (ADMIN) e `POST /api/v1/atletas/{id}/convite` (TECNICO/ADMIN).
- `KeycloakOrganizationGateway` (interface) + **placeholder** `KeycloakOrganizationGatewayImpl` que **lança** `UnsupportedOperationException` — substituir pelo adapter real abaixo.
- `JwtTenantFilter` resolve `tenant_id` por claim direta `tenant_id` **ou** por `organization.<org>.tenant_id`, e libera `/api/admin/**` sem tenant.

## 1. Habilitar Organizations no realm `menthoros-app`

1. Keycloak 26.x (docker-compose). Realm Settings → **Organizations: On**.
2. Confirmar que o `keycloak-admin-client` em uso expõe a API de Organizations (em 25.0.3 é preview; preferir alinhar a versão do admin-client ao servidor 26.x se a API não estiver disponível).

## 2. Mapper de claim `tenant_id`

Configurar um **attribute/protocol mapper** na Organization que projete o atributo `tenant_id` da Organization no token de acesso, no formato já suportado pelo filtro:
- claim direta `tenant_id`, **ou**
- `organization.<org>.tenant_id` (mapa por organização).

Validar com um token de teste que `JwtTenantFilter.extractTenantId` resolve o `tenant_id` (Task 17 do plano — só ajustar o parsing se o shape real divergir).

## 3. Client role `ATLETA`

Criar a role `ATLETA` (realm/client role conforme o mapeamento de `realm_access.roles`) — o `UsuarioSyncServiceImpl.mapToUserRole` já a reconhece.

## 4. Adapter real `KeycloakOrganizationGatewayImpl`

Substituir o placeholder por uma implementação com `keycloak-admin-client`:
- `criarOrganization(nome, dominio, tenantId)`: cria a Organization no realm, seta o atributo `tenant_id=[tenantId]`, retorna o id da Organization.
- `enviarConviteAtleta(orgId, email, atletaId)`: cria/convida o membro na Organization (convite nativo de Organization quando disponível); o aceite provisiona a conta `ATLETA`.
- Construir o `Keycloak`/`KeycloakBuilder` de forma **lazy** a partir de config (`keycloak.admin.*`: server-url, realm, client-id, client-secret) para não conectar no startup.
- Cobrir com teste de integração (Testcontainers Keycloak) ou verificação manual — não é unit-test puro.

## 5. Migração Groups → Organizations (Fase 6)

Procedimento idempotente, sem downtime (durante a transição, claim direta `tenant_id` e claim de Organization coexistem; o filtro resolve qualquer um):

1. Habilitar Organizations no realm.
2. Para cada `Assessoria` com `keycloakGroupId`: criar a Organization equivalente, migrar membros do Group para a Organization, setar o atributo `tenant_id`, gravar `keycloakOrganizationId`.
3. Tenant `default`: criar a Organization `default` com o mesmo `tenant_id` já usado.
4. Após validação, desabilitar a emissão do claim por Group e depreciar `keycloakGroupId`.

## Verificação end-to-end

- `POST /api/admin/assessorias` (ADMIN) → 201 com `keycloakOrganizationId` preenchido; domínio repetido → 409.
- Cadastrar `Atleta` com email → `POST /api/v1/atletas/{id}/convite` → 202; aceitar o convite (primeiro login ATLETA) → `tb_atleta.usuario_id` preenchido.
- Token de ATLETA resolve `tenant_id`; endpoint tenant-aware retorna apenas dados do tenant; atleta de outro tenant → 404.
