# Keycloak - Guia Completo de Autenticação

**Projeto**: Menthoros
**Data**: 2025-10-13
**Versão**: 1.0.0

> **Nota**: Este documento é uma consolidação dos arquivos `KEYCLOAK_AUTHENTICATION_GUIDE.md` e `KEYCLOAK_MANUAL_SETUP.md`, oferecendo uma guia completa de autenticação e configuração do Keycloak para o Menthoros.

---

## 📋 Sumário

1. [Setup e Instalação](#1-setup-e-instalação)
2. [Configuração Manual Passo a Passo](#2-configuração-manual-passo-a-passo)
3. [Configuração do Client](#3-configuração-do-client)
4. [Fluxos de Autenticação](#4-fluxos-de-autenticação)
5. [Obtendo Tokens JWT](#5-obtendo-tokens-jwt)
6. [Testando a API](#6-testando-a-api)
7. [Troubleshooting](#7-troubleshooting)
8. [Checklist de Configuração](#8-checklist-de-configuração)

---

## 1. Setup e Instalação

### Opção A: Setup Automático (Recomendado)

Antes de seguir os passos manuais, utilize o **setup automático** disponível em `scripts/`:

#### Realm Import (mais rápido — na primeira inicialização)

O arquivo `scripts/keycloak/menthoros-app-realm.json` é importado automaticamente pelo Keycloak no primeiro `docker compose up`. Ele cria:

- Realm `menthoros-app` com todas as configurações
- Clients `menthoros-backend` e `menthoros-frontend`
- Roles: ADMIN, TECNICO, VISUALIZADOR, ATLETA
- Mappers JWT: `tenant_id`, `roles`, `groups`
- Grupos de teste: `assessoria-test-1` e `assessoria-test-2`
- Usuários de teste prontos para uso

> Keycloak ignora o import se o realm já existir. O volume está configurado no `docker-compose.multi-tenancy.yml`.

#### Script REST API (para realm existente ou re-configuração)

```bash
# Executar a partir da raiz do workspace
./scripts/setup-keycloak.sh

# Com variáveis customizadas:
KC_CLIENT_SECRET=meu-secret KC_ADMIN_PASSWORD=minhasenha ./scripts/setup-keycloak.sh
```

O script é **idempotente** — pode ser executado múltiplas vezes sem duplicar recursos.

#### Usuários de Teste criados pelo setup automático

| Usuário | Senha | Role | Assessoria |
|---------|-------|------|------------|
| `admin.test1` | `Admin123!` | ADMIN | assessoria-test-1 |
| `tecnico.test1` | `Tecnico123!` | TECNICO | assessoria-test-1 |
| `atleta.test1` | `Atleta123!` | ATLETA | assessoria-test-1 |
| `admin.test2` | `Admin123!` | ADMIN | assessoria-test-2 |

> ⚠️ Após o setup, criar as assessorias correspondentes no banco (ver seção PASSO 4 abaixo).

---

## 2. Configuração Manual Passo a Passo

### Problema

Se o setup automático não foi utilizado, seu JWT pode não conter os claims necessários:

- ❌ `tenant_id` (obrigatório - identifica a assessoria)
- ❌ `roles` (obrigatório - ADMIN, TECNICO, VISUALIZADOR)

**Sem esses claims, a aplicação rejeitará o token com HTTP 403.**

### PASSO 1: Criar Client Roles

1. **Acesse**: http://localhost:8080/admin
2. **Login**: admin / admin123
3. **Navegue**:
   - Realm: `menthoros-app` (dropdown superior esquerdo)
   - Menu lateral: **Clients**
   - Clique em: `menthoros-backend`
4. **Aba: Roles**
5. **Criar 4 roles**:

   **Role 1: ADMIN**
   - Clique: **Create role**
   - Role name: `ADMIN`
   - Description: `Administrador da assessoria - acesso total à plataforma`
   - Clique: **Save**

   **Role 2: TECNICO**
   - Clique: **Create role**
   - Role name: `TECNICO`
   - Description: `Técnico - gerencia atletas, planos de treino e prescrições`
   - Clique: **Save**

   **Role 3: VISUALIZADOR**
   - Clique: **Create role**
   - Role name: `VISUALIZADOR`
   - Description: `Visualizador - apenas leitura dos dados da assessoria`
   - Clique: **Save**

   **Role 4: ATLETA**
   - Clique: **Create role**
   - Role name: `ATLETA`
   - Description: `Atleta - acesso ao próprio perfil, dados de treino e histórico`
   - Clique: **Save**

✅ **Resultado**: 4 roles criadas no client `menthoros-backend`

| Role | Acesso |
|------|--------|
| `ADMIN` | Acesso total: usuários, atletas, planos, configurações da assessoria |
| `TECNICO` | Gerencia atletas e planos de treino, sem acesso a configurações |
| `VISUALIZADOR` | Apenas leitura: visualiza dados sem modificar |
| `ATLETA` | Acesso restrito ao próprio perfil, treinos e histórico pessoal |

### PASSO 2: Criar Token Mapper para "roles"

1. **Ainda em**: Clients → `menthoros-backend`
2. **Aba: Client scopes**
3. **Clique**: em `menthoros-backend-dedicated` (link azul)
4. **Aba: Mappers**
5. **Clique**: **Add mapper** → **By configuration**
6. **Selecione**: **User Client Role**
7. **Preencha**:
   ```
   Name: client-roles
   Client ID: menthoros-backend
   Token Claim Name: roles
   Claim JSON Type: String
   Multivalued: ON (toggle ativado)
   Add to access token: ON
   Add to userinfo: ON
   ```
8. **Clique**: **Save**

✅ **Resultado**: Mapper que incluirá as roles do client no JWT

### PASSO 3: Criar Token Mapper para "tenant_id"

1. **Ainda em**: Client scopes → `menthoros-backend-dedicated`
2. **Aba: Mappers**
3. **Clique**: **Add mapper** → **By configuration**
4. **Selecione**: **User Attribute**
5. **Preencha**:
   ```
   Name: tenant_id
   User Attribute: tenant_id
   Token Claim Name: tenant_id
   Claim JSON Type: String
   Add to ID token: ON
   Add to access token: ON
   Add to userinfo: ON
   ```
6. **Clique**: **Save**

✅ **Resultado**: Mapper que incluirá o `tenant_id` do Group no JWT

### PASSO 4: Criar UUID para Assessoria

Antes de criar o Group, você precisa ter uma assessoria cadastrada no banco.

**Opção A: Verificar assessoria existente**

```sql
-- Conectar no PostgreSQL
docker exec -it menthoros-db psql -U menthoros -d menthoros-multi

-- Listar assessorias
SELECT id, nome, cnpj, plano FROM tb_assessoria;

-- Copiar o UUID de uma assessoria
-- Exemplo: 123e4567-e89b-12d3-a456-426614174000
```

**Opção B: Criar nova assessoria**

```sql
-- Gerar UUID
SELECT gen_random_uuid();
-- Copie o UUID gerado

-- Criar assessoria
INSERT INTO tb_assessoria (id, nome, cnpj, plano, ativo, created_at, updated_at)
VALUES (
    'COLE_UUID_AQUI',  -- UUID gerado acima
    'Assessoria Teste',
    '12345678000100',
    'BASICO',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- Verificar
SELECT id, nome FROM tb_assessoria WHERE nome = 'Assessoria Teste';
```

**⚠️ IMPORTANTE**: Copie o UUID da assessoria. Você vai usar no próximo passo!

### PASSO 5: Criar Group com Attribute tenant_id

1. **Acesse**: Admin Console
2. **Navegue**: Menu lateral → **Groups**
3. **Clique**: **Create group**
4. **Preencha**:
   ```
   Name: Assessoria Teste
   ```
5. **Clique**: **Create**
6. **Clique** no grupo recém-criado: `Assessoria Teste`
7. **Aba: Attributes**
8. **Adicione attribute**:
   ```
   Key: tenant_id
   Value: [COLE O UUID DA ASSESSORIA DO PASSO 4]
   ```
   Exemplo: `123e4567-e89b-12d3-a456-426614174000`
9. **Clique**: **Save** ou ícone de confirmação

✅ **Resultado**: Group criado com attribute `tenant_id`

### PASSO 6: Adicionar Usuário ao Group

1. **Navegue**: Menu lateral → **Users**
2. **Clique** no usuário: `admin` (ou seu usuário)
3. **Aba: Groups**
4. **Clique**: **Join Group**
5. **Selecione**: `Assessoria Teste`
6. **Clique**: **Join**

✅ **Resultado**: Usuário `admin` agora faz parte do grupo (e herdará o `tenant_id`)

### PASSO 7: Atribuir Role ao Usuário

1. **Ainda em**: Users → `admin`
2. **Aba: Role mapping**
3. **Clique**: **Assign role**
4. **Filtrar**:
   - Clique em: **Filter by clients**
   - Selecione: `menthoros-backend`
5. **Selecione**: ✅ `ADMIN` (ou outra role)
6. **Clique**: **Assign**

✅ **Resultado**: Usuário tem role `ADMIN` do client `menthoros-backend`

---

## 3. Configuração do Client

### Erro Comum: "Client not allowed for direct access grants"

Este erro ocorre quando o client não está configurado para permitir o fluxo `password` (Direct Access Grants).

**Solução via Admin Console:**

1. Acesse: http://localhost:8443/admin
2. Login com admin credentials
3. Selecione realm: `menthoros-app`
4. Menu: **Clients** → `menthoros-backend`
5. **Settings** ou **Capability config**:
   - ✅ **Direct access grants enabled** (Resource Owner Password Credentials)
   - ✅ **Standard flow enabled** (Authorization Code Flow)
   - ✅ **Service accounts roles enabled** (Client Credentials)
   - 🔴 **Client authentication**: ON (confidential client)
6. Clique em **Save**

**Solução via Script:**

```bash
# Execute o script automático
./scripts/keycloak-enable-direct-grants.sh
```

### Configurações Recomendadas do Client

```json
{
  "clientId": "menthoros-backend",
  "enabled": true,
  "clientAuthenticatorType": "client-secret",
  "redirectUris": ["http://localhost:8098/*"],
  "webOrigins": ["http://localhost:8098"],
  "directAccessGrantsEnabled": true,
  "standardFlowEnabled": true,
  "serviceAccountsEnabled": true,
  "publicClient": false,
  "protocol": "openid-connect"
}
```

---

## 4. Fluxos de Autenticação

### 4.1 Password Grant (Direct Access Grants) - Desenvolvimento

**Use quando:** Testando a API manualmente, scripts, Postman

```bash
POST http://localhost:8443/realms/menthoros-app/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
client_id=menthoros-backend
client_secret=SEU_CLIENT_SECRET
username=usuario@example.com
password=senha123
```

**Resposta:**

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI...",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI...",
  "token_type": "Bearer",
  "scope": "openid profile email"
}
```

### 4.2 Authorization Code Flow - Produção

**Use quando:** Aplicação web com frontend (React, Angular, Vue)

```bash
# 1. Redireciona usuário para login
GET http://localhost:8443/realms/menthoros-app/protocol/openid-connect/auth?
  response_type=code&
  client_id=menthoros-backend&
  redirect_uri=http://localhost:8098/callback&
  scope=openid profile email

# 2. Após login, Keycloak redireciona com code
http://localhost:8098/callback?code=AUTH_CODE

# 3. Troca code por token
POST http://localhost:8443/realms/menthoros-app/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
client_id=menthoros-backend
client_secret=SEU_CLIENT_SECRET
code=AUTH_CODE
redirect_uri=http://localhost:8098/callback
```

### 4.3 Client Credentials - Serviços Backend

**Use quando:** Comunicação service-to-service, jobs, integrações

```bash
POST http://localhost:8443/realms/menthoros-app/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
client_id=menthoros-backend
client_secret=SEU_CLIENT_SECRET
```

---

## 5. Obtendo Tokens JWT

### 5.1 Via cURL

```bash
#!/bin/bash

# Obter Client Secret
# Admin Console → Clients → menthoros-backend → Credentials → Client Secret

KEYCLOAK_URL="http://localhost:8443"
REALM="menthoros-app"
CLIENT_ID="menthoros-backend"
CLIENT_SECRET="COLE_AQUI_O_CLIENT_SECRET"
USERNAME="usuario@example.com"
PASSWORD="senha123"

# Obter token
TOKEN_RESPONSE=$(curl -sS -X POST \
  "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=$CLIENT_ID" \
  -d "client_secret=$CLIENT_SECRET" \
  -d "username=$USERNAME" \
  -d "password=$PASSWORD")

# Extrair access_token
ACCESS_TOKEN=$(echo $TOKEN_RESPONSE | jq -r '.access_token')

echo "Access Token:"
echo $ACCESS_TOKEN
echo ""

# Decodificar JWT (payload)
echo "JWT Payload:"
echo $ACCESS_TOKEN | cut -d'.' -f2 | base64 -d 2>/dev/null | jq .
```

### 5.2 Via Postman

**1. Configure as variáveis:**

```
KEYCLOAK_URL: http://localhost:8443
REALM: menthoros-app
CLIENT_ID: menthoros-backend
CLIENT_SECRET: [obter do Keycloak]
USERNAME: usuario@example.com
PASSWORD: senha123
```

**2. Crie request:**

- **Method**: POST
- **URL**: `{{KEYCLOAK_URL}}/realms/{{REALM}}/protocol/openid-connect/token`
- **Body** (x-www-form-urlencoded):
  ```
  grant_type: password
  client_id: {{CLIENT_ID}}
  client_secret: {{CLIENT_SECRET}}
  username: {{USERNAME}}
  password: {{PASSWORD}}
  ```

**3. Extraia token:**

- Aba **Tests**, adicione script:
  ```javascript
  var response = pm.response.json();
  pm.environment.set("ACCESS_TOKEN", response.access_token);
  ```

### 5.3 Via HTTPie

```bash
http POST http://localhost:8443/realms/menthoros-app/protocol/openid-connect/token \
  grant_type=password \
  client_id=menthoros-backend \
  client_secret=SEU_CLIENT_SECRET \
  username=usuario@example.com \
  password=senha123
```

---

## 6. Testando a API

### 6.1 Verificar Claims do JWT

```bash
# Salvar token em variável
ACCESS_TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI..."

# Decodificar e verificar claims
echo $ACCESS_TOKEN | cut -d'.' -f2 | base64 -d 2>/dev/null | jq .
```

**Claims esperados:**

```json
{
  "sub": "uuid-do-usuario",
  "email": "usuario@example.com",
  "given_name": "João",
  "family_name": "Silva",
  "tenant_id": "uuid-da-assessoria",  ← CRÍTICO!
  "roles": ["ADMIN"],                  ← CRÍTICO!
  "email_verified": true,
  "iat": 1697123456,
  "exp": 1697123756,
  "iss": "http://localhost:8443/realms/menthoros-app"
}
```

**⚠️ IMPORTANTE:** Se `tenant_id` ou `roles` não aparecerem, revise a configuração dos Token Mappers no Keycloak!

### 6.2 Testar Endpoint da API

```bash
# Health check (público - sem token)
curl -X GET http://localhost:8098/actuator/health

# Listar atletas (requer autenticação)
curl -X GET http://localhost:8098/api/atletas \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json"
```

**Resposta esperada (sucesso):**

```json
[
  {
    "id": "uuid",
    "nome": "João Silva",
    "email": "joao@example.com",
    ...
  }
]
```

**Respostas de erro:**

```bash
# 401 Unauthorized - Token inválido ou expirado
{
  "error": "invalid_token",
  "error_description": "Token signature validation failed"
}

# 403 Forbidden - JWT sem tenant_id
{
  "error": "JWT sem tenant_id. Verifique a configuração do Keycloak Group."
}

# 403 Forbidden - Tentando acessar dados de outro tenant
{
  "error": "Acesso negado"
}
```

### 6.3 Verificar Sincronização de Usuário

Após fazer login, verifique se o usuário foi criado/atualizado em `tb_usuario`:

```sql
-- Conectar no PostgreSQL
docker exec -it menthoros-db psql -U menthoros -d menthoros-multi

-- Verificar usuários sincronizados
SELECT
    id,
    keycloak_id,
    email,
    nome,
    sobrenome,
    role,
    ativo,
    ultimo_acesso,
    ultima_sinc
FROM tb_usuario
ORDER BY ultimo_acesso DESC;
```

### 6.4 Verificar Logs da Aplicação

```bash
# Ver logs em tempo real
docker-compose logs -f menthoros-app

# Filtrar logs de autenticação
docker-compose logs menthoros-app | grep -E "Tenant|Usuario|JWT"
```

**Logs esperados (sucesso):**

```
2025-10-13 15:00:00 DEBUG TenantContext: Setting tenant abc123... for current thread
2025-10-13 15:00:00 DEBUG UsuarioSyncService: Sincronizando usuário: keycloakId=user123...
2025-10-13 15:00:00 INFO  UsuarioSyncService: Usuário sincronizado: id=user123, email=user@example.com
2025-10-13 15:00:00 DEBUG JwtTenantFilter: Tenant abc123... configurado para a requisição /api/atletas
```

---

## 7. Troubleshooting

### Erro: "Client not allowed for direct access grants"

**Causa:** Direct Access Grants não habilitado no client

**Solução:**
1. Admin Console → Clients → menthoros-backend → Settings
2. ✅ Habilitar: **Direct access grants enabled**
3. Salvar

Ou execute:
```bash
./scripts/keycloak-enable-direct-grants.sh
```

---

### Erro: "Invalid client credentials"

**Causa:** Client secret incorreto

**Solução:**
1. Admin Console → Clients → menthoros-backend → Credentials
2. Copiar **Client Secret**
3. Usar o secret correto na requisição

---

### Erro: JWT sem claim "tenant_id"

**Causa:** Token Mapper não configurado

**Solução:**

1. **Criar Group Attribute:**
   - Admin Console → Groups → Selecionar grupo
   - Aba **Attributes**
   - Adicionar: `tenant_id` = `uuid-da-assessoria`

2. **Criar Token Mapper:**
   - Admin Console → Clients → menthoros-backend → Client scopes
   - Selecionar scope padrão (ex: `menthoros-backend-dedicated`)
   - Aba **Mappers** → **Add mapper** → **By configuration**
   - Selecionar: **User Attribute**
   - Configurar:
     ```
     Name: tenant_id
     User Attribute: tenant_id (do Group)
     Token Claim Name: tenant_id
     Claim JSON Type: String
     ✅ Add to ID token
     ✅ Add to access token
     ✅ Add to userinfo
     ```

3. **Testar novamente:**
   - Obter novo token
   - Decodificar e verificar se `tenant_id` aparece

---

### Erro: JWT sem claim "roles"

**Causa:** Role Mapper não configurado

**Solução:**

1. **Criar Client Roles:**
   - Admin Console → Clients → menthoros-backend → Roles
   - Criar roles: `ADMIN`, `TECNICO`, `VISUALIZADOR`

2. **Atribuir roles aos usuários:**
   - Admin Console → Users → Selecionar usuário
   - Aba **Role mapping** → **Assign role**
   - Filtrar por client: `menthoros-backend`
   - Selecionar roles apropriadas

3. **Criar Token Mapper:**
   - Admin Console → Clients → menthoros-backend → Client scopes
   - Selecionar scope padrão
   - Aba **Mappers** → **Add mapper** → **By configuration**
   - Selecionar: **User Client Role**
   - Configurar:
     ```
     Name: client-roles
     Client ID: menthoros-backend
     Token Claim Name: roles
     Claim JSON Type: String
     ✅ Add to access token
     Multivalued: ON
     ```

---

### Erro: "401 Unauthorized" ao acessar API

**Possíveis causas:**

1. **Token expirado:**
   - Tokens expiram (padrão: 5 minutos)
   - Obtenha um novo token

2. **Issuer incorreto:**
   - Verifique `application.yml`:
     ```yaml
     spring.security.oauth2.resourceserver.jwt.issuer-uri:
       http://localhost:8443/realms/menthoros-app
     ```
   - URL deve estar acessível da aplicação

3. **JWK Set não acessível:**
   - Teste: `curl http://localhost:8443/realms/menthoros-app/protocol/openid-connect/certs`
   - Deve retornar as chaves públicas do Keycloak

---

### Erro: "403 Forbidden" - Acesso negado

**Possíveis causas:**

1. **Sem tenant_id no JWT:**
   - Ver seção "JWT sem claim tenant_id"

2. **Tentando acessar dados de outro tenant:**
   - Cada usuário só acessa dados do próprio tenant
   - Verificar que `tenant_id` do JWT corresponde aos dados

3. **Role insuficiente:**
   - VISUALIZADOR: apenas leitura
   - TECNICO: gerencia atletas
   - ADMIN: acesso total
   - Verificar roles no JWT

---

### Erro: Usuário não sincronizado em tb_usuario

**Causa:** Erro na sincronização ou tenant não existe

**Verificar logs:**
```bash
docker-compose logs menthoros-app | grep -A5 "Erro ao sincronizar"
```

**Possíveis problemas:**

1. **Tenant não existe:**
   ```sql
   -- Verificar se assessoria existe
   SELECT id, nome FROM tb_assessoria;

   -- Criar assessoria se necessário
   INSERT INTO tb_assessoria (id, nome, cnpj, plano, ativo)
   VALUES ('uuid-do-tenant', 'Assessoria Teste', '12345678000100', 'BASICO', true);
   ```

2. **tenant_id no Keycloak inválido:**
   - Group Attribute `tenant_id` deve ser UUID válido
   - Deve corresponder a uma assessoria existente

---

### Problema: JWT não tem `tenant_id`

**Verificar:**
1. Group tem attribute `tenant_id`? (Admin Console → Groups → seu grupo → Attributes)
2. Usuário está no Group? (Admin Console → Users → seu usuário → Groups)
3. Mapper está configurado? (Client scopes → menthoros-backend-dedicated → Mappers → tenant_id)
4. Token é **NOVO**? (obtido após as configurações)

---

### Problema: JWT não tem `roles`

**Verificar:**
1. Client Roles existem? (Clients → menthoros-backend → Roles: ADMIN, TECNICO, VISUALIZADOR, ATLETA)
2. Usuário tem role? (Users → seu usuário → Role mapping → filtrar por client `menthoros-backend`)
3. Mapper está configurado? (Client scopes → menthoros-backend-dedicated → Mappers → client-roles)
4. Mapper tem `Multivalued: ON`?
5. Token é **NOVO**?

---

### Problema: API retorna 403 "Assessoria não encontrada"

**Causa**: `tenant_id` no JWT não existe em `tb_assessoria`

**Solução**:
```sql
-- Verificar se tenant existe
SELECT id, nome FROM tb_assessoria WHERE id = 'UUID_DO_TENANT_ID_DO_JWT';

-- Se não existir, criar
INSERT INTO tb_assessoria (id, nome, cnpj, plano, ativo, created_at, updated_at)
VALUES ('UUID_DO_TENANT_ID_DO_JWT', 'Assessoria Teste', '12345678000100', 'BASICO', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
```

---

### Problema: Usuário não aparece em tb_usuario

**Verificar logs**:
```bash
docker-compose logs menthoros-app | grep -E "Usuario|Sincroniz"
```

**Se houver erro de sincronização**:
- Verificar se assessoria existe (query acima)
- Verificar se application está rodando
- Verificar logs completos para detalhes do erro

---

## 8. Checklist de Configuração

Use este checklist para garantir que tudo está configurado:

### Keycloak

- [ ] Realm `menthoros-app` criado
- [ ] Client `menthoros-backend` criado
- [ ] ✅ Direct access grants enabled
- [ ] ✅ Client authentication ON (confidential)
- [ ] Client secret gerado e copiado
- [ ] Client Roles criadas: ADMIN, TECNICO, VISUALIZADOR, ATLETA
- [ ] Token Mapper `tenant_id` configurado
- [ ] Token Mapper `roles` configurado
- [ ] Group criado para assessoria
- [ ] Group Attribute `tenant_id` configurado (UUID válido)
- [ ] Usuário de teste criado
- [ ] Usuário adicionado ao Group
- [ ] Roles atribuídas ao usuário

### Aplicação

- [ ] `application.yml` com issuer-uri correto
- [ ] `application.yml` com jwk-set-uri correto
- [ ] SecurityConfig com OAuth2 Resource Server
- [ ] JwtTenantFilter configurado
- [ ] UsuarioSyncService implementado
- [ ] Assessoria (tenant) criada no banco

### Testes

- [ ] Obter token JWT com sucesso
- [ ] JWT contém claim `tenant_id`
- [ ] JWT contém claim `roles`
- [ ] GET /actuator/health retorna 200
- [ ] GET /api/atletas com token retorna 200
- [ ] Usuário sincronizado em tb_usuario
- [ ] Logs mostram tenant configurado

---

## 📚 Próximos Passos

Após completar esta configuração:

1. **Criar mais usuários** com diferentes roles (TECNICO, VISUALIZADOR)
2. **Criar mais groups** para representar outras assessorias
3. **Testar isolamento** de tenant (usuário do tenant A não vê dados do tenant B)
4. **Implementar testes** de segurança (ver `docs/SECURITY_TESTS.md`)
5. **Adicionar filtros** de tenant nos repositories (ver `docs/SECURITY_CHECKLIST.md`)

---

**Última atualização**: 2025-10-13
**Responsável**: Equipe Menthoros
**Dúvidas?** Consulte os arquivos originais ou entre em contato com a equipe de desenvolvimento
