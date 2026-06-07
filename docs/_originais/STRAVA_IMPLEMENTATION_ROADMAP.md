# 🗺️ Roadmap de Implementação - Integração Strava

## 📅 Cronograma Sugerido

### **SPRINT 1 - Fundação (Semana 1)**
**Objetivo**: Configurar infraestrutura básica e autenticação OAuth2

#### Tarefas:
- [ ] **1.1** Adicionar dependências ao `pom.xml`
  - `spring-boot-starter-oauth2-client`
  - `spring-boot-starter-webflux`

- [ ] **1.2** Configurar variáveis de ambiente
  - Criar `.env` com credenciais do Strava
  - Adicionar propriedades ao `application.yml`

- [ ] **1.3** Criar entidade `StravaAuth`
  - Arquivo: `src/main/java/com/menthoros/entity/StravaAuth.java`

- [ ] **1.4** Criar migration do banco
  - Arquivo: `src/main/resources/db/migration/V7__Create_strava_auth_table.sql`
  - Executar: `mvn flyway:migrate`

- [ ] **1.5** Criar DTOs de comunicação
  - `StravaTokenResponse.java`
  - `StravaAthleteDto.java`

- [ ] **1.6** Criar `StravaProperties` configuration

- [ ] **1.7** Criar `StravaAuthRepository`

**Entregáveis**: Estrutura de dados e configuração completa

---

### **SPRINT 2 - Autenticação OAuth2 (Semana 1-2)**
**Objetivo**: Implementar fluxo completo de autenticação

#### Tarefas:
- [ ] **2.1** Implementar `StravaOAuthService`
  - Método: `getAuthorizationUrl()`
  - Método: `exchangeCodeForToken()`
  - Método: `refreshAccessToken()`
  - Método: `getValidToken()`

- [ ] **2.2** Criar `StravaAuthController`
  - Endpoint: `GET /api/strava/auth`
  - Endpoint: `GET /api/strava/callback`
  - Endpoint: `GET /api/strava/status/{atletaId}`
  - Endpoint: `DELETE /api/strava/disconnect/{atletaId}`

- [ ] **2.3** Implementar tratamento de erros

- [ ] **2.4** Adicionar logs detalhados

- [ ] **2.5** Testes unitários
  - `StravaOAuthServiceTest.java`

- [ ] **2.6** Teste integração manual
  - Autorizar atleta via browser
  - Verificar tokens salvos no banco

**Entregáveis**: Autenticação OAuth2 funcionando end-to-end

---

### **SPRINT 3 - Sincronização de Atividades (Semana 2-3)**
**Objetivo**: Importar atividades do Strava

#### Tarefas:
- [ ] **3.1** Criar DTOs de Atividade
  - `StravaActivityDto.java`
  - `StravaSplitDto.java`

- [ ] **3.2** Implementar `StravaActivityService`
  - Método: `fetchActivities(atletaId, after, before)`
  - Método: `fetchActivityById(activityId)`
  - Método: `syncActivities(atletaId)`

- [ ] **3.3** Implementar mapeamento Strava → TreinoRealizado
  - Método: `mapStravaActivityToTreinoRealizado()`
  - Converter distância metros → km
  - Converter velocidade m/s → pace min/km
  - Mapear tipo de atividade

- [ ] **3.4** Adicionar lógica de deduplicação
  - Verificar por `external_id` antes de salvar

- [ ] **3.5** Criar `StravaActivityController`
  - Endpoint: `POST /api/strava/sync/{atletaId}`
  - Endpoint: `GET /api/strava/activities/{atletaId}`

- [ ] **3.6** Implementar paginação

- [ ] **3.7** Testes de integração

**Entregáveis**: Importação manual de atividades funcionando

---

### **SPRINT 4 - Webhooks (Semana 3-4)**
**Objetivo**: Sincronização em tempo real

#### Tarefas:
- [ ] **4.1** Criar `StravaWebhookController`
  - Endpoint: `GET /api/strava/webhook` (validação subscription)
  - Endpoint: `POST /api/strava/webhook` (receber eventos)

- [ ] **4.2** Implementar validação de subscription
  ```java
  hub.mode=subscribe
  hub.verify_token=WEBHOOK_TOKEN
  hub.challenge=random_string
  ```

- [ ] **4.3** Criar `StravaWebhookService`
  - Processar eventos: `create`, `update`, `delete`
  - Fila de processamento assíncrono

- [ ] **4.4** Registrar webhook via Strava API
  ```bash
  curl -X POST https://www.strava.com/api/v3/push_subscriptions \
    -F client_id=YOUR_CLIENT_ID \
    -F client_secret=YOUR_CLIENT_SECRET \
    -F callback_url=https://your-domain.com/api/strava/webhook \
    -F verify_token=YOUR_WEBHOOK_TOKEN
  ```

- [ ] **4.5** Implementar rate limiting

- [ ] **4.6** Adicionar retry logic para falhas

- [ ] **4.7** Testes com webhook simulator

**Entregáveis**: Sincronização automática em tempo real

---

### **SPRINT 5 - Cálculo de TSS (Semana 4)**
**Objetivo**: Calcular TSS baseado em dados do Strava

#### Tarefas:
- [ ] **5.1** Integrar Suffer Score do Strava
  - Mapear para campo `tssCalculado`

- [ ] **5.2** Implementar cálculo alternativo de TSS
  - Baseado em FC média vs FC limiar
  - Baseado em Pace médio vs Pace limiar

- [ ] **5.3** Criar service `TSSCalculatorService`
  - Método: `calculateFromHeartRate()`
  - Método: `calculateFromPace()`
  - Método: `calculateFromSufferScore()`

- [ ] **5.4** Adicionar lógica de escolha de método
  - Prioridade: Suffer Score > FC > Pace > RPE

- [ ] **5.5** Atualizar metadados do atleta
  - CTL, ATL, TSB após cada treino importado

**Entregáveis**: TSS calculado automaticamente

---

### **SPRINT 6 - Comparação Planejado vs Realizado (Semana 5)**
**Objetivo**: Comparar treinos planejados com realizados

#### Tarefas:
- [ ] **6.1** Implementar matching automático
  - Por data + tipo de treino
  - Por distância similar

- [ ] **6.2** Criar relatório de comparação
  - Distância planejada vs realizada
  - Pace planejado vs realizado
  - TSS planejado vs realizado

- [ ] **6.3** Endpoint de análise
  - `GET /api/treinos/comparacao/{treinoPlanejadoId}`

- [ ] **6.4** Dashboard de aderência
  - % treinos completados
  - Diferença média de volume
  - Diferença média de intensidade

**Entregáveis**: Análise de aderência ao plano

---

### **SPRINT 7 - Polimento e Testes (Semana 5-6)**
**Objetivo**: Garantir qualidade e segurança

#### Tarefas:
- [ ] **7.1** Segurança
  - Criptografar tokens no banco
  - Validar webhook signatures
  - Rate limiting em todos endpoints

- [ ] **7.2** Documentação
  - Swagger/OpenAPI completo
  - README com setup
  - Guia de troubleshooting

- [ ] **7.3** Testes
  - Cobertura > 80%
  - Testes de integração end-to-end
  - Testes de carga

- [ ] **7.4** Monitoramento
  - Logs estruturados
  - Métricas de sincronização
  - Alertas de falha

- [ ] **7.5** Deploy
  - Environment variables em produção
  - HTTPS configurado
  - Webhook subscription em produção

**Entregáveis**: Integração production-ready

---

## 🎯 Marcos de Entrega

| Marco | Descrição | Prazo Sugerido |
|-------|-----------|----------------|
| **M1** | Autenticação OAuth2 funcionando | Fim da Semana 1 |
| **M2** | Importação manual de atividades | Fim da Semana 2 |
| **M3** | Webhooks recebendo eventos | Fim da Semana 3 |
| **M4** | TSS calculado automaticamente | Fim da Semana 4 |
| **M5** | Comparação planejado vs realizado | Fim da Semana 5 |
| **M6** | Deploy em produção | Fim da Semana 6 |

---

## 🚀 Quick Start - Começar Hoje

### Comandos para Iniciar (Sprint 1):

```bash
# 1. Adicionar dependências ao pom.xml
# (Copiar do guia principal)

# 2. Criar arquivo .env
cat > .env << EOF
STRAVA_CLIENT_ID=YOUR_CLIENT_ID
STRAVA_CLIENT_SECRET=YOUR_CLIENT_SECRET
STRAVA_REDIRECT_URI=http://localhost:8098/api/strava/callback
STRAVA_WEBHOOK_TOKEN=menthoros_webhook_secret
EOF

# 3. Criar estrutura de pastas
mkdir -p src/main/java/com/menthoros/entity
mkdir -p src/main/java/com/menthoros/dto/strava
mkdir -p src/main/java/com/menthoros/services
mkdir -p src/main/java/com/menthoros/controller
mkdir -p src/main/java/com/menthoros/repository
mkdir -p src/main/resources/db/migration

# 4. Compilar
mvn clean compile

# 5. Executar migration
mvn flyway:migrate
```

---

## 📋 Checklist Final

Antes de considerar a integração completa:

### Funcionalidade
- [ ] Atleta consegue autorizar via OAuth2
- [ ] Tokens são renovados automaticamente
- [ ] Atividades são importadas corretamente
- [ ] Webhooks recebem eventos em tempo real
- [ ] TSS é calculado com precisão
- [ ] Comparação planejado vs realizado funciona

### Qualidade
- [ ] Cobertura de testes > 80%
- [ ] Documentação completa
- [ ] Logs estruturados
- [ ] Error handling robusto

### Segurança
- [ ] Credenciais em variáveis de ambiente
- [ ] Tokens criptografados
- [ ] HTTPS em produção
- [ ] Rate limiting ativo
- [ ] Webhook signature validada

### Performance
- [ ] Sincronização < 5 segundos
- [ ] Paginação implementada
- [ ] Cache de tokens
- [ ] Processamento assíncrono

---

**Próximo Passo**: Começar pelo Sprint 1, tarefa 1.1! 🎯