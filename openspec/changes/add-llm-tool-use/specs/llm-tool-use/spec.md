## ADDED Requirements

### Requirement: Contrato único para ferramentas expostas ao LLM
O sistema SHALL expor ferramentas ao LLM apenas por meio de beans que implementem o contrato `LlmTool<I, O>`.

#### Scenario: Registro automático via Spring
- **WHEN** um bean `@Component` implementar `LlmTool`
- **THEN** o sistema SHALL descobri-lo no startup e torná-lo disponível ao `ChatClient` quando o feature flag `app.llm.tool-use.enabled` estiver `true`

#### Scenario: Contrato completo
- **WHEN** uma ferramenta for registrada
- **THEN** ela SHALL expor `name` único, `description` em linguagem natural, `inputSchema` em JSON Schema e método `execute(I input)` tipado

#### Scenario: Nome duplicado
- **WHEN** dois beans de `LlmTool` declararem o mesmo `name`
- **THEN** o sistema SHALL falhar no startup com mensagem clara (evita ambiguidade no lado do LLM)

---

### Requirement: Persistência e auditoria de chamadas de ferramenta
O sistema SHALL persistir metadados de cada invocação de tool na tabela `tb_llm_tool_call`.

#### Scenario: Chamada bem-sucedida
- **WHEN** o LLM invocar uma tool e ela executar sem erro
- **THEN** o sistema SHALL persistir uma linha com `status=SUCCESS`, `input_payload`, `output_payload` e `duration_ms`

#### Scenario: Chamada com erro
- **WHEN** a tool lançar exceção
- **THEN** o sistema SHALL persistir uma linha com `status=ERROR` e `error_message`; a exceção original SHALL ser convertida em resposta textual ao LLM sem vazar stacktrace

#### Scenario: Sanitização de PII
- **WHEN** o input/output contiver campos sensíveis (email, telefone, dados de saúde)
- **THEN** o sistema SHALL sanitizar antes de persistir, substituindo por hashes ou marcadores

---

### Requirement: Feature flag controlada
O sistema SHALL controlar o uso de tool calling por feature flag configurável.

#### Scenario: Feature flag desligada
- **WHEN** `app.llm.tool-use.enabled=false`
- **THEN** o sistema SHALL operar no modo legado (prompt monolítico) e nenhuma tool SHALL ser registrada no `ChatClient`

#### Scenario: Feature flag ligada
- **WHEN** `app.llm.tool-use.enabled=true`
- **THEN** as tools registradas SHALL ser expostas e o prompt SHALL ser montado na versão `compact`

---

### Requirement: Multi-tenancy em chamadas de ferramenta
O sistema SHALL garantir isolamento de tenant em todas as invocações de tool.

#### Scenario: Contexto de execução carrega tenant
- **WHEN** uma tool for invocada
- **THEN** o sistema SHALL popular `LlmToolExecutionContext` com `tenantId` do `TenantContext` atual antes de chamar `execute()`

#### Scenario: Acesso cross-tenant bloqueado
- **WHEN** a tool consultar dados de outro atleta
- **THEN** toda query executada dentro da tool SHALL filtrar por `tenant_id` e retornar vazio se o atleta pertencer a outro tenant

---

### Requirement: Cache opcional por idempotência
O sistema SHALL oferecer cache opcional para ferramentas idempotentes.

#### Scenario: Tool marcada como idempotente
- **WHEN** uma tool for anotada como idempotente
- **THEN** o sistema SHALL consultar cache Caffeine com chave `(sessionId, toolName, inputHash)` e TTL de 5 minutos antes de invocar `execute()`

#### Scenario: Tool não idempotente
- **WHEN** uma tool não for marcada como idempotente
- **THEN** o sistema SHALL sempre invocar `execute()` sem consultar cache
