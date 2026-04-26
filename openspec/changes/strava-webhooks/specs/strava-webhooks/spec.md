## ADDED Requirements

### Requirement: Sistema valida subscription de webhook do Strava
O sistema SHALL responder ao challenge de validação enviado pelo Strava ao registrar uma subscription de webhook, confirmando o `hub.challenge` quando o `hub.verify_token` corresponder ao token configurado.

#### Scenario: Validação de subscription bem-sucedida
- **WHEN** `GET /api/strava/webhook?hub.mode=subscribe&hub.verify_token={token}&hub.challenge={challenge}` é recebido com `verify_token` correto
- **THEN** o sistema responde HTTP 200 com `{ "hub.challenge": "{challenge}" }` em JSON

#### Scenario: Validação com token incorreto
- **WHEN** `GET /api/strava/webhook` é recebido com `hub.verify_token` diferente do configurado em `STRAVA_WEBHOOK_TOKEN`
- **THEN** o sistema responde HTTP 403 sem revelar o token correto

---

### Requirement: Sistema recebe eventos de criação de atividade via webhook
O sistema SHALL processar eventos `POST /api/strava/webhook` com `object_type = "activity"` e `aspect_type = "create"`, importando a nova atividade do atleta correspondente.

#### Scenario: Evento de nova atividade para atleta conectado
- **WHEN** um evento webhook `aspect_type = "create"` é recebido com `owner_id` (Strava athlete ID) correspondente a um atleta com `IntegracaoExterna` ativa
- **THEN** o sistema responde HTTP 200 imediatamente e processa de forma assíncrona: busca a atividade via `GET /activities/{object_id}`, mapeia para `TreinoRealizado` e persiste com deduplicação por `externalId`

#### Scenario: Evento de nova atividade para atleta não conectado
- **WHEN** um evento webhook é recebido com `owner_id` que não corresponde a nenhum atleta cadastrado ou com `IntegracaoExterna` ativa
- **THEN** o sistema responde HTTP 200 (para evitar retries do Strava) e descarta o evento com log de aviso

#### Scenario: Atividade do tipo não suportado
- **WHEN** o evento é de tipo `swim`, `ride` ou outro não mapeado para `TipoTreino` do Menthoros
- **THEN** o sistema importa a atividade com `TipoTreino = null` ou tipo padrão, sem falhar o processamento

---

### Requirement: Sistema processa eventos de atualização de atividade via webhook
O sistema SHALL processar eventos com `aspect_type = "update"` atualizando o `TreinoRealizado` correspondente com os campos modificados.

#### Scenario: Atualização de atividade existente
- **WHEN** um evento `aspect_type = "update"` é recebido para uma atividade com `external_id` já existente em `TreinoRealizado`
- **THEN** o sistema busca a atividade atualizada na API Strava e atualiza os campos mapeáveis do registro existente

#### Scenario: Atualização de atividade não importada
- **WHEN** um evento `aspect_type = "update"` é recebido para uma atividade cujo `external_id` não existe em `TreinoRealizado`
- **THEN** o sistema trata como criação: importa a atividade completa e cria novo `TreinoRealizado`

---

### Requirement: Sistema processa eventos de exclusão de atividade via webhook
O sistema SHALL processar eventos com `aspect_type = "delete"` marcando o `TreinoRealizado` correspondente com `statusSincronizacao = CANCELADO` ao invés de deletar o registro.

#### Scenario: Exclusão de atividade importada
- **WHEN** um evento `aspect_type = "delete"` é recebido para uma atividade com `external_id` existente
- **THEN** o sistema atualiza `TreinoRealizado.statusSincronizacao = CANCELADO` e adiciona nota em `metadados_sincronizacao` indicando exclusão no Strava

#### Scenario: Exclusão de atividade não importada
- **WHEN** um evento `aspect_type = "delete"` é recebido para `external_id` inexistente
- **THEN** o sistema responde HTTP 200 e descarta o evento silenciosamente

---

### Requirement: Sistema responde imediatamente ao webhook e processa de forma assíncrona
O sistema SHALL retornar HTTP 200 ao endpoint de webhook em menos de 2 segundos. Todo o processamento de negócio (chamadas à API Strava, persistência) MUST ocorrer de forma assíncrona após a resposta.

#### Scenario: Processamento assíncrono de evento
- **WHEN** um evento válido é recebido em `POST /api/strava/webhook`
- **THEN** o sistema responde HTTP 200 em menos de 500ms e delega o processamento para executor assíncrono

#### Scenario: Falha no processamento assíncrono
- **WHEN** o processamento assíncrono falha (ex: API Strava indisponível, erro de banco)
- **THEN** o sistema registra o erro em log com detalhes do evento (object_id, owner_id, aspect_type) para reprocessamento manual

---

### Requirement: Sistema valida integridade dos eventos de webhook
O sistema SHALL verificar que o `owner_id` do evento corresponde a um `external_athlete_id` cadastrado em `IntegracaoExterna` antes de processar. Eventos com `owner_id` desconhecido MUST ser descartados.

#### Scenario: Evento com owner_id válido
- **WHEN** o webhook recebe evento com `owner_id` que existe em `IntegracaoExterna.external_athlete_id`
- **THEN** o sistema identifica o atleta e processa o evento no contexto do tenant correto

#### Scenario: Evento com owner_id desconhecido
- **WHEN** o webhook recebe evento com `owner_id` sem correspondência
- **THEN** o sistema responde HTTP 200, descarta o evento e registra log de aviso com o `owner_id` recebido
