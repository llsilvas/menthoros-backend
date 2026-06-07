# Configuração Manual do Keycloak - Passo a Passo

**Projeto**: Menthoros
**Objetivo**: Adicionar `tenant_id` e `roles` ao JWT
**Tempo estimado**: 15 minutos

---

## 🚀 Setup Automático (Recomendado)

Antes de seguir os passos manuais, tente o **setup automático** disponível em `scripts/`:

### Opção A: Realm Import (mais rápido — na primeira inicialização)

O arquivo `scripts/keycloak/menthoros-app-realm.json` é importado automaticamente pelo Keycloak
no primeiro `docker compose up`. Ele cria:
- Realm `menthoros-app` com todas as configurações
- Clients `menthoros-backend` e `menthoros-frontend`
- Roles: ADMIN, TECNICO, VISUALIZADOR, ATLETA
- Mappers JWT: `tenant_id`, `roles`, `groups`
- Grupos de teste: `assessoria-test-1` e `assessoria-test-2`
- Usuários de teste prontos para uso

> Keycloak ignora o import se o realm já existir. O volume está configurado no `docker-compose.multi-tenancy.yml`.

### Opção B: Script REST API (para realm existente ou re-configuração)

```bash
# Executar a partir da raiz do workspace
./scripts/setup-keycloak.sh

# Com variáveis customizadas:
KC_CLIENT_SECRET=meu-secret KC_ADMIN_PASSWORD=minhasenha ./scripts/setup-keycloak.sh
```

O script é **idempotente** — pode ser executado múltiplas vezes sem duplicar recursos.

### Usuários de Teste criados pelo setup automático

| Usuário | Senha | Role | Assessoria |
|---------|-------|------|------------|
| `admin.test1` | `Admin123!` | ADMIN | assessoria-test-1 |
| `tecnico.test1` | `Tecnico123!` | TECNICO | assessoria-test-1 |
| `atleta.test1` | `Atleta123!` | ATLETA | assessoria-test-1 |
| `admin.test2` | `Admin123!` | ADMIN | assessoria-test-2 |

> ⚠️ Após o setup, criar as assessorias correspondentes no banco (ver seção PASSO 4 abaixo).

---

## 📋 Problema Atual (Setup Manual)

Seu JWT não contém os claims necessários:
- ❌ `tenant_id` (obrigatório - identifica a assessoria)
- ❌ `roles` (obrigatório - ADMIN, TECNICO, VISUALIZADOR)

**Sem esses claims, a aplicação rejeitará o token com HTTP 403.**

---

## 🛠️ Solução: Configuração Manual

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

---

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

---

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

---

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

---

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

---

### PASSO 6: Adicionar Usuário ao Group

1. **Navegue**: Menu lateral → **Users**
2. **Clique** no usuário: `admin` (ou seu usuário)
3. **Aba: Groups**
4. **Clique**: **Join Group**
5. **Selecione**: `Assessoria Teste`
6. **Clique**: **Join**

✅ **Resultado**: Usuário `admin` agora faz parte do grupo (e herdará o `tenant_id`)

---

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

## 🧪 TESTE: Obter Novo Token

Agora que tudo está configurado, obtenha um novo token:

```bash
# Obter Client Secret (se ainda não tem)
# Admin Console → Clients → menthoros-backend → Credentials → Copy Secret

# Obter token (substitua CLIENT_SECRET e senha)
curl -X POST http://localhost:8443/realms/menthoros-app/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=menthoros-backend" \
  -d "client_secret=SEU_CLIENT_SECRET" \
  -d "username=admin" \
  -d "password=SUA_SENHA" | jq .
```

**Copie o `access_token` da resposta e decodifique:**

```bash
# Colar o access_token aqui
TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."

# Decodificar payload
echo $TOKEN | cut -d'.' -f2 | base64 -d 2>/dev/null | jq .
```

---

## ✅ Verificação: Claims Esperados

Você deve ver estes claims no JWT:

```json
{
  "sub": "ebe459eb-a9de-4b72-945d-aa8eb3fc9bee",
  "email": "lsilva.info@gmail.com",
  "given_name": "LEANDRO",
  "family_name": "Alves DA SILVA",
  "tenant_id": "123e4567-e89b-12d3-a456-426614174000",  ← ✅ NOVO!
  "roles": ["ADMIN"],                                   ← ✅ NOVO!
  "email_verified": true,
  ...
}
```

**Se `tenant_id` e `roles` aparecerem, SUCESSO!** 🎉

---

## 🚀 Testar na API

```bash
# Usar o token obtido acima
curl -X GET http://localhost:8098/api/atletas \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

**Respostas esperadas:**

✅ **200 OK** - Lista de atletas (pode estar vazia se não houver dados)
```json
[]
```

✅ **Logs da aplicação** (docker-compose logs -f menthoros-app):
```
DEBUG TenantContext: Setting tenant 123e4567-... for current thread
DEBUG UsuarioSyncService: Sincronizando usuário: keycloakId=ebe459eb-...
INFO  UsuarioSyncService: Usuário sincronizado: id=ebe459eb-..., email=lsilva.info@gmail.com, role=ADMIN
```

❌ **403 Forbidden** - JWT sem tenant_id:
```json
{
  "error": "JWT sem tenant_id. Verifique a configuração do Keycloak Group."
}
```
→ Revisar PASSO 5 (Group com attribute)

❌ **401 Unauthorized** - Token inválido/expirado:
```json
{
  "error": "invalid_token"
}
```
→ Obter novo token

---

## 📊 Checklist de Configuração

Marque cada item conforme completa:

- [ ] **PASSO 1**: 4 Client Roles criadas (ADMIN, TECNICO, VISUALIZADOR, ATLETA)
- [ ] **PASSO 2**: Mapper "client-roles" criado
- [ ] **PASSO 3**: Mapper "tenant_id" criado
- [ ] **PASSO 4**: UUID de assessoria obtido/criado em tb_assessoria
- [ ] **PASSO 5**: Group criado com attribute tenant_id
- [ ] **PASSO 6**: Usuário adicionado ao Group
- [ ] **PASSO 7**: Role atribuída ao usuário
- [ ] **TESTE**: Token obtido com sucesso
- [ ] **TESTE**: JWT contém `tenant_id`
- [ ] **TESTE**: JWT contém `roles`
- [ ] **TESTE**: API retorna 200 OK
- [ ] **TESTE**: Usuário sincronizado em tb_usuario

---

## 🔍 Troubleshooting

### Problema: JWT não tem `tenant_id`

**Verificar:**
1. Group tem attribute `tenant_id`? (Admin Console → Groups → seu grupo → Attributes)
2. Usuário está no Group? (Admin Console → Users → seu usuário → Groups)
3. Mapper está configurado? (Client scopes → menthoros-backend-dedicated → Mappers → tenant_id)
4. Token é **NOVO**? (obtido após as configurações)

### Problema: JWT não tem `roles`

**Verificar:**
1. Client Roles existem? (Clients → menthoros-backend → Roles: ADMIN, TECNICO, VISUALIZADOR, ATLETA)
2. Usuário tem role? (Users → seu usuário → Role mapping → filtrar por client `menthoros-backend`)
3. Mapper está configurado? (Client scopes → menthoros-backend-dedicated → Mappers → client-roles)
4. Mapper tem `Multivalued: ON`?
5. Token é **NOVO**?

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

## 📚 Próximos Passos

Após completar esta configuração:

1. **Criar mais usuários** com diferentes roles (TECNICO, VISUALIZADOR)
2. **Criar mais groups** para representar outras assessorias
3. **Testar isolamento** de tenant (usuário do tenant A não vê dados do tenant B)
4. **Implementar testes** de segurança (ver `docs/SECURITY_TESTS.md`)
5. **Adicionar filtros** de tenant nos repositories (ver `docs/SECURITY_CHECKLIST.md`)

---

**Última atualização**: 2025-10-13
**Autor**: Claude Code
**Dúvidas?** Consulte: `docs/KEYCLOAK_AUTHENTICATION_GUIDE.md`
