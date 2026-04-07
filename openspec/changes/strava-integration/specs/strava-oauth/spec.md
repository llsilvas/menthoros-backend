## ADDED Requirements

### Requirement: Atleta pode iniciar autorização OAuth2 com o Strava
O sistema SHALL redirecionar o atleta para a página de autorização do Strava quando o endpoint de início de fluxo for chamado com um `atletaId` válido. A URL de redirecionamento MUST incluir `client_id`, `redirect_uri`, `response_type=code`, `scope=read,activity:read_all` e `state=atletaId` (para identificar o atleta no callback).

#### Scenario: Início do fluxo OAuth2 com atletaId válido
- **WHEN** `GET /api/strava/auth?atletaId={uuid}` é chamado com um UUID de atleta existente
- **THEN** o sistema responde com redirecionamento HTTP 302 para a URL de autorização do Strava contendo todos os parâmetros OAuth2 obrigatórios

#### Scenario: Início do fluxo com atletaId inexistente
- **WHEN** `GET /api/strava/auth?atletaId={uuid}` é chamado com UUID que não corresponde a nenhum atleta
- **THEN** o sistema responde com HTTP 404 e mensagem de erro

---

### Requirement: Sistema processa callback OAuth2 do Strava
O sistema SHALL processar o callback do Strava trocando o `code` recebido por tokens de acesso e refresh, associando-os ao atleta identificado pelo parâmetro `state`. Os tokens MUST ser armazenados em `tb_integracao_externa` com `plataforma = STRAVA`.

#### Scenario: Callback com código válido
- **WHEN** `GET /api/strava/callback?code={code}&state={atletaId}` é recebido com código válido
- **THEN** o sistema troca o código por tokens na API do Strava, salva `access_token`, `refresh_token`, `token_expira_em` e `external_athlete_id` em `IntegracaoExterna`, e redireciona para o frontend com `strava=success`

#### Scenario: Callback com erro de autorização
- **WHEN** `GET /api/strava/callback?error=access_denied&state={atletaId}` é recebido
- **THEN** o sistema registra o erro em log e redireciona para o frontend com `strava=error`

#### Scenario: Callback com código inválido ou expirado
- **WHEN** a troca de código com a API do Strava falha (HTTP 4xx ou 5xx)
- **THEN** o sistema registra o erro, não persiste nenhum token e redireciona para o frontend com `strava=error`

---

### Requirement: Sistema renova access token automaticamente antes de chamadas à API
O sistema SHALL verificar se o `access_token` está expirado ou expira nos próximos 5 minutos antes de qualquer chamada à API do Strava. Se expirado, MUST usar o `refresh_token` para obter novo par de tokens e atualizar `IntegracaoExterna` antes de prosseguir.

#### Scenario: Token válido
- **WHEN** o sistema precisa chamar a API do Strava e o `access_token` ainda é válido
- **THEN** o sistema usa o token existente sem chamadas adicionais de autenticação

#### Scenario: Token expirado
- **WHEN** o sistema precisa chamar a API do Strava e `token_expira_em <= now() + 5min`
- **THEN** o sistema chama `POST /oauth/token` com `grant_type=refresh_token`, salva o novo `access_token`, `refresh_token` e `token_expira_em`, e prossegue com a chamada original

#### Scenario: Refresh token inválido
- **WHEN** a renovação de token falha com erro de autenticação do Strava
- **THEN** o sistema atualiza `ativo = false` em `IntegracaoExterna` e lança exceção indicando que o atleta precisa reautorizar

---

### Requirement: Sistema expõe status de conexão Strava por atleta
O sistema SHALL retornar se um atleta tem conta Strava conectada e ativa, incluindo o `strava_athlete_id` e data de última sincronização quando conectado.

#### Scenario: Atleta com Strava conectado
- **WHEN** `GET /api/strava/status/{atletaId}` é chamado e existe registro ativo em `IntegracaoExterna` para o atleta com `plataforma = STRAVA`
- **THEN** o sistema retorna `connected: true`, `externalAthleteId`, `ultimaSincronizacao`

#### Scenario: Atleta sem Strava conectado
- **WHEN** `GET /api/strava/status/{atletaId}` é chamado e não existe registro ou `ativo = false`
- **THEN** o sistema retorna `connected: false`

---

### Requirement: Atleta pode desconectar conta Strava
O sistema SHALL desativar a integração Strava de um atleta sem excluir o histórico de treinos já importados. O registro em `IntegracaoExterna` MUST ser marcado com `ativo = false` e os tokens removidos.

#### Scenario: Desconexão bem-sucedida
- **WHEN** `DELETE /api/strava/disconnect/{atletaId}` é chamado e existe conexão ativa
- **THEN** o sistema atualiza `ativo = false` e limpa `access_token` e `refresh_token` em `IntegracaoExterna`, e retorna HTTP 204

#### Scenario: Desconexão de atleta sem conexão ativa
- **WHEN** `DELETE /api/strava/disconnect/{atletaId}` é chamado e não existe conexão ativa
- **THEN** o sistema retorna HTTP 404

---

### Requirement: Isolamento multi-tenancy nos dados de integração
O sistema SHALL garantir que operações de OAuth e consulta de status respeitem o `tenant_id` do usuário autenticado. Um atleta de uma assessoria MUST NOT acessar dados de integração de atletas de outra assessoria.

#### Scenario: Acesso a dados do próprio tenant
- **WHEN** um usuário autenticado chama endpoints de status ou desconexão para um atleta do seu tenant
- **THEN** o sistema processa a requisição normalmente

#### Scenario: Acesso a dados de outro tenant
- **WHEN** um usuário tenta acessar dados de integração de um atleta de outro tenant
- **THEN** o sistema retorna HTTP 404 (não expõe existência do recurso)
