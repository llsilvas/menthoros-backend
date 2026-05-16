## Context

O backend já possui uma camada AI funcional: `IaServiceImpl` usa `ChatClientConfig` para gerar planos de treino via Spring AI com OpenAI. O problema é que existe apenas um único `ChatClient` (GPT-4o) injetado como bean default, o que impede roteamento por complexidade de tarefa.

A análise pós-treino (`workout-analyzer`) exige raciocínio de dois estágios: (1) cálculo objetivo de deltas (simples, poderia ser HAIKU), (2) interpretação de causa-raiz com contexto fisiológico (requer SONNET ou GPT-4o). Usar o modelo errado em cada estágio desperdiça tokens ou produz análise insuficiente.

Estado atual relevante:
- `ChatClientConfig.java` — configura um único `ChatClient` injetado com `@Primary`
- `PromptTemplateLoader.java` — carrega templates de `src/main/resources/` em runtime
- `TreinoRegistradoEvent` — não existe ainda (precisa ser criado)
- `TreinoRealizado` / `TreinoPlanejado` — entidades existentes com dados suficientes

## Goals / Non-Goals

**Goals:**
- Introduzir 4 `ChatClient` beans nomeados (mini, haiku, sonnet, gpt) sem quebrar o bean `@Primary` existente
- Implementar `ModelRouter` com enum `TaskComplexity` que resolve o `ChatClient` correto
- Implementar análise pós-treino assíncrona via Spring Event com resultado persistido em BD
- Traduzir resultado da skill (produzido em EN) para PT antes de persistir
- Expor endpoint `GET /api/v1/analise/treino/{treinoRealizadoId}` (multi-tenant, somente leitura)

**Non-Goals:**
- UI frontend (Onda 2)
- Integração com Strava diretamente (já existe `TreinoRealizado`)
- Análise em tempo real (síncrona) — sempre assíncrona
- Migrar `IaServiceImpl` para usar `ModelRouter` agora

## Decisions

### 1. Múltiplos `ChatClient` beans com `@Qualifier`

**Decisão:** Criar beans `@Bean @Qualifier("mini|haiku|sonnet|gpt")` em `MultiModelConfig.java` separado de `ChatClientConfig.java` existente.

**Rationale:** Não alterar `ChatClientConfig.java` evita risco de regressão no `IaService` existente (plano de treino). O bean `@Primary` existente (OpenAI) permanece sem mudança — apenas os novos beans nomeados são adicionados.

**Alternativa rejeitada:** Usar `ChatClientFactory` dinâmica — desnecessária quando os modelos são fixos em 4.

---

### 2. `TaskComplexity` enum como contrato de roteamento

**Decisão:** `ModelRouter.route(TaskComplexity)` retorna o `ChatClient` adequado. O enum define: `SIMPLE → mini`, `STANDARD → haiku`, `COMPLEX → sonnet`, `EXPERT → gpt`.

**Rationale:** Centraliza a política de roteamento em um ponto único. Chamar o router via enum é type-safe e testável sem precisar de strings mágicas.

**Alternativa rejeitada:** Passar o nome do modelo como string — propenso a erros de typo e difícil de testar.

---

### 3. Spring Events para desacoplar análise do registro

**Decisão:** `TreinoService.save()` publica `TreinoRegistradoEvent`. `WorkoutAnalysisListener` consome com `@TransactionalEventListener(phase = AFTER_COMMIT)` em thread separada (`@Async`).

**Rationale:** O registro de treino não deve falhar se a análise AI falhar. Usando `AFTER_COMMIT`, garantimos que o treino está persistido antes de iniciar a análise. Async garante que o HTTP response retorna imediatamente.

**Alternativa rejeitada:** Análise síncrona no service — bloqueia o request por vários segundos e acopla o loop de registro ao AI.

---

### 4. SKILL.md carregado via `PromptTemplateLoader` existente

**Decisão:** Armazenar `src/main/resources/skills/analise/workout-analyzer/SKILL.md` e carregar via `PromptTemplateLoader.load("skills/analise/workout-analyzer/SKILL.md")`.

**Rationale:** Reutiliza infraestrutura existente. Mantém a skill como arquivo versionado no repositório. Separação clara entre configuração (Java) e conhecimento de domínio (Markdown).

---

### 5. Tradução em camada separada (`WorkoutAnalysisTranslator`)

**Decisão:** A skill produz output em EN (mais preciso para terminologia técnica). `WorkoutAnalysisTranslator` usa `haikuChatClient` para traduzir apenas os campos textuais (`summary`, `technical_interpretation`, `recommendation`, `rationale`) para PT.

**Rationale:** Modelos produzem análise fisiológica mais consistente em inglês. Tradução com Haiku é barata e suficiente para texto explicativo. Separar tradução de análise permite evoluir cada parte independentemente.

---

### 6. Persistência em tabela dedicada `tb_analise_workout`

**Decisão:** Nova entidade `AnaliseWorkout` com FK para `tb_treino_realizado`. Não adicionar campos em `TreinoRealizado`.

**Rationale:** Evita poluição da entidade principal. Permite múltiplas análises por treino (re-análise futura). A análise pode ser `null` (ainda processando) sem impactar o treino.

## Risks / Trade-offs

| Risco | Mitigação |
|-------|----------|
| Análise falha silenciosamente (AI indisponível) | Logar erro com `treinoRealizadoId`; estado da análise permanece `PENDING`; endpoint retorna 204 se não há análise ainda |
| Custo de tokens elevado em volume alto | `SIMPLE` e `STANDARD` tasks usam modelos baratos; análise só dispara quando `rpe` está disponível no `TreinoRealizado` |
| Tradução distorce termos técnicos | Campos enum (`primary_cause`, `tags`) não são traduzidos; apenas texto livre |
| `@TransactionalEventListener` não dispara em testes unitários | Testes de integração para o listener com `@SpringBootTest` |
| Múltiplos beans `ChatClient` confundem injeção de `IaService` | `MultiModelConfig` usa apenas `@Qualifier` (nunca `@Primary`); bean primário existente inalterado |

## Migration Plan

1. Adicionar dependências Maven (se ausentes): `spring-ai-anthropic-spring-boot-starter`
2. Adicionar env vars no Railway: `ANTHROPIC_API_KEY`
3. Criar Flyway migration `V{N}__add_analise_workout.sql`
4. Deploy sem feature flag — o evento só dispara quando `TreinoRealizado` for salvo; análise é assíncrona e não impacta fluxo existente

**Rollback:** Remover `WorkoutAnalysisListener` do contexto Spring (comentar `@Component`) desativa toda a análise sem risco para o restante do sistema.

## Open Questions

- Qual model exato para EXPERT? `gpt-4o` ou `claude-opus`? (bmad-config.yaml diz GPT-4o — seguir por ora)
- Versão do modelo Sonnet: `claude-sonnet-4-20250514` ou `claude-sonnet-4-6`? (verificar na impl de `IaServiceImpl`)
- O `rpe` está sempre disponível no `TreinoRealizado` ou é opcional? (verificar entidade — gate antes de disparar evento)
