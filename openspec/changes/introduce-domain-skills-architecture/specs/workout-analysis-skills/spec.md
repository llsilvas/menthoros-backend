## ADDED Requirements

### Requirement: Analisar treinos realizados com prioridade para dados por etapa
O sistema SHALL analisar treinos realizados usando `EtapaRealizada` como fonte prioritária quando disponível, com fallback para métricas agregadas do `TreinoRealizado`.

#### Scenario: Intervalado com etapas detalhadas
- **WHEN** um treino `INTERVALADO` possuir múltiplas `EtapaRealizada`
- **THEN** a análise SHALL usar os blocos de esforço e recuperação para extrair métricas de execução

#### Scenario: Treino sem etapas detalhadas
- **WHEN** o treino não possuir `EtapaRealizada`
- **THEN** o sistema SHALL executar análise degradada baseada nas métricas agregadas do treino

### Requirement: Analisar treinos intervalados com métricas estruturadas
O sistema SHALL disponibilizar uma skill de análise de intervalados capaz de interpretar a execução da sessão de forma estruturada.

#### Scenario: Decaimento de performance
- **WHEN** existirem pelo menos duas repetições comparáveis em um treino intervalado
- **THEN** o sistema SHALL calcular decaimento de pace e/ou velocidade entre as repetições
- **THEN** o resultado SHALL classificar a execução em faixas interpretáveis

#### Scenario: Consistência entre repetições
- **WHEN** um treino intervalado possuir repetições suficientes
- **THEN** o sistema SHALL medir consistência do ritmo entre os blocos principais

#### Scenario: Recuperação entre repetições
- **WHEN** houver dados de frequência cardíaca nas etapas principais e de recuperação
- **THEN** o sistema SHALL calcular a recuperação cardíaca entre blocos

### Requirement: Analisar longões e contínuos extensivos
O sistema SHALL disponibilizar uma skill para análise de longões e sessões contínuas extensivas, produzindo sinais de eficiência e custo fisiológico.

#### Scenario: Drift cardíaco
- **WHEN** um longo possuir dados suficientes de pace e FC ao longo da sessão
- **THEN** o sistema SHALL calcular desacoplamento ou drift cardíaco

#### Scenario: Distribuição de ritmo
- **WHEN** um longo ou contínuo tiver blocos comparáveis entre início e fim
- **THEN** o sistema SHALL detectar padrão de `negative split`, `even pace` ou `positive split`

### Requirement: Resultados das análises devem alimentar evolução do atleta
O sistema SHALL produzir resultados estruturados de análise de treino que possam ser reutilizados em avaliação de evolução, revisão semanal e prescrição futura.

#### Scenario: Reaproveitamento em revisão semanal
- **WHEN** a revisão semanal ou geração do próximo plano precisar considerar a execução recente
- **THEN** o sistema SHALL poder consumir os resultados salvos das skills de treino
