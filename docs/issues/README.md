# Issues — Cálculos TSS/TSB e Alertas de Treino

Análise especializada dos cálculos de Training Stress Score (TSS), Training Stress Balance (TSB) e sistema de alertas para monitoramento de atletas de corrida de rua.

**Data da análise original:** 2026-02-16
**Última reorganização:** 2026-04-22

**Arquivos analisados:**
- `services/impl/TsbServiceImpl.java` — Cálculo de CTL/ATL/TSB (média móvel exponencial)
- `services/helper/TssCalculatorService.java` — Cálculo de TSS por FC/Pace/RPE
- `services/impl/MetricasAlertaService.java` — Geração de alertas e status
- `enums/FaixaTsb.java` — Classificação de TSB por faixas
- `enums/MetricasThresholds.java` — Constantes de threshold

---

## Status atual da pasta

Esta pasta foi reorganizada em 2026-04-22 após a criação do `docs/ROADMAP.md`:

- **Issues resolvidas (01 a 06):** movidas para `resolved/` como registro histórico / pós-mortem. O fix está em código + testes; o .md é mantido como contexto para onboarding e lacunas de cobertura de testes a endereçar futuramente.
- **Issues pendentes (07 a 10):** promovidas para o change `openspec/changes/refine-tss-tsb-precision/`. A fonte canônica de especificação passou a ser openspec. Ver ROADMAP, Onda 5.

Novas issues de cálculo de TSS/TSB/alertas devem seguir o mesmo padrão: se é bug pontual que pode ser corrigido direto, abrir PR; se justifica spec, criar change em `openspec/changes/`.

---

## Issues resolvidas (resolved/)

| # | Issue | Severidade | Tipo |
|:---:|---|:---:|---|
| [01](resolved/ISSUE-01-bug-status-fadiga-critica-rebaixado.md) | Status "FADIGA CRITICA" rebaixado para "FADIGA ALTA" | ALTA | Bug |
| [02](resolved/ISSUE-02-bug-mapeamento-rpe-if-subestimado.md) | Mapeamento RPE→IF subestima intensidade em ~30-40% | ALTA | Bug |
| [03](resolved/ISSUE-03-bug-interpretacao-duplicada-faixa-tsb.md) | Interpretação duplicada entre FADIGA_ALTA e FADIGA_EXCESSIVA | MÉDIA | Bug |
| [04](resolved/ISSUE-04-inconsistencia-fator-impacto-dupla-contagem-fc.md) | Fator de impacto causa dupla contagem com TSS por FC | MÉDIA | Inconsistência |
| [05](resolved/ISSUE-05-inconsistencia-ramp-rate-thresholds-absolutos.md) | Ramp Rate usa thresholds absolutos (deveria ser relativo ao CTL) | MÉDIA | Inconsistência |
| [06](resolved/ISSUE-06-inconsistencia-dias-consecutivos-defasado.md) | `diasConsecutivosTreino` defasado durante análise de alertas | MÉDIA | Inconsistência |

### Resumo dos fixes

- **ISSUE-01:** `MetricasAlertaService.calcularStatus()` L107-131 — boolean `tsbCritico` diferencia FADIGA CRÍTICA de FADIGA ALTA em todos os branches. Testes: `MetricasAlertaServiceTest.java` (5 casos).
- **ISSUE-02:** `TssCalculatorService.converterRpeParaIf()` L287-293 — mapeamento piecewise-linear com RPE 8 = IF 1.0 (limiar). Testes: `TssCalculatorServiceRpeMappingTest.java` (3 casos).
- **ISSUE-03:** `FaixaTsb.FADIGA_ALTA` L31 — interpretação alterada de "Fadiga excessiva" para "Fadiga alta". Testes: `FaixaTsbInterpretacaoTest.java` (1 caso).
- **ISSUE-04:** `TssCalculatorService.aplicarFatorImpactoTreino()` L82-95 — atenuação de 50% do componente extra para cálculo por FC. Testes: `TssCalculatorServiceImpactFactorTest.java` (5 casos).
- **ISSUE-05:** `MetricasThresholds` L48-63 + `MetricasAlertaService.RampRateInfo` L28-48 + thresholds relativos L67-72. Testes: `MetricasAlertaServiceRampRateRelativoTest.java` (2 casos) + `TsbServiceImplRampRateTest.java` (2 casos).
- **ISSUE-06:** `TsbServiceImpl.contarDiasConsecutivosTreino()` + chamada em `atualizarMetaDados()` antes de `analisarMetricas()`. Testes: `TsbServiceImplDiasConsecutivosTest.java` (7 casos).

---

## Lacunas de cobertura de testes (a cobrir futuramente)

### ISSUE-01 — MetricasAlertaServiceTest
- [ ] Cenário `tsbCritico` + `diasConsecutivos >= DIAS_CONSECUTIVOS_CRITICO` (L119-123): TSB < -35 com 6+ dias consecutivos deve retornar "FADIGA CRITICA" (prioridade da fadiga crítica sobre dias consecutivos)
- [ ] Cenário `diasConsecutivos >= CRITICO` sem sobrecarga (L127): deve retornar "MUITOS DIAS CONSECUTIVOS"
- [ ] Cenário status "COLETANDO DADOS" (L103-105): TSB e CTL ambos null
- [ ] Cenário "FORMA IDEAL" (L135-136): TSB entre 5 e 15 sem alertas compostos

### ISSUE-02 — TssCalculatorServiceRpeMappingTest
- [ ] RPE 1 (extremo baixo): IF = 0.45, TSS/h ~20
- [ ] RPE 3 (leve): IF = 0.60, TSS/h ~36
- [ ] RPE 7 (forte/sublimiar): IF = 0.93, TSS/h ~86
- [ ] RPE 9 (VO2max): IF = 1.125, TSS/h ~127
- [ ] Treino sem RPE e sem FC/Pace: TSS = 0

### ISSUE-04 — TssCalculatorServiceImpactFactorTest
- [ ] FC + REGENERATIVO (fator 0.85 < 1.0): não sofre atenuação (fator 0.85 aplicado cheio)
- [ ] FC + SUBIDA (fator 1.6, maior do projeto): atenuação correta (1.0 + 0.6*0.5 = 1.3)
- [ ] FC + FÁCIL (fator 1.0): tssBase == tssAjustado (fator neutro)
- [ ] Treino sem tipo definido: retorna tssBase sem ajuste

### ISSUE-05 — MetricasAlertaServiceRampRateRelativoTest / TsbServiceImplRampRateTest
- [ ] Fallback para absoluto quando CTL é null: rampRate > 10 pts aciona alerta mesmo sem CTL
- [ ] CTL exatamente no mínimo (`CTL_MINIMO_RAMP_RELATIVO = 10`): cálculo com denominador = 10
- [ ] Ramp rate negativo (atleta descansando, CTL caindo): não emite alerta
- [ ] `RampRateInfo.formatarResumo()`: formato "X%/sem (Y pts)" e fallback "X pts/sem"

### ISSUE-06 — TsbServiceImplDiasConsecutivosTest
- [ ] Integração com `atualizarMetaDados()`: validar que `metaDados.getDiasConsecutivosTreino()` é atualizado antes de `analisarMetricas()` ser chamado (integração com mocks)
- [ ] Dia de descanso (TSS=0, sem treinos): contador reseta para 0

---

## Issues pendentes — agora em openspec

As quatro melhorias originalmente documentadas como ISSUE-07 a ISSUE-10 foram promovidas para:

- **`openspec/changes/refine-tss-tsb-precision/`**

Essa spec agrupa:
- ~~ISSUE-07~~ → Requirement: Fator de elevação contabiliza subida e descida
- ~~ISSUE-08~~ → Requirement: Ramp Rate com fallback para histórico parcial
- ~~ISSUE-09~~ → Requirement: TSS calculado por etapa quando disponível
- ~~ISSUE-10~~ → Requirement: Classificação de TSB ajustada por nível de experiência

Execução recomendada na Onda 5 do `docs/ROADMAP.md`, depois que `fix-tsb-semantics`, `add-continuous-daily-load-management` e `progressao-treinos` estiverem concluídas.

---

## Referências científicas

- **Banister, E.W. et al. (1975)** — Modelo original de impulso-resposta para CTL/ATL/TSB
- **Coggan, A.** — Definição de IF, TSS e NP (TrainingPeaks/WKO)
- **Gabbett, T.J. (2016)** — "The training–injury prevention paradox" (BJSM) — ACWR
- **Minetti, A.E. et al. (2002)** — "Energy cost of walking and running at extreme gradients" (JAP)
- **Vernillo, G. et al. (2017)** — "Biomechanics and Physiology of Uphill and Downhill Running" (Sports Medicine)
- **Meeusen, R. et al. (2013)** — "Prevention, Diagnosis, and Treatment of the Overtraining Syndrome" (EJSS)
- **Borg, G. (1998)** — Escala CR-10 de percepção de esforço
- **Gottschall, J.S. & Kram, R. (2005)** — Forças de impacto em descida vs plano
