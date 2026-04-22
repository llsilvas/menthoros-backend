## ADDED Requirements

### Requirement: Fator de elevação contabiliza subida e descida
O sistema SHALL contabilizar tanto `elevacaoGanhoMetros` quanto `elevacaoPerdaMetros` no fator de elevação aplicado ao TSS.

#### Scenario: Treino sem elevação
- **WHEN** o treino não possuir ganho nem perda de elevação
- **THEN** o fator de elevação SHALL retornar 1.0 (comportamento atual preservado)

#### Scenario: Treino somente com subida
- **WHEN** o treino tiver `elevacaoGanhoMetros > 0` e `elevacaoPerdaMetros` nulo ou zero
- **THEN** o fator SHALL ser calculado exclusivamente pelo gradiente de subida, mantendo o valor equivalente ao cálculo atual

#### Scenario: Treino com descida líquida
- **WHEN** o treino tiver `elevacaoPerdaMetros > 0` significativa em relação à distância
- **THEN** o fator SHALL somar um componente de descida com peso ~60% do componente equivalente de subida

#### Scenario: Limite superior do fator
- **WHEN** a soma dos componentes de subida e descida ultrapassar 2.0
- **THEN** o fator SHALL ser limitado a 2.0

---

### Requirement: Ramp Rate com fallback para histórico parcial
O sistema SHALL calcular Ramp Rate mesmo quando não houver registro exato de 7 dias antes da data de referência.

#### Scenario: Registro exato de 7 dias disponível
- **WHEN** existir `MetricasDiarias` para `data - 7`
- **THEN** o Ramp Rate SHALL ser `ctlAtual - ctl(data - 7)` (comportamento atual preservado)

#### Scenario: Registro mais próximo dentro da janela 5–9 dias
- **WHEN** não houver registro exato de 7 dias mas existir algum em `[data-9, data-5]`
- **THEN** o Ramp Rate SHALL ser estimado por interpolação linear: `(ctlAtual - ctl(ref)) / diasEntre * 7`

#### Scenario: Atleta com histórico inicial menor que 5 dias
- **WHEN** não houver registro em `[data-9, data-5]` mas existir primeiro registro em `[data-14, data-1]`
- **THEN** o Ramp Rate SHALL ser estimado a partir do primeiro registro: `(ctlAtual - ctl(primeiro)) / diasDesdeInicio * 7`

#### Scenario: Sem referência disponível
- **WHEN** não houver nenhum `MetricasDiarias` anterior à data
- **THEN** o Ramp Rate SHALL retornar 0.0

---

### Requirement: TSS calculado por etapa quando disponível
O sistema SHALL calcular TSS somando contribuições por `EtapaRealizada` quando o treino possuir etapas populadas, preservando o cálculo pela média geral como fallback.

#### Scenario: Treino com etapas populadas
- **WHEN** `TreinoRealizado.etapasRealizadas` não for nulo nem vazio
- **THEN** o TSS SHALL ser calculado somando `duracaoHoras * IF² * 100` de cada etapa

#### Scenario: IF por etapa segue prioridade FC > Pace > RPE
- **WHEN** uma etapa individual for avaliada
- **THEN** o IF SHALL ser calculado na seguinte ordem: FC média (se disponível e com FC máxima/repouso do atleta), Pace médio (se disponível e com pace limiar do atleta), RPE (se disponível)

#### Scenario: Treino sem etapas
- **WHEN** `TreinoRealizado.etapasRealizadas` for nulo ou vazio
- **THEN** o TSS SHALL ser calculado pelo método atual de média geral (fallback)

#### Scenario: Desigualdade de Jensen preservada
- **WHEN** um treino possuir etapas com variação de intensidade entre zonas
- **THEN** o TSS calculado por etapas SHALL ser maior ou igual ao TSS calculado pela média geral do mesmo treino

---

### Requirement: Classificação de TSB ajustada por nível de experiência
O sistema SHALL oferecer classificação de `FaixaTsb` que considere o nível de experiência do atleta, preservando a API atual como padrão retrocompatível.

#### Scenario: Classificação padrão (sem nível informado)
- **WHEN** `FaixaTsb.classificar(tsb)` for chamado sem `NivelExperiencia`
- **THEN** o resultado SHALL ser idêntico ao comportamento anterior (equivalente ao nível AVANCADO)

#### Scenario: Atleta iniciante tem thresholds mais restritivos
- **WHEN** `FaixaTsb.classificar(tsb, INICIANTE)` for chamado
- **THEN** os thresholds SHALL ser escalados por fator 1.3, resultando em faixas mais severas para o mesmo TSB

#### Scenario: Atleta elite tem thresholds mais tolerantes
- **WHEN** `FaixaTsb.classificar(tsb, ELITE)` for chamado
- **THEN** os thresholds SHALL ser escalados por fator 0.75, resultando em faixas menos severas para o mesmo TSB

#### Scenario: Comparação entre níveis no mesmo TSB
- **WHEN** um iniciante e um atleta elite tiverem TSB = -25
- **THEN** o iniciante SHALL cair em faixa mais severa que o elite (p.ex. `FADIGA_ALTA` vs `ACUMULANDO_FADIGA`)

#### Scenario: Observabilidade durante rollout
- **WHEN** a classificação ajustada por nível for emitida
- **THEN** o sistema SHALL registrar log comparando a faixa ajustada com a faixa sem ajuste, para auditoria durante o período de rollout

---

### Requirement: Piso de pace para cálculo de IF saturável
O sistema SHALL evitar subestimação de TSS em sessões de qualidade aplicando teto e piso ao cálculo de IF por pace.

#### Scenario: Pace muito mais rápido que o limiar
- **WHEN** o pace médio do treino for significativamente mais rápido que o pace limiar do atleta (ex: intervalado em Z5)
- **THEN** o IF SHALL ser limitado pelo teto `IF_TETO` (ex: 1.20), evitando explosão do TSS

#### Scenario: Pace ligeiramente mais rápido que o limiar
- **WHEN** o pace médio estiver entre o pace limiar e o pace de 5km previsto
- **THEN** o IF SHALL refletir a aceleração proporcionalmente sem saturar prematuramente

#### Scenario: Pace no ou abaixo do limiar
- **WHEN** o pace médio for igual ou mais lento que o pace limiar
- **THEN** o IF SHALL ser calculado pela razão `paceLimiar / paceTreino` sem aplicação de piso

#### Scenario: Combinação com prioridade FC > Pace
- **WHEN** FC média e pace médio ambos estiverem disponíveis
- **THEN** o cálculo por FC SHALL prevalecer, e o piso de pace SHALL operar apenas como fallback

---

### Requirement: Validação de consistência entre pace, distância e duração
O sistema SHALL validar e, quando possível, derivar automaticamente campos ausentes no triângulo pace/distância/duração antes do cálculo de TSS.

#### Scenario: Dois campos presentes, um ausente
- **WHEN** `TreinoRealizado` tiver apenas 2 dos 3 campos (pace, distância, duração)
- **THEN** o sistema SHALL derivar o terceiro automaticamente antes do cálculo de TSS

#### Scenario: Três campos presentes e consistentes
- **WHEN** os três campos estiverem presentes e `paceMedio × distanciaKm` for igual a `duracaoMin` com tolerância de 5%
- **THEN** o sistema SHALL prosseguir com o cálculo de TSS sem ajustes

#### Scenario: Três campos presentes mas inconsistentes
- **WHEN** os três campos divergirem em mais de 5%
- **THEN** o sistema SHALL registrar log WARN identificando `campoSuspeito` e usar o par mais confiável (prioridade: duração + distância > pace) para recalcular o campo divergente

#### Scenario: Nenhuma inconsistência bloqueia ingestão
- **WHEN** o triângulo for inconsistente
- **THEN** o endpoint de criação/atualização SHALL persistir o treino mesmo assim (comportamento silencioso para ingestões externas), confiando no log para auditoria
