## Why

O Menthoros gera planos de treino mas atualmente não fornece análise estruturada do que aconteceu após a execução — o coach recebe os dados do Strava mas não tem interpretação automática de causa-raiz (fadiga acumulada, erro de ritmo, estresse ambiental). A skill `workout-analyzer` fecha esse loop: transforma `TreinoRealizado` + `TreinoPlanejado` em diagnóstico técnico acionável, consumindo os quatro modelos LLM com roteamento por complexidade de tarefa.

## What Changes

- Adiciona `MultiModelConfig` com quatro `ChatClient` beans: `miniChatClient` (GPT-4o-mini), `haikuChatClient` (Claude Haiku), `sonnetChatClient` (Claude Sonnet), `gptChatClient` (GPT-4o)
- Adiciona `ModelRouter` que seleciona o `ChatClient` adequado com base em `TaskComplexity` (SIMPLE / STANDARD / COMPLEX / EXPERT)
- Adiciona `WorkoutAnalysisTranslator` que converte análises produzidas em inglês para português antes de persistir
- Adiciona `WorkoutAnalysisListener` como Spring Event listener que dispara a análise assincronamente após `TreinoRegistradoEvent`
- Adiciona entidade `AnaliseWorkout` e repositório `AiWorkoutAnalysisRepository` para persistir o resultado da análise
- Adiciona endpoint `GET /api/v1/analise/treino/{treinoRealizadoId}` para recuperar a análise pelo frontend
- Cria arquivo `src/main/resources/skills/analise/workout-analyzer/SKILL.md` com a especificação da skill para consumo pelo `ChatClient`

## Capabilities

### New Capabilities

- `multi-model-routing`: Infraestrutura de roteamento inteligente entre quatro LLMs; configura beans tipados por modelo e `ModelRouter` que resolve `ChatClient` por `TaskComplexity`
- `post-workout-analysis`: Análise pós-treino assíncrona disparada por evento, comparando `TreinoPlanejado` vs `TreinoRealizado` e produzindo diagnóstico estruturado (score, causa-raiz, recomendação, tags) em português via `workout-analyzer` skill

### Modified Capabilities

_(nenhuma — não altera specs existentes)_

## Impact

- **Novas dependências Maven:** `spring-ai-anthropic-spring-boot-starter`, `spring-ai-openai-spring-boot-starter` (se não presentes — verificar `pom.xml`)
- **Novos env vars obrigatórios:** `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`
- **Nova tabela de banco:** `tb_analise_workout` via Flyway migration
- **Pacotes afetados:** `config/external/`, `services/` (novo listener + translator), `entity/`, `repository/`, `controller/`, `dto/output/`
- **Sem breaking changes** em controllers, entities ou repositories existentes
- **Impacto em testes:** Novos testes de integração para `WorkoutAnalysisListener`; testes existentes não são afetados
