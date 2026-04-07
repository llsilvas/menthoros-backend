## ADDED Requirements

### Requirement: Interpretar treino realizado de forma estruturada
O sistema SHALL interpretar treinos realizados e produzir um debrief estruturado com score de execução, leitura da sessão, riscos observados e recomendação para a sequência do ciclo.

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

### Requirement: Debrief deve influenciar a sequência de treino
O sistema SHALL produzir recomendação operacional sobre a sequência do ciclo após o treino realizado.

#### Scenario: Sessão executada acima do custo esperado
- **WHEN** o treino for interpretado como excessivo ou com fadiga elevada
- **THEN** o sistema SHALL recomendar ajuste conservador na sequência

#### Scenario: Sessão bem executada
- **WHEN** o treino for interpretado como bem executado e compatível com a fase atual
- **THEN** o sistema SHALL sinalizar manutenção ou progressão apropriada
