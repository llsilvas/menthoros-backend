# Bake-off OpenAI — sucessor do gpt-4o

Juiz: `gpt-4o` (o mesmo para todos os candidatos)

Casos por candidato: 5

| candidato | modelo servido | violações qualidade | violações compliance | juiz (média) | latência média (s) | tokens entrada/plano | tokens saída/plano | custo/plano (USD) | custo do juiz (USD) | falhas |
|---|---|---|---|---|---|---|---|---|---|---|
| gpt-4o | gpt-4o-2024-08-06 | 8 | 0 | 4.20 | 5.7 | 11499 | 1134 | 0.040088 | 0.0381425000 | 0 |
| gpt-6-luna:none | gpt-6-luna | 6 | 0 | 4.60 | 9.2 | 11493 | 1160 | 0.001730 | 0.0391650000 | 0 |
| gpt-6-luna:low | gpt-6-luna | 3 | 2 | 4.60 | 16.4 | 11493 | 1947 | 0.002123 | 0.0432000000 | 0 |
| gpt-6-luna:medium | gpt-6-luna | 1 | 2 | 4.80 | 30.6 | 11493 | 3507 | 0.002903 | 0.0421750000 | 0 |

## Violações por caso

### gpt-4o

- qualidade — avancado-tsb-baixo: ViolacaoQualidade[key=PACE_TETO, mensagem=Treino LONGO em SABADO com pace 5.5833 mais rápido que o teto 5.6350]
- qualidade — avancado-tsb-baixo: ViolacaoQualidade[key=PACE_TETO, mensagem=Etapa de LONGO em SABADO com pace 5.5833 mais rápido que o teto 5.6350]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=PACE_TETO, mensagem=Treino CONTINUO em DOMINGO com pace 4.1667 mais rápido que o teto 5.7984]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=PACE_TETO, mensagem=Etapa de CONTINUO em DOMINGO com pace 4.1667 mais rápido que o teto 5.7984]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=DIAS_PERMITIDOS, mensagem=Treino em DOMINGO fora dos dias permitidos [TERCA, QUINTA, SABADO]]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=DIAS_PERMITIDOS, mensagem=Treino em SEGUNDA fora dos dias permitidos [TERCA, QUINTA, SABADO]]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=DIAS_PERMITIDOS, mensagem=Treino em QUARTA fora dos dias permitidos [TERCA, QUINTA, SABADO]]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=DIAS_PERMITIDOS, mensagem=Treino em SEXTA fora dos dias permitidos [TERCA, QUINTA, SABADO]]

### gpt-6-luna:none

- qualidade — iniciante-sem-lesao: ViolacaoQualidade[key=PACE_TETO, mensagem=Treino REGENERATIVO em SEGUNDA com pace 6.6167 mais rápido que o teto 6.8600]
- qualidade — iniciante-sem-lesao: ViolacaoQualidade[key=PACE_TETO, mensagem=Etapa de REGENERATIVO em SEGUNDA com pace 6.6167 mais rápido que o teto 6.8600]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=PACE_TETO, mensagem=Treino CONTINUO em DOMINGO com pace 4.1667 mais rápido que o teto 5.7984]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=PACE_TETO, mensagem=Etapa de CONTINUO em DOMINGO com pace 4.1667 mais rápido que o teto 5.7984]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=PACE_TETO, mensagem=Etapa de CONTINUO em DOMINGO com pace 4.1667 mais rápido que o teto 5.7984]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=DIAS_PERMITIDOS, mensagem=Treino em DOMINGO fora dos dias permitidos [TERCA, QUINTA, SABADO]]

### gpt-6-luna:low

- qualidade — iniciante-sem-lesao: ViolacaoQualidade[key=PACE_TETO, mensagem=Treino REGENERATIVO em SEGUNDA com pace 6.6167 mais rápido que o teto 6.8600]
- qualidade — iniciante-sem-lesao: ViolacaoQualidade[key=PACE_TETO, mensagem=Etapa de REGENERATIVO em SEGUNDA com pace 6.6167 mais rápido que o teto 6.8600]
- qualidade — iniciante-sem-lesao: ViolacaoQualidade[key=PACE_TETO, mensagem=Etapa de REGENERATIVO em SEGUNDA com pace 6.6167 mais rápido que o teto 6.8600]
- compliance — taper-semana-prova: PlannerViolation[key=CONSTRAINT_DURA_VIOLADA, mensagem=Sessao em 2026-01-20 (TUESDAY) fora dos dias disponiveis do atleta]
- compliance — taper-semana-prova: PlannerViolation[key=CONSTRAINT_DURA_VIOLADA, mensagem=Sessao em 2026-01-22 (THURSDAY) fora dos dias disponiveis do atleta]

### gpt-6-luna:medium

- compliance — taper-semana-prova: PlannerViolation[key=CONSTRAINT_DURA_VIOLADA, mensagem=Sessao em 2026-01-20 (TUESDAY) fora dos dias disponiveis do atleta]
- compliance — taper-semana-prova: PlannerViolation[key=CONSTRAINT_DURA_VIOLADA, mensagem=Sessao em 2026-01-22 (THURSDAY) fora dos dias disponiveis do atleta]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=DIAS_PERMITIDOS, mensagem=Treino em DOMINGO fora dos dias permitidos [TERCA, QUINTA, SABADO]]
