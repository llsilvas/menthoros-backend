## ADDED Requirements

### Requirement: Orquestrar skills de domínio aplicáveis
O sistema SHALL executar skills de domínio aplicáveis para um atleta e/ou sessão a partir de um `SkillContext`, consolidando os resultados em um fluxo padronizado e reaproveitável.

#### Scenario: Execução de skills na geração de plano
- **WHEN** um plano semanal é solicitado para um atleta
- **THEN** o sistema SHALL montar um `SkillContext` com histórico, metadados e provas
- **THEN** o sistema SHALL executar as skills aplicáveis antes da chamada ao LLM

#### Scenario: Execução de skills no pós-treino
- **WHEN** um treino realizado é persistido ou recalculado
- **THEN** o sistema SHALL executar as skills compatíveis com aquele tipo de sessão

#### Scenario: Skill não aplicável
- **WHEN** uma skill não possuir dados mínimos para análise ou não suportar o contexto
- **THEN** o sistema SHALL marcá-la como `not_applicable` ou equivalente
- **THEN** o fluxo global NÃO deve falhar por isso

### Requirement: Produzir snapshot estruturado de análise do atleta
O sistema SHALL consolidar os resultados das skills em um `AthleteAnalysisSnapshot` estruturado, reutilizável pela geração de plano, revisão semanal e explicações ao usuário.

#### Scenario: Snapshot antes do prompt
- **WHEN** `PlanoTreinoPromptBuilder` construir o contexto de uma nova semana
- **THEN** ele SHALL receber o `AthleteAnalysisSnapshot` já consolidado
- **THEN** o snapshot SHALL conter constraints e sinais analíticos centrais do atleta

#### Scenario: Snapshot com múltiplos sinais
- **WHEN** skills de recuperação, progressão e capacidade intervalada forem executadas
- **THEN** o snapshot SHALL expor seus resumos em estrutura própria
- **THEN** os resultados brutos SHALL continuar acessíveis para auditoria

### Requirement: Persistir resultados de skill para auditoria
O sistema SHALL persistir execuções e resultados de skills relevantes em armazenamento próprio, incluindo versão da skill, severidade, payload e evidências.

#### Scenario: Persistência de execução pós-treino
- **WHEN** uma skill de análise de treino for executada com sucesso
- **THEN** o sistema SHALL persistir uma execução associada ao `TreinoRealizado`

#### Scenario: Persistência de execução na geração de plano
- **WHEN** a orquestração de skills ocorrer durante a geração do plano
- **THEN** o sistema SHALL permitir associação da execução ao atleta e opcionalmente ao `PlanoSemanal`

#### Scenario: Versionamento da regra
- **WHEN** uma skill retornar um resultado
- **THEN** o registro persistido SHALL incluir `skillKey` e `skillVersion`

### Requirement: O LLM deve consumir skills como contexto, não como substituto
O sistema SHALL garantir que o resultado das skills determinísticas seja tratado como fonte de verdade no fluxo de geração de plano e explicação, sem permitir que o LLM sobrescreva constraints críticas.

#### Scenario: Constraint determinística no snapshot
- **WHEN** uma skill determinar restrição de intensidade ou limitação fisiológica
- **THEN** o snapshot SHALL carregar essa restrição de forma explícita
- **THEN** o fluxo posterior SHALL tratar a restrição como mandatória
