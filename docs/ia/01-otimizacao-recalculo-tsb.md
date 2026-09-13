# Otimização do recálculo de TSB/PMC

**Status:** achado — ainda não é uma change no OpenSpec.
**Origem:** análise de logs reais no HomeLab durante o QA de `add-plan-generation-ledger`, 2026-09-13.
**Radar:** registrado em `menthoros-product/openspec/SPRINTS.md` (seção "Radar — specs no horizonte").

## O problema

`TsbServiceImpl` recalcula PMC/CTL/ATL/TSB do zero — todo o histórico do atleta, mais de um
ano em alguns casos — sempre que uma atividade duplicada é reconciliada. O próprio código se
autodescreve como custoso no log:

```
🔄 RECALCULANDO HISTÓRICO COMPLETO ... - operação custosa!
```

O disparo vem de `ActivityDedupServiceImpl`, quando uma atividade chega duplicada por mais de
uma fonte (Strava, Garmin, intervals.icu) e precisa ser reconciliada.

## Evidência

Na sessão do HomeLab analisada (boot iniciado 17:17:37, 5 gerações reais de plano entre
17:24:40 e 17:26:58), 2 dos 5 atletas tiveram esse recálculo disparado no meio do fluxo de
geração de plano. Resultado: 26 a 28 segundos de atraso entre "Plano gerado com sucesso" e
"Plano salvo", com o recálculo respondendo por 94% das linhas de log da janela (~1700 de
~1800 linhas).

Não é uma regressão de `add-plan-generation-ledger` — o comportamento já existia antes; foi só
a primeira vez que apareceu correlacionado, no log, com uma geração de plano em andamento.

## Por que importa

PMC, CTL e ATL são recorrências exponenciais: cada dia depende só do valor do dia anterior e
da carga do próprio dia. Recalcular a série inteira a cada dedup é `O(histórico completo)`
quando o correto seria `O(dias desde o ponto de inserção/alteração)`. Com atletas acumulando
histórico ao longo dos meses, o custo desse recálculo cresce indefinidamente — e hoje ele roda
de forma síncrona, podendo bloquear outros fluxos (como a persistência do plano gerado).

## Direções possíveis (não decididas)

1. **Recálculo incremental** — reprocessar só a partir do primeiro dia afetado pela
   reconciliação, propagando a recorrência adiante, em vez de reprocessar tudo.
2. **Mover para fora do caminho síncrono** — mesma classe de problema que motivou tirar a
   chamada ao LLM de dentro da transação (`refactor-llm-call-outside-transaction`); a dedup e
   o recálculo poderiam rodar assíncronos, sem seguravar o fluxo que os disparou.

Nenhuma das duas foi avaliada em profundidade — este documento registra o achado para uma
avaliação futura, não uma decisão de design.
