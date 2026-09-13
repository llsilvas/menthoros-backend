# Motor de Geração de Planos — Análise Arquitetural e Plano

**Data:** 2026-09-13
**Escopo:** geração semanal de treinos via LLM (`IaServiceImpl` e pipeline em volta)
**Stack:** Spring Boot 3.5.14 · Spring AI 1.1.6 · Java 21 · rota `plano` → gpt-4o, JSON Schema strict
**Escala-alvo:** 10 assessorias × 10 atletas gerando planos no MVP

Este documento consolida a análise feita em 2026-09-13 e o plano de implementação em sete fases.
Ele complementa `LLM_BEST_PRACTICES.md` (práticas gerais) e `PROMPTS_EVOLUCAO.md` (histórico do
prompt). As changes citadas vivem em `menthoros-product/openspec/changes/`.

---

## 1. Tese

O problema do motor não é custo nem provider. Com os números reais de produção, um plano custa
cerca de US$ 0,05, e cem planos por semana ficam abaixo de US$ 25 por mês. O problema é que o LLM
recebe um monólogo de 12,8 mil tokens, sem cache, e é obrigado a produzir aritmética (distâncias,
durações, ritmos, faixas de FC por etapa) que o Java depois refaz em cerca de mil linhas de reparo.
Quando o reparo não salva, a resposta inteira é descartada e regenerada do zero com uma dica de 300
caracteres. Isso explica os 31 a 40 segundos por plano, os 70 segundos no cold-start e a taxa de
aceitação sem edição em torno de 60%.

A direção já está decidida no produto (ADR-0011, `planner-engine-enforcement`): **o Java decide
quanto e quando; o LLM decide o quê e escreve.** O plano abaixo leva essa decisão até o contrato de
saída do modelo, onde ela ainda não chegou, e coloca observabilidade e avaliação antes de qualquer
troca de provider ou tool calling.

### Números reais (logs de produção, 2026-08-18 e 2026-09-07)

| Métrica | Valor |
|---|---|
| Tokens de prompt por plano | 12.778 a 12.819 |
| Tokens em cache | 0 em 3 de 4 chamadas (1.920 na quarta, mesmo atleta minutos depois) |
| Tokens de saída | 1.043 a 1.433 |
| Latência por plano | 31,5 a 40,7 s (70,6 s no cold-start medido em produção) |
| Custo por plano (gpt-4o, `llm-pricing.yml`) | ≈ US$ 0,046 |
| Linhas na `IaServiceImpl` | 1.699, das quais ~1.000 de reparo e validação |

---

## 2. O pipeline hoje

Caminho real: `PlanoTreinoController` → `PlanoServiceImpl.gerarPlanoTreino` (sem transação, três
fases: load, LLM, persist) → `IaServiceImpl.geraPlanoSemanalAvancado` → `PlanGenerationPersister`.
Em lote, o mesmo miolo roda em virtual threads sob o `LlmConcurrencyLimiter`.

1. **Loader.** Atleta, histórico de 14 dias, zonas, prova, skeleton do planner (flag desligada em
   produção).
2. **PromptBuilder.** 17 dependências, 8 formatters, template de 523 linhas (~24 KB estáticos + 8 a
   12 KB dinâmicos). `PlanoTreinoPromptBuilder.buildOptimizedPrompt`.
3. **gpt-4o.** Uma única mensagem `user`, JSON Schema strict, temperature 0,2, max_tokens 12.000,
   timeout 120 s, sem streaming. `IaServiceImpl.defaultJsonSchemaOptions`.
4. **Reparo e validação.** ~1.000 linhas: expansão de etapas agregadas, correção de distâncias
   temporais, reconciliação distância vs. etapas, FC por zona LTHR, pace contra teto e piso,
   triângulo pace/duração/distância, `PlanoEstruturaReparador`.
5. **Retry.** `PlanoResilienceService` regenera do zero com o motivo truncado em 300 caracteres.
   Máximo de 2 gerações em 100 s.
6. **Persister.** Redistribuição de dias, prova garantida no plano (`ProvaNoPlanoService`),
   compliance estágio 2.

**O que está bem resolvido e deve ser preservado:** chamada ao LLM fora de transação; limiter
justo por tenant com reserva interativa; índice único parcial como árbitro de idempotência;
orçamento único de gerações por requisição; `Constraint` como seam entre prompt e
`PlanQualityChecker`; golden-master do prompt; métricas de custo por rota via
`CostTrackingAdvisor`; planner determinístico com compliance em dois estágios.

---

## 3. Achados, por severidade

### A1 (crítico) — O contrato de saída pede ao LLM o que o Java sabe calcular

O schema exige, por etapa, distância, duração, `ritmoAlvo` com regex e `fcAlvoEtapa` em bpm, e
por treino a soma coerente de tudo. O modelo erra aritmética com frequência conhecida, e o backend
responde com `corrigirDistanciasEtapasTemporais`, `expandirEtapasAgregadas`,
`reconciliarDistanciaComEtapas`, `recalcularDuracaoTreino`,
`validarTrianguloPaceDuracaoDistancia`, validação de FC por zona e o reparador estrutural. Cada
regra nova de treino vira mais reparo. O cold-start mostrou que o reparo deixa passar
inconsistências como aviso.

É a causa raiz de A2, A3 e do tamanho da classe. A alternativa é um **schema semântico**: o LLM
devolve a estrutura de cada sessão em zonas e unidades (tipo, quantidade, unidade, zona,
recuperação), e o Java resolve pace, FC, distância e duração a partir das zonas do atleta, que ele
já possui. Saída menor, sem regex de ritmo, sem triângulo a validar.

Evidência: `IaServiceImpl.java:150-285` (schema), `:403-558`, `:965-1064`, `:1276-1508`;
`plano-treino-otimizado-claude.txt:485-523`.

### A2 (crítico) — Retry regenera do zero em vez de corrigir

`PlanoResilienceService` reenvia o prompt inteiro (12,8 mil tokens) com um parágrafo de motivo
truncado em 300 caracteres. O modelo não vê o que produziu nem o que exatamente falhou, então a
segunda tentativa é uma nova amostra, não uma correção. Com cache de prefixo ativo, um **turno de
reparo** (histórico da conversa + saída anterior + lista completa de violações + pedido de correção)
custa quase só tokens de saída e converge muito mais.

Evidência: `PlanoResilienceService.java:88-98`, `IaServiceImpl.java:343-352`.

### A3 (crítico) — Cache de prefixo está estruturalmente quebrado

O template abre com nome e idade do atleta na linha 6. As ~24 KB de regras estáticas vêm depois, e
não há mensagem `system`. A OpenAI cacheia por prefixo idêntico (mínimo 1.024 tokens), logo o
prefixo cacheável tem cinco linhas. Produção confirma: `cachedTokens=0` em três de quatro chamadas;
a única leitura de cache (1.920 tokens) foi do mesmo atleta minutos depois.

A change `system-user-prompt-split` foi deferida em 2026-07-07 com a premissa "cache por prefixo
já economiza", que os dados desmentem.

Evidência: `plano-treino-otimizado-claude.txt:6-12`, `PlanoTreinoPromptBuilder.java:376-388`,
logs de 2026-09-07 00:24 a 00:34.

### A4 (alto) — Não existe registro persistido de geração

Tokens, custo, latência, modelo, versão do prompt, número de tentativas, violações e resultado da
revisão do coach não vivem em tabela nenhuma. Há métricas Prometheus agregadas e um log de uso, mas
nada que permita perguntar "quais planos rejeitados vieram de qual versão do prompt". Sem isso,
nenhuma mudança de prompt, schema ou modelo pode ser avaliada com evidência, e o sinal de ouro que
já existe (ACCEPTED / MODIFIED / REJECTED do coach) não se conecta à geração que o produziu.

Evidência: `LlmUsageLogger.java:40`, `CostTrackingAdvisor.java:146-152`, migration V58
(`planner_metadata_json`, sem prompt nem tokens).

### A5 (alto) — Prompt sem versão; eval mede o texto, não o resultado

Só o planner tem versão (`PlannerVersion.CURRENT = "planner-v1"`). O prompt é "versionado" pelo
golden-master (`PlanoTreinoPromptBuilderGoldenTest`), que garante que o texto não mudou, não que o
plano ficou melhor. Não há conjunto de avaliação de planos gerados nem juiz (determinístico ou
LLM) que rode quando o prompt muda.

### A6 (alto) — `IaServiceImpl` está acoplada à OpenAI apesar do `ModelRouter`

`defaultJsonSchemaOptions()` devolve `OpenAiChatOptions`. O roteador abstrai o `ChatClient`, mas a
rota `plano` não pode apontar para Claude sem mudar código. Na linha Spring AI 1.1.x, structured
output nativo só existe para OpenAI (`ResponseFormat.Type.JSON_SCHEMA`); o Claude ganha
`outputSchema` apenas no **Spring AI 2.0, que exige Spring Boot 4**. Um teste A/B de provider hoje
é impossível.

Evidência: `IaServiceImpl.java:108-123`, `MultiModelConfig.java:197-215`.

### A7 (alto) — Tool calling para leitura de dados é overhead, não ganho

As changes `add-llm-tool-use` e `rag-tool-calling-prescription-engine` propõem expor histórico,
métricas e prova como tools `@Tool`. O conjunto de entradas de um plano semanal é conhecido e
limitado; o modelo não precisa "descobrir" o que buscar. Cada round-trip reenvia o histórico,
quebra o cache e soma latência num fluxo já de 35 s. O próprio design da change RAG (D10)
reconhece isso.

Tool calling tem valor real em dois lugares: como **ferramenta de validação** no turno de reparo,
e mais tarde como **canal de RAG**. Não como substituto do contexto determinístico.

### A8 (baixo) — Código morto e configuração órfã

- `gerarPlanoSemanal` (legado) e `gerarPlanosEmLote` (não implementado) na interface `IaService`;
  `plano-treino-prompt.txt` só do caminho legado.
- `llmTaskExecutor` (`LLMConfig`), `app.llm.max-tokens/temperature/timeout` e
  `app.ia.service.strategy` sem leitor.
- `spring.ai.retry.*` não configurado: `LlmRetryConfig` herda os defaults do Spring AI (10
  tentativas, backoff 2 s × 5) para 5xx e 429.
- `PlanQualityChecker` só mede: violação de constraint declarada não bloqueia nem entra no retry.
- Modelo `gpt-4o` e `llm-pricing.yml` com vigência 2026-05; roteamento ainda cita
  `claude-sonnet-4-6`.

---

## 4. Avaliação da `IaServiceImpl`

A classe tem 1.699 linhas, 14 colaboradores injetados e quatro responsabilidades:

| Responsabilidade | Linhas | Observação |
|---|---|---|
| Construção do JSON Schema | 108 a 285 | `buildSchemaTightInlineOrDefs` endurece o schema à mão |
| Orquestração da chamada | 290 a 398 | inclui o legado `gerarPlanoSemanal` |
| Validação e normalização | 403 a 1.690 | `validarTreinoIntervalado` sozinho tem 232 linhas |
| Parsing de descrição livre | espalhado | regex de repetições e fartlek |

Seis classes de teste acessam métodos privados por reflexão, o sintoma mais claro de que os
colaboradores certos ainda não existem.

A change `refactor-iaservice-decomposition` já descreve a decomposição correta
(`LlmJsonSchemaBuilder`, `PlanoLlmValidator`, helpers), mas está em NO-GO por um `review.md` cujo
bloqueador principal, "schema exige `provaId` do LLM", foi resolvido no PR #101
(`fix/prova-id-null-vs-llm-schema`): o schema hoje remove `provaId`, `descricao` e `zonaAlvo`, e o
`ProvaNoPlanoService` preenche no servidor. A revisão precisa ser refeita contra o código atual.

**Recomendação de forma.** Não decompor "para ficar menor". Decompor em torno de uma porta:

- `PlanoLlmClient` — prompt + schema → DTO tipado, com usage. Um adaptador OpenAI hoje, espaço
  para um Anthropic depois.
- `PlanoLlmSchemaBuilder` — schema versionado.
- `PlanoLlmNormalizer` — reparo determinístico, puro.
- `PlanoLlmValidator` — invariantes, puro.
- `IaServiceImpl` — orquestradora de ~150 linhas.

Quando A1 for feito, o normalizer encolhe sozinho porque a maior parte do reparo deixa de ter razão
de existir.

---

## 5. Arquitetura-alvo

```
Planner ──► Contexto determinístico ──► LLM turno 1 ──► Resolver ──► Validator ──► LLM turno de reparo ──► Ledger
(skeleton:   (system estático +          (sessões         (zonas →     (invariantes   (mesma conversa,          (tokens, custo,
 dia, TSS,    user dinâmico,              semânticas        pace, FC,    hard,          violações completas,      latência, versão,
 zona/slot)   versionado)                 por slot)         dist, dur)   estágio 1)     cache quente)             resultado, revisão)
```

Princípios:

- **Quanto e quando é do Java; o quê e o texto são do LLM.** O schema de saída expressa intenção
  de treino em zonas e unidades. Números absolutos nunca vêm do modelo.
- **Prompt em duas partes.** `system` imutável por versão (persona, regras, formato, checklist) e
  `user` com o contexto do atleta. Liga o cache de prefixo na OpenAI e o cache explícito de 1 h na
  Anthropic (`AnthropicCacheStrategy.SYSTEM_ONLY`), e re-baseia o golden-master uma única vez.
- **Reparo, não regeneração.** A segunda chamada continua a conversa com a saída anterior e a lista
  completa de violações, dentro do mesmo orçamento (máx. 2 gerações, 100 s).
- **Provider atrás de uma porta.** Um adaptador por provider; a rota escolhe. O Claude entra por
  tool-as-schema (Spring AI 1.1) ou `outputSchema` (Spring AI 2.0), quando houver eval para julgar.
- **Toda geração deixa rastro.** Uma linha em `tb_llm_call` por Chamada LLM, ligada ao plano por
  `generation_request_id` (join, nada copiado) e ao veredito do coach pelo mesmo caminho. É o
  dataset do eval e o painel de custo por tenant.
- **Eval antes de trocar.** Nenhuma mudança de prompt, schema ou modelo sem rodar o conjunto de
  avaliação: checkers determinísticos + juiz LLM com rubrica + taxa histórica de aceitação.

---

## 6. O que fazer com as changes existentes

| Change | Estado (2026-09-13) | Recomendação |
|---|---|---|
| `planner-engine-enforcement` | 27/29, gate 8.4 | **Terminar.** Alicerce de tudo; gate de duas portas continua fail-closed. Não está mais bloqueada: a Decisão 4b foi fechada em 2026-09-09 e virou ADR-0011. |
| `fix-cold-start-load-model` | 8/11, DoR fechado | **Terminar** (piloto, QA, PR). Independente do resto. |
| `system-user-prompt-split` | deferida (ROI ~0) | **Reabrir** com a premissa corrigida (A3). Vira a Fase 1. |
| `refactor-iaservice-decomposition` | NO-GO em `review.md` | **Re-revisar** contra o código pós-#101; reescopar em torno da porta `PlanoLlmClient`. Fase 2. |
| `fix-cold-start-calibration-plan-generation` | 4/31, §13.4 aberto | Os quatro pontos de §13.4 (tolerâncias, gate de etapas, tipos sem estrutura, WARN→hard) são **absorvidos pela Fase 4**: com schema semântico, tolerância aritmética deixa de existir. Manter só o snapshot de calibração pré-prompt. |
| `validate-interval-workout-standards` | 0/79, backlog frio | **Fechar como superada** pelas Fases 3 e 4. O "feedback loop ao LLM" é o turno de reparo. |
| `add-llm-tool-use` | 4/31 (`tasks-v2.md`) | **Reescopar**: só o spike (tool loop × strict) e uma tool de validação para o turno de reparo. Sem tools de leitura de dados no caminho crítico. Marcar `tasks.md` legado como obsoleto. |
| `rag-tool-calling-prescription-engine` | 0/64 | **Descartar fase 1** (tool calling de leitura) e **fase 3** (absorvida). Manter fase 2 (RAG) para depois do eval. |
| `migrate-plan-prompt-to-skills` | 0/37 | **Adiar** para depois da Fase 4: o conteúdo do prompt muda quando o schema muda. Fazer antes seria migrar formatters que serão reescritos. |
| `llm-code-switching` | 0/21 | **Juntar à Fase 1**: o novo `system` já nasce em inglês com saída PT-BR (ADR-0006). |
| `rag-injury-aware-prescription`, `rag-coach-methodology-personalization` | 0/24, 0/29 | Backlog; dependem de ledger e eval para provar valor. |

---

## 7. Plano de implementação

Sete fases, cada uma fechável em um PR com flag e gate próprio. As três primeiras são pequenas e
desbloqueiam a medição; a quarta é o salto de qualidade; as demais dependem dos dados que as
anteriores geram.

### Fase 0 — Ledger e versão do prompt

**Tamanho:** S (~3 dias) · **Change:** nova, `add-plan-generation-ledger`

- **Implementada em 2026-09-13** (nome final: `tb_llm_call`, não `tb_plan_generation` — uma
  linha por chamada, em toda rota; enriquecimento só na rota `plano`). `tenant_id`, `atleta_id`
  (FK `ON DELETE SET NULL`), `generation_request_id` (agrupa as tentativas; a ligação com o plano é
  por essa coluna em `tb_plano_semanal`, não por `plano_id`), rota, modelo, tokens in/out/cache,
  custo, latência, `attempt`, `prompt_version`/`prompt_hash`/`schema_version`, `result`
  (`PENDING`/`SUCCESS`/`VALIDATION_REJECTED`/`PARSE_ERROR`/`LLM_ERROR`/`TIMEOUT` — mais granular
  que o rascunho original), `request_outcome` (desfecho da requisição, gravado na última chamada:
  `PERSISTED`/`CONFLICT`/`REJECTED_POST_LLM`/`PERSIST_ERROR`), `transport_retries`, `violations`
  (JSONB), `response_json` (JSONB, nome do atleta redigido). Sem o texto do prompt, como a V58.
- `PromptVersion.CURRENT` (`plano-v1`) ao lado de `PlannerVersion`; `SchemaVersion.CURRENT`
  separado (a Fase 4 muda o schema sem mudar o prompt); hash SHA-256 do template no startup.
- Escrita em transação própria (`REQUIRES_NEW`, teto de 5 s) — não "fora de transação": os
  listeners assíncronos (`WorkoutAnalysisListener`, `WeeklyFocusNarrativeService`) chamam o LLM
  **dentro** de uma transação, e o ledger não pode ser derrubado pelo rollback deles.
- Desfecho ligado por `generation_request_id`, não por job noturno de `review_status` — a última
  chamada da requisição recebe `PERSISTED`/`CONFLICT`/`REJECTED_POST_LLM`/`PERSIST_ERROR`
  diretamente em `PlanoServiceImpl.gerarPlanoTreino`; o veredito do coach continua sendo join.
- Purga diária (`LlmCallRetentionScheduler`, 90 dias) anula `response_json`; exclusão do atleta
  (soft delete — `Atleta` não tem hard delete no domínio) também anonimiza suas linhas.
- `spring.ai.retry` explícito (3 tentativas, backoff 1 s × 2, máx. 10 s) saiu antes, como `chore`
  separado (PR #115), em vez do default de 10 tentativas herdado.

**Gate:** painel (Grafana ou SQL) com custo e latência p50/p95 por tenant e por versão de prompt.

### Fase 1 — System/user split e cache

**Tamanho:** S (~3 dias) · **Change:** reabre `system-user-prompt-split`; absorve
`llm-code-switching`

- Dividir o template: `plano-system-v2.txt` (persona, regras, formato, checklist; em inglês) e
  `plano-user-v2.txt` (perfil, histórico, skeleton; PT-BR). Ordem estável: tudo estático antes de
  qualquer valor do atleta.
- `chatClient.prompt().system(...).user(...)`; no bean Anthropic, manter `SYSTEM_ONLY` com TTL de
  1 h já configurado.
- Re-baseline do golden-master com revisão humana, uma vez.

**Gate:** `cachedTokens` ≥ 60% do prompt na segunda geração de qualquer atleta do mesmo tenant.
Latência esperada cai de 3 a 8 s.

### Fase 2 — Porta `PlanoLlmClient` e decomposição

**Tamanho:** M (~1 semana) · **Change:** `refactor-iaservice-decomposition`, re-revisada

- Refazer o `review.md` contra o código atual; o bloqueador de `provaId` está resolvido por #101.
- Extrair `PlanoLlmSchemaBuilder`, `PlanoLlmNormalizer`, `PlanoLlmValidator` (puros, testados sem
  reflexão) e a porta `PlanoLlmClient` com `OpenAiPlanoLlmClient`.
- Testes de caracterização primeiro: fixtures reais de resposta gpt-4o gravadas do ledger (Fase 0)
  em WireMock, para que a saída observável não mude.
- Remover o código morto de A8.

**Gate:** golden-master e fixtures idênticos antes e depois; `IaServiceImpl` < 200 linhas.

### Fase 3 — Turno de reparo

**Tamanho:** S/M (~4 dias) · **Change:** nova, `plan-generation-repair-turn`

- `PlanoResilienceService` passa a manter a conversa: `[system, user, assistant(json anterior),
  user(violações completas + "corrija só isto")]`, mesmo orçamento e deadline.
- Mensagem de violação estruturada por treino e etapa, gerada pelo validator (não mais 300
  caracteres truncados).
- Violações do `PlanQualityChecker` passam a alimentar o reparo, não só a métrica.
- Opcional, atrás do spike de `add-llm-tool-use`: expor `validar_plano` como tool para o modelo se
  auto-checar antes do `end_turn`.

**Gate:** taxa de `plano_retry` que termina em sucesso ≥ 80%; `plano_geracao_falha_final` < 2%.

### Fase 4 — Schema semântico de sessão

**Tamanho:** L (~2 semanas) · **Change:** nova, `semantic-session-schema`, atrás de flag. Absorve
§13.4 de `fix-cold-start-calibration-plan-generation` e `validate-interval-workout-standards`. É o
salto de qualidade.

- Novo DTO de saída, versão 2, por slot do skeleton:

  ```json
  {
    "tipo": "INTERVALADO",
    "foco": "VO2max",
    "blocos": [
      { "papel": "AQUEC",     "quantidade": 15, "unidade": "MIN", "zona": "Z2" },
      { "papel": "PRINCIPAL", "quantidade": 6,  "unidade": "REP", "zona": "Z5",
        "repeticaoDe": { "quantidade": 800, "unidade": "M" },
        "recuperacao": { "quantidade": 400, "unidade": "M", "zona": "Z1" } },
      { "papel": "DESAQ",     "quantidade": 10, "unidade": "MIN", "zona": "Z1" }
    ],
    "racional": "...",
    "notaCoach": "..."
  }
  ```

  Sem pace, FC, distância total ou duração total.
- `SessionResolver` (puro, no domínio) transforma blocos em etapas absolutas usando as zonas de FC
  e pace do atleta, reaproveitando `zonaParaFc` e `PaceValidator`. Aritmética correta por
  construção. O front continua recebendo etapas absolutas.
- Validator reduzido a invariantes de estrutura (papéis, ordem, mínimo de blocos por tipo, TSS do
  slot ±20%) e às hard do planner.
- Prompt encolhe: as ~40 linhas de "INSTRUÇÕES CRÍTICAS DE FORMATO" sobre aritmética somem. Saída
  cai para ~600 a 800 tokens.
- Rollout por flag `app.llm.plano.schema-version=2` por tenant, com `schema_version` no ledger
  para comparar v1 × v2 no mesmo período.

**Gate:** piloto de 2 tenants por 2 semanas com retry ≤ 10%, violações estruturais ≤ 5%,
aceitação sem edição do coach ≥ v1 + 10 p.p., latência p50 ≤ 20 s.

### Fase 5 — Eval set e juiz

**Tamanho:** M (~1 semana) · **Change:** nova, `plan-generation-eval-set`

- Amostrar 40 a 60 gerações do ledger, estratificadas por arquétipo, cold-start e veredito do
  coach; anonimizar; guardar como fixtures.
- Três graders: checkers determinísticos (invariantes + compliance), juiz LLM com rubrica de coach
  (progressão, polarização, especificidade para a prova, clareza do texto), e concordância com o
  plano final editado pelo coach quando houver.
- Runner Maven separado (`-Peval`), roda sob demanda e em toda change que toca prompt, schema ou
  modelo. Custo por rodada medido e documentado.

**Gate:** nenhuma change de prompt, schema ou modelo é mergeada sem tabela de eval no PR.

### Fase 6 — Bake-off de modelo e provider

**Tamanho:** M (~1 semana) · **Change:** nova, `plan-llm-provider-bakeoff` · depende de 4 e 5

- Candidatos: `gpt-4o` (baseline), o sucessor atual da OpenAI na mesma faixa, `claude-sonnet-5` e
  `claude-haiku-4-5`. Rodar o eval com os quatro; decidir por qualidade, depois latência, depois
  custo.
- Adaptador Anthropic em Spring AI 1.1: tool única com `input_schema` = schema v2 e
  `ToolChoiceTool` forçado, que dá saída estruturada sem o upgrade. Alternativa: spike de Spring AI
  2.0 (`outputSchema`), que exige Spring Boot 4, avaliado como change própria de plataforma.
- Atualizar `llm-pricing.yml` e as rotas para a geração atual; o modelo vencedor entra por tenant
  via flag.

**Gate:** eval do vencedor ≥ baseline em qualidade com latência p50 ≤ 15 s.

### Fase 7 — Conhecimento e personalização

**Tamanho:** L · backlog · depende de 5

- Só depois do eval: RAG de lesão (`rag-injury-aware-prescription`) e few-shot da metodologia do
  coach (`rag-coach-methodology-personalization`) entram como contexto adicional no `user`, medidos
  contra a rubrica.
- Tool calling volta à mesa aqui, como canal de recuperação, não de leitura de dados do atleta.
- `migrate-plan-prompt-to-skills` entra depois que o schema estabilizar.

---

## 8. Capacidade para 100 atletas

O lote de domingo à noite é o cenário de pico: 10 assessorias, 10 atletas cada, disparando geração
em lote perto do mesmo horário.

| Cenário | Latência/plano | Concorrência lote | 100 planos | Custo/100 |
|---|---|---|---|---|
| Hoje (gpt-4o, sem cache, retry ~25%) | 35 a 70 s | 3 | 25 a 40 min | ≈ US$ 5,5 |
| Após Fase 1 (cache ligado) | 28 a 60 s | 3 | 20 a 35 min | ≈ US$ 4,0 |
| Após Fase 4 (saída semântica) | 15 a 25 s | 6 | 5 a 8 min | ≈ US$ 3,0 |

- **Hikari não é gargalo.** A chamada ao LLM roda fora de transação desde 2026-09-01
  (`refactor-llm-call-outside-transaction`). Pool de 10 continua suficiente; o limiter é o
  controle real.
- **Subir `BATCH_PLAN_LLM_CONCORRENCIA` de 4 para 8** depois da Fase 1, mantendo reserva
  interativa 1 e cap por tenant 2 (um tenant nunca monopoliza). Limites de TPM da OpenAI comportam
  8 × 13k tokens em voo com folga.
- **Timeout por rota** de 120 s e orçamento de 100 s continuam; com o turno de reparo, o pior caso
  fica em 2 chamadas dentro dos 100 s.
- **Semáforos são por JVM.** Com uma réplica no Railway está correto. Uma segunda réplica exige
  mover o limiter para o banco (advisory lock) ou aceitar o dobro do cap. Registrar como gatilho
  no ADR-0008.
- **Batch API** (OpenAI e Anthropic, 50% de desconto, assíncrona) só vale a partir de ~1.000
  planos por semana; o Spring AI não a cobre e exigiria SDK direto. Fora do horizonte do MVP.

---

## 9. Decisões pendentes (de produto)

1. **Aceitar a reordenação de prioridades** da seção 6: reabrir o split, re-revisar a
   decomposição, fechar `validate-interval-workout-standards`, reescopar as duas changes de tool
   calling. Mudança de roadmap, não de código.
2. **Schema semântico (Fase 4) como aposta central.** É a mudança mais invasiva e a de maior
   retorno. A alternativa é continuar crescendo o reparo.
3. **Provider.** Recomendação: permanecer na OpenAI até a Fase 6 e decidir por eval. O upgrade
   para Spring AI 2.0 / Spring Boot 4 é change de plataforma separada.
4. **Quem valida a rubrica do juiz LLM.** Um coach real em 20 casos iniciais; sem isso o juiz mede
   o gosto do modelo, não o do treinador.

---

## 10. Referências

**Spring AI**
- 1.1 GA: https://spring.io/blog/2025/11/12/spring-ai-1-1-GA-released/
- Prompt caching Anthropic: https://spring.io/blog/2025/10/27/spring-ai-anthropic-prompt-caching-blog/
- 1.1 Anthropic Chat (cache, tool choice, sem structured output nativo):
  https://docs.spring.io/spring-ai/reference/1.1/api/chat/anthropic-chat.html
- 1.1 OpenAI Chat (JSON_SCHEMA strict):
  https://docs.spring.io/spring-ai/reference/1.1/api/chat/openai-chat.html
- 2.0 upgrade notes (Boot 4, `ToolCallingAdvisor`, `outputSchema`):
  https://docs.spring.io/spring-ai/reference/upgrade-notes.html
- Tools API (loop manual, `ToolCallingManager`):
  https://docs.spring.io/spring-ai/reference/api/tools.html

**Código analisado**
`IaServiceImpl`, `PlanoResilienceService`, `PlanoTreinoPromptBuilder`, `MultiModelConfig`,
`LlmRoutingProperties`, `LlmConcurrencyLimiter`, `CostTrackingAdvisor`, `LlmUsageLogger`,
`PlanGenerationPersister`, `llm-pricing.yml`, `application.yml`; logs de produção 2026-08-18 e
2026-09-07.

**Produto**
ADR-0006 (governança de prompts e idioma), ADR-0008 (resiliência externa), ADR-0011 (composição
determinística por fase), ADR-0012 (modelo de carga cold-start); changes listadas na seção 6.
