# Boas Práticas para Prescrição de Pace em Treinos Gerados por IA

## Contexto e Problema

O sistema gera treinos com um intervalo de pace alvo (`ritmoAlvo`, ex: `"5:00-5:30/km"`).
Atualmente esse valor é computado pelo LLM a partir das zonas teóricas de treinamento
(`ZonaTreinoService`) e do `paceLimiar` cadastrado no perfil do atleta. O problema é que:

- O `paceLimiar` pode estar desatualizado (último teste há mais de 3 meses).
- O LLM não confronta o pace prescrito com o pace *realmente executado* nos treinos recentes.
- Paces acima da capacidade atual geram frustração, aumento indevido de RPE e risco de lesão.

---

## Princípios Fisiológicos Fundamentais

### 1. O pace limiar como âncora primária

Todo o sistema de zonas de pace deriva do **pace no limiar de lactato** (~ pace sustentável por
40-60 minutos em esforço máximo, correspondendo ao esforço de uma prova de 10 km para corredores
recreacionais).

> **Regra**: Quando `paceLimiar` está atualizado (< 90 dias), ele deve ser a âncora absoluta de
> todos os cálculos de zona.

### 2. O pace recente como validação e âncora secundária

O `paceLimiar` reflete a capacidade *potencial* do atleta. Porém, a capacidade *atual e
demonstrada* nos últimos 4-6 treinos é o que importa para evitar frustrações.

> **Regra**: O pace prescrito nunca deve ser mais do que 3-5% mais rápido do que o melhor pace
> demonstrado pelo atleta no mesmo tipo de esforço nas últimas 4 semanas.

### 3. Defasagem de adaptação (lag fisiológico)

Ganhos de condicionamento demoram **4-6 semanas** para se manifestar de forma consistente.
Isso significa que se o atleta treinando há 3 semanas não conseguiu reproduzir um determinado
pace, o sistema não deve prescrevê-lo ainda.

### 4. Ajuste por fadiga (TSB)

O atleta descansado e o atleta fatigado não têm a mesma capacidade de pace, mesmo com a mesma
aptidão (CTL). O TSB regula isso:

| TSB | Ajuste no pace prescrito |
|-----|--------------------------|
| < -20 (muito fatigado) | +10 a +15 seg/km |
| -20 a -10 (fatigado) | +5 a +10 seg/km |
| -10 a 0 (levemente fatigado) | +0 a +5 seg/km |
| 0 a +10 (recuperado) | sem ajuste |
| > +10 (muito descansado) | pode usar pace potencial pleno |

---

## Zonas de Pace e Correspondência com Tipos de Treino

Baseado no modelo de Jack Daniels (VDOT) adaptado, com referência ao `paceLimiar` (PL):

| Zona | Nome | Esforço | Offset em relação ao PL | Tipo de treino |
|------|------|---------|--------------------------|----------------|
| Z1 | Recuperação | RPE 2-3 | PL + 60 a +90 seg/km | REGENERATIVO |
| Z2 | Aeróbico fácil | RPE 3-4 | PL + 30 a +60 seg/km | FACIL, CONTINUO, LONGO |
| Z3 | Aeróbico forte | RPE 5-6 | PL + 10 a +25 seg/km | FARTLEK (parte leve) |
| Z4 | Limiar | RPE 7-8 | PL ± 0 a +10 seg/km | TEMPO_RUN, FARTLEK (parte forte) |
| Z5 | VO2max | RPE 8-9 | PL - 15 a -30 seg/km | INTERVALADO |
| Z6 | Anaeróbico/Sprint | RPE 9-10 | PL - 30 a -60 seg/km | TIRO, SUBIDA |

> **Nota sobre o LONGO**: apesar de longo em duração, o ritmo deve ser Z2 (PL + 30-60 seg/km).
> É um erro comum prescrever LONGO em ritmo mais rápido que Z2 — isso compromete a recuperação
> e o principal objetivo fisiológico (lipólise, eficiência aeróbica mitocondrial).

---

## Regras Práticas para o Sistema

### Regra 1 — Derivar o pace de referência dos treinos recentes

```
pace_referencia = média(paceMedia dos últimos 4 treinos do tipo FACIL ou CONTINUO)

Se não há treinos FACIL/CONTINUO recentes:
    pace_referencia = paceLimiar + 45 seg/km  (estimativa de pace fácil)
```

Essa referência de Z2 é o ponto de ancoragem. Todos os outros paces são derivados dela:

```
Z1 = pace_referencia + 20 a +35 seg/km
Z2 = pace_referencia (é a definição)
Z3 = pace_referencia - 15 a -25 seg/km
Z4 = pace_referencia - 30 a -45 seg/km  (≈ paceLimiar)
Z5 = pace_referencia - 55 a -75 seg/km
Z6 = pace_referencia - 80 a -110 seg/km
```

### Regra 2 — Teto de pace por tipo de treino (anti-frustração)

Para cada tipo de treino, o sistema deve:
1. Buscar o **melhor pace recente** do atleta em treinos do mesmo tipo (últimas 4 semanas).
2. Garantir que o pace prescrito **não seja mais rápido** do que esse teto + 2%.

```
Exemplo:
  Últimos INTERVALADO do atleta: 4:45/km, 4:50/km, 4:52/km
  Melhor recente = 4:45/km
  Teto prescrito = 4:45 × 1.02 ≈ 4:43/km (não pode ser mais rápido que isso)
```

Isso evita que o LLM prescreva paces baseados em potencial teórico que o atleta ainda não
atingiu na prática.

### Regra 3 — Amplitude do intervalo de pace

O `ritmoAlvo` é um intervalo (`"5:00-5:30/km"`), não um ponto fixo. A amplitude deve refletir
a variabilidade natural do esforço:

| Tipo de treino | Amplitude sugerida | Justificativa |
|---|---|---|
| REGENERATIVO | 25-35 seg/km | Esforço muito livre, varia muito |
| FACIL / CONTINUO | 20-30 seg/km | Fácil mas com alguma consistência |
| LONGO | 20-30 seg/km | Idem CONTINUO |
| FARTLEK | 45-60 seg/km | Alterna zonas — amplitude natural |
| TEMPO_RUN | 10-15 seg/km | Deve ser mais preciso (esforço controlado) |
| INTERVALADO | 8-12 seg/km | Alta precisão — meta é VO2max |
| TIRO | 5-10 seg/km | Máximo esforço — pouca margem |

### Regra 4 — Sinalizar quando o `paceLimiar` está desatualizado

Se `dataUltimoTestePace` > 90 dias ou `paceLimiar` == null:
- O sistema deve usar o `paceMedia` recente como âncora **e** sinalizar no prompt que os paces
  são estimados com base no histórico.
- A confiança na prescrição cai, portanto as amplitudes devem ser alargadas em 10-15 seg/km.

```
⚠️ Pace limiar não testado há mais de 90 dias.
   Usando paceMedia recente como âncora. Ampliar intervalos de pace em ±10 seg/km.
```

---

## O que o Prompt do LLM deve Receber

Atualmente o prompt já inclui as zonas teóricas (`ZonaTreinoService`) e o histórico recente
(últimos 14 dias com `paceMedia`). O que **falta** para uma prescrição mais aderente:

### Dados ausentes hoje

1. **Pace médio por tipo de treino (últimas 4 semanas)**
   ```
   ### Pace Demonstrado Recentemente (referência para prescrição)
   - FACIL/CONTINUO: 5:45/km (média 6 treinos)
   - LONGO: 5:52/km (média 2 treinos)
   - INTERVALADO: 4:48/km no esforço principal (média 3 treinos)
   - TEMPO_RUN: 5:05/km (1 treino)
   ```

2. **Instrução explícita ao LLM sobre o teto de pace**
   ```
   REGRA OBRIGATÓRIA: O ritmoAlvo de cada treino NÃO pode ser mais rápido
   do que o pace demonstrado pelo atleta no tipo equivalente nas últimas 4
   semanas. Consulte os dados de "Pace Demonstrado Recentemente" acima.
   ```

3. **Ajuste por TSB** (já existe no prompt, mas sem aplicação direta ao pace)
   - Incluir instrução: "Com TSB = X, aplicar penalidade de Y seg/km aos paces"

---

## Fluxo de Cálculo Proposto

```
[1] Buscar TreinoRealizado das últimas 4 semanas
      → agrupar por tipoTreino
      → calcular min/avg/max de paceMedia por tipo

[2] Validar paceLimiar (dataUltimoTestePace < 90 dias?)
      → Sim: usar paceLimiar como âncora Z4
      → Não: derivar Z4 estimado da média recente de TEMPO_RUN ou ajuste de Z2

[3] Calcular pace_referencia (Z2)
      → média dos treinos FACIL/CONTINUO recentes
      → fallback: paceLimiar + 45 seg/km

[4] Gerar faixas por zona com TSB adjustment

[5] Calcular teto por tipo (anti-frustração)
      → máx permitido = melhor pace recente × 1.02

[6] Incluir no prompt como bloco estruturado
```

---

## Referências Científicas Utilizadas

- **Jack Daniels, "Daniels' Running Formula"** — modelo VDOT e percentuais de VO2max por zona
- **Joe Friel, "The Triathlete's Training Bible"** — zonas de pace pelo FTP/pace crítico
- **Phil Maffetone** — base aeróbica e paces de Z2 como fundação do treinamento
- **Stephen Seiler** — modelo polarizado 80/20: 80% do volume em Z1-Z2, 20% em Z4-Z6
- **Inigo Mujika** — TSB e tapering: efeitos do estado de fadiga na performance de pace

---

## Arquivos Relevantes no Projeto

| Arquivo | Responsabilidade relacionada |
|---|---|
| [ZonaTreinoService.java](../src/main/java/com/menthoros/services/helper/ZonaTreinoService.java) | Cálculo das zonas teóricas com base no `paceLimiar` |
| [PlanoTreinoPromptBuilder.java](../src/main/java/com/menthoros/services/prompt/PlanoTreinoPromptBuilder.java) | Montagem do prompt — local ideal para adicionar pace por tipo de treino |
| [MetricasPromptFormatter.java](../src/main/java/com/menthoros/services/prompt/MetricasPromptFormatter.java) | Formato das métricas de carga no prompt |
| [TreinoRealizado.java](../src/main/java/com/menthoros/entity/TreinoRealizado.java) | Entidade com `paceMedia` dos treinos executados |
| [Atleta.java](../src/main/java/com/menthoros/entity/Atleta.java) | `paceLimiar`, `dataUltimoTestePace`, `fcLimiar` |

---

---

## Alinhamento com `ZonaTreinoService` Existente

O `ZonaTreinoService` já calcula as 6 zonas usando **fatores multiplicativos** sobre o
`paceLimiar`. Para `paceLimiar = 5:00/km`:

| Zona | Fator | Resultado | Offset aproximado |
|------|-------|-----------|-------------------|
| Z1 | 1.15 – 1.25 | 5:45 – 6:15/km | +45 a +75 seg |
| Z2 | 1.05 – 1.15 | 5:15 – 5:45/km | +15 a +45 seg |
| Z3 | 0.98 – 1.05 | 4:54 – 5:15/km | -6 a +15 seg |
| Z4 | 0.95 – 1.00 | 4:45 – 5:00/km | -15 a 0 seg |
| Z5 | 0.90 – 0.97 | 4:30 – 4:51/km | -30 a -9 seg |
| Z6 | 0.82 – 0.90 | 4:06 – 4:30/km | -54 a -30 seg |

Os offsets variam com o `paceLimiar` do atleta (abordagem multiplicativa é fisiologicamente
mais precisa). A implementação deve **reusar** `ZonaTreinoService.calcularZonasPace()` como
base e aplicar ajustes de TSB sobre os resultados, não recalcular do zero.

---

## Plano de Implementação por Prioridade

### Fase 1 — Informar o LLM com paces reais (Prioridade ALTA)
> **Impacto imediato.** Mudança mais simples, maior ganho na qualidade da prescrição.

**Problema que resolve:** o LLM não sabe a que velocidade o atleta *realmente treinou* —
só sabe os valores teóricos de zona.

**O que fazer:**

1. Criar **`PaceHistoricoFormatter`**
   - Localização: `src/main/java/com/menthoros/services/prompt/PaceHistoricoFormatter.java`
   - Responsabilidade: consultar `TreinoRealizado` das últimas 4 semanas, agrupar por
     `tipoTreino`, calcular `min`/`média`/`max` de `paceMedia` por grupo
   - Entrada: `List<TreinoRealizado>` (últimas 4 semanas, já disponível via `TreinoHistoricoProvider`)
   - Saída: bloco de texto Markdown para o prompt

   Formato do bloco gerado:
   ```
   ## 🏃 PACE DEMONSTRADO NOS ÚLTIMOS TREINOS

   > REGRA OBRIGATÓRIA: o ritmoAlvo de cada treino NÃO pode ser mais rápido
   > do que o pace abaixo para o tipo equivalente. Estes são valores reais,
   > não teóricos.

   | Tipo       | Pace mínimo | Pace médio | Pace máximo | Treinos |
   |------------|-------------|------------|-------------|---------|
   | FACIL      | 5:30/km     | 5:45/km    | 6:00/km     | 4       |
   | CONTINUO   | 5:25/km     | 5:40/km    | 5:55/km     | 3       |
   | LONGO      | 5:40/km     | 5:52/km    | 6:05/km     | 2       |
   | INTERVALADO| 4:40/km     | 4:48/km    | 5:00/km     | 2       |

   ⚠️ Tipos sem histórico recente: usar a zona calculada (Z correspondente) como referência.
   ```

2. Integrar em **`PlanoTreinoPromptBuilder.buildOptimizedPrompt`**
   - Adicionar chamada ao `PaceHistoricoFormatter` após `formatarHistoricoTreinos`
   - Inserir bloco no `historicoCompleto` como nova etapa

---

### Fase 2 — Ajuste dinâmico por TSB (Prioridade ALTA)
> **Segurança fisiológica.** Evita prescrição de paces irreais em semanas de fadiga.

**Problema que resolve:** o TSB já está no prompt, mas o LLM não recebe uma instrução
numérica explícita de quanto ajustar o pace.

**O que fazer:**

1. Criar **`PaceZoneCalculator`**
   - Localização: `src/main/java/com/menthoros/services/helper/PaceZoneCalculator.java`
   - Responsabilidade: receber as zonas calculadas por `ZonaTreinoService` + TSB atual,
     retornar zonas ajustadas
   - **Reaproveitar** `ZonaTreinoService.calcularZonasPace()` como entrada

   Lógica de ajuste (em segundos por km):
   ```java
   int ajusteSegundos = switch (true) {
       case tsb < -20 -> 12;
       case tsb < -10 -> 7;
       case tsb <   0 -> 3;
       default        -> 0;
   };
   // somar ajusteSegundos a paceMin e paceMax de cada ZonaPace
   ```

2. Incluir no prompt a instrução de ajuste com valor calculado:
   ```
   ⚠️ TSB atual = -15 → paces com penalidade de +7 seg/km já aplicada nas zonas acima.
   ```

---

### Fase 3 — Teto por tipo de treino anti-frustração (Prioridade MÉDIA)
> **Proteção contra paces "inalcançáveis".** Garante que nenhum pace prescrito supere
> a melhor performance recente do atleta no mesmo esforço.

**Problema que resolve:** mesmo com histórico visível, o LLM pode ignorá-lo e usar
o valor teórico da zona por "excesso de otimismo".

**O que fazer:**

1. Adicionar ao **`PaceHistoricoFormatter`** um método `calcularTetoPorTipo`:
   - Para cada `tipoTreino` com histórico: `teto = melhorPaceRecente × 0.98`
   - Para tipos sem histórico: `teto = zona correspondente (fator 1.02)`

2. Incluir no prompt um bloco de **tetos obrigatórios** separado da tabela histórica:
   ```
   ## ⛔ TETO DE PACE POR TIPO (NÃO ULTRAPASSAR)

   - INTERVALADO: não mais rápido que 4:43/km
   - TIRO:        não mais rápido que 4:10/km
   - TEMPO_RUN:   não mais rápido que 5:02/km
   ```

---

### Fase 4 — Validação pós-LLM do ritmoAlvo (Prioridade MÉDIA)
> **Safety net.** Garante que mesmo se o LLM ignorar as instruções, o sistema
> corrija o pace antes de salvar o plano.

**Problema que resolve:** LLMs ocasionalmente ignoram regras numéricas — esta fase
é o último "freio" antes de persistir a prescrição.

**O que fazer:**

1. Criar **`PaceValidator`**
   - Localização: `src/main/java/com/menthoros/services/helper/PaceValidator.java`
   - Responsabilidade: receber `ritmoAlvo` gerado + teto calculado, verificar e corrigir
   - Parsear o formato `"5:00-5:30/km"` → `(paceMin, paceMax)`
   - Comparar `paceMin` com `teto`: se mais rápido, ajustar adicionando a diferença + buffer

2. Integrar em **`IaServiceImpl.validarENormalizarPlanoGerado`** (método já existe)
   - Chamar `PaceValidator` para cada `TreinoPlanejadoLlmDto` gerado
   - Logar correções aplicadas: `log.warn("ritmoAlvo corrigido: {} → {}", original, corrigido)`

---

### Fase 5 — Validação de `paceLimiar` desatualizado (Prioridade BAIXA)
> **Qualidade de dados.** Sem dados confiáveis de limiar, toda a prescrição degrada.

**O que fazer:**

1. Em **`PlanoTreinoPromptBuilder`** ou no `PaceHistoricoFormatter`:
   - Verificar `atleta.getDataUltimoTestePace()`
   - Se > 90 dias ou `null`: adicionar alerta ao prompt e ampliar amplitude em +15 seg/km
   - Sugerir ao atleta que realize novo teste de limiar

2. Exibir no prompt:
   ```
   ⚠️ Pace limiar não atualizado (último teste: 95 dias atrás).
      Paces calculados com base no histórico recente. Margem ampliada em ±15 seg/km.
      Recomendado: realizar teste de Cooper ou corrida de 20 min para atualizar o limiar.
   ```

---

## Estratégia de Testes

### `PaceHistoricoFormatterTest`
**Localização:** `src/test/java/com/menthoros/services/prompt/PaceHistoricoFormatterTest.java`

```
Cenários obrigatórios:

1. deveAgregarPaceMediaPorTipoTreino
   - Dado: 5 treinos CONTINUO com paceMedia [5:20, 5:30, 5:40, 5:25, 5:35]
   - Esperado: bloco contém "5:20/km" como mínimo e média ≈ "5:30/km"

2. deveExibirApenasUltimaQuatroSemanas
   - Dado: 3 treinos há 30 dias + 3 treinos há 50 dias (fora da janela)
   - Esperado: apenas 3 treinos contabilizados

3. deveGerarAvisoParaTipoSemHistorico
   - Dado: sem treinos INTERVALADO nas últimas 4 semanas
   - Esperado: INTERVALADO aparece como "sem histórico recente — usar zona Z5"

4. deveLidarComListaVazia
   - Dado: nenhum treino realizado
   - Esperado: bloco indica "sem histórico disponível; usar zonas teóricas"

5. deveCalcularTetoPorTipo
   - Dado: INTERVALADO com melhor pace = 4:48/km
   - Esperado: teto calculado ≤ 4:43/km (2% mais lento que o melhor)
```

---

### `PaceZoneCalculatorTest`
**Localização:** `src/test/java/com/menthoros/services/helper/PaceZoneCalculatorTest.java`

```
Cenários obrigatórios:

1. deveMantarZonasIguaisComTsbPositivo
   - Dado: TSB = +5, paceLimiar = 5:00/km
   - Esperado: zonas idênticas às de ZonaTreinoService (sem ajuste)

2. deveAplicarPenalidadeComTsbNegativo
   - Dado: TSB = -15, paceLimiar = 5:00/km
   - Esperado: cada zona acrescida de +7 seg/km (arredondado)

3. deveAplicarPenalidadeMaximaComTsbMuitoNegativo
   - Dado: TSB = -25, paceLimiar = 5:00/km
   - Esperado: cada zona acrescida de +12 seg/km

4. deveRetornarZonasValidas_QuandoPaceLimiarNulo
   - Dado: paceLimiar = null
   - Esperado: sem exceção, retorna zonas com paceMin/paceMax = ZERO (delegado ao ZonaTreinoService)

5. deveReutilizarResultadoDoZonaTreinoService
   - Verificar que PaceZoneCalculator chama ZonaTreinoService.calcularZonasPace()
     e não reimplementa a lógica de percentuais
```

---

### `PaceValidatorTest`
**Localização:** `src/test/java/com/menthoros/services/helper/PaceValidatorTest.java`

```
Cenários obrigatórios:

1. deveAceitarPaceValido
   - Dado: ritmoAlvo = "5:00-5:30/km", teto = 4:50/km
   - Esperado: sem correção, retorna original

2. deveCorrigirPaceMaisRapidoQueOTeto
   - Dado: ritmoAlvo = "4:40-4:50/km", teto = 4:55/km
   - Esperado: ritmoAlvo corrigido para "4:55-5:05/km" (diferença + buffer)

3. deveParsearFormatoCorreto
   - Dado: "5:00-5:30/km", "4:45-5:00/km", "10:00-10:30/km"
   - Esperado: todas parseadas sem exceção, paceMin < paceMax

4. deveRetornarOriginalParaFormatoInvalido
   - Dado: ritmoAlvo = "ritmo variado" (não parseável)
   - Esperado: retorna original sem lançar exceção; log.warn emitido

5. deveLogWarningAoCORRIGIR
   - Verificar que log.warn é chamado quando uma correção é aplicada
```

---

### Testes de integração no `PlanoTreinoPromptBuilder`

```
Cenários complementares nos testes existentes:

1. deveTerBlocoDePaceHistoricoNoPrompt
   - Dado: atleta com treinos realizados
   - Esperado: prompt contém "PACE DEMONSTRADO NOS ÚLTIMOS TREINOS"

2. deveTerAvisoDeTetoNoPrompt
   - Dado: atleta com treinos INTERVALADO recentes
   - Esperado: prompt contém "TETO DE PACE" com valor numérico

3. deveTerAvisoDeAjusteTsbNoPrompt
   - Dado: TSB = -18
   - Esperado: prompt contém "penalidade de +7 seg/km" (ou equivalente)

4. deveTerAvisoDePaceLimiarDesatualizado
   - Dado: dataUltimoTestePace há 100 dias
   - Esperado: prompt contém aviso de limiar desatualizado
```
