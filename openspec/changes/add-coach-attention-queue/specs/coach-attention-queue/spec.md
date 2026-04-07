## ADDED Requirements

### Requirement: Priorizar atletas que exigem ação do treinador
O sistema SHALL manter uma fila de atenção do treinador, priorizando atletas e situações que exigem revisão operacional.

#### Scenario: Contrato mínimo do item
- **WHEN** um item da fila for gerado
- **THEN** ele SHALL conter `atletaId`, `priorityScore`, `severity`, `primaryReason` e `suggestedAction`

#### Scenario: Atleta com fadiga alta
- **WHEN** um atleta apresentar sinais relevantes de fadiga, sobrecarga ou baixa prontidão
- **THEN** o sistema SHALL incluí-lo na fila de atenção

#### Scenario: Atleta com aderência ruim
- **WHEN** o atleta acumular ausência de treinos-chave, subexecução frequente ou baixa aderência
- **THEN** o sistema SHALL sinalizar necessidade de intervenção

#### Scenario: Priorização da fila
- **WHEN** múltiplos atletas estiverem sinalizados
- **THEN** o sistema SHALL ordenar a fila por severidade, urgência e impacto potencial

#### Scenario: Deduplicação por motivo principal
- **WHEN** um atleta possuir múltiplos sinais do mesmo motivo agregado
- **THEN** o sistema SHALL consolidar esses sinais em um único item principal

### Requirement: Cada item da fila deve ser acionável
O sistema SHALL exibir motivo principal e ação sugerida para cada item da fila.

#### Scenario: Item de atenção exibido
- **WHEN** um atleta for listado na fila
- **THEN** o item SHALL incluir o motivo principal da priorização
- **THEN** o item SHALL incluir uma recomendação de ação ou revisão

#### Scenario: Evidências do item
- **WHEN** um item da fila for consultado em detalhe
- **THEN** o sistema SHALL disponibilizar evidências resumidas que justificam a priorização
