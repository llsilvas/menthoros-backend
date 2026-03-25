# ⚡ Docker Multi-Tenancy - Quick Start (5 minutos)

**Comece em 5 minutos com esses 4 passos**

---

## 📋 Pré-requisitos

```bash
✅ docker --version        # Docker 20.10+
✅ docker compose version  # Docker Compose 2.0+
✅ OpenAI API Key         # Obter em https://platform.openai.com/api-keys
```

---

## 🚀 4 Passos para Começar

### 1️⃣ Copie a Configuração

```bash
cd menthoros

# Copiar arquivo de exemplo para .env.multi-tenancy
cp .env.multi-tenancy.example .env.multi-tenancy

# Editar arquivo (adicione sua OpenAI API Key)
nano .env.multi-tenancy

# Procure por OPENAI_API_KEY e substitua:
# OPENAI_API_KEY=sk-seu-key-aqui
```

### 2️⃣ Inicie os Serviços

```bash
# Iniciar todos os containers
docker compose --env-file .env.multi-tenancy \
    -f docker-compose.multi-tenancy.yml up -d

# Aguarde ~30 segundos para inicialização
```

### 3️⃣ Aguarde Keycloak Estar Pronto

```bash
# Ver logs de inicialização (aguarde "Keycloak X.X.X started")
docker compose -f docker-compose.multi-tenancy.yml logs -f keycloak

# Pressione Ctrl+C quando ver a mensagem de sucesso
# (levará ~2 minutos)
```

### 4️⃣ Teste a Conexão

```bash
# Verificar se API está respondendo
curl http://localhost:8099/actuator/health

# Resposta esperada:
# {"status":"UP",...}

# ✅ Tudo pronto!
```

---

## 🌐 Acessos

| O Quê | URL | Credenciais |
|-------|-----|-------------|
| **Keycloak Admin** | http://localhost:8080/admin | admin / admin123 |
| **Spring Boot API** | http://localhost:8099 | (autenticação via Keycloak) |

---

## 📊 Verificar Status

```bash
# Ver todos os containers rodando
docker compose -f docker-compose.multi-tenancy.yml ps

# Saída esperada:
# NAME                          STATUS
# menthoros-postgres-db         Up (healthy)
# menthoros-postgres-mt         Up (healthy)
# menthoros-redis               Up (healthy)
# menthoros-keycloak            Up (healthy)
# menthoros-app-mt              Up
```

---

## 🐛 Problemas Comuns

### "Erro de conexão ao Keycloak"
```bash
# Aguarde mais tempo (~2 minutos)
# Verifique os logs:
docker compose -f docker-compose.multi-tenancy.yml logs keycloak | tail -20
```

### "OpenAI API Key inválido"
```bash
# Editar .env.multi-tenancy
nano .env.multi-tenancy

# Procure: OPENAI_API_KEY=sk-...
# Atualize com sua chave e reinicie:
docker compose -f docker-compose.multi-tenancy.yml restart app
```

### "Porta já em uso"
```bash
# Ver qual processo está usando a porta
lsof -i :5433  # ou 5432, 5434, 8080, 8099

# Alternativa: mudar porta no .env.multi-tenancy
nano .env.multi-tenancy
# Altere as portas e tente novamente
```

---

## 📚 Próximos Passos

Depois que tudo estiver rodando:

1. **Ler documentação completa:**
   - `docs/DOCKER_SETUP_MULTI_TENANCY.md` (setup detalhado)
   - `docs/DOCKER_MULTITENANCY_SUMMARY.md` (visão geral)

2. **Configurar Keycloak:**
   - Acessar http://localhost:8080/admin
   - Criar Realm: menthoros-app
   - Criar Groups para assessorias
   - Configurar Clients (backend, frontend)

3. **Começar Sprint 1:**
   - Ler `/docs/SPRINT_1_KICKOFF.md`
   - Implementar TenantContext
   - Corrigir Repositories

---

## ✅ Checklist Rápido

- [ ] `cp .env.multi-tenancy.example .env.multi-tenancy`
- [ ] Editar `.env.multi-tenancy` (OpenAI API Key)
- [ ] `docker compose ... up -d`
- [ ] Aguardar Keycloak (2 min)
- [ ] `curl http://localhost:8099/actuator/health`
- [ ] Acessar http://localhost:8080/admin
- [ ] ✅ Pronto!

---

## 💾 Parar Tudo

```bash
docker compose -f docker-compose.multi-tenancy.yml down

# Se quiser remover dados também:
docker compose -f docker-compose.multi-tenancy.yml down -v
```

---

**Você está pronto para começar! 🚀**

Dúvidas? Ver `docs/DOCKER_SETUP_MULTI_TENANCY.md` para troubleshooting completo.
