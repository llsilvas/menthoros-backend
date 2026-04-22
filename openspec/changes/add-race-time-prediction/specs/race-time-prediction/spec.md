## ADDED Requirements

### Requirement: Predição de tempo por atleta e prova
O sistema SHALL calcular e expor predição de tempo para cada prova-alvo do atleta, usando a melhor referência disponível.

#### Scenario: Referência é prova recente da mesma distância
- **WHEN** existir prova realizada nos últimos 6 meses com a mesma distância da prova-alvo
- **THEN** a predição SHALL usar o tempo da prova recente diretamente (método `RIEGEL` com `D2=D1`, equivalente a reprodução)

#### Scenario: Referência é prova recente de distância próxima
- **WHEN** não houver prova exata mas existir prova de distância próxima (≥ 5 km e ≤ 30 km de diferença absoluta)
- **THEN** a predição SHALL aplicar fórmula de Riegel com expoente 1.06

#### Scenario: Referência é teste de campo
- **WHEN** não houver provas mas existir teste de campo recente com pace limiar estimado
- **THEN** a predição SHALL usar método `VDOT` convertendo pace limiar para VO2max equivalente e estimando tempo pela tabela de Daniels

#### Scenario: Sem referências diretas
- **WHEN** não houver provas nem testes recentes
- **THEN** a predição SHALL usar método `ESTIMATIVA` baseado em CTL atual e pace limiar do atleta, com `confiabilidade` reduzida

---

### Requirement: Confiabilidade da predição
O sistema SHALL expor um valor de confiabilidade entre 0 e 1 junto de cada predição.

#### Scenario: Confiabilidade alta
- **WHEN** a referência for uma prova realizada nos últimos 30 dias da mesma distância
- **THEN** `confiabilidade` SHALL ser ≥ 0.85

#### Scenario: Confiabilidade média
- **WHEN** a referência for teste de campo ou prova de distância próxima dos últimos 90 dias
- **THEN** `confiabilidade` SHALL estar entre 0.50 e 0.85

#### Scenario: Confiabilidade baixa
- **WHEN** a referência for estimativa por CTL ou referência com mais de 90 dias
- **THEN** `confiabilidade` SHALL ser < 0.50 e o sistema SHALL recomendar teste de campo ou prova de calibração

---

### Requirement: Histórico e snapshots
O sistema SHALL persistir snapshots de predição ao longo do tempo para permitir análise de evolução.

#### Scenario: Snapshot semanal automatizado
- **WHEN** o job agendado rodar
- **THEN** o sistema SHALL persistir uma nova linha em `tb_predicao_prova` por atleta ativo com prova-alvo

#### Scenario: Consulta de histórico
- **WHEN** o endpoint de histórico for chamado
- **THEN** o sistema SHALL retornar snapshots ordenados por `calculado_em` DESC

---

### Requirement: Gap exposto ao contexto de prescrição
O sistema SHALL expor o gap entre predição e objetivo no contexto enviado ao LLM para geração de plano.

#### Scenario: Atleta acima do objetivo
- **WHEN** `predicaoTempoSeg > objetivoTempoSeg`
- **THEN** o contexto SHALL conter `gapSeg` positivo indicando distância do objetivo

#### Scenario: Atleta à frente do objetivo
- **WHEN** `predicaoTempoSeg < objetivoTempoSeg`
- **THEN** o contexto SHALL conter `gapSeg` negativo indicando folga sobre o objetivo
