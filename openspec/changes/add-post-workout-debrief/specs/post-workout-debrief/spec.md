## ADDED Requirements

### Requirement: Interpretar treino realizado de forma estruturada
O sistema SHALL interpretar treinos realizados e produzir um debrief estruturado com score de execução, leitura da sessão, riscos observados e recomendação para a sequência do ciclo.

#### Scenario: Contrato mínimo do debrief
- **WHEN** um debrief for gerado para um `TreinoRealizado`
- **THEN** o resultado SHALL conter `executionScore`, `executionStatus`, `summary` e `nextStepRecommendation`
- **THEN** o resultado MAY conter risco principal e payload detalhado

#### Scenario: Treino com planejado associado
- **WHEN** um `TreinoRealizado` estiver associado a um `TreinoPlanejado`
- **THEN** o sistema SHALL comparar planejado versus realizado
- **THEN** o debrief SHALL indicar se a execução ficou abaixo, dentro ou acima do esperado

#### Scenario: Treino com etapas detalhadas
- **WHEN** o treino possuir `EtapaRealizada`
- **THEN** o sistema SHALL priorizar análise por etapa

#### Scenario: Treino sem etapas
- **WHEN** o treino não possuir `EtapaRealizada`
- **THEN** o sistema SHALL executar análise degradada com base nos dados agregados disponíveis

#### Scenario: Treino sem planejado vinculado
- **WHEN** um `TreinoRealizado` não possuir `TreinoPlanejado` associado
- **THEN** o sistema SHALL gerar o debrief com base na execução observada
- **THEN** o sistema SHALL marcar que não houve comparação direta com planejado

#### Scenario: Dados insuficientes para conclusão forte
- **WHEN** não houver dados mínimos para classificar adequadamente a sessão
- **THEN** o sistema SHALL marcar o debrief como `INCONCLUSIVO`
- **THEN** o sistema SHALL evitar recomendar progressão agressiva com base nesse debrief

### Requirement: Debrief deve influenciar a sequência de treino
O sistema SHALL produzir recomendação operacional sobre a sequência do ciclo após o treino realizado.

#### Scenario: Sessão executada acima do custo esperado
- **WHEN** o treino for interpretado como excessivo ou com fadiga elevada
- **THEN** o sistema SHALL recomendar ajuste conservador na sequência

#### Scenario: Sessão bem executada
- **WHEN** o treino for interpretado como bem executado e compatível com a fase atual
- **THEN** o sistema SHALL sinalizar manutenção ou progressão apropriada

### Requirement: Debrief deve ser persistível e reutilizável
O sistema SHALL persistir ou disponibilizar o debrief de modo que ele possa ser reutilizado por revisão semanal, fila de atenção e próxima prescrição.

#### Scenario: Reuso na revisão semanal
- **WHEN** a revisão semanal do atleta for consolidada
- **THEN** o sistema SHALL poder consumir o debrief dos treinos relevantes da semana
