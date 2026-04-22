## ADDED Requirements

### Requirement: Composição determinística do macrociclo
O sistema SHALL compor um macrociclo com duração e estrutura de mesociclos determinadas pela distância da prova principal.

#### Scenario: Macrociclo para maratona
- **WHEN** a prova principal for ≥ 42 km e a data de início permitir
- **THEN** o macrociclo SHALL ter no mínimo 16 semanas de duração

#### Scenario: Macrociclo para meia-maratona
- **WHEN** a prova principal for entre 15 km e 30 km
- **THEN** o macrociclo SHALL ter no mínimo 12 semanas de duração

#### Scenario: Macrociclo para 10 km
- **WHEN** a prova principal for entre 8 km e 15 km
- **THEN** o macrociclo SHALL ter no mínimo 8 semanas de duração

#### Scenario: Data de início insuficiente
- **WHEN** a data de início não permitir a duração mínima por distância
- **THEN** o sistema SHALL retornar 400 Bad Request com mensagem explicando gap mínimo necessário

---

### Requirement: Distribuição de mesociclos por fase
O sistema SHALL distribuir mesociclos em ordem cronológica com proporções determinadas pelas fases canônicas.

#### Scenario: Distribuição padrão
- **WHEN** o macrociclo for gerado com configuração padrão
- **THEN** as proporções SHALL ser aproximadamente: BASE 40%, ESPECIFICO 30%, PICO 15%, TAPER 10%, TRANSICAO 5%

#### Scenario: Ordem cronológica
- **WHEN** os mesociclos forem persistidos
- **THEN** a ordem SHALL ser: BASE → ESPECIFICO → PICO → TAPER → TRANSICAO, sem sobreposição entre `inicio` e `fim`

#### Scenario: Coincidência com PeriodoTaper
- **WHEN** existir `PeriodoTaper` ativo para a mesma prova
- **THEN** o mesociclo de fase `TAPER` SHALL ter `inicio` e `fim` iguais aos do `PeriodoTaper`

---

### Requirement: Herança de fase pelo plano semanal
O sistema SHALL permitir que `PlanoSemanalService` herde `fase` e `objetivoCarga` do mesociclo vigente na data de geração da semana.

#### Scenario: Mesociclo vigente
- **WHEN** a data de início da semana estiver dentro de um mesociclo
- **THEN** a `PlanoSemanal` SHALL referenciar o `mesociclo` e adotar seu `objetivoCarga` como guia base

#### Scenario: Fora do macrociclo
- **WHEN** a data da semana estiver fora de qualquer macrociclo
- **THEN** `PlanoSemanalService` SHALL operar no comportamento atual sem referência a mesociclo

---

### Requirement: Exposição do progresso do macrociclo ao LLM
O sistema SHALL expor o estado do macrociclo e mesociclo no contexto enviado ao LLM.

#### Scenario: Contexto completo
- **WHEN** o plano for gerado e o atleta estiver dentro de um macrociclo
- **THEN** o contexto SHALL conter `mesocicloAtual` com `fase`, `semanaNdeM`, `objetivoCarga`, `destaques` e `macrocicloProgresso` com `semanaAtual`, `totalSemanas`

#### Scenario: Sem macrociclo
- **WHEN** não houver macrociclo ativo
- **THEN** as seções `mesocicloAtual` e `macrocicloProgresso` SHALL ser omitidas do contexto
