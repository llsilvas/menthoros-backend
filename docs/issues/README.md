# Issues — Calculos TSS/TSB e Alertas de Treino

Analise especializada dos calculos de Training Stress Score (TSS), Training Stress Balance (TSB) e sistema de alertas para monitoramento de atletas de corrida de rua.

**Data da analise:** 2026-02-16
**Arquivos analisados:**
- `services/impl/TsbServiceImpl.java` — Calculo de CTL/ATL/TSB (media movel exponencial)
- `services/helper/TssCalculatorService.java` — Calculo de TSS por FC/Pace/RPE
- `services/impl/MetricasAlertaService.java` — Geracao de alertas e status
- `enums/FaixaTsb.java` — Classificacao de TSB por faixas
- `enums/MetricasThresholds.java` — Constantes de threshold

---

## Resumo

| # | Issue | Severidade | Tipo | Status |
|:---:|---|:---:|---|:---:|
| [01](ISSUE-01-bug-status-fadiga-critica-rebaixado.md) | Status "FADIGA CRITICA" rebaixado para "FADIGA ALTA" | **ALTA** | Bug | RESOLVIDA |
| [02](ISSUE-02-bug-mapeamento-rpe-if-subestimado.md) | Mapeamento RPE->IF subestima intensidade em ~30-40% | **ALTA** | Bug | RESOLVIDA |
| [03](ISSUE-03-bug-interpretacao-duplicada-faixa-tsb.md) | Interpretacao duplicada entre FADIGA_ALTA e FADIGA_EXCESSIVA | **MEDIA** | Bug | RESOLVIDA |
| [04](ISSUE-04-inconsistencia-fator-impacto-dupla-contagem-fc.md) | Fator de impacto causa dupla contagem com TSS por FC | **MEDIA** | Inconsistencia | RESOLVIDA |
| [05](ISSUE-05-inconsistencia-ramp-rate-thresholds-absolutos.md) | Ramp Rate usa thresholds absolutos (deveria ser relativo ao CTL) | **MEDIA** | Inconsistencia | RESOLVIDA |
| [06](ISSUE-06-inconsistencia-dias-consecutivos-defasado.md) | `diasConsecutivosTreino` defasado durante analise de alertas | **MEDIA** | Inconsistencia | RESOLVIDA |
| [07](ISSUE-07-melhoria-elevacao-descida-ignorada-tss.md) | Elevacao de descida ignorada no fator de elevacao | **BAIXA** | Melhoria | Pendente |
| [08](ISSUE-08-melhoria-ramp-rate-zero-primeira-semana.md) | Ramp Rate retorna zero na primeira semana do atleta | **BAIXA** | Melhoria | Pendente |
| [09](ISSUE-09-melhoria-tss-por-etapas-nao-utilizado.md) | TSS por etapas (EtapaRealizada) nao utilizado | **BAIXA** | Melhoria | Pendente |
| [10](ISSUE-10-melhoria-tsb-thresholds-por-nivel-atleta.md) | TSB thresholds nao consideram nivel de experiencia do atleta | **BAIXA** | Melhoria | Pendente |

---

## Issues Resolvidas — Code Review

### ISSUE-01 — RESOLVIDA
**Fix:** `MetricasAlertaService.calcularStatus()` L107-131 — boolean `tsbCritico` diferencia FADIGA CRITICA de FADIGA ALTA em todos os branches.
**Testes:** 5 testes em `MetricasAlertaServiceTest.java` (TSB critico, TSB alto, TSB moderado, compostos com ramp rate).

### ISSUE-02 — RESOLVIDA
**Fix:** `TssCalculatorService.converterRpeParaIf()` L287-293 — mapeamento piecewise-linear com RPE 8 = IF 1.0 (limiar).
**Testes:** 3 testes em `TssCalculatorServiceRpeMappingTest.java` (RPE 5/8/10).

### ISSUE-03 — RESOLVIDA
**Fix:** `FaixaTsb.FADIGA_ALTA` L31 — interpretacao alterada de "Fadiga excessiva" para "Fadiga alta".
**Testes:** 1 teste em `FaixaTsbInterpretacaoTest.java`.

### ISSUE-04 — RESOLVIDA
**Fix:** `TssCalculatorService.aplicarFatorImpactoTreino()` L82-95 — atenuacao de 50% do componente extra para calculo por FC.
**Testes:** 5 testes em `TssCalculatorServiceImpactFactorTest.java` (FC atenuado, Pace e RPE mantidos).

### ISSUE-05 — RESOLVIDA
**Fix:** `MetricasThresholds` L48-63 (constantes relativas) + `MetricasAlertaService.RampRateInfo` L28-48 (calculo de percentual) + thresholds relativos L67-72.
**Testes:** 2 testes em `MetricasAlertaServiceRampRateRelativoTest.java` + 2 testes em `TsbServiceImplRampRateTest.java`.

### ISSUE-06 — RESOLVIDA
**Fix:** `TsbServiceImpl.contarDiasConsecutivosTreino()` + chamada em `atualizarMetaDados()` antes de `analisarMetricas()`.
**Testes:** 7 testes em `TsbServiceImplDiasConsecutivosTest.java` (0 dias, 1 dia, 2 dias, 5 dias, 6 dias/critico, gap interrompe, limite 14).

---

## Lacunas de Cobertura de Testes (a cobrir futuramente)

### ISSUE-01 — MetricasAlertaServiceTest
- [ ] Cenario `tsbCritico` + `diasConsecutivos >= DIAS_CONSECUTIVOS_CRITICO` (L119-123): testar que TSB < -35 com 6+ dias consecutivos retorna "FADIGA CRITICA" (prioridade da fadiga critica sobre dias consecutivos)
- [ ] Cenario `diasConsecutivos >= CRITICO` sem sobrecarga (L127): testar que retorna "MUITOS DIAS CONSECUTIVOS"
- [ ] Cenario status "COLETANDO DADOS" (L103-105): testar com TSB e CTL ambos null
- [ ] Cenario "FORMA IDEAL" (L135-136): testar que TSB entre 5 e 15 sem alertas compostos retorna "FORMA IDEAL"

### ISSUE-02 — TssCalculatorServiceRpeMappingTest
- [ ] RPE 1 (extremo baixo): validar que IF = 0.45, TSS/h ~20
- [ ] RPE 3 (leve): validar que IF = 0.60, TSS/h ~36
- [ ] RPE 7 (forte/sublimiar): validar que IF = 0.93, TSS/h ~86
- [ ] RPE 9 (VO2max): validar que IF = 1.125, TSS/h ~127
- [ ] Treino sem RPE e sem FC/Pace: validar que retorna TSS = 0

### ISSUE-04 — TssCalculatorServiceImpactFactorTest
- [ ] FC + REGENERATIVO (fator 0.85 < 1.0): validar que nao sofre atenuacao (fator 0.85 aplicado cheio)
- [ ] FC + SUBIDA (fator 1.6, maior do projeto): validar atenuacao correta (1.0 + 0.6*0.5 = 1.3)
- [ ] FC + FACIL (fator 1.0): validar que tssBase == tssAjustado (fator neutro)
- [ ] Treino sem tipo definido: validar que retorna tssBase sem ajuste

### ISSUE-05 — MetricasAlertaServiceRampRateRelativoTest / TsbServiceImplRampRateTest
- [ ] Fallback para absoluto quando CTL e null: validar que rampRate > 10 pts aciona alerta mesmo sem CTL
- [ ] CTL exatamente no minimo (`CTL_MINIMO_RAMP_RELATIVO = 10`): validar calculo com denominador = 10
- [ ] Ramp rate negativo (atleta descansando, CTL caindo): validar que nao emite alerta
- [ ] `RampRateInfo.formatarResumo()`: validar formato "X%/sem (Y pts)" e fallback "X pts/sem"

### ISSUE-06 — TsbServiceImplDiasConsecutivosTest
- [ ] Integracao com `atualizarMetaDados()`: validar que `metaDados.getDiasConsecutivosTreino()` e atualizado antes de `analisarMetricas()` ser chamado (teste de integracao com mocks)
- [ ] Dia de descanso (TSS=0, sem treinos): validar que o contador reseta para 0

---

## Issues Pendentes (Melhorias Futuras)

### Fase 3 — Melhorias de Precisao
7. **ISSUE-07** — Fator de elevacao com descida (1 arquivo, ~25 linhas)
8. **ISSUE-08** — Ramp Rate com fallback para primeira semana (2 arquivos, ~20 linhas)

### Fase 4 — Evolucoes Arquiteturais
9. **ISSUE-09** — TSS por etapas (2 arquivos, lazy loading)
10. **ISSUE-10** — TSB thresholds por nivel de atleta (4 arquivos, breaking change interna)

---

## Referencias Cientificas

- **Banister, E.W. et al. (1975)** — Modelo original de impulso-resposta para CTL/ATL/TSB
- **Coggan, A.** — Definicao de IF, TSS e NP (TrainingPeaks/WKO)
- **Gabbett, T.J. (2016)** — "The training—injury prevention paradox" (BJSM) — ACWR
- **Minetti, A.E. et al. (2002)** — "Energy cost of walking and running at extreme gradients" (JAP)
- **Vernillo, G. et al. (2017)** — "Biomechanics and Physiology of Uphill and Downhill Running" (Sports Medicine)
- **Meeusen, R. et al. (2013)** — "Prevention, Diagnosis, and Treatment of the Overtraining Syndrome" (EJSS)
- **Borg, G. (1998)** — Escala CR-10 de percepcao de esforco
