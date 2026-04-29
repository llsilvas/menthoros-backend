## ADDED Requirements

### Requirement: Backend MUST expor endpoint de login público integrado ao Keycloak
O sistema SHALL aceitar credenciais de login via endpoint público backend e autenticar o usuário no Keycloak, retornando token de acesso no contrato padrão do Menthoros.

#### Scenario: Login com credenciais válidas
- **WHEN** `POST /api/public/auth/login` recebe `username` e `password` válidos
- **THEN** o backend autentica no Keycloak e retorna `200` com `accessToken`, `tokenType` e `expiresIn`

#### Scenario: Login com credenciais inválidas
- **WHEN** `POST /api/public/auth/login` recebe credenciais inválidas
- **THEN** o backend retorna `401` com mensagem funcional de credenciais inválidas

#### Scenario: Falha de integração com Keycloak no login
- **WHEN** o Keycloak está indisponível ou falha durante autenticação
- **THEN** o backend retorna erro de upstream (`502` ou `503`) sem expor detalhes internos sensíveis

---

### Requirement: Backend MUST provisionar usuário no Keycloak vinculado ao tenant correto
O sistema SHALL permitir criação administrativa de usuário e MUST refletir o vínculo de tenant para que o JWT emitido contenha `tenant_id` compatível com o enforcement multi-tenant.

#### Scenario: Criação de usuário com sucesso
- **WHEN** `POST /api/admin/usuarios` recebe payload válido com `tenantId` existente
- **THEN** o backend cria usuário no Keycloak, configura senha/roles, sincroniza `tb_usuario` e retorna `201`

#### Scenario: Conflito de usuário já existente
- **WHEN** o email ou username já existe no Keycloak
- **THEN** o backend retorna `409` com mensagem de conflito sem criar duplicidade local

#### Scenario: Tenant inexistente
- **WHEN** `POST /api/admin/usuarios` recebe `tenantId` inexistente
- **THEN** o backend retorna `404` e não cria usuário no Keycloak

---

### Requirement: Endpoint de provisionamento MUST ser restrito a administradores
O sistema SHALL exigir perfil administrativo para criação de usuários, bloqueando usuários sem permissão.

#### Scenario: Admin cria usuário
- **WHEN** um usuário autenticado com role administrativa chama `POST /api/admin/usuarios`
- **THEN** a operação é autorizada e processada normalmente

#### Scenario: Usuário sem permissão tenta criar usuário
- **WHEN** um usuário sem role administrativa chama `POST /api/admin/usuarios`
- **THEN** o backend retorna `403`

---

### Requirement: Sistema MUST evitar inconsistência entre Keycloak e base local em falhas parciais
O sistema SHALL aplicar estratégia de compensação quando a criação no Keycloak ocorre mas a persistência local falha.

#### Scenario: Falha local após criação no Keycloak
- **WHEN** o usuário é criado no Keycloak, mas ocorre erro ao salvar em `tb_usuario`
- **THEN** o backend tenta rollback no Keycloak e retorna erro controlado sem deixar estado inconsistente silencioso

#### Scenario: Falha de rollback no Keycloak
- **WHEN** a compensação falha
- **THEN** o backend registra log estruturado com `tenantId`, `email` e `keycloakUserId` para intervenção operacional

---

### Requirement: Sistema MUST proteger dados sensíveis de autenticação
O sistema SHALL não registrar em logs o conteúdo de senha, access token ou refresh token.

#### Scenario: Erro durante login
- **WHEN** ocorre falha de autenticação no endpoint de login
- **THEN** logs registram contexto técnico mínimo sem incluir senha/token em texto puro

#### Scenario: Erro durante provisionamento
- **WHEN** ocorre falha na integração com Keycloak para criar usuário
- **THEN** logs registram identificadores operacionais (tenant, email, actor) sem segredo sensível
