## Why

O Menthoros precisa consumir dados reais de treino para alimentar seus modelos de IA com precisão. Sem integração com dispositivos e plataformas externas, os atletas precisam registrar treinos manualmente — o que reduz a adesão, introduz erros e priva o sistema de dados ricos (FC por split, pace por km, dados de GPS) necessários para calibrar TSS, CTL/ATL/TSB e gerar planos mais assertivos.

O Strava é a plataforma dominante entre corredores amadores e profissionais, e já possui toda a infraestrutura documentada para integração OAuth2 e webhooks. Implementar essa integração agora desbloqueia a automação do ciclo planejado → realizado → análise → próximo plano.

## What Changes

- **Nova entidade `IntegracaoExterna`**: armazena tokens OAuth2 por atleta e plataforma (design extensível para Garmin, TrainingPeaks etc.)
- **Novos campos em `TreinoRealizado`**: `statusSincronizacao`, `sincronizadoEm`, `urlExterno`, `metadadosSincronizacao`, `elapsedTimeSeg`, `sufferScore`, `deviceName`, `gearName`
- **Novos campos em `EtapaRealizada`**: `splitIndex`, `elevacaoGanhoMetros`, `elevacaoPerdaMetros`
- **Novo serviço OAuth2**: fluxo de autorização, troca de código, refresh automático de token
- **Novo serviço de sincronização**: importação de atividades Strava → `TreinoRealizado` com deduplicação por `externalId`
- **Novo serviço de webhooks**: processamento de eventos em tempo real (create, update, delete)
- **Novos DTOs Strava**: `StravaTokenResponse`, `StravaAthleteDto`, `StravaActivityDto`, `StravaSplitDto`
- **Novas migrations Flyway**: tabela `tb_integracao_externa`, campos em `tb_treino_realizado` e `tb_etapa_realizada`
- **Novas dependências Maven**: `spring-boot-starter-oauth2-client`, `spring-boot-starter-webflux`
- **Novas variáveis de ambiente**: `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, `STRAVA_REDIRECT_URI`, `STRAVA_WEBHOOK_TOKEN`

## Capabilities

### New Capabilities

- `strava-oauth`: Fluxo OAuth2 completo com Strava — autorização, callback, troca de tokens, refresh automático, status de conexão e desconexão por atleta
- `strava-activity-sync`: Importação e sincronização de atividades Strava para `TreinoRealizado`, com mapeamento de campos, deduplicação por `externalId`, importação de laps para `EtapaRealizada` e cálculo de TSS
- `strava-webhooks`: Recebimento e processamento de eventos Strava em tempo real (create, update, delete de atividades), com validação de subscription e processamento assíncrono

### Modified Capabilities

<!-- Nenhuma spec existente tem requisitos alterados por esta mudança -->

## Impact

**Entidades e banco:**
- Nova tabela: `tb_integracao_externa` (tokens OAuth2 multi-plataforma)
- Tabela alterada: `tb_treino_realizado` (8 novos campos)
- Tabela alterada: `tb_etapa_realizada` (3 novos campos)
- Migrations: V26, V27, V28 (a confirmar sequência com base nas existentes)

**APIs:**
- `GET /api/strava/auth?atletaId=` — inicia fluxo OAuth2
- `GET /api/strava/callback` — callback do Strava
- `GET /api/strava/status/{atletaId}` — status de conexão
- `DELETE /api/strava/disconnect/{atletaId}` — desconecta conta
- `POST /api/strava/sync/{atletaId}` — importação manual de atividades
- `GET /api/strava/webhook` — validação de subscription
- `POST /api/strava/webhook` — recebimento de eventos

**Dependências:**
- `spring-boot-starter-oauth2-client` (nova)
- `spring-boot-starter-webflux` / `WebClient` (nova)

**Sistemas externos:**
- Strava API v3 (OAuth2 + Activities + Webhooks)
- Keycloak (autenticação do usuário que autoriza o fluxo)
- Multi-tenancy: `tenant_id` obrigatório em `tb_integracao_externa`

**Segurança:**
- Tokens armazenados em coluna TEXT — criptografia a ser implementada
- `STRAVA_CLIENT_SECRET` somente via variável de ambiente
- Rate limiting nos endpoints públicos de webhook
