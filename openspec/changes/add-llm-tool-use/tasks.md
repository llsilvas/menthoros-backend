## 1. Infraestrutura base

- [ ] 1.1 Validar versão do Spring AI (1.0.0-M6) quanto a suporte de tool calling com OpenAI; documentar em `design.md` se houver necessidade de upgrade
- [ ] 1.2 Criar pacote `com.menthoros.llm.tool` para abrigar contrato e registrador
- [ ] 1.3 Criar interface `LlmTool<I, O>` com métodos `getName()`, `getDescription()`, `getInputSchema()`, `execute(I)`
- [ ] 1.4 Criar `LlmToolRegistry` que descobre beans `LlmTool` e expõe lista de `FunctionCallbackWrapper` para `ChatClient.Builder`
- [ ] 1.5 Criar `LlmToolExecutionContext` carregando `tenantId`, `atletaId`, `sessionId` para que tools saibam contexto

## 2. Persistência de invocações

- [ ] 2.1 Criar entidade `LlmToolCall` com `sessionId`, `atletaId`, `toolName`, `inputPayload` (JSONB), `outputPayload` (JSONB), `durationMs`, `status` (SUCCESS/ERROR), `errorMessage`, `tenantId`
- [ ] 2.2 Criar migration `Vxx__Create_llm_tool_call_table.sql` com índice `(session_id, created_at)`
- [ ] 2.3 Criar `LlmToolCallRepository` com `findBySessionIdOrderByCreatedAtAsc`
- [ ] 2.4 Interceptar execução em `LlmToolRegistry` para persistir cada chamada com `inputPayload` sanitizado (sem PII)

## 3. Ferramentas MVP

- [ ] 3.1 Criar `GetAtletaMetricasTool` que consulta `MetricasAgregadasService` e retorna `{ ctl, atl, tsb, paceLimiar, dataCalculo }`
- [ ] 3.2 Criar `GetHistoricoTreinosTool` que consulta `TreinoRealizadoRepository` paginado (default N=10) retornando `{ data, tss, distanciaKm, tipoTreino, rpe }`
- [ ] 3.3 Criar `GetProvaAlvoTool` que consulta `ProvaRepository` e retorna `{ nome, data, distanciaKm, tempoObjetivoSeg }` da prova-alvo ativa

## 4. Schema JSON para LLM

- [ ] 4.1 Implementar serialização do `getInputSchema()` em formato JSON Schema compatível com OpenAI (campos `type`, `properties`, `required`)
- [ ] 4.2 Criar testes unitários validando que schema gerado é aceito pelo endpoint OpenAI `function` de cada ferramenta

## 5. Integração com IaService

- [ ] 5.1 Adicionar opção no `IaService.gerarPlano()` para usar `ChatClient` com tools registrados (feature flag `app.llm.tool-use.enabled`, default `false`)
- [ ] 5.2 Se feature flag ativa, registrar as 3 tools do MVP e simplificar prompt para referir-se a elas
- [ ] 5.3 Manter caminho legado (prompt monolítico) como fallback enquanto feature flag estiver desligada
- [ ] 5.4 Logar ao final da geração: quantas tools foram chamadas, com qual latência total adicional

## 6. Migração gradual do PlanoTreinoPromptBuilder

- [ ] 6.1 Preparar versão `compact` do prompt usada quando `tool-use.enabled=true`: contexto mínimo + instrução explícita sobre quais tools consultar
- [ ] 6.2 Manter versão `full` atual como default
- [ ] 6.3 Adicionar testes A/B locais comparando qualidade textual dos planos (checklist subjetivo, não automatizado) entre os dois modos

## 7. Cache de idempotência

- [ ] 7.1 Adicionar cache Caffeine local `llm-tool-results` com TTL 5min e chave `(sessionId, toolName, inputHash)`
- [ ] 7.2 Aplicar cache apenas para tools explicitamente marcadas como idempotentes (getters de estado atual — `GetAtletaMetricasTool`, `GetProvaAlvoTool`; não para operações que escrevem)
- [ ] 7.3 Métrica: `llm_tool_cache_hits_total` e `llm_tool_cache_misses_total`

## 8. Observabilidade e administração

- [ ] 8.1 Criar endpoint interno `GET /api/llm/tool-calls?sessionId=X` protegido por role `ADMIN`
- [ ] 8.2 Adicionar métricas Micrometer: `llm_tool_calls_total{tool_name, status}`, `llm_tool_call_duration_seconds{tool_name}`
- [ ] 8.3 Log estruturado com JSON quando chamada de tool falhar (para correlação com sessão)

## 9. Multi-tenancy

- [ ] 9.1 Garantir que toda tool recebe `tenantId` via `LlmToolExecutionContext` e propaga em queries
- [ ] 9.2 Teste que tool invocada em sessão de tenant A NÃO consegue retornar dados de tenant B

## 10. Testes

- [ ] 10.1 Testes unitários de cada tool (`GetAtletaMetricasTool`, `GetHistoricoTreinosTool`, `GetProvaAlvoTool`)
- [ ] 10.2 Teste de integração do registry: beans são descobertos e expostos como FunctionCallbackWrapper
- [ ] 10.3 Teste end-to-end com mock do ChatClient simulando LLM que invoca tool e usa resposta
- [ ] 10.4 Teste de persistência: cada chamada resulta em linha em `tb_llm_tool_call`
- [ ] 10.5 Teste de cache: chamadas idempotentes repetidas dentro do TTL retornam do cache

## 11. Documentação

- [ ] 11.1 Criar `design.md` detalhando: decisão de manter fallback, comparação de versões Spring AI, contratos de Schema JSON, PII handling, e plano de migração de Skills
