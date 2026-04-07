## ADDED Requirements

### Requirement: Priorizar atletas que exigem ação do treinador
O sistema SHALL manter uma fila de atenção do treinador, priorizando atletas e situações que exigem revisão operacional.

#### Scenario: Atleta com fadiga alta
- **WHEN** um atleta apresentar sinais relevantes de fadiga, sobrecarga ou baixa prontidão
- **THEN** o sistema SHALL incluí-lo na fila de atenção

#### Scenario: Atleta com aderência ruim
- **WHEN** o atleta acumular ausência de treinos-chave, subexecução frequente ou baixa aderência
- **THEN** o sistema SHALL sinalizar necessidade de intervenção

#### Scenario: Priorização da fila
- **WHEN** múltiplos atletas estiverem sinalizados
- **THEN** o sistema SHALL ordenar a fila por severidade, urgência e impacto potencial

### Requirement: Cada item da fila deve ser acionável
O sistema SHALL exibir motivo principal e ação sugerida para cada item da fila.

#### Scenario: Item de atenção exibido
- **WHEN** um atleta for listado na fila
- **THEN** o item SHALL incluir o motivo principal da priorização
- **THEN** o item SHALL incluir uma recomendação de ação ou revisão
