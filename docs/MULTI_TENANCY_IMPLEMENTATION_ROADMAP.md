# 🗺️ Roadmap de Implementação - Multi-Tenancy com Keycloak

## 📅 Cronograma Sugerido

### **SPRINT 1 - Configurar Keycloak e Infraestrutura (Semana 1-2)**
**Objetivo**: Keycloak rodando e configurado para multi-tenancy

#### Tarefas:
- [ ] **1.1** Configurar docker-compose com Keycloak + PostgreSQL
  - Keycloak 23.0.0
  - PostgreSQL 15 para Keycloak
  - PostgreSQL 15 para Menthoros (pode ser o mesmo)

- [ ] **1.2** Acessar Admin Console e criar Realm
  - Realm: `menthoros-app`
  - Configurar email settings (SMTP)
  - Customizar tema (opcional)

- [ ] **1.3** Criar Client para Backend
  - Client ID: `menthoros-backend`
  - Client authentication: ON
  - Client secret configurado
  - Valid redirect URIs configuradas

- [ ] **1.4** Criar Client para Frontend (SPA)
  - Client ID: `menthoros-frontend`
  - Client authentication: OFF (public)
  - Standard flow enabled
  - Direct access grants enabled

- [ ] **1.5** Criar Client Roles
  - ADMIN, TECNICO, VISUALIZADOR
  - Atribuir composite roles se necessário

- [ ] **1.6** Configurar Token Mappers
  - User Attribute Mapper: tenant_id
  - Group Membership Mapper: groups
  - Audience Mapper: para validar token
  - Roles Mapper: mapear client roles

- [ ] **1.7** Testar obtenção de tokens
  ```bash
  curl -X POST http://localhost:8080/realms/menthoros-app/protocol/openid-connect/token \
    -d "client_id=menthoros-backend" \
    -d "client_secret=..." \
    -d "grant_type=password" \
    -d "username=admin@test.com" \
    -d "password=123456"
  ```

**Entregáveis**: Keycloak funcionando e emitindo tokens JWT

---

### **SPRINT 2 - Fundação do Multi-Tenancy (Semana 2-3)**
**Objetivo**: Criar estrutura de dados e migrations

#### Tarefas:
- [ ] **2.1** Adicionar dependências ao `pom.xml`
  - `spring-boot-starter-oauth2-resource-server`
  - `spring-boot-starter-security`
  - `keycloak-admin-client` (23.0.0)
  - `spring-boot-starter-validation`

- [ ] **2.2** Criar/Atualizar entidade `Assessoria`
  - Arquivo: `src/main/java/com/menthoros/entity/Assessoria.java`
  - Adicionar campos: `keycloakGroupId`, `keycloakRealm`
  - Manter campos existentes: plano, features, endereço, etc.

- [ ] **2.3** Criar enum `PlanoAssessoria` (se não existir)
  - Arquivo: `src/main/java/com/menthoros/enums/PlanoAssessoria.java`
  - Valores: BASIC, PRO, ENTERPRISE

- [ ] **2.4** Criar/Atualizar entidade `Usuario`
  - Arquivo: `src/main/java/com/menthoros/entity/Usuario.java`
  - **IMPORTANTE**: Esta é uma entidade de CACHE do Keycloak
  - Campos: id (UUID do Keycloak), keycloakId, email, nome, role, tenant_id
  - Campos de sync: ultimaSinc, emailVerificado

- [ ] **2.5** Criar enum `UserRole`
  - Arquivo: `src/main/java/com/menthoros/enums/UserRole.java`
  - Valores: ADMIN, TECNICO, VISUALIZADOR

- [ ] **2.6** Criar migration V8
  - Arquivo: `src/main/resources/db/migration/V8__Create_keycloak_multi_tenancy.sql`
  - ALTER TABLE tb_assessoria: adicionar keycloak_group_id, keycloak_realm
  - CREATE TABLE tb_usuario (com keycloak_id)
  - Adicionar `tenant_id` em TODAS as tabelas existentes (se ainda não foi feito)

- [ ] **2.7** Executar migration
  ```bash
  mvn flyway:migrate
  ```

- [ ] **2.8** Criar repositories
  - `AssessoriaRepository.java`
  - `UsuarioRepository.java`: findByKeycloakId(), findByEmail()

**Entregáveis**: Modelo de dados preparado para Keycloak

---

### **SPRINT 3 - Configurar Spring Security OAuth2 (Semana 3-4)**
**Objetivo**: Autenticação via JWT do Keycloak

#### Tarefas:
- [ ] **3.1** Configurar application.yml
  ```yaml
  spring:
    security:
      oauth2:
        resourceserver:
          jwt:
            issuer-uri: http://localhost:8080/realms/menthoros-app
            jwk-set-uri: http://localhost:8080/realms/menthoros-app/protocol/openid-connect/certs

  keycloak:
    realm: menthoros-app
    auth-server-url: http://localhost:8080
    admin:
      client-id: menthoros-backend
      client-secret: ${KEYCLOAK_CLIENT_SECRET}
  ```

- [ ] **3.2** Criar `SecurityConfig`
  - Arquivo: `src/main/java/com/menthoros/config/SecurityConfig.java`
  - Configurar OAuth2 Resource Server
  - Desabilitar CSRF (API REST)
  - Configurar endpoints públicos vs protegidos
  - JwtAuthenticationConverter para extrair roles

- [ ] **3.3** Criar `JwtTenantFilter`
  - Arquivo: `src/main/java/com/menthoros/security/JwtTenantFilter.java`
  - Extrair tenant_id do JWT
  - Configurar `TenantContext`
  - Chamar `UsuarioSyncService` para sincronizar usuário
  - Limpar contexto após requisição

- [ ] **3.4** Criar `TenantContext`
  - Arquivo: `src/main/java/com/menthoros/multitenancy/TenantContext.java`
  - ThreadLocal para armazenar tenant atual
  - Métodos: setTenantId(), getTenantId(), clear()

- [ ] **3.5** Criar `UsuarioSyncService`
  - Arquivo: `src/main/java/com/menthoros/services/UsuarioSyncService.java`
  - syncUserFromJwt(): criar/atualizar usuário no banco
  - Extrair dados do JWT: sub, email, name, roles
  - Criar Usuario se não existir, atualizar se existir

- [ ] **3.6** Testar autenticação
  - Obter token do Keycloak
  - Fazer request para endpoint protegido com header `Authorization: Bearer {token}`
  - Verificar que TenantContext foi configurado
  - Verificar que usuário foi sincronizado no banco

**Entregáveis**: Autenticação OAuth2 funcionando

---

### **SPRINT 4 - Isolamento de Dados (Semana 4-5)**
**Objetivo**: Garantir isolamento por tenant_id

#### Tarefas:
- [ ] **4.1** Criar interface `TenantAware`
  - Arquivo: `src/main/java/com/menthoros/multitenancy/TenantAware.java`
  - Interface marker para entidades multi-tenant

- [ ] **4.2** Criar annotation `@TenantFilter`
  - Arquivo: `src/main/java/com/menthoros/multitenancy/TenantFilter.java`
  - Hibernate Filter: `@FilterDef` com parameter tenantId
  - `@Filter` com condition: `tenant_id = :tenantId`

- [ ] **4.3** Atualizar TODAS as entidades
  - Adicionar campo `assessoria` (ManyToOne)
  - Implementar interface `TenantAware`
  - Aplicar annotation `@TenantFilter`
  - Entidades: Atleta, TreinoRealizado, TreinoPlanejado, PlanoSemanal, PlanoMetaDados, Prova, MetricasDiarias

- [ ] **4.4** Criar `TenantEntityListener`
  - Arquivo: `src/main/java/com/menthoros/multitenancy/TenantEntityListener.java`
  - `@PrePersist` para setar tenant automaticamente

- [ ] **4.5** Configurar Hibernate Filters
  - Arquivo: `src/main/java/com/menthoros/config/HibernateConfig.java`
  - Ativar filtro `tenantFilter` globalmente
  - Setar parâmetro tenantId do TenantContext

- [ ] **4.6** Criar testes de isolamento
  - Arquivo: `src/test/java/com/menthoros/multitenancy/TenantIsolationTest.java`
  - Criar 2 assessorias
  - Criar atletas em cada uma
  - Verificar que queries não retornam dados cruzados

**Entregáveis**: Isolamento de dados 100% funcional

---

### **SPRINT 5 - Gestão de Assessorias no Keycloak (Semana 5-6)**
**Objetivo**: CRUD de assessorias com sincronização Keycloak

#### Tarefas:
- [ ] **5.1** Criar `KeycloakAdminService`
  - Arquivo: `src/main/java/com/menthoros/services/KeycloakAdminService.java`
  - Métodos para interagir com Keycloak Admin API:
    - createGroup(nome, tenantId)
    - addUserToGroup(userId, groupId)
    - assignRoleToUser(userId, role)
    - deleteGroup(groupId)

- [ ] **5.2** Criar DTOs
  - `AssessoriaInputDto.java`: dados de criação/atualização
  - `AssessoriaOutputDto.java`: dados de resposta
  - `AssessoriaConfigDto.java`: configurações visuais

- [ ] **5.3** Criar `AssessoriaService`
  - Arquivo: `src/main/java/com/menthoros/services/AssessoriaService.java`
  - Métodos: criar, atualizar, buscar, listar, desativar
  - **IMPORTANTE**: Ao criar assessoria, criar Group no Keycloak
  - Sincronizar keycloakGroupId no banco

- [ ] **5.4** Criar `AssessoriaController`
  - Arquivo: `src/main/java/com/menthoros/controller/AssessoriaController.java`
  - `POST /api/assessorias` - Criar assessoria
  - `GET /api/assessorias/{id}` - Buscar por ID
  - `PUT /api/assessorias/{id}` - Atualizar
  - `DELETE /api/assessorias/{id}` - Desativar
  - `GET /api/assessorias/{id}/config` - Configurações

- [ ] **5.5** Implementar validações de negócio
  - Validar domínio único
  - Validar limites de atletas/técnicos
  - Validar plano vs features habilitadas

- [ ] **5.6** Testes de integração com Keycloak
  - Criar assessoria e verificar Group criado no Keycloak
  - Verificar atributo tenant_id no Group

**Entregáveis**: Gestão completa de assessorias + Keycloak sync

---

### **SPRINT 6 - Gestão de Usuários (Keycloak como Fonte) (Semana 6-7)**
**Objetivo**: Gestão de usuários via Keycloak

#### Tarefas:
- [ ] **6.1** Criar `UsuarioKeycloakService`
  - Arquivo: `src/main/java/com/menthoros/services/UsuarioKeycloakService.java`
  - **IMPORTANTE**: Usuários são criados NO KEYCLOAK, não no banco
  - Métodos:
    - createUser(email, nome, password, tenantId, role)
    - updateUser(userId, dados)
    - deleteUser(userId)
    - assignToAssessoria(userId, tenantId)
    - changeRole(userId, role)

- [ ] **6.2** Criar DTOs
  - `UsuarioInputDto.java`
  - `UsuarioOutputDto.java`
  - `AlterarSenhaDto.java`
  - `ConviteUsuarioDto.java`

- [ ] **6.3** Criar `UsuarioController`
  - Arquivo: `src/main/java/com/menthoros/controller/UsuarioController.java`
  - **OBS**: Endpoints fazem operações no Keycloak
  - `POST /api/usuarios` - Criar usuário no Keycloak
  - `GET /api/usuarios` - Listar usuários (ler de tb_usuario - cache)
  - `GET /api/usuarios/{id}` - Buscar por ID
  - `PUT /api/usuarios/{id}` - Atualizar no Keycloak
  - `PUT /api/usuarios/{id}/senha` - Alterar senha (Keycloak)
  - `DELETE /api/usuarios/{id}` - Desativar (Keycloak)

- [ ] **6.4** Implementar sistema de convites
  - Arquivo: `src/main/java/com/menthoros/services/ConviteService.java`
  - Enviar email com link de cadastro
  - Link redireciona para Keycloak com pre-fill de dados
  - Após registro, adicionar ao Group correto

- [ ] **6.5** Implementar validações
  - Apenas ADMIN pode criar outros usuários
  - Validar limite de técnicos por plano
  - Email único no realm

- [ ] **6.6** Testes de autorização
  - Testar que TECNICO não cria outros usuários
  - Testar que usuário não acessa dados de outra assessoria

**Entregáveis**: Gestão completa de usuários via Keycloak

---

### **SPRINT 7 - Atualizar Endpoints Existentes (Semana 7-8)**
**Objetivo**: Aplicar multi-tenancy em todos os endpoints

#### Tarefas:
- [ ] **7.1** Atualizar `AtletaController`
  - Remover verificação manual de tenant_id
  - Confiar no filtro automático do Hibernate
  - Adicionar `@PreAuthorize` onde necessário

- [ ] **7.2** Atualizar `PlanoTreinoController`
  - Garantir isolamento por tenant
  - Validar que plano pertence ao tenant

- [ ] **7.3** Atualizar `TreinoRealizadoController`
  - Filtrar por tenant automaticamente

- [ ] **7.4** Atualizar todos os Services
  - Remover lógica manual de filtragem
  - Confiar no `@TenantFilter`
  - Adicionar validações se necessário

- [ ] **7.5** Criar `TenantValidator`
  - Arquivo: `src/main/java/com/menthoros/multitenancy/TenantValidator.java`
  - Método para validar se entidade pertence ao tenant atual
  - Usar em casos específicos

- [ ] **7.6** Atualizar Swagger/OpenAPI
  - Adicionar esquema de autenticação OAuth2
  - Configurar security schemes
  - Documentar header `Authorization: Bearer {token}`
  - Endpoints públicos vs protegidos

**Entregáveis**: Todos os endpoints com multi-tenancy

---

### **SPRINT 8 - Dashboard e Métricas por Tenant (Semana 8)**
**Objetivo**: Dashboard para admins da assessoria

#### Tarefas:
- [ ] **8.1** Criar `DashboardService`
  - Arquivo: `src/main/java/com/menthoros/services/DashboardService.java`
  - Métricas filtradas por tenant:
    - Total atletas ativos
    - Treinos realizados no mês
    - TSS médio
    - Aderência ao plano

- [ ] **8.2** Criar `DashboardController`
  - Endpoint: `GET /api/dashboard/metricas`
  - Endpoint: `GET /api/dashboard/atletas-ativos`
  - Endpoint: `GET /api/dashboard/treinos-mes`

- [ ] **8.3** Implementar cache por tenant
  - Cache key incluindo tenant_id
  - TTL configurável por assessoria
  - Invalidar cache ao criar/atualizar dados

- [ ] **8.4** Criar relatórios
  - Relatório de aderência ao plano
  - Relatório de progressão dos atletas
  - Export CSV/PDF

**Entregáveis**: Dashboard funcional

---

### **SPRINT 9 - Onboarding e Trial (Semana 9-10)**
**Objetivo**: Processo de cadastro de novas assessorias

#### Tarefas:
- [ ] **9.1** Criar endpoint público de registro
  - Endpoint: `POST /api/public/assessorias/register`
  - Fluxo:
    1. Criar assessoria no banco
    2. Criar Group no Keycloak com tenant_id
    3. Criar usuário admin no Keycloak
    4. Adicionar usuário ao Group
    5. Atribuir role ADMIN
    6. Ativar trial automático (14 dias)

- [ ] **9.2** Criar `OnboardingService`
  - Arquivo: `src/main/java/com/menthoros/services/OnboardingService.java`
  - Configurações iniciais
  - Email de boas-vindas
  - Tour guiado (frontend)

- [ ] **9.3** Implementar job de expiração de trial
  - Verificar diariamente assessorias com trial expirado
  - Desativar automaticamente
  - Enviar email de notificação
  - Desabilitar usuários no Keycloak (opcional)

- [ ] **9.4** Criar wizard de configuração inicial
  - Tela 1: Dados da assessoria + primeiro usuário
  - Tela 2: Personalização (cores, logo)
  - Tela 3: Convites para técnicos
  - Tela 4: Importar atletas (CSV)

**Entregáveis**: Onboarding self-service completo

---

### **SPRINT 10 - Features por Plano (Semana 10)**
**Objetivo**: Diferenciação de planos

#### Tarefas:
- [ ] **10.1** Criar `FeatureGuard`
  - Arquivo: `src/main/java/com/menthoros/multitenancy/FeatureGuard.java`
  - Verificar se assessoria tem feature habilitada
  - Lançar exceção se não tiver

- [ ] **10.2** Criar annotation `@RequiresFeature`
  - Exemplo: `@RequiresFeature("IA_AVANCADA")`
  - Aspect para interceptar e validar feature

- [ ] **10.3** Implementar limitadores por plano
  - BASIC: até 20 atletas, sem IA avançada
  - PRO: até 100 atletas, IA avançada
  - ENTERPRISE: ilimitado, todas features

- [ ] **10.4** Criar serviço de upgrade de plano
  - Endpoint: `POST /api/assessorias/{id}/upgrade`
  - Atualizar limites
  - Habilitar features
  - Enviar confirmação

- [ ] **10.5** Validar limites em tempo de criação
  - Bloquear criação de atleta se atingiu limite
  - Bloquear features desabilitadas

**Entregáveis**: Planos diferenciados funcionando

---

### **SPRINT 11 - Testes e Segurança (Semana 11)**
**Objetivo**: Garantir qualidade e segurança

#### Tarefas:
- [ ] **11.1** Testes de isolamento
  - Criar 2 assessorias de teste
  - Criar atletas em cada uma
  - Verificar que queries não retornam dados cruzados
  - Testar com múltiplos usuários simultâneos

- [ ] **11.2** Testes de autorização
  - ADMIN pode tudo
  - TECNICO pode gerenciar atletas
  - VISUALIZADOR apenas lê
  - Testar `@PreAuthorize`

- [ ] **11.3** Testes de sincronização Keycloak
  - Criar usuário no Keycloak
  - Fazer login
  - Verificar que usuário foi sincronizado no banco
  - Atualizar usuário no Keycloak
  - Fazer novo login
  - Verificar que dados foram atualizados

- [ ] **11.4** Testes de performance
  - Benchmark com múltiplos tenants
  - Verificar impacto dos índices
  - Otimizar queries lentas
  - Verificar N+1 queries

- [ ] **11.5** Testes de carga
  - Simular 100 tenants simultâneos
  - Verificar vazamento de memória
  - Testar ThreadLocal cleanup
  - Monitorar Keycloak

- [ ] **11.6** Auditoria de segurança
  - Validação de JWT (expiração, assinatura)
  - Testes de vazamento de dados
  - SQL Injection (impossível com JPA, mas validar)
  - XSS (frontend)
  - Rate limiting

- [ ] **11.7** Implementar rate limiting por tenant
  - Limitar requests por assessoria
  - Usar Redis para contador
  - Configurar limites por plano

- [ ] **11.8** Logs estruturados
  - Incluir tenant_id em TODOS os logs
  - Incluir user_id (keycloak_id)
  - Facilitar troubleshooting

**Entregáveis**: Sistema auditado e seguro

---

### **SPRINT 12 - Documentação e Deploy (Semana 12)**
**Objetivo**: Preparar para produção

#### Tarefas:
- [ ] **12.1** Documentação técnica
  - Arquitetura multi-tenant com Keycloak
  - Fluxo de autenticação OAuth2
  - Modelo de dados
  - Sincronização Keycloak ↔ Menthoros

- [ ] **12.2** Documentação de API
  - Swagger completo
  - Exemplos de requests
  - Códigos de erro
  - Fluxo OAuth2

- [ ] **12.3** Guia de desenvolvimento
  - Como adicionar nova entidade multi-tenant
  - Como criar novo endpoint protegido
  - Como adicionar nova feature flag
  - Boas práticas

- [ ] **12.4** Guia de administração Keycloak
  - Como criar uma nova assessoria manualmente
  - Como adicionar usuário a uma assessoria
  - Como resetar senha
  - Como configurar MFA

- [ ] **12.5** Scripts de deploy
  - Dockerfile (Menthoros backend)
  - docker-compose.yml (completo)
  - Kubernetes manifests (opcional)
  - Scripts de backup

- [ ] **12.6** Configurar ambientes
  - DEV, STAGING, PROD
  - Variáveis de ambiente
  - Secrets (Keycloak client secret, etc)
  - Configurar Keycloak em produção (banco externo, HA)

- [ ] **12.7** Monitoramento
  - Logs centralizados (ELK/Loki)
  - Métricas (Prometheus + Grafana)
  - Alertas (falhas de autenticação, tenants inativos, etc)
  - Dashboard de Keycloak

- [ ] **12.8** Backup strategy
  - Backup automático diário (PostgreSQL)
  - Backup de configurações do Keycloak
  - Possibilidade de restaurar um tenant específico
  - Testes de restore

**Entregáveis**: Sistema production-ready

---

## 🎯 Marcos de Entrega

| Marco | Descrição | Prazo Sugerido |
|-------|-----------|----------------|
| **M1** | Keycloak configurado e emitindo tokens | Fim da Semana 2 |
| **M2** | Modelo de dados multi-tenant | Fim da Semana 3 |
| **M3** | Autenticação OAuth2 funcionando | Fim da Semana 4 |
| **M4** | Isolamento de dados 100% | Fim da Semana 5 |
| **M5** | CRUD de assessorias + Keycloak sync | Fim da Semana 6 |
| **M6** | Gestão de usuários via Keycloak | Fim da Semana 7 |
| **M7** | Endpoints migrados | Fim da Semana 8 |
| **M8** | Onboarding self-service | Fim da Semana 10 |
| **M9** | Features por plano | Fim da Semana 10 |
| **M10** | Testes e segurança | Fim da Semana 11 |
| **M11** | Deploy em produção | Fim da Semana 12 |

---

## 🚀 Quick Start - Começar Hoje

### Comandos para Iniciar (Sprint 1):

```bash
# 1. Criar docker-compose.yml
cat > docker-compose.yml << 'EOF'
version: '3.8'
services:
  keycloak:
    image: quay.io/keycloak/keycloak:23.0.0
    container_name: menthoros-keycloak
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin123
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
      KC_DB_USERNAME: keycloak
      KC_DB_PASSWORD: keycloak123
    command: start-dev
    ports:
      - "8080:8080"
    depends_on:
      - postgres
    networks:
      - menthoros-network

  postgres:
    image: postgres:15-alpine
    container_name: menthoros-postgres
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./init-databases.sql:/docker-entrypoint-initdb.d/init-databases.sql
    networks:
      - menthoros-network

volumes:
  postgres-data:

networks:
  menthoros-network:
EOF

# 2. Criar script de inicialização de bancos
cat > init-databases.sql << 'EOF'
-- Banco para Keycloak
CREATE DATABASE keycloak OWNER postgres;

-- Banco para Menthoros
CREATE DATABASE "menthoros-multi" OWNER postgres;
\c "menthoros-multi"
CREATE USER menthoros WITH PASSWORD 'menthoros123';
GRANT ALL PRIVILEGES ON DATABASE "menthoros-multi" TO menthoros;
GRANT ALL ON SCHEMA public TO menthoros;
EOF

# 3. Subir containers
docker-compose up -d

# 4. Aguardar Keycloak iniciar
echo "Aguardando Keycloak iniciar..."
sleep 30

# 5. Abrir Admin Console
echo "Acesse: http://localhost:8080"
echo "Login: admin / admin123"
```

---

## 📋 Checklist Final

Antes de considerar multi-tenancy completo:

### Funcionalidade
- [ ] Keycloak configurado (realm, clients, roles, mappers)
- [ ] Múltiplas assessorias cadastradas (Groups no Keycloak)
- [ ] Isolamento de dados 100% funcional
- [ ] Autenticação OAuth2 com JWT
- [ ] Sincronização automática Keycloak → tb_usuario
- [ ] Usuários com roles diferentes
- [ ] Dashboard por assessoria
- [ ] Onboarding self-service

### Qualidade
- [ ] Cobertura de testes > 80%
- [ ] Testes de isolamento passando
- [ ] Testes de sincronização Keycloak
- [ ] Performance adequada com 100+ tenants
- [ ] Documentação completa

### Segurança
- [ ] JWT validation funcionando
- [ ] Token expiration configurado
- [ ] Senhas gerenciadas pelo Keycloak
- [ ] Rate limiting por tenant
- [ ] Logs auditáveis com tenant_id
- [ ] Testes de vazamento de dados
- [ ] MFA habilitado (opcional)

### Keycloak
- [ ] Backup automático de configurações
- [ ] Monitoring ativo
- [ ] Alta disponibilidade (produção)
- [ ] Temas customizados (opcional)
- [ ] Email SMTP configurado

### Produção
- [ ] Backups automáticos (Postgres + Keycloak)
- [ ] Monitoramento ativo
- [ ] Alertas configurados
- [ ] Documentação de runbook
- [ ] Disaster recovery testado

---

## ⚠️ Riscos e Mitigações (Keycloak)

| Risco | Impacto | Mitigação |
|-------|---------|-----------|
| **Keycloak indisponível** | CRÍTICO | HA cluster, cache local de usuários |
| **Vazamento de dados entre tenants** | CRÍTICO | Testes automatizados de isolamento |
| **Sincronização falha** | ALTO | Retry logic, logs detalhados |
| **Performance JWT validation** | MÉDIO | Cache de JWK, validação local |
| **Migração de dados existentes** | ALTO | Script testado, rollback plan |
| **Complexidade operacional** | MÉDIO | Documentação, monitoramento |

---

## 📚 Recursos Adicionais

### Bibliotecas Utilizadas
- **Keycloak**: Gestão de identidades
- **Spring Security OAuth2**: Resource Server
- **Hibernate Filters**: Isolamento automático
- **PostgreSQL**: Banco de dados

### Padrões de Design
- **OAuth2 Resource Server Pattern**: Validação de tokens
- **Tenant Context Pattern**: ThreadLocal para contexto
- **Cache Aside Pattern**: tb_usuario como cache do Keycloak
- **Repository Pattern**: Isolamento na camada de dados
- **Strategy Pattern**: Diferentes planos

### Documentação Oficial
- [Keycloak Admin REST API](https://www.keycloak.org/docs-api/latest/rest-api/index.html)
- [Spring Security OAuth2](https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html)
- [Keycloak Multi-Tenancy](https://www.keycloak.org/docs/latest/server_admin/#_per_realm_admin_permissions)

---

## 🔄 Diferenças vs. Autenticação Própria

| Aspecto | Com Keycloak | Sem Keycloak |
|---------|--------------|--------------|
| **Gestão de senhas** | Keycloak | Backend (BCrypt) |
| **MFA** | Nativo | Implementar manualmente |
| **Social Login** | Nativo | Implementar manualmente |
| **SSO** | Nativo | Implementar manualmente |
| **Auditoria** | Completa | Implementar manualmente |
| **Reset de senha** | Fluxo pronto | Implementar manualmente |
| **Email verification** | Fluxo pronto | Implementar manualmente |
| **Complexidade inicial** | Maior | Menor |
| **Manutenção** | Menor | Maior |
| **Escalabilidade** | Alta | Média |

---

**Próximo Passo**: Começar pelo Sprint 1, tarefa 1.1! 🎯

**Tempo estimado total**: 12 semanas (3 meses)
**Equipe recomendada**: 2-3 desenvolvedores
**Versão**: 2.0.0 (Keycloak)
