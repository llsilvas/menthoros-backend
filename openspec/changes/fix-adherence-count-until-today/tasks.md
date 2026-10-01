## 1. Repositório — Query com limite superior

- [x] 1.1 Adicionar `findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, dataInicio,
  dataFim): List<TreinoPlanejado>` em `TreinoPlanejadoRepository` (aditivo — método original
  permanece para `RevisaoSemanalServiceImpl`, que já trata o limite superior em memória)
- [x] 1.2 Teste de integração `TreinoPlanejadoRepositoryTest.findComRealizadoByAtletaAndPeriodoAteDataExcluiFuturos`
  provando `dataTreino <= dataFim` contra o schema real (Testcontainers) — escrito e compila; não
  executável nesta sessão (ver nota na seção 4)

## 2. Serviços — Migrar chamadores com o viés

- [x] 2.1 `AtletaProgressServiceImpl.getAderenciaSemanal`: usar o novo método com `dataFim = hoje`
- [x] 2.2 `CoachDashboardServiceImpl.montarResumo`: usar o novo método com `dataFim = hoje`
  (parâmetro já recebido pelo método)
- [x] 2.3 `ProgressaoTreinoServiceImpl.calcularHistorico`: usar o novo método com `dataFim = hoje`
  (corrige o denominador de `calcularAderencia()`/`treinosPlanejados21d`)
- [x] 2.4 `RevisaoSemanalServiceImpl`: **não alterar** — já filtra em memória com `semanaFim`
  explícito; fora do escopo

## 3. Testes — Atualizar mocks e cobrir o contrato

- [x] 3.1 `AtletaProgressServiceImplTest.GetAderenciaSemanal`: stubs migrados para
  `findComRealizadoByAtletaAndPeriodoAteData(..., HOJE)`; `calculaPercentualSemanal` troca a data
  futura (Jun 18) por uma data válida (Jun 16) dentro da semana corrente
- [x] 3.2 `CoachDashboardServiceImplTest`: stubs de `aderenciaPercentual`/`aderenciaPercentualNullSemPlano`
  migrados com `eq(HOJE)`
- [x] 3.3 `ProgressaoTreinoServiceImplTest`: stubs migrados (`any()` extra para `dataFim`); novo
  teste `planejadosLimitadosAHoje` verifica via `verify(...)` que o repositório é chamado com
  `dataFim = hoje`
- [x] 3.4 `./mvnw clean compile` — 0 erros após as migrações de assinatura

## 4. Validação Final

- [x] 4.1 `./mvnw clean test -Dtest=AtletaProgressServiceImplTest,CoachDashboardServiceImplTest,ProgressaoTreinoServiceImplTest,RevisaoSemanalServiceImplTest`
  — 101/101 passando (0 failures, 0 errors)
- [x] 4.2 `./mvnw clean test` (suíte completa) — 4339 testes, 0 *failures*, 244 *errors*; os 244
  erros são 100% `ApplicationContext failure`/`Failed to load ApplicationContext` em 46 classes
  `*Test`/`*IT` que estendem `AbstractIntegrationTest` (Testcontainers) — **nenhuma delas tocada por
  esta change** (ex.: `KudosRepositoryTest`, `AssinaturaRepositoryTest`, `MultiTenantIsolationTest`).
  Confirmado pré-existente e não relacionado: nesta sessão (cloud container sem daemon Docker —
  `docker info` falha com "failed to connect to the docker API"; `service docker start` falha por
  falta de privilégio para `ulimit`), até o teste `TreinoPlanejadoRepositoryTest.selecionaApenasEstadosDeRetry`
  **já existente, sem nenhuma linha alterada por esta change**, falha com o mesmo erro.
  `./mvnw clean verify` (que soma os `*IT` via Failsafe) não foi executado por depender do mesmo
  Testcontainers — **rodar em ambiente com Docker antes do merge** para confirmar o teste novo
  (`findComRealizadoByAtletaAndPeriodoAteDataExcluiFuturos`) e os `*IT` existentes.
- [x] 4.3 Tarefas marcadas como concluídas neste arquivo
