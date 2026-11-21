# Bake-off OpenAI — sucessor do gpt-4o

Juiz: `gpt-4o` (o mesmo para todos os candidatos)

Casos por candidato: 5

| candidato | modelo servido | violações qualidade | violações compliance | juiz (média) | latência média (s) | custo total (USD) | falhas |
|---|---|---|---|---|---|---|---|
| gpt-4o | gpt-4o-2024-08-06 | 10 | 0 | 4.20 | 6.9 | 0.2438675000 | 0 |
| gpt-6-luna:medium | gpt-6-luna | 0 | 2 | 4.60 | 32.4 | 0.0556101000 | 0 |

## Violações por caso

### gpt-4o

- qualidade — iniciante-sem-lesao: ViolacaoQualidade[key=PACE_TETO, mensagem=Treino REGENERATIVO em SEGUNDA com pace 6.8333 mais rápido que o teto 6.8600]
- qualidade — iniciante-sem-lesao: ViolacaoQualidade[key=PACE_TETO, mensagem=Etapa de REGENERATIVO em SEGUNDA com pace 6.8333 mais rápido que o teto 6.8600]
- qualidade — avancado-tsb-baixo: ViolacaoQualidade[key=PACE_TETO, mensagem=Treino LONGO em SABADO com pace 5.6333 mais rápido que o teto 5.6350]
- qualidade — avancado-tsb-baixo: ViolacaoQualidade[key=PACE_TETO, mensagem=Etapa de LONGO em SABADO com pace 5.6333 mais rápido que o teto 5.6350]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=PACE_TETO, mensagem=Treino CONTINUO em DOMINGO com pace 4.1667 mais rápido que o teto 5.7984]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=PACE_TETO, mensagem=Etapa de CONTINUO em DOMINGO com pace 4.1667 mais rápido que o teto 5.7984]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=DIAS_PERMITIDOS, mensagem=Treino em SEGUNDA fora dos dias permitidos [TERCA, QUINTA, SABADO]]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=DIAS_PERMITIDOS, mensagem=Treino em QUARTA fora dos dias permitidos [TERCA, QUINTA, SABADO]]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=DIAS_PERMITIDOS, mensagem=Treino em SEXTA fora dos dias permitidos [TERCA, QUINTA, SABADO]]
- qualidade — taper-semana-prova: ViolacaoQualidade[key=DIAS_PERMITIDOS, mensagem=Treino em DOMINGO fora dos dias permitidos [TERCA, QUINTA, SABADO]]

### gpt-6-luna:medium

- compliance — taper-semana-prova: PlannerViolation[key=CONSTRAINT_DURA_VIOLADA, mensagem=Sessao em 2026-01-20 (TUESDAY) fora dos dias disponiveis do atleta]
- compliance — taper-semana-prova: PlannerViolation[key=CONSTRAINT_DURA_VIOLADA, mensagem=Sessao em 2026-01-22 (THURSDAY) fora dos dias disponiveis do atleta]
