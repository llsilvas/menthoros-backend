## Why

Prompts 100% em português reduzem a assertividade do LLM em análises técnicas de treino (~20% de perda estimada), enquanto prompts 100% em inglês exigem tradução bidirecional — dobrando custo e latência. Adoptar uma estratégia de **code-switching** estruturado captura o melhor dos dois mundos sem overhead de tradução.

## What Changes

- **Reescrever `system-prompt.txt`** em inglês: papel do agente, regras de segurança, validações obrigatórias e princípios de treinamento.
- **Reescrever `plano-treino-otimizado-claude.txt`** em inglês: seções de instrução, rótulos de análise obrigatória e chaves do formato JSON de saída; valores descritivos (observações, justificativas) permanecem em português.
- **Reescrever `plano-treino-prompt.txt`** (template legado) em inglês onde aplicável, mantendo dados do atleta em português.
- **Refatorar formatters PT→EN** nos cabeçalhos/labels gerados programaticamente em `PlanoTreinoPromptBuilder`: seções `##`, rótulos de métricas, marcadores de alerta e hierarquia de decisão.
- **Preservar português** em: dados de entrada do atleta (`nome`, `objetivo`, `observações`), feedback/observações de treino realizado, e valores de `justificativaIa` na resposta JSON.
- **Preservar termos esportivos em inglês** nos valores PT: `threshold`, `TSS`, `ATL`, `CTL`, `TSB`, `VO2max`, `Z1–Z5`, `HR drift`, `long run`, `tempo run`, `fartlek`, `decoupling`.

## Capabilities

### New Capabilities
- `llm-code-switching`: Estratégia de composição de prompts em quatro camadas linguísticas (Instructions EN / Technical Context EN / User Content PT / Output Format híbrido) aplicada a todos os builders e templates de prompt do módulo de geração de plano de treino.

### Modified Capabilities

## Impact

- **Arquivos de template:** `src/main/resources/prompts/system-prompt.txt`, `plano-treino-otimizado-claude.txt`, `plano-treino-prompt.txt`
- **Código Java:** `PlanoTreinoPromptBuilder` e todos os formatters em `services/prompt/` que geram seções de texto injetadas no prompt (`AlertasPromptFormatter`, `MetricasPromptFormatter`, `RecuperacaoPromptFormatter`, `PeriodizacaoPromptFormatter`, `VariabilidadePromptFormatter`, `DisponibilidadePromptFormatter`, `PaceHistoricoFormatter`)
- **Sem impacto em:** DTOs, entidades, repositórios, controllers, lógica de validação pós-LLM em `IaServiceImpl`
- **Testes existentes** nos formatters precisarão atualizar assertions de strings PT→EN nos cabeçalhos/labels
