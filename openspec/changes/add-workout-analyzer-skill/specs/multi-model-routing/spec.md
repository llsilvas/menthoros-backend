## ADDED Requirements

### Requirement: Múltiplos ChatClient beans por modelo

O sistema SHALL configurar quatro `ChatClient` beans Spring nomeados com `@Qualifier`:
- `@Qualifier("gpt4oMiniClient")` → OpenAI `gpt-4o-mini` (tarefas simples, baixo custo)
- `@Qualifier("claudeHaikuClient")` → Anthropic `claude-haiku-4-5` (tarefas padrão, velocidade)
- `@Qualifier("claudeSonnetClient")` → Anthropic `claude-sonnet-4-6` (tarefas complexas, qualidade)
- `@Qualifier("gpt4oClient")` → OpenAI `gpt-4o` (tarefas especialistas, máxima capacidade)

O bean `ChatClient` existente (injetado em `IaServiceImpl`) SHALL permanecer inalterado com `@Primary`.
Nenhum dos novos beans SHALL ser anotado com `@Primary`.

#### Scenario: Bean gpt4oMiniClient disponível
- **WHEN** o contexto Spring é inicializado com `OPENAI_API_KEY` configurado
- **THEN** o bean deve estar disponível para injeção com `@Qualifier("gpt4oMiniClient")`

#### Scenario: Bean claudeSonnetClient disponível
- **WHEN** o contexto Spring é inicializado com `ANTHROPIC_API_KEY` configurado
- **THEN** o bean deve estar disponível para injeção com `@Qualifier("claudeSonnetClient")`

#### Scenario: Bean primário inalterado
- **WHEN** `IaServiceImpl` injeta `ChatClient` sem `@Qualifier`
- **THEN** o bean `@Primary` existente (OpenAI GPT-4o) deve ser injetado, sem alteração de comportamento

---

### Requirement: ModelRouter resolve ChatClient por TaskComplexity

O sistema SHALL implementar `ModelRouter` como `@Component` Spring que aceita um `TaskComplexity` enum e retorna o `ChatClient` correspondente.

O enum `TaskComplexity` SHALL ter os valores: `SIMPLE`, `STANDARD`, `COMPLEX`, `EXPERT`.

O mapeamento SHALL ser:
- `SIMPLE` → `@Qualifier("gpt4oMiniClient")`
- `STANDARD` → `@Qualifier("claudeHaikuClient")`
- `COMPLEX` → `@Qualifier("claudeSonnetClient")`
- `EXPERT` → `@Qualifier("gpt4oClient")`

#### Scenario: Roteamento para tarefa SIMPLE
- **WHEN** `ModelRouter.route(TaskComplexity.SIMPLE)` é chamado
- **THEN** retorna o `ChatClient` configurado com `gpt-4o-mini`

#### Scenario: Roteamento para tarefa COMPLEX
- **WHEN** `ModelRouter.route(TaskComplexity.COMPLEX)` é chamado
- **THEN** retorna o `ChatClient` configurado com `claude-sonnet-4-6`

#### Scenario: Roteamento para TaskComplexity null
- **WHEN** `ModelRouter.route(null)` é chamado
- **THEN** lança `IllegalArgumentException` com mensagem descritiva

---

### Requirement: Configuração de API keys via variáveis de ambiente

O sistema SHALL carregar as API keys de variáveis de ambiente:
- `ANTHROPIC_API_KEY` para beans Anthropic (haiku, sonnet)
- `OPENAI_API_KEY` para beans OpenAI (mini, gpt) — já em uso pelo bean primário

Se `ANTHROPIC_API_KEY` estiver ausente, os beans Anthropic SHALL falhar na inicialização com erro claro.

#### Scenario: Startup sem ANTHROPIC_API_KEY
- **WHEN** a aplicação inicia sem `ANTHROPIC_API_KEY` definido
- **THEN** o contexto Spring falha com `BeanCreationException` indicando a variável ausente

#### Scenario: Startup com todas as chaves presentes
- **WHEN** a aplicação inicia com `ANTHROPIC_API_KEY` e `OPENAI_API_KEY` configurados
- **THEN** todos os quatro beans `ChatClient` são criados sem erros
