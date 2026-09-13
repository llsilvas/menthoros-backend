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

Segunda ocorrência real, capturada após a limpeza dos logs para a nova geração do atleta
Leandro (2026-09-13, mesmo dia):

```
2026-09-13 17:54:38,964 virtual-169 WARN  b.c.m.b.services.impl.TsbServiceImpl [] [] - 🔄 RECALCULANDO HISTÓRICO COMPLETO para atleta d83c4c31-607b-4370-8bfe-de270ad33121 - operação custosa!
2026-09-13 17:54:39,074 virtual-169 INFO  b.c.m.b.services.impl.TsbServiceImpl [] [] - 📅 Intervalo de recálculo: 2025-08-05 até 2026-09-13
2026-09-13 17:54:39,074 virtual-169 INFO  b.c.m.b.services.impl.TsbServiceImpl [] [] - 📊 Recalculando 405 dias em 14 blocos de até 30 dias (de 2025-08-05 até 2026-09-13)
```

Mesmo atleta (`d83c4c31`) e mesmo padrão da primeira ocorrência: 405 dias de histórico
reprocessados numa única reconciliação, minutos depois da sessão original.

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
