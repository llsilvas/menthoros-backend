## Why

Hoje o backend valida JWT do Keycloak, mas não possui um fluxo padronizado para:
- criar usuários de forma consistente entre Keycloak e domínio Menthoros;
- vincular o usuário criado ao tenant correto;
- oferecer um endpoint de login backend-friendly para o dashboard consumir sem acoplamento direto ao Keycloak.

Sem esse change, o onboarding depende de ações manuais no Keycloak e o login do frontend fica frágil para evolução operacional.

## What Changes

- Adicionar fluxo de provisionamento de usuário no backend com criação no Keycloak e vínculo ao tenant da assessoria.
- Adicionar endpoint público de login no backend (`/api/public/auth/login`) que realiza troca de credenciais no Keycloak e retorna token padronizado para o frontend.
- Garantir idempotência e tratamento de erros de conflito (usuário já existente) e credenciais inválidas.
- Definir trilha de auditoria mínima para criação e autenticação (sem logar senha/token em texto puro).

## Impact

- APIs novas:
  - `POST /api/public/auth/login`
  - `POST /api/admin/usuarios` (ou rota equivalente protegida para provisionamento)
- Segurança:
  - endpoint de login permitido em `SecurityConfig`;
  - endpoint de criação protegido por role administrativa.
- Integração:
  - backend passa a depender de credenciais de service account/admin client do Keycloak para criar usuário.
