# Proposal: fix-adherence-count-until-today

## Status

Proposed

## Why

`TreinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodo(atletaId, tenantId, dataInicio)` não
tem limite superior em `dataTreino` — ela devolve todo `TreinoPlanejado` a partir de `dataInicio`,
sem parar em "hoje". Três cálculos de aderência usam essa consulta para o período que inclui a
semana corrente (ainda em andamento):

1. `AtletaProgressServiceImpl.getAderenciaSemanal` — aderência semanal exibida no perfil do atleta.
2. `CoachDashboardServiceImpl.montarResumo` — `aderenciaPercentual` no roster do coach.
3. `ProgressaoTreinoServiceImpl.calcularHistorico` — `treinosPlanejados21d`, usado por
   `calcularAderencia()` para decidir progressão de volume/longão/RPE do próximo plano.

Em todos os três, o denominador ("planejados") inclui treinos com `dataTreino` no futuro — dias da
semana corrente que ainda não chegaram — enquanto o numerador ("realizados") só pode contar o que já
aconteceu. Resultado: a semana corrente aparece com aderência artificialmente **mais baixa** do que a
real até ela terminar, e — no caso 3 — essa métrica alimenta diretamente a decisão de progressão do
plano de treino (`DecisaoProgressao`), então o viés não é só cosmético.

O código já tem precedente para o padrão correto: `TreinoPlanejadoRepository.findPendentesAteHojeDoPlano`
usa `tp.dataTreino <= :hoje`, e `RevisaoSemanalServiceImpl.consolidar` já comenta explicitamente o
defeito desta query e o compensa com um filtro em memória (`dataTreino <= semanaFim`) — prova de que o
problema é conhecido e tem uma correção direta.

## What Changes

- Adiciona `TreinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId,
  dataInicio, dataFim)`: mesma query, com `AND tp.dataTreino <= :dataFim`. **Aditivo** — o método
  original (`findComRealizadoByAtletaAndPeriodo`, sem limite) permanece inalterado para o único
  chamador que já trata o limite superior corretamente (`RevisaoSemanalServiceImpl`, que filtra em
  memória até `semanaFim` — não precisa mudar).
- Migra os três chamadores com o viés (`AtletaProgressServiceImpl.getAderenciaSemanal`,
  `CoachDashboardServiceImpl.montarResumo`, `ProgressaoTreinoServiceImpl.calcularHistorico`) para o
  novo método, passando `hoje` (já disponível via `Clock` injetado em cada um) como `dataFim`.
- Semanas já concluídas não mudam de resultado (toda `dataTreino` já é `<= hoje`); só a semana em
  andamento passa a ter denominador correto.

## Capabilities

### Modified Capabilities

- `adherence-tracking` (implícito — primeira formalização): aderência semanal/21d de um atleta,
  quando o período de cálculo inclui a semana/janela corrente, conta apenas treinos planejados com
  `dataTreino <= hoje`. Dias futuros da mesma semana entram no cálculo somente quando chegarem.

## Impact

- **Arquivos afetados:** `TreinoPlanejadoRepository.java` (novo método),
  `AtletaProgressServiceImpl.java`, `CoachDashboardServiceImpl.java`, `ProgressaoTreinoServiceImpl.java`
  (trocam de método), mais os testes correspondentes (`AtletaProgressServiceImplTest`,
  `CoachDashboardServiceImplTest`, `ProgressaoTreinoServiceImplTest`,
  `TreinoPlanejadoRepositoryTest` — novo teste de integração provando o `AND dataTreino <= :dataFim`
  contra o schema real).
- **Sem breaking changes de contrato de API:** os DTOs de saída (`AderenciasSemanalDto`,
  `CoachAtletaResumoDto`, `ProgressaoHistoricoResumo`) não mudam de forma; só o valor numérico da
  semana em andamento fica mais alto (correto).
- **Efeito colateral intencional:** `calcularAderencia()` em `ProgressaoTreinoServiceImpl` passa a
  refletir a aderência real da janela de 21 dias — pode mudar a `DecisaoProgressao` retornada para
  atletas cuja semana corrente tinha treinos futuros entrando no denominador antes da correção.
- **`MetricasAdesaoService` (`/api/v1/metricas/**`) tem o mesmo padrão de bug** (`calcularSemana`,
  `getAdesaoDiaria`, `getAdesaoDiariaAssessoria` usam `countPlannedTrainings`/janelas sem recortar em
  `hoje`), mas é um cálculo de aderência independente, sobre outro repositório
  (`countPlannedTrainings`/`countRealizedTrainings`), sem relação de código com
  `findComRealizadoByAtletaAndPeriodo`. Fica fora do escopo desta change — abrir change separada se
  o produto também quiser corrigi-lo lá.
- **Sem novas dependências Maven. Sem migração Flyway** (query JPQL, não schema).
