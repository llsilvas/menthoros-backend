# 🐳 Setup Docker para Multi-Tenancy

**Guia Completo de Configuração de Infraestrutura**
**Data:** 01 de março de 2026
**Versão:** 1.0

---

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Arquitetura de Bancos](#arquitetura-de-bancos)
3. [Pré-requisitos](#pré-requisitos)
4. [Configuração Rápida](#configuração-rápida)
5. [Detalhes da Configuração](#detalhes-da-configuração)
6. [Gerenciamento de Serviços](#gerenciamento-de-serviços)
7. [Troubleshooting](#troubleshooting)
8. [Monitoramento](#monitoramento)
9. [Reset de Dados](#reset-de-dados)

---

## 🎯 Visão Geral

A infraestrutura multi-tenancy utiliza **2 containers PostgreSQL com 3 databases logicamente separados**:

```
┌─────────────────────────────────────────────────────────────────┐
│                    DOCKER MULTI-TENANCY                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  postgres-db (5432)           [menthoros-db]                    │
│  └─ NÃO ALTERADO ✅            Mantém compatibilidade           │
│                                                                  │
│  postgres-mt (5433)           [menthoros-multi + keycloak]      │
│  ├─ menthoros-multi ✅         Multi-tenancy Spring Boot        │
│  └─ keycloak ✅                Autenticação OAuth2/OIDC         │
│                                                                  │
│  redis (6379)                 [Cache compartilhado]             │
│  └─ CACHE ✅                   Todos compartilham               │
│                                                                  │
│  keycloak (8080)              [Autenticação]                    │
│  └─ SSO ✅                     Login centralizado               │
│                                                                  │
│  app (8099)                   [Spring Boot]                     │
│  └─ API ✅                     Conecta a postgres-mt            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Isolamento de Dados

- **postgres-db**: Banco atual (menthoros-db) - **INTACTO**
- **postgres-mt/menthoros-multi**: Banco novo para multi-tenancy - **USADO PELA APP**
- **postgres-mt/keycloak**: Banco de Keycloak (mesmo container, database diferente) - **AUTENTICAÇÃO**

**Nenhuma alteração afeta o banco menthoros-db atual!**

---

## 📊 Arquitetura de Bancos

### Antes (Single-Tenancy)
```
┌─────────────────────────────────┐
│    menthoros-db                 │
├─────────────────────────────────┤
│  tb_atleta (todos os atletas)   │ ← Sem tenant_id
│  tb_plano (todos os planos)     │
│  tb_treino (todos os treinos)   │
└─────────────────────────────────┘
```

### Depois (Multi-Tenancy)
```
┌─────────────────────────────────────────────────────────────┐
│              menthoros-multi (NOVO)                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  tb_assessoria  (Tenants Master)                            │
│  ├─ id (UUID)                                               │
│  ├─ nome: "Assessoria A"                                    │
│  └─ keycloak_group_id: "assessoria-a"                       │
│                                                              │
│  tb_usuario (Cache do Keycloak)                             │
│  ├─ id (UUID)                                               │
│  ├─ email: "joao@assessoria-a.com"                          │
│  └─ assessoria_id → FK tb_assessoria                        │
│                                                              │
│  tb_atleta                                                   │
│  ├─ id (UUID)                                               │
│  ├─ nome: "João Silva"                                      │
│  └─ assessoria_id → FK tb_assessoria  ✅ NOVO!             │
│                                                              │
│  tb_plano_semanal                                            │
│  ├─ id (UUID)                                               │
│  └─ assessoria_id → FK tb_assessoria  ✅ NOVO!             │
│                                                              │
│  tb_treino_realizado                                         │
│  ├─ id (UUID)                                               │
│  └─ assessoria_id → FK tb_assessoria  ✅ NOVO!             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**Resultado:** Cada tenant vê APENAS seus dados (filtro por assessoria_id)

---

## ✅ Pré-requisitos

### Sistema Operacional
- Linux / macOS / Windows (com WSL2)
- 8GB RAM mínimo (recomendado 16GB para 3 PostgreSQL)
- 20GB de espaço em disco

### Software Obrigatório
```bash
# Docker Desktop 4.0+
docker --version
# Docker version 20.10.0, build 9d988398e9

# Docker Compose 2.0+
docker compose version
# Docker Compose version v2.x.x

# Git
git --version
```

### Contas Necessárias
- OpenAI API Key (para Spring AI)
  - Obter em: https://platform.openai.com/api-keys
  - Usar modelo: `gpt-4o-mini`

---

## 🚀 Configuração Rápida

### 1. Clone o Repositório
```bash
cd /home/lsilva/Dev/workspace/menthoros/menthoros
```

### 2. Copie e Configure o .env
```bash
# Copiar arquivo de exemplo
cp .env.multi-tenancy.example .env.multi-tenancy

# Editar com seus valores
nano .env.multi-tenancy
# Principais variáveis a alterar:
# - OPENAI_API_KEY=sk-...
# - KC_HOSTNAME=localhost (ou seu domínio)
# - KC_CLIENT_SECRET=seu-secret
```

### 3. Inicie os Serviços
```bash
# Iniciar todos os serviços em background
docker compose --env-file .env.multi-tenancy \
    -f docker-compose.multi-tenancy.yml up -d

# Verificar se tudo está rodando
docker compose -f docker-compose.multi-tenancy.yml ps
```

### 4. Aguarde Keycloak Estar Pronto
```bash
# Verificar logs de inicialização
docker compose -f docker-compose.multi-tenancy.yml logs -f keycloak

# Aguarde por: "Keycloak X.X.X started"
# Ctrl+C para sair dos logs
```

### 5. Teste a Conexão
```bash
# Verificar health check da API
curl http://localhost:8099/actuator/health

# Resposta esperada: {"status":"UP"}
```

### 6. Acesse Keycloak Admin
```bash
# Abra no navegador
http://localhost:8080/admin

# Login com:
# Username: admin
# Password: admin123 (do .env)
```

---

## 🔧 Detalhes da Configuração

### Arquivo: docker-compose.multi-tenancy.yml

#### postgres-db (Banco Atual)
```yaml
postgres-db:
  image: pgvector/pgvector:pg17
  container_name: menthoros-postgres-db
  ports:
    - "5432:5432"  # ← PORTA PADRÃO
  volumes:
    - pg_data_db:/var/lib/postgresql/data  # Dados SEPARADOS
  environment:
    POSTGRES_DB: menthoros-db  # Nome do banco ATUAL
```

**Características:**
- Porta: `5432` (padrão PostgreSQL)
- Banco: `menthoros-db` (atual, não modificado)
- Volume: `pg_data_db` (dados separados)
- **Status:** ✅ NÃO ALTERADO

#### postgres-mt (Banco Multi-Tenancy)
```yaml
postgres-mt:
  image: pgvector/pgvector:pg17
  container_name: menthoros-postgres-mt
  ports:
    - "5433:5432"  # ← PORTA DIFERENTE (DBeaver conecta aqui)
  volumes:
    - pg_data_mt:/var/lib/postgresql/data
    - ./src/main/resources/db/init/menthoros_mt_init.sql:/docker-entrypoint-initdb.d/01-init.sql
    - ./src/main/resources/db/init/keycloak_init.sql:/docker-entrypoint-initdb.d/02-keycloak.sql
  environment:
    POSTGRES_DB: menthoros-multi  # Banco principal
    POSTGRES_INITDB_ARGS: >
      -c shared_buffers=256MB
      -c effective_cache_size=1GB
      -c work_mem=64MB
```

**Características:**
- Porta: `5433` (diferente de 5432 para evitar conflito)
- Bancos:
  - `menthoros-multi` (aplicação)
  - `keycloak` (autenticação - criado por keycloak_init.sql)
- Volume: `pg_data_mt` (compartilhado para ambos)
- Scripts init:
  - `menthoros_mt_init.sql` (cria extensões, users)
  - `keycloak_init.sql` (cria database keycloak)
- **Status:** ✅ COMPARTILHADO (2 databases, 1 container)

#### Keycloak
```yaml
keycloak:
  image: quay.io/keycloak/keycloak:23.0.0
  ports:
    - "8080:8080"  # Admin Console
  environment:
    KC_DB: postgres
    KC_DB_URL: jdbc:postgresql://postgres-mt:5432/keycloak
    KC_DB_USERNAME: menthoros
    KC_DB_PASSWORD: menthoros123
    KC_BOOTSTRAP_ADMIN_USERNAME: admin
    KC_BOOTSTRAP_ADMIN_PASSWORD: admin123
```

**Características:**
- Admin Console: http://localhost:8080/admin
- Banco: Conecta a `postgres-mt` (MESMO container que menthoros-multi!)
- Database: `keycloak` (separado de menthoros-multi)
- Realm: `menthoros-app`

#### Spring Boot App
```yaml
app:
  build:
    dockerfile: docker/Dockerfile.multi-tenancy
  ports:
    - "8099:8099"
  environment:
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres-mt:5432/menthoros-multi
    SPRING_DATASOURCE_USERNAME: menthoros
    SPRING_DATASOURCE_PASSWORD: menthoros123
```

**Características:**
- API: http://localhost:8099
- Banco: Conecta a `postgres-mt` (menthoros-multi)
- Database: `menthoros-multi` (separado de keycloak)
- Dockerfile: `docker/Dockerfile.multi-tenancy`

---

## 🎮 Gerenciamento de Serviços

### Iniciar Todos os Serviços
```bash
docker compose --env-file .env.multi-tenancy \
    -f docker-compose.multi-tenancy.yml up -d
```

### Parar Todos os Serviços
```bash
docker compose -f docker-compose.multi-tenancy.yml down
```

### Ver Status
```bash
docker compose -f docker-compose.multi-tenancy.yml ps
```

**Saída esperada:**
```
NAME                      STATUS
menthoros-postgres-db     Up (healthy)
menthoros-postgres-mt     Up (healthy)
menthoros-redis           Up (healthy)
menthoros-keycloak        Up (healthy)
menthoros-app-mt          Up
```

### Ver Logs
```bash
# Todos os logs
docker compose -f docker-compose.multi-tenancy.yml logs -f

# Apenas de um serviço
docker compose -f docker-compose.multi-tenancy.yml logs -f app
docker compose -f docker-compose.multi-tenancy.yml logs -f keycloak
docker compose -f docker-compose.multi-tenancy.yml logs -f postgres-mt
```

### Parar um Serviço Específico
```bash
docker compose -f docker-compose.multi-tenancy.yml stop app
```

### Reiniciar um Serviço
```bash
docker compose -f docker-compose.multi-tenancy.yml restart app
```

### Ver Recursos Utilizados
```bash
# Ver stats de todos os containers
docker stats

# Ou especificar apenas os containers de multi-tenancy
docker stats menthoros-postgres-db menthoros-postgres-mt menthoros-keycloak menthoros-redis menthoros-app-mt
```

---

## 🐛 Troubleshooting

### Problema: Porta 5432/5433 já em uso

**Solução:** Alterar portas no docker-compose.multi-tenancy.yml

```yaml
postgres-db:
  ports:
    - "5432:5432"    # Mudar para "5440:5432" se conflito

postgres-mt:
  ports:
    - "5433:5432"    # Mudar para "5441:5432" se conflito
```

**Nota:** postgres-mt contém AMBOS menthoros-multi e keycloak, portanto apenas 2 portas!
Keycloak não tem porta separada - usa a mesma porta de postgres-mt.

Depois:
```bash
docker compose -f docker-compose.multi-tenancy.yml down
docker compose --env-file .env.multi-tenancy \
    -f docker-compose.multi-tenancy.yml up -d
```

### Problema: Keycloak não inicia

**Verificar logs:**
```bash
docker compose -f docker-compose.multi-tenancy.yml logs keycloak | tail -50
```

**Erro comum:** "postgres-mt:5432 refused connection"

**Solução:** Esperar mais tempo (Keycloak leva ~90s para iniciar e postgres-mt precisa estar pronto)

```bash
# Aguarde 2 minutos, depois:
curl http://localhost:8080/admin
```

### Problema: Aplicação não conecta ao banco

**Verificar:**
```bash
# Confirmar postgres-mt está saudável
docker compose -f docker-compose.multi-tenancy.yml logs postgres-mt

# Conectar manualmente à menthoros-multi
psql -h localhost -p 5433 -U menthoros -d menthoros-multi -c "SELECT 1"

# Conectar ao keycloak
psql -h localhost -p 5433 -U menthoros -d keycloak -c "SELECT 1"
```

**Erro comum:** "menthoros-multi" database does not exist

**Solução:** Flyway criará automaticamente. Aguarde 30s e verifique logs da app:
```bash
docker compose -f docker-compose.multi-tenancy.yml logs app | grep -i flyway
```

### Problema: OpenAI API Key inválida

**Solução:** Verificar .env.multi-tenancy

```bash
# Verificar se chave está configurada
grep "OPENAI_API_KEY" .env.multi-tenancy

# Se vazio:
echo "OPENAI_API_KEY=sk-seu-key-aqui" >> .env.multi-tenancy

# Reiniciar app
docker compose -f docker-compose.multi-tenancy.yml restart app
```

### Problema: "menthoros-db" recebe novas tabelas

**Causa:** Docker-compose incorreto apontando para postgres-db em vez de postgres-mt

**Verificar:**
```bash
# Confirmar SPRING_DATASOURCE_URL da app
docker compose -f docker-compose.multi-tenancy.yml logs app | grep DATASOURCE_URL

# Deve mostrar: jdbc:postgresql://postgres-mt:5432/menthoros-multi
```

**Solução:** Usar o .env.multi-tenancy correto

```bash
docker compose --env-file .env.multi-tenancy \
    -f docker-compose.multi-tenancy.yml up -d app
```

---

## 📊 Monitoramento

### Health Checks

#### Banco Dados menthoros-multi (postgres-mt)
```bash
psql -h localhost -p 5433 -U menthoros -d menthoros-multi -c "SELECT 1"
# Output: 1
```

#### Banco Dados keycloak (postgres-mt)
```bash
psql -h localhost -p 5433 -U menthoros -d keycloak -c "SELECT 1"
# Output: 1
```

#### Redis
```bash
redis-cli -h localhost ping
# Output: PONG
```

#### Keycloak
```bash
curl http://localhost:8080/health/ready
# Output: {"status":"UP"}
```

#### Spring Boot App
```bash
curl http://localhost:8099/actuator/health
# Output: {"status":"UP","components":{...}}
```

### Ver Estatísticas

```bash
# CPU e memória dos containers
docker stats

# Tamanho dos bancos de dados em postgres-mt
docker exec menthoros-postgres-mt psql -U menthoros -d menthoros-multi -c "
  SELECT datname, pg_size_pretty(pg_database_size(datname))
  FROM pg_database
  ORDER BY pg_database_size(datname) DESC;
"

# Listar todos os databases
docker exec menthoros-postgres-mt psql -U menthoros -l
```

---

## 🔄 Reset de Dados

### Reset APENAS do Banco Multi-Tenancy (postgres-mt)
```bash
# NÃO AFETA menthoros-db

# 1. Parar serviços
docker compose -f docker-compose.multi-tenancy.yml stop app postgres-mt

# 2. Remover volume (apaga dados)
docker volume rm menthoros-pg-data-mt

# 3. Iniciar novamente (cria banco vazio)
docker compose --env-file .env.multi-tenancy \
    -f docker-compose.multi-tenancy.yml up -d

# 4. Flyway criará schema automaticamente
docker compose -f docker-compose.multi-tenancy.yml logs -f app
```

### Reset COMPLETO (incluindo Keycloak)
```bash
# ⚠️ CUIDADO: Apaga dados de postgres-mt (menthoros-multi + keycloak), mas NÃO menthoros-db

docker compose -f docker-compose.multi-tenancy.yml down -v

# Remover volumes específicos
docker volume rm menthoros-pg-data-mt menthoros-redis-data

# Reiniciar tudo
docker compose --env-file .env.multi-tenancy \
    -f docker-compose.multi-tenancy.yml up -d

# Aguarde ~30s para Keycloak inicializar
docker compose -f docker-compose.multi-tenancy.yml logs -f keycloak
```

### NUNCA Fazer Isto (protege menthoros-db)
```bash
# ❌ NÃO FAZER:
docker volume rm menthoros-pg-data-db    # Apagaria menthoros-db!

# A separação de volumes garante que postgres-db nunca é afetado
```

---

## 📝 Variáveis de Ambiente

Todas as variáveis estão documentadas em `.env.multi-tenancy.example`

### Variáveis Críticas

```bash
# Banco Multi-Tenancy (em postgres-mt)
DB_MT_NAME=menthoros-multi          # Nome do banco (NOVO - era menthoros-app)
DB_MT_USER=menthoros                # Usuário
DB_MT_PASSWORD=menthoros123         # Senha

# Keycloak (em postgres-mt, database separado)
KC_DB_NAME=keycloak                 # Database do Keycloak
KC_DB_USER=menthoros                # Mesmo usuário (postgres-mt é compartilhado)
KC_DB_PASSWORD=menthoros123         # Mesma senha
KC_ADMIN_USER=admin                 # Usuário admin do Keycloak
KC_ADMIN_PASSWORD=admin123          # Senha admin do Keycloak
KC_HOSTNAME=localhost               # Host para OAuth2 redirects
KC_CLIENT_SECRET=seu-secret         # Mude em produção!

# OpenAI
OPENAI_API_KEY=sk-...               # Obrigatório!

# Application
SERVER_PORT=8099                    # Porta da API
SPRING_PROFILES=docker,multi-tenancy # Profiles ativos
```

---

## 🌐 Acessos Rápidos

| Serviço | URL | Credenciais |
|---------|-----|-------------|
| **Keycloak Admin** | http://localhost:8080/admin | admin / admin123 |
| **Spring Boot API** | http://localhost:8099 | (OAuth2) |
| **PostgreSQL MT** (menthoros-multi) | localhost:5433 | menthoros / menthoros123 |
| **PostgreSQL MT** (keycloak) | localhost:5433 | menthoros / menthoros123 |
| **PostgreSQL DB** (menthoros-db) | localhost:5432 | menthoros / menthoros123 |
| **Redis** | localhost:6379 | (sem auth) |

---

## ✅ Checklist de Iniciação

- [ ] Clonar repositório
- [ ] Copiar `.env.multi-tenancy.example` → `.env.multi-tenancy`
- [ ] Editar `OPENAI_API_KEY` em `.env.multi-tenancy`
- [ ] Executar `docker compose ... up -d`
- [ ] Aguardar Keycloak iniciar (~2 min)
- [ ] Verificar `docker compose ... ps` (todos com "Up")
- [ ] Testar `curl http://localhost:8099/actuator/health`
- [ ] Acessar Keycloak Admin (http://localhost:8080/admin)
- [ ] Pronto para começar!

---

## 📚 Próximos Passos

Após infraestrutura pronta:

1. **Configurar Keycloak:**
   - Criar Realm `menthoros-app`
   - Criar Groups (assessorias)
   - Criar Clients (backend, frontend)
   - Mapear tenant_id nos tokens

2. **Iniciar Sprint 1:**
   - Consultar `/docs/SPRINT_1_KICKOFF.md`
   - Implementar TenantContext
   - Configurar Spring Security

3. **Testes:**
   - Verificar isolamento multi-tenant
   - Testar JWT do Keycloak
   - Validar filtros de assessoria_id

---

**Você está pronto para multi-tenancy! 🚀**
