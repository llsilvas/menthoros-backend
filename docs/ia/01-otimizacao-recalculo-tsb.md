# Otimização do recálculo de TSB/PMC

**Status:** achado — causa raiz confirmada por leitura de código (grilling de 2026-09-13). Ainda
não é uma change no OpenSpec.
**Origem:** análise de logs reais no HomeLab durante o QA de `add-plan-generation-ledger`,
2026-09-13. Causa raiz corrigida via `/grill-with-docs` no mesmo dia.
**Radar:** registrado em `menthoros-product/openspec/SPRINTS.md` (seção "Radar — specs no
horizonte").

## Correção de entendimento (2026-09-13)

A primeira versão deste documento atribuía o disparo do recálculo a
`ActivityDedupServiceImpl`, "quando uma atividade duplicada é reconciliada". **Isso estava
errado.** A leitura do código mostrou que:

- `TsbService.recalcularHistoricoCompleto` é chamado por `BaselineCalculatorImpl.calcular`
  (`BaselineCalculatorImpl.java:61`), **incondicionalmente**, toda vez que
  `OnboardingServiceImpl.montarContexto` roda — e esse método roda em **toda tentativa de
  geração de plano**, para qualquer atleta que já tenha um baseline estabelecido
  (`PlanGenerationContextLoader.resolverOnboardingContext`, linhas 154-159: "atleta com baseline
  sempre tem o contexto recalculado").
- As linhas de dedup que aparecem no log logo antes do recálculo são só um passo interno de
  `OnboardingServiceImpl.normalizarEDeduplicarHistorico`, chamado na mesma sequência — não há
  relação causal entre dedup e o recálculo completo.
- `ActivityDedupServiceImpl.deduplicar` é uma transformação **pura em memória** sobre a lista já
  lida do banco; o único efeito colateral é gravar auditoria em
  `AtividadeProvenienciaDescartada` — nunca escreve em `TreinoRealizado` nem em `MetricasDiarias`.

## O problema real

`BaselineCalculatorImpl.calcular` recebe `historicoDeduplicado` como parâmetro mas **não o usa**
na chamada a `recalcularHistoricoCompleto` — o método relê `TreinoRealizado` do banco do zero, e
o código chamador, logo em seguida, só lê a **última** linha de `MetricasDiarias`
(`metricasDiariasRepository.findLatestByAtletaId`). Ou seja: recalcula a série inteira só para
usar o valor mais recente.

Isso só seria necessário se `MetricasDiarias` pudesse estar desatualizado — mas já existe um
caminho **incremental** para mantê-lo em dia: `TsbService.recalcularDesde(atletaId, data)`,
que reprocessa só a partir do dia afetado, propagando a recorrência CTL/ATL adiante (muito mais
barato que o recálculo completo). Uma investigação de código (2026-09-13) confirmou que **todo
caminho de persistência de treino real encontrado no backend já chama esse método
incremental**, com uma única exceção não relacionada a este achado (ver "Achado colateral"
abaixo):

- `IngestaoTreinoRealizadoServiceImpl` (3 pontos) — caminho comum de registro/reprocessamento.
- `StravaActivityServiceImpl.syncActivitiesInternal` (backfill em lote da API do Strava) e o
  webhook individual — ambos delegam a `IngestaoTreinoRealizadoService.registrar`/`reprocessar`
  por atividade.
- `FitTreinoPersister.persistir` — delega ao mesmo serviço.
- `ManualReconciliationServiceImpl` (3 pontos) — sempre chama `reprocessar` após o `save`.
- `IntervalsIcuLapsBackfillPersister` — só grava `EtapaRealizada` (laps), não mexe em
  `tssCalculado` nem em TSB.

O único consumidor legítimo de `recalcularHistoricoCompleto` que a investigação encontrou é o
endpoint manual `AtletaController` → `AtletaServiceImpl.recalcularMetricasAtleta`
(`AtletaServiceImpl.java:237-238`) — acionado explicitamente, não em todo fluxo automático.

**Conclusão: a chamada em `BaselineCalculatorImpl` é código defensivo/redundante.** Não há gap
de integridade identificado que a justifique — `MetricasDiarias` já deveria estar correto pelo
caminho incremental no momento em que o baseline é calculado.

## Achado colateral (bug real, distinto deste achado)

`IntervalsIcuActivityPersister.persistir` (usado por
`IntervalsIcuActivityIngestionServiceImpl.importarAtividade`, import individual de atividade do
intervals.icu, potencialmente retroativa) chama `TsbService.atualizarTsbDia` **diretamente**, em
vez de `recalcularDesde`. Isso contraria o contrato documentado no próprio `TsbService.java`
("todo caminho de ingestão retroativo precisa deste método [`recalcularDesde`] em vez de
`atualizarTsbDia` isolado"): se a atividade importada for de uma data passada, os dias
subsequentes em `MetricasDiarias` ficam desatualizados, sem que nada os corrija depois.

Este é um bug incremental separado — **não valida a chamada em `BaselineCalculatorImpl`**, que
continua sem justificativa própria. Não registrado como item de radar próprio ainda; mencionado
aqui para não se perder.

## Evidência de custo (mantém-se válida)

Na sessão do HomeLab analisada (boot iniciado 17:17:37, 5 gerações reais de plano entre
17:24:40 e 17:26:58), 2 dos 5 atletas tiveram esse recálculo disparado no meio do fluxo de
geração de plano. Resultado: 26 a 28 segundos de atraso entre "Plano gerado com sucesso" e
"Plano salvo", com o recálculo respondendo por 94% das linhas de log da janela (~1700 de
~1800 linhas). Segunda ocorrência real, mesmo atleta (`d83c4c31`), minutos depois, na sessão de
geração do atleta Leandro:

```
2026-09-13 17:54:38,964 virtual-169 WARN  b.c.m.b.services.impl.TsbServiceImpl [] [] - 🔄 RECALCULANDO HISTÓRICO COMPLETO para atleta d83c4c31-607b-4370-8bfe-de270ad33121 - operação custosa!
2026-09-13 17:54:39,074 virtual-169 INFO  b.c.m.b.services.impl.TsbServiceImpl [] [] - 📅 Intervalo de recálculo: 2025-08-05 até 2026-09-13
2026-09-13 17:54:39,074 virtual-169 INFO  b.c.m.b.services.impl.TsbServiceImpl [] [] - 📊 Recalculando 405 dias em 14 blocos de até 30 dias (de 2025-08-05 até 2026-09-13)
```

405 dias de histórico reprocessados numa única geração de plano. Não é uma regressão de
`add-plan-generation-ledger` — o comportamento já existia antes; foi só a primeira vez que
apareceu correlacionado, no log, com uma geração de plano em andamento.

## Por que importa

Esse custo não acontece "ocasionalmente, quando há uma atividade duplicada" — acontece **em
toda geração de plano**, para todo atleta com baseline (ou seja, praticamente todos, exceto
atletas legados sem migração), rodando dentro da transação síncrona de
`OnboardingServiceImpl.montarContexto` (`@Transactional`), que por sua vez roda dentro da
transação de `PlanGenerationContextLoader.load`. Com atletas acumulando histórico ao longo dos
meses, o custo cresce indefinidamente.

## Direção recomendada (2026-09-13, pós-investigação)

Não se trata de "otimizar" o recálculo completo tornando-o incremental — trata-se de **remover
a chamada** em `BaselineCalculatorImpl.calcular` e confiar no valor já mantido
incrementalmente em `MetricasDiarias` (ler direto via
`metricasDiariasRepository.findLatestByAtletaId`, que é exatamente o que o código já faz logo
depois de recalcular tudo). Isso elimina o custo por completo, sem precisar de recálculo
incremental nem de mover nada para fora da transação — o problema desaparece com a remoção, não
com uma versão mais barata do mesmo trabalho.

Antes de remover, checar: por que esse código foi escrito assim originalmente (pode ter sido uma
rede de segurança de um momento em que o caminho incremental não existia ou não era confiável) —
vale uma leitura do histórico do commit/PR original de `athlete-onboarding-baseline` antes de
tirar a chamada, para não reintroduzir o bug que ela talvez estivesse evitando.

Ainda não decidido: se vira change agora ou fica registrada aguardando priorização (decisão do
founder, 2026-09-13: aguardar — achado ainda não validado por completo quando a decisão foi
tomada; agora está validado, mas a prioridade segue em aberto).
