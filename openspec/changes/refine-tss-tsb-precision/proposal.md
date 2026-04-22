## Why

O cálculo de TSS/TSB do Menthoros já cobre os cenários principais após os fixes de alta prioridade (ISSUE-01 a 06, resolvidas em código). Restam quatro melhorias de precisão que, embora individualmente pequenas, afetam a qualidade do sinal em cenários reais importantes: provas com descida líquida, primeira semana de atleta novo, treinos intervalados e diferenciação por nível de experiência.

Essas melhorias são candidatas naturais a serem agrupadas em um único change porque compartilham o mesmo motor de cálculo (`TssCalculatorService`, `TsbServiceImpl`, `FaixaTsb`, `MetricasThresholds`), os mesmos atletas beneficiados (todos) e o mesmo tipo de validação (comparação de TSS/TSB antes/depois com treinos históricos). Tratar como um change único evita múltiplos ciclos de regressão sobre o mesmo cálculo sensível.

As quatro melhorias estão documentadas historicamente em `docs/issues/ISSUE-07` a `ISSUE-10`, agora promovidas para esta spec.

## What Changes

- **Fator de elevação considera descida** (ISSUE-07): `calcularFatorElevacao()` passa a contabilizar `elevacaoPerdaMetros` com peso ~60% do equivalente de subida, refletindo custo muscular excêntrico e impacto articular.
- **Ramp Rate com fallback para primeira semana** (ISSUE-08): `calcularRampRate()` ganha estratégia de fallback quando não há registro exato de 7 dias atrás — interpola a partir do registro mais próximo (janela 5–9 dias) ou estima a partir do primeiro registro do atleta (janela 14 dias).
- **TSS calculado por etapa quando disponível** (ISSUE-09): novo caminho `calcularTssPorEtapas()` usado quando `TreinoRealizado` tem `EtapaRealizada` populadas. Corrige subestimação de TSS em treinos intervalados (desigualdade de Jensen).
- **Thresholds de TSB por nível de experiência** (ISSUE-10): `FaixaTsb.classificar()` ganha overload recebendo `NivelExperiencia`; thresholds base são escalados por fator (iniciante: 1.3x / intermediário: 1.1x / avançado: 1.0x / elite: 0.75x).
- **Piso de pace para IF saturável** (BACKLOG P2-A): `calcularIfPorPace()` passa a aplicar teto (`IF_TETO`) e piso (`IF_PISO_POR_ZONA`) evitando subestimação de TSS em sessões de qualidade (ex: 400m em Z5 com limiar em Z3).
- **Triângulo pace/distância/duração** (BACKLOG P2-B): novo `TreinoConsistenciaValidator` verifica/deriva o trio antes do cálculo de TSS, corrigindo inconsistências silenciosas de ingestão (apps externos, Strava, Garmin).

## Capabilities

### New Capabilities

- `tss-tsb-precision`: refinamentos de precisão no cálculo de TSS/TSB que cobrem elevação bidirecional, bootstrap de ramp rate, granularidade por etapa e personalização de thresholds por nível de experiência.

### Modified Capabilities

<!-- Nenhuma capability existente tem requisitos alterados por este change — as melhorias são aditivas ao motor de cálculo atual. -->

## Impact

**Entidades e banco:**
- Nenhuma alteração de schema. Todas as melhorias operam sobre dados já existentes.

**APIs:**
- Nenhuma alteração de endpoint. Valores de TSS/TSB calculados podem mudar para dados históricos; endpoints de recálculo (`GET /atleta/{id}/recalcular-metricas`) aplicam a nova lógica.

**Repositórios:**
- `MetricasDiariasRepository`: nova query `findTopByAtletaIdAndDataBetweenOrderByDataDesc` e `findTopByAtletaIdAndDataBeforeOrderByDataDesc` (ISSUE-08).
- `TreinoRealizadoRepository`: avaliar `@EntityGraph` ou `JOIN FETCH` para carregar `etapasRealizadas` sem N+1 (ISSUE-09).

**Sinais e alertas:**
- Atletas iniciantes podem começar a receber alertas de ramp rate na primeira semana que antes eram suprimidos (ISSUE-08) — comportamento intencional.
- Classificação `FaixaTsb` pode mudar retroativamente por atleta após deploy (ISSUE-10) — mitigação via logs comparativos durante rollout.

**Compatibilidade:**
- `FaixaTsb.classificar(Double tsb)` mantido como overload de retrocompatibilidade, delegando para `classificar(tsb, NivelExperiencia.AVANCADO)`.
- Treinos sem `EtapaRealizada` continuam usando cálculo pela média geral (fallback preservado).

**Dependências:**
- Este change deve ser executado **depois** de `fix-tsb-semantics`, `add-continuous-daily-load-management` e `progressao-treinos` (Onda 2 do ROADMAP). Caso contrário, a base de cálculo ainda estará mudando e a regressão destas melhorias será desperdiçada.

## Referências científicas

- Vernillo, G. et al. (2017) — "Biomechanics and Physiology of Uphill and Downhill Running" (Sports Medicine) — custo muscular da descida.
- Gottschall, J.S. & Kram, R. (2005) — forças de impacto em descida vs plano.
- Coggan, A. — definição original de TSS/IF, NP e propriedades convexas de IF².
- Meeusen, R. et al. (2013) — "Prevention, Diagnosis, and Treatment of the Overtraining Syndrome" (EJSS) — diferença de tolerância à fadiga por nível.
