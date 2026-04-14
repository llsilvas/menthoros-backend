## ADDED Requirements

### Requirement: Tipo de treino TESTE_LIMIAR
O sistema SHALL suportar `TESTE_LIMIAR` como um valor válido do enum `TipoTreino`, com fator de impacto `1.35` (esforço máximo de 3km, equivalente a uma prova curta), zona alvo "Zona 4-5 (Limiar/VO2max)" e cor de identificação visual `#E53935`.

#### Scenario: Treino planejado criado como teste de limiar
- **WHEN** um `TreinoPlanejado` é criado com `tipoTreino = TESTE_LIMIAR`
- **THEN** o sistema SHALL aceitar o treino e persistir com o tipo correto

#### Scenario: Fator de impacto usado no cálculo de TSS
- **WHEN** o TSS de um `TreinoPlanejado` do tipo `TESTE_LIMIAR` é calculado
- **THEN** o sistema SHALL aplicar o fator de impacto `1.35` ao TSS base

---

### Requirement: Prescrição estruturada do Teste de 3K
Ao criar um `TreinoPlanejado` do tipo `TESTE_LIMIAR`, o sistema SHALL gerar automaticamente as seguintes etapas (`EtapaTreino`) na ordem indicada:

1. **Aquecimento** (ordem 1): tipo `AQUECIMENTO`, duração 20 min, descrição "Trote leve em Zona 1-2 + 4x 80m progressivos com 60s de recuperação entre cada", FC alvo "60-75% FCmax"
2. **Teste 3K** (ordem 2): tipo `ESFORCO_PRINCIPAL`, distância 3.0 km, descrição "3km em esforço máximo controlado — saída conservadora, progressão nos últimos 500m. Manter ritmo constante e evitar iniciar muito forte.", FC alvo "90-100% FCmax"
3. **Desaquecimento** (ordem 3): tipo `DESAQUECIMENTO`, duração 12 min, descrição "Trote leve em Zona 1 para recuperação ativa", FC alvo "55-65% FCmax"

#### Scenario: Etapas geradas automaticamente na criação do treino
- **WHEN** um `TreinoPlanejado` do tipo `TESTE_LIMIAR` é persistido
- **THEN** o sistema SHALL criar exatamente 3 etapas com as propriedades especificadas acima

#### Scenario: Distância total do treino
- **WHEN** um `TreinoPlanejado` do tipo `TESTE_LIMIAR` é criado
- **THEN** a `distanciaKm` total do treino SHALL ser `3.0` (referente à parte do teste)

#### Scenario: Duração estimada do treino
- **WHEN** um `TreinoPlanejado` do tipo `TESTE_LIMIAR` é criado
- **THEN** a `duracaoMin` total SHALL ser 45 minutos (20 aquecimento + ~12 teste + 13 desaquecimento)

---

### Requirement: Registro do resultado do Teste de 3K
O sistema SHALL armazenar campos específicos do teste no `TreinoRealizado` quando o treino associado for do tipo `TESTE_LIMIAR`:

- `testeTempoTotalSegundos` (Integer): Tempo total em segundos para completar a distância do teste
- `testeDistanciaKm` (Decimal): Distância efetivamente percorrida no esforço (default 3.0)
- `testePaceMediaSegPorKm` (Integer): Pace médio em segundos por km durante o esforço do teste
- `testeTemperaturaC` (Integer, opcional): Temperatura ambiente em graus Celsius
- `testeTipoSuperficie` (String, opcional): Tipo de superfície — "PISTA", "ASFALTO", "TERRA", "ESTEIRA"
- `testeObservacoes` (String, opcional): Observações adicionais do treinador

#### Scenario: Resultado registrado com dados obrigatórios
- **WHEN** um `TreinoRealizado` é salvo para um treino do tipo `TESTE_LIMIAR`
- **THEN** o sistema SHALL aceitar e persistir `testeTempoTotalSegundos` e `testeDistanciaKm`

#### Scenario: Resultado registrado sem os campos do teste
- **WHEN** um `TreinoRealizado` é salvo para um treino do tipo `TESTE_LIMIAR` sem os campos `teste_*`
- **THEN** o sistema SHALL persistir o treino normalmente, com os campos de teste como `NULL`

---

### Requirement: Cálculo automático do pace de limiar
Após o registro de um `TreinoRealizado` do tipo `TESTE_LIMIAR` com `testeTempoTotalSegundos` informado, o sistema SHALL calcular automaticamente o `paceLimiarSegPorKm` usando a fórmula:

```
paceTesteSeg = testeTempoTotalSegundos / testeDistanciaKm
paceLimiar   = round(paceTesteSeg * 1.05)
```

Os paces derivados por zona SHALL ser calculados como:
- Z1 (regenerativo):  `round(paceTesteSeg * 1.45)`
- Z2 (base aeróbica): `round(paceTesteSeg * 1.25)`
- Z3 (moderado):      `round(paceTesteSeg * 1.12)`
- Z4 (limiar):        `round(paceTesteSeg * 1.05)`
- Z5 (VO2max):        `round(paceTesteSeg * 0.98)`

Após o cálculo, o sistema SHALL atualizar `PlanoMetaDados.paceLimiarSegPorKm` com o novo valor e registrar `dataUltimoTesteLimiar` com a data do treino realizado.

#### Scenario: Pace de limiar calculado e persistido
- **WHEN** um resultado de teste é registrado com `testeTempoTotalSegundos = 900` (15 min para 3km = 5:00/km)
- **THEN** `paceLimiar` SHALL ser `315` segundos/km (5:15/km)
- **AND** `PlanoMetaDados.paceLimiarSegPorKm` SHALL ser atualizado para `315`
- **AND** `PlanoMetaDados.dataUltimoTesteLimiar` SHALL ser atualizado

#### Scenario: Resultado sem tempo do teste não dispara cálculo
- **WHEN** um `TreinoRealizado` do tipo `TESTE_LIMIAR` é salvo sem `testeTempoTotalSegundos`
- **THEN** o sistema SHALL NOT atualizar o `paceLimiarSegPorKm` no `PlanoMetaDados`

---

### Requirement: Histórico de testes do atleta
O sistema SHALL expor um endpoint `GET /atletas/{atletaId}/testes-limiar` que retorna a lista de todos os `TreinoRealizado` do tipo `TESTE_LIMIAR` do atleta, ordenados por data decrescente, com os seguintes campos:

- `id`, `dataTreino`, `testeDistanciaKm`, `testeTempoTotalSegundos`, `testePaceMediaSegPorKm`
- `paceLimiarCalculado` (calculado: `testePaceMediaSegPorKm * 1.05`)
- `testeTemperaturaC`, `testeTipoSuperficie`, `testeObservacoes`
- `fcMedia`, `fcMax`, `percepcaoEsforco`

#### Scenario: Histórico retornado em ordem decrescente
- **WHEN** o endpoint é chamado para um atleta com 3 testes registrados
- **THEN** o sistema SHALL retornar os 3 testes ordenados do mais recente para o mais antigo

#### Scenario: Atleta sem testes
- **WHEN** o endpoint é chamado para um atleta sem nenhum teste registrado
- **THEN** o sistema SHALL retornar uma lista vazia com status HTTP 200

#### Scenario: Multi-tenancy respeitado
- **WHEN** o endpoint é chamado por um usuário de uma assessoria
- **THEN** o sistema SHALL retornar apenas testes de atletas da mesma assessoria

---

### Requirement: Inclusão opcional do teste pelo treinador
O sistema SHALL incluir o Teste de 3K no plano semanal **somente quando o treinador enviar o parâmetro `incluirTesteLimiar: true`** no payload de geração do plano. Sem esse parâmetro, o teste não é inserido mesmo que o prazo esteja vencido.

#### Scenario: Treinador solicita inclusão do teste
- **WHEN** o payload de geração do plano contém `incluirTesteLimiar: true`
- **THEN** o sistema SHALL criar um `TreinoPlanejado` do tipo `TESTE_LIMIAR` na semana, substituindo o treino de qualidade

#### Scenario: Treinador não solicita o teste
- **WHEN** o payload de geração do plano não contém `incluirTesteLimiar` ou contém `incluirTesteLimiar: false`
- **THEN** o sistema SHALL NOT criar nenhum treino do tipo `TESTE_LIMIAR` nessa semana

#### Scenario: Inserção independe do status de alerta
- **WHEN** o treinador envia `incluirTesteLimiar: true` mesmo que o prazo não tenha chegado ainda
- **THEN** o sistema SHALL criar o `TreinoPlanejado` normalmente sem validar a periodicidade

---

### Requirement: Alerta de proximidade do teste — TESTE_LIMIAR_PROXIMO
O sistema SHALL emitir um alerta do tipo `TESTE_LIMIAR_PROXIMO` quando o atleta estiver a 14 ou menos dias de atingir o intervalo configurado em `periodicidadeTesteMeses`, mas ainda não tiver vencido o prazo. O alerta SHALL ser incluído na resposta do plano semanal e na consulta do atleta, contendo `diasRestantes` e `dataProximoTeste`.

#### Scenario: Alerta emitido dentro da janela de 14 dias
- **WHEN** `dataUltimoTesteLimiar + periodicidadeTesteMeses meses - hoje <= 14 dias`
- **AND** o prazo ainda não foi atingido (`diasRestantes > 0`)
- **THEN** o sistema SHALL incluir alerta `TESTE_LIMIAR_PROXIMO` com `diasRestantes` positivo

#### Scenario: Fora da janela de aviso antecipado
- **WHEN** o próximo teste está a mais de 14 dias
- **THEN** o sistema SHALL NOT gerar alerta de proximidade

---

### Requirement: Alerta de prazo vencido — TESTE_LIMIAR_VENCIDO
O sistema SHALL emitir um alerta do tipo `TESTE_LIMIAR_VENCIDO` quando o prazo para o próximo teste já foi atingido ou ultrapassado. O alerta SHALL conter `diasRestantes` com valor zero ou negativo e `dataProximoTeste`.

#### Scenario: Prazo exatamente atingido
- **WHEN** `hoje == dataUltimoTesteLimiar + periodicidadeTesteMeses meses`
- **THEN** o sistema SHALL emitir alerta `TESTE_LIMIAR_VENCIDO` com `diasRestantes = 0`

#### Scenario: Prazo ultrapassado
- **WHEN** `hoje > dataUltimoTesteLimiar + periodicidadeTesteMeses meses`
- **THEN** o sistema SHALL emitir alerta `TESTE_LIMIAR_VENCIDO` com `diasRestantes` negativo

#### Scenario: Atleta sem histórico de testes — prazo vencido desde o início
- **WHEN** `PlanoMetaDados.dataUltimoTesteLimiar` é `NULL`
- **THEN** o sistema SHALL emitir alerta `TESTE_LIMIAR_VENCIDO` (primeiro teste nunca realizado)

---

### Requirement: Nunca dois alertas simultâneos de teste
O sistema SHALL emitir apenas **um** alerta por vez: `TESTE_LIMIAR_PROXIMO` ou `TESTE_LIMIAR_VENCIDO`, nunca ambos.

#### Scenario: Prazo já vencido não gera alerta de proximidade
- **WHEN** o prazo foi atingido ou ultrapassado
- **THEN** o sistema SHALL emitir somente `TESTE_LIMIAR_VENCIDO`, NOT `TESTE_LIMIAR_PROXIMO`

#### Scenario: Nenhum alerta quando prazo está distante
- **WHEN** o próximo teste está a mais de 14 dias e o prazo não foi atingido
- **THEN** o sistema SHALL NOT emitir nenhum alerta de teste de limiar

---

### Requirement: Campos de periodicidade no PlanoMetaDados
O sistema SHALL adicionar os seguintes campos à entidade `PlanoMetaDados`:

- `dataUltimoTesteLimiar` (LocalDate, nullable): Data da última realização do Teste de 3K
- `periodicidadeTesteMeses` (Integer, default 3): Intervalo em meses entre testes

#### Scenario: Valor padrão de periodicidade
- **WHEN** um `PlanoMetaDados` é criado sem informar `periodicidadeTesteMeses`
- **THEN** o campo SHALL ter valor `3`
