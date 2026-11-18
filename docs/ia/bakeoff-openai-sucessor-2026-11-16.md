# Bake-off OpenAI — sucessor do gpt-4o

Juiz: `gpt-5.2` (o mesmo para todos os candidatos)

Casos por candidato: 5

| candidato | modelo servido | violações qualidade | violações compliance | juiz (média) | latência média (s) | tokens entrada/plano | tokens saída/plano | custo/plano (USD) | custo do juiz (USD) | falhas |
|---|---|---|---|---|---|---|---|---|---|---|
| gpt-4o | gpt-4o-2024-08-06 | 18 | 0 | 3.00 | 8.3 | 11499 | 1247 | 0.041218 | 0.0624032500 | 0 |
| gpt-6-luna:medium | gpt-6-luna | 0 | 2 | 3.00 | 34.6 | 11493 | 3633 | 0.002966 | 0.0665192500 | 0 |

## Violações por caso

### gpt-4o

- qualidade — avancado-tsb-baixo: ViolacaoQualidade[key=PACE_TETO, mensagem=Treino CONTINUO em QUARTA com pace 5.3833 mais rápido que o teto 5.3900]
- qualidade — avancado-tsb-baixo: ViolacaoQualidade[key=PACE_TETO, mensagem=Etapa de CONTINUO em QUARTA com pace 5.3833 mais rápido que o teto 5.3900]
- qualidade — avancado-tsb-baixo: ViolacaoQualidade[key=PACE_TETO, mensagem=Treino LONGO em SABADO com pace 5.6333 mais rápido que o teto 5.6350]
- qualidade — avancado-tsb-baixo: ViolacaoQualidade[key=PACE_TETO, mensagem=Etapa de LONGO em SABADO com pace 5.6333 mais rápido que o teto 5.6350]
- qualidade — com-lesao-ativa: ViolacaoQualidade[key=PACE_TETO, mensagem=Treino CONTINUO em QUARTA com pace 5.6000 mais rápido que o teto 5.7984]
- qualidade — com-lesao-ativa: ViolacaoQualidade[key=PACE_TETO, mensagem=Etapa de CONTINUO em QUARTA com pace 5.6000 mais rápido que o teto 5.7984]
- qualidade — com-lesao-ativa: ViolacaoQualidade[key=PACE_TETO, mensagem=Etapa de CONTINUO em QUARTA com pace 5.6000 mais rápido que o teto 5.7984]
- qualidade — com-lesao-ativa: ViolacaoQualidade[key=PACE_TETO, mensagem=Etapa de CONTINUO em QUARTA com pace 5.6000 mais rápido que o teto 5.7984]
- qualidade — com-lesao-ativa: ViolacaoQualidade[key=PACE_TETO, mensagem=Treino CONTINUO em SEXTA com pace 5.6000 mais rápido que o teto 5.7984]
- qualidade — com-lesao-ativa: ViolacaoQualidade[key=PACE_TETO, mensagem=Etapa de CONTINUO em SEXTA com pace 5.6000 mais rápido que o teto 5.7984]
- qualidade — com-lesao-ativa: ViolacaoQualidade[key=PACE_TETO, mensagem=Etapa de CONTINUO em SEXTA com pace 5.6000 mais rápido que o teto 5.7984]
- qualidade — com-lesao-ativa: ViolacaoQualidade[key=PACE_TETO, mensagem=Etapa de CONTINUO em SEXTA com pace 5.6000 mais rápido que o teto 5.7984]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=PACE_TETO, mensagem=Treino LONGO em DOMINGO com pace 4.1667 mais rápido que o teto 5.7166]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=PACE_TETO, mensagem=Etapa de LONGO em DOMINGO com pace 4.1667 mais rápido que o teto 5.7166]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=DIAS_PERMITIDOS, mensagem=Treino em DOMINGO fora dos dias permitidos [TERCA, QUINTA, SABADO]]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=DIAS_PERMITIDOS, mensagem=Treino em SEGUNDA fora dos dias permitidos [TERCA, QUINTA, SABADO]]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=DIAS_PERMITIDOS, mensagem=Treino em QUARTA fora dos dias permitidos [TERCA, QUINTA, SABADO]]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=DIAS_PERMITIDOS, mensagem=Treino em SEXTA fora dos dias permitidos [TERCA, QUINTA, SABADO]]

### gpt-6-luna:medium

- compliance — taper-semana-prova: PlannerViolation[key=CONSTRAINT_DURA_VIOLADA, mensagem=Sessao em 2026-01-20 (TUESDAY) fora dos dias disponiveis do atleta]
- compliance — taper-semana-prova: PlannerViolation[key=CONSTRAINT_DURA_VIOLADA, mensagem=Sessao em 2026-01-22 (THURSDAY) fora dos dias disponiveis do atleta]

## Smoke da rota simple — bloco do atleta (task 3.4, 2026-11-16)

3 chamadas do prompt do `AthleteMessageGenerator` (skill `athlete-workout-motivation`), gpt-4o-mini × gpt-6-luna none. Aproximação: sem as instruções de formato que o Spring AI acrescenta.

| caso | gpt-4o-mini | gpt-6-luna none |
|---|---|---|
| 1 NORMAL | 2,7 s | 2,0 s |
| 2 ACCUMULATED_FATIGUE | 3,0 s — afirma ritmo "mais lento que o esperado" (era mais rápido) | 2,2 s — só fatos |
| 3 ACCUMULATED_FATIGUE | 2,7 s — cita ritmo que o caso não traz | 2,0 s — só fatos |

Os dois mandam o atleta falar com o coach no caso de fadiga. O bloco vai direto ao atleta, filtrado por `AthleteMessageValidator` e `AthleteMessageClassifier` (decisão de 2026-08-29, `analise-ia-treino-atleta`), sem ação do coach.
