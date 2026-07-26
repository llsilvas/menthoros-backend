---
status: accepted
---

# Resiliência de chamada externa por timeout por rota, sem circuit breaker

Chamadas externas (LLM, Strava) podiam bloquear indefinidamente. O dano não era só ocupar uma thread
do pool de LLM: a geração de plano é `@Transactional` e a chamada ao modelo acontece **dentro** da
transação, então cada chamada pendurada segurava uma conexão do pool do Hikari (default 10), com o
lote rodando 4 em paralelo. Um provider lento drenava o pool de conexões e derrubava o app inteiro —
login e telas do atleta incluídos —, não apenas as funcionalidades de IA.

A proposta original (`add-external-call-resilience`, versão de 2026-07-20) previa timeouts **e**
circuit breaker via Resilience4j nas três integrações. A sessão de grilling de 2026-07-26, feita com
leitura do código, levou a um escopo menor.

**Decisão 1 — timeout por rota de LLM, não por provider.** No Spring AI o timeout vive no cliente
HTTP do `ChatModel`, e só existem dois (OpenAI e Anthropic). Como as rotas `SIMPLE` (foco semanal,
1k tokens) e `PLANO` (geração, 12k tokens) são o mesmo provider, um timeout no nível do provider
acoplaria justamente o par com maior diferença de latência: um valor generoso para o plano não
protege o foco semanal, e um valor apertado para o foco mata o plano. Por isso cada rota ganha
`ChatModel` próprio com timeout distinto, configurado em `app.llm.routing.*.timeout` — ao lado de
`model`/`temperature`/`maxTokens`, que já eram por rota.

**Decisão 2 — timeout não é retentado.** Timeout e 5xx/429 costumam ser agrupados como "falhas
transitórias", mas têm custo oposto: 5xx e 429 falham rápido e barato, então retentar é quase de
graça; timeout falha *devagar por definição*, e retentar paga o pior caso duas vezes exatamente
quando o sistema já está sob pressão. Retry de 5xx/429 permanece; timeout vai direto para o
fallback da rota.

**Decisão 3 — sem circuit breaker por ora.** O valor de um CB é não pagar o timeout repetidamente, e
o único ponto do sistema que repete chamadas em rajada é o processamento em lote de planos. Os
caminhos interativos são pontuais — o coach clica uma vez e recebe 503; a decisão de tentar de novo
é dele. Keycloak e Strava não têm volume que justifique, e o Strava está em descontinuação
(ver ADR-0003). O comportamento útil de um CB no lote foi obtido com um corte após N falhas
consecutivas: poucas linhas, sem dependência nova, e sem thresholds que — mal calibrados — mascarem
erro real.

**Gatilho de reavaliação do circuit breaker:** quando existir mais de um caminho repetindo chamadas
em rajada, ou volume de tenants que torne o desperdício de timeouts repetidos relevante. Até lá,
adotar Resilience4j seria pagar dependência nova, instâncias nomeadas e calibragem de threshold em
três integrações para resolver um problema que existe em uma.

**Consequência aceita:** com uma dependência externa fora do ar, cada caminho interativo ainda paga
o timeout completo da sua rota antes de falhar (até 120s na geração de plano). É desperdício
consciente — o custo é do usuário que pediu aquela operação, não do sistema, e o pool de conexões
deixa de ser drenado porque a posse passou de ilimitada a limitada. O acoplamento entre transação e
chamada externa **não** foi resolvido aqui: o timeout limita o tempo de posse da conexão, mas a
transação continua aberta durante a chamada ao LLM. Isso fica para change própria.
