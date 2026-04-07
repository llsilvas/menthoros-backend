## ADDED Requirements

### Requirement: Recomendações relevantes devem ser explicáveis
O sistema SHALL disponibilizar explicabilidade para recomendações, ajustes e bloqueios relevantes de prescrição.

#### Scenario: Contrato mínimo da explicação
- **WHEN** uma explicação for gerada para uma recomendação relevante
- **THEN** ela SHALL conter `primaryReason`, pelo menos uma evidência e a origem da regra ou skill

#### Scenario: Recomendação de ajuste
- **WHEN** o sistema recomendar redução, manutenção ou progressão de estímulo
- **THEN** ele SHALL informar os dados e sinais principais usados na decisão

#### Scenario: Restrição mandatória
- **WHEN** uma recomendação for bloqueada por regra determinística
- **THEN** o sistema SHALL informar qual regra ou skill acionou o bloqueio

#### Scenario: Nível de confiança da explicação
- **WHEN** a explicação depender de dados incompletos ou fallback
- **THEN** o sistema SHALL indicar confiança reduzida ou limitação de evidência

### Requirement: Explicabilidade deve servir ao treinador
O sistema SHALL expor explicabilidade em formato útil para o treinador, com evidências e motivo operacional.

#### Scenario: Visualização por treinador
- **WHEN** o treinador consultar uma recomendação ou item de atenção
- **THEN** o sistema SHALL mostrar motivo principal, evidências e ação sugerida

### Requirement: Explicabilidade deve ser reutilizável entre capabilities
O sistema SHALL permitir reutilização da estrutura de explicabilidade em prescrição, fila de atenção e debrief pós-treino.

#### Scenario: Reuso em múltiplos fluxos
- **WHEN** uma recomendação ou bloqueio aparecer em diferentes contextos do produto
- **THEN** o sistema SHALL poder reutilizar a mesma estrutura de explicabilidade sem remontar texto inconsistente
