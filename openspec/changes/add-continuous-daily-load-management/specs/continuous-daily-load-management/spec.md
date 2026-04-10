## ADDED Requirements

### Requirement: Manter série contínua de métricas diárias por atleta
O sistema SHALL manter uma série contínua de `MetricasDiarias` por atleta, inclusive em dias sem treino.

#### Scenario: Dia sem treino dentro da série
- **WHEN** um atleta não executa treino em um dia dentro da janela monitorada
- **THEN** o sistema SHALL persistir uma métrica diária para esse dia
- **THEN** essa métrica SHALL conter `tss = 0`, `volumeKm = 0` e marcação explícita de descanso

#### Scenario: Continuidade temporal entre dias
- **WHEN** existirem métricas em dias consecutivos
- **THEN** o fim do dia anterior SHALL servir de base para o início do dia seguinte

### Requirement: Recalcular a janela afetada por eventos de treino
O sistema SHALL recalcular a série diária a partir da data afetada até o presente sempre que a carga histórica mudar.

#### Scenario: Lançamento retroativo de treino
- **WHEN** um treino for lançado com data passada
- **THEN** o sistema SHALL recalcular `MetricasDiarias` da data do treino até `hoje`

#### Scenario: Treino sincronizado de fonte externa
- **WHEN** uma atividade externa for importada ou atualizada em data passada
- **THEN** o sistema SHALL recalcular a janela afetada para o atleta correspondente

#### Scenario: Remoção de treino realizado
- **WHEN** um treino realizado for removido
- **THEN** o sistema SHALL recalcular a série diária a partir da data removida

### Requirement: Tratar descanso como sinal fisiológico explícito
O sistema SHALL usar os dias sem treino para refletir recuperação e mudança de prontidão.

#### Scenario: Decaimento em dia de descanso
- **WHEN** um dia tiver `tss = 0`
- **THEN** `ATL` e `CTL` SHALL decair conforme a lógica fisiológica vigente

#### Scenario: Descanso melhora prontidão do dia seguinte
- **WHEN** houver um dia de descanso válido entre dois dias da série
- **THEN** a prontidão do dia seguinte SHALL refletir o efeito de recuperação desse descanso

### Requirement: Expor prontidão operacional diária
O sistema SHALL expor uma leitura operacional diária de prontidão derivada de múltiplos sinais fisiológicos e de sequência.

#### Scenario: Score diário disponível
- **WHEN** o status diário do atleta for consultado
- **THEN** o resultado SHALL conter `readinessScore`, `readinessStatus` e `primaryReason`

#### Scenario: Prontidão baseada em múltiplos sinais
- **WHEN** o readiness score for calculado
- **THEN** o sistema SHALL considerar ao menos `tsbInicioDia`, relação entre `ATL` e `CTL`, `Ramp Rate`, dias consecutivos e descanso recente

#### Scenario: Base canônica de prontidão
- **WHEN** existir separação entre início e fim do dia
- **THEN** o readiness score SHALL usar `tsbInicioDia` como sinal principal de prontidão

### Requirement: Tornar a prontidão reutilizável por outras capabilities
O sistema SHALL permitir que a leitura diária de prontidão seja consumida por prescrição, revisão e priorização operacional.

#### Scenario: Reuso na geração de plano
- **WHEN** uma decisão de intensidade for tomada para o dia ou para a semana
- **THEN** o fluxo de prescrição SHALL poder consumir o status diário de prontidão

#### Scenario: Reuso na fila do treinador
- **WHEN** a fila de atenção do treinador for gerada
- **THEN** atletas com prontidão baixa ou degradada SHALL poder influenciar a priorização

#### Scenario: Reuso na revisão semanal
- **WHEN** a revisão semanal do atleta for consolidada
- **THEN** a série contínua de carga e recuperação SHALL poder ser usada como evidência da leitura da semana
