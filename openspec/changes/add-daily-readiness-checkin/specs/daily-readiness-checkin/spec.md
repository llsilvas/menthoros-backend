## ADDED Requirements

### Requirement: Captura diária de prontidão subjetiva
O sistema SHALL aceitar registro diário de prontidão subjetiva por atleta, com sinais de sono, humor, dores, energia e estresse.

#### Scenario: Criar checkin do dia
- **WHEN** um atleta registrar checkin de uma data com todos os sinais subjetivos
- **THEN** o sistema SHALL persistir o checkin e calcular `readinessScore` e `nivelProntidao`

#### Scenario: Idempotência por data
- **WHEN** houver tentativa de registrar checkin para `(atleta, data)` já existente
- **THEN** o sistema SHALL atualizar o checkin existente em vez de criar duplicado

#### Scenario: Validação de faixa
- **WHEN** qualquer sinal subjetivo estiver fora da faixa esperada
- **THEN** o sistema SHALL retornar 400 Bad Request sem persistir

---

### Requirement: Cálculo determinístico de readiness
O sistema SHALL calcular `readinessScore` entre 0 e 1 e classificar em `PRONTO`, `CAUTELOSO` ou `DESCANSAR` de forma determinística.

#### Scenario: Score alto com todos os sinais positivos
- **WHEN** sono, humor e energia forem altos e dores/estresse forem baixos
- **THEN** `readinessScore` SHALL ser ≥ 0.75 e `nivelProntidao` SHALL ser `PRONTO`

#### Scenario: Score crítico
- **WHEN** sono, humor ou energia forem muito baixos, ou dores forem muito altas
- **THEN** `readinessScore` SHALL ser < 0.50 e `nivelProntidao` SHALL ser `DESCANSAR`

#### Scenario: Score intermediário
- **WHEN** o conjunto de sinais resultar em score entre 0.50 e 0.74
- **THEN** `nivelProntidao` SHALL ser `CAUTELOSO`

---

### Requirement: Modulação da elegibilidade de intervalado por readiness
O sistema SHALL considerar `nivelProntidao` do dia ao decidir sobre prescrição de intervalado.

#### Scenario: Bloqueio por DESCANSAR
- **WHEN** `nivelProntidao` do dia for `DESCANSAR`
- **THEN** o portão de elegibilidade de intervalado SHALL bloquear a prescrição, mesmo que outros portões permitam

#### Scenario: Atenuação por CAUTELOSO
- **WHEN** `nivelProntidao` for `CAUTELOSO`
- **THEN** o sistema SHALL permitir intervalado com recomendação de atenuação de volume entre 20% e 30%

#### Scenario: Sem checkin do dia
- **WHEN** não houver checkin para a data de decisão
- **THEN** o motor SHALL operar sem considerar readiness e registrar log WARN

---

### Requirement: Exposição de readiness no contexto de prescrição
O sistema SHALL expor readiness no contexto enviado ao LLM para geração de plano.

#### Scenario: Contexto enriquecido com histórico
- **WHEN** o plano for gerado
- **THEN** o contexto SHALL conter a sequência dos últimos 7 dias de `nivelProntidao` e o `readinessScore` do dia atual
