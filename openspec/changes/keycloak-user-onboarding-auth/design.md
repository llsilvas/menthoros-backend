## Context

O Menthoros backend é um OAuth2 Resource Server: ele valida JWT emitido pelo Keycloak, mas não emite token por conta própria. Este change adiciona duas capacidades backend:
1. provisionar usuários no Keycloak vinculados ao tenant;
2. centralizar login em endpoint público do backend como fachada para o token endpoint do Keycloak.

## Goals

- Permitir criação de usuário por fluxo administrativo da API.
- Garantir vínculo multi-tenant no momento da criação.
- Expor contrato de login estável para o frontend sem quebrar o modelo atual de segurança JWT.

## Non-Goals

- Substituir o Keycloak como Identity Provider.
- Implementar refresh token rotation custom.
- Implementar registro público aberto sem autorização.

## Decisions

### D1: Backend como fachada de login

`POST /api/public/auth/login` recebe `username` e `password`, chama o endpoint de token do Keycloak (Direct Grant) e retorna payload normalizado (`accessToken`, `expiresIn`, `refreshToken` opcional).

Racional: desacopla frontend de detalhes de URL/realm/client_id e facilita governança futura (PKCE, antifraude, limitação).

### D2: Provisionamento via client administrativo do Keycloak

`POST /api/admin/usuarios` usa credenciais técnicas para:
- criar usuário no Keycloak;
- configurar senha inicial;
- atribuir grupo/claim necessário para `tenant_id`;
- atribuir roles esperadas pelo backend.

### D3: Persistência e sincronização do domínio

Após criação no Keycloak, o backend garante que `tb_usuario` esteja alinhada (por `keycloak_user_id`/`sub`), com `tenant_id` correto e dados básicos (`nome`, `email`, `ativo`).

### D4: Segurança e autorização

- Login é público (`/api/public/auth/login`) e rate-limited no gateway/reverse-proxy (quando disponível).
- Criação de usuário exige role administrativa (`ROLE_ADMIN` ou equivalente definida no projeto).
- Não registrar senha, access token ou refresh token em logs.

### D5: Erros padronizados

- Login inválido: `401` com mensagem funcional.
- Usuário já existe (email/username): `409`.
- Tenant inválido/inexistente: `404`.
- Falha Keycloak/transiente: `502` (ou `503` conforme estratégia do projeto).

## API Contracts (Propostos)

### 1) Login

`POST /api/public/auth/login`

Request:
- `username: string`
- `password: string`

Response 200:
- `accessToken: string`
- `expiresIn: number`
- `refreshToken?: string`
- `tokenType: "Bearer"`

### 2) Criação de usuário

`POST /api/admin/usuarios`

Request:
- `nome: string`
- `email: string`
- `username: string`
- `senhaTemporaria: string`
- `tenantId: uuid`
- `roles: string[]`

Response 201:
- `id: uuid` (domínio Menthoros)
- `keycloakUserId: string`
- `tenantId: uuid`
- `email: string`
- `username: string`

## Observabilidade e Auditoria

- Log estruturado para eventos de provisionamento (sucesso/falha, tenantId, actor, keycloakUserId quando existir).
- Métricas recomendadas:
  - `auth_login_success_total`
  - `auth_login_failure_total`
  - `user_provision_success_total`
  - `user_provision_failure_total`

## Riscos

- Dependência de disponibilidade do Keycloak para login e criação.
- Inconsistência parcial entre Keycloak e banco local em falhas intermediárias.
- Necessidade de boa política de senha inicial e troca obrigatória.

## Mitigações

- Fluxo transacional compensatório: se persistência local falhar após criação no Keycloak, tentar rollback no Keycloak.
- Timeout e retry com backoff para chamadas administrativas.
- Testes de integração cobrindo cenários de falha parcial.
