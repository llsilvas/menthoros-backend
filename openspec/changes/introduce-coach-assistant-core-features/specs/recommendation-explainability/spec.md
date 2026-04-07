## ADDED Requirements

### Requirement: Recomendações relevantes devem ser explicáveis
O sistema SHALL disponibilizar explicabilidade para recomendações, ajustes e bloqueios relevantes de prescrição.

#### Scenario: Recomendação de ajuste
- **WHEN** o sistema recomendar redução, manutenção ou progressão de estímulo
- **THEN** ele SHALL informar os dados e sinais principais usados na decisão

#### Scenario: Restrição mandatória
- **WHEN** uma recomendação for bloqueada por regra determinística
- **THEN** o sistema SHALL informar qual regra ou skill acionou o bloqueio

### Requirement: Explicabilidade deve servir ao treinador
O sistema SHALL expor explicabilidade em formato útil para o treinador, com evidências e motivo operacional.

#### Scenario: Visualização por treinador
- **WHEN** o treinador consultar uma recomendação ou item de atenção
- **THEN** o sistema SHALL mostrar motivo principal, evidências e ação sugerida
