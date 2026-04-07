## Why

O Menthoros já possui componentes determinísticos valiosos para análise e prescrição, como `IntervaladoElegibilidadeService`, `MetricasAlertaService`, `ZonaTreinoService` e a montagem rica de contexto em `PlanoTreinoPromptBuilder`. Porém, esse conhecimento ainda está distribuído entre helpers, prompt builders, texto de prompt e documentação, sem um contrato formal único que permita reaproveitamento, auditoria e evolução controlada.

Ao mesmo tempo, o sistema já começou a se preparar para análises granulares com `EtapaRealizada` e para ingestão rica de dados com o roadmap de integração Strava. Sem uma camada explícita de skills de domínio, o Menthoros corre o risco de:

- depender em excesso do LLM para interpretação esportiva
- perder auditabilidade sobre decisões críticas de prescrição
- não reaproveitar a mesma inteligência entre pós-treino, revisão semanal e geração de plano
- desperdiçar os dados granulares que virão de `EtapaRealizada` e de integrações externas

Esta mudança formaliza uma arquitetura de **domain skills determinísticas**, com uso opcional de **agent skills/tools** apenas para consulta e explicação pelo LLM.

## What Changes

- **Nova camada `skills/` no backend**: contratos base, registry, orchestrator e DTOs de snapshot analítico
- **Novo snapshot estruturado de análise**: consolidar recuperação, progressão, constraints e resultados das skills antes da chamada ao LLM
- **Formalização de skills existentes**: transformar a lógica hoje espalhada em serviços como `IntervaladoElegibilidadeService` e `MetricasAlertaService` em skills versionáveis
- **Novas skills de análise de treino**: análise de intervalados e longões baseada em `TreinoRealizado` e `EtapaRealizada`
- **Nova skill de guarda de prescrição**: validar planos gerados pela IA antes de persistir
- **Persistência de execuções de skill**: suporte a auditoria, comparabilidade e explainability
- **Integração com fluxo de IA**: `PlanoTreinoPromptBuilder` e `IaServiceImpl` passam a consumir `AthleteAnalysisSnapshot`
- **Compatibilidade com Spring AI tools**: skills poderão ser expostas ao LLM como tools, mas sem delegar ao modelo a decisão determinística

## Capabilities

### New Capabilities

- `domain-skill-orchestration`: executar skills de domínio aplicáveis, consolidar resultados e produzir um snapshot analítico reutilizável
- `workout-analysis-skills`: analisar treinos realizados com granularidade por tipo de sessão e por etapa, gerando sinais estruturados de execução, evolução e fadiga
- `training-prescription-guard`: validar qualquer plano semanal proposto pela IA contra limites fisiológicos, históricos e de periodização antes da persistência

### Modified Capabilities

- A geração de plano semanal existente passa a consumir snapshot estruturado em vez de depender apenas de contexto textual montado em prompt

## Impact

**Camadas de código:**
- novo pacote `src/main/java/com/menthoros/skills/`
- adaptação de `PlanoTreinoPromptBuilder`, `IaServiceImpl`, `TssCalculatorService`
- refatoração gradual de `IntervaladoElegibilidadeService` e `MetricasAlertaService`

**Banco de dados:**
- nova tabela para persistência de execuções/resultados de skills

**Fluxos de negócio:**
- pós-treino passa a gerar análise estruturada
- geração de plano passa a validar resultado por skill determinística
- revisão semanal poderá usar os mesmos resultados salvos

**Integrações futuras:**
- a mudança prepara o sistema para extrair valor real de `EtapaRealizada` e do roadmap Strava
