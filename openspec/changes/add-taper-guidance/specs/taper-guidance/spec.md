## ADDED Requirements

### Requirement: Cálculo determinístico da janela de taper
O sistema SHALL calcular a janela de taper para cada prova-alvo do atleta com duração e estratégia determinadas pela distância e nível de experiência.

#### Scenario: Meia-maratona ou mais longa
- **WHEN** a prova-alvo tiver distância ≥ 21 km
- **THEN** a duração padrão do taper SHALL ser de 21 dias (3 semanas)

#### Scenario: 10 km
- **WHEN** a prova-alvo tiver distância entre 8 km e 15 km
- **THEN** a duração padrão do taper SHALL ser de 14 dias (2 semanas)

#### Scenario: 5 km ou menor
- **WHEN** a prova-alvo tiver distância < 8 km
- **THEN** a duração padrão do taper SHALL ser de 7 dias (1 semana)

#### Scenario: Ajuste por nível de experiência
- **WHEN** o atleta for `INICIANTE`
- **THEN** a duração calculada SHALL ser reduzida em 25% (arredondada para semanas inteiras)
- **WHEN** o atleta for `AVANCADO`
- **THEN** a duração calculada SHALL permanecer no valor máximo recomendado pela distância

---

### Requirement: Redução progressiva de volume durante o taper
O sistema SHALL aplicar redução de volume semanal determinada pela estratégia ativa.

#### Scenario: Estratégia STEP
- **WHEN** a estratégia for `STEP` e o taper tiver 3 semanas
- **THEN** semana 1 (mais distante da prova) SHALL ter volume = 90% do volume-base, semana 2 = 75%, semana 3 (semana da prova) = 60%

#### Scenario: Estratégia LINEAR
- **WHEN** a estratégia for `LINEAR`
- **THEN** a redução SHALL ser distribuída uniformemente dia a dia até atingir 50% do volume-base no dia anterior à prova

#### Scenario: Estratégia EXPONENCIAL
- **WHEN** a estratégia for `EXPONENCIAL`
- **THEN** a redução SHALL ser suave nos primeiros dias do taper e acentuada nos últimos 7 dias (queda ≥ 40% nesse bloco)

---

### Requirement: Modulação de intervalados durante o taper
O sistema SHALL restringir intervalados pesados nos últimos 7 dias antes da prova e permitir apenas sessões de ativação.

#### Scenario: Bloqueio de intervalado de alto volume
- **WHEN** estiver a ≤ 7 dias da prova e for prescrito intervalado com volume efetivo > 4 km em ritmo acima do limiar
- **THEN** o portão `taperPermite` SHALL bloquear a prescrição

#### Scenario: Permissão de tune-up
- **WHEN** estiver entre 4 e 6 dias da prova
- **THEN** o sistema SHALL permitir sessões curtas de ativação como 4×400 m em pace-alvo ou 3×1 km em pace-alvo, com pausa completa

#### Scenario: Corridas leves nos últimos 2 dias
- **WHEN** estiver a ≤ 2 dias da prova
- **THEN** o sistema SHALL prescrever somente corridas em Z1/Z2 com duração ≤ 30 minutos

---

### Requirement: Exposição do estado de taper ao contexto de prescrição
O sistema SHALL expor o estado do taper no contexto enviado ao LLM.

#### Scenario: Taper ativo
- **WHEN** a data atual estiver dentro da janela de taper
- **THEN** o contexto SHALL conter `estaEmTaper=true`, `diasAteProva`, `estrategia`, `reducaoVolumePct`

#### Scenario: Taper inativo
- **WHEN** a data atual estiver antes do início do taper ou após a prova
- **THEN** o contexto SHALL conter `estaEmTaper=false` e omitir campos específicos de taper
