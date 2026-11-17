# Revisão cega de planos — gpt-4o × sucessor (migrate-openai-retired-models, task 3.5)

Cada caso é um arquétipo do eval set com o contexto do atleta e dois planos, A e B, gerados pelo
mesmo prompt de produção e passados pela normalização de produção (o que o coach recebe). A ordem é
embaralhada por caso; a chave está no fim.

**Para cada caso, anote:** (1) qual você mandaria ao atleta: A, B ou empate; (2) se algum dos dois
tem algo que você barraria (risco de lesão, carga errada, dia proibido, ritmo incoerente).

**Nota da rodada 2:** um treino foi reprovado por um gate estrutural da normalização (em produção
iria para o turno de reparo) e aparece como a LLM escreveu. A distância de cada treino contínuo é a
soma das etapas, exceto quando o total da LLM é menor que o aquecimento e o desaquecimento gerados
pelo sistema (aí a divergência é mostrada como veio).

**Critério (CA4b):** o sucessor não pode ser preferido em menos casos que o gpt-4o, e não pode ter
item que você barraria.


---

# Arquétipo: avancado-tsb-baixo

<details><summary>Contexto do atleta</summary>

```
Nível: AVANCADO
Treinos recentes: 5 sessões
```

</details>


## Caso 1 — avancado-tsb-baixo, repetição 1


### Plano A

**Objetivo da semana:** Manter 66 km sem progressão, consolidar base aeróbica para a maratona sub-3h e priorizar recuperação com TSB -20 e fadiga acumulada.  
**Volume planejado:** 66.0 km (alvo 66.0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | CONTINUO | 13.17 km | 75:00 | 5:30-5:45/km | 146-153 bpm | 4 | AQUECIMENTO 1.67 km 10 min @ 5:45-6:15/km FC 129-146 bpm<br>PRINCIPAL 10.67 km 60 min @ 5:30-5:45/km FC 146-153 bpm<br>DESAQUECIMENTO 0.83 km 5 min @ 5:45-6:15/km FC 129-146 bpm | — |
| QUARTA | CONTINUO | 14.4 km | 90:00 | 6:00-6:30/km | 146-153 bpm | 4 | AQUECIMENTO 1.6 km 10 min @ 6:00-6:30/km FC 129-146 bpm<br>PRINCIPAL 12.0 km 75 min @ 6:00-6:30/km FC 146-153 bpm<br>DESAQUECIMENTO 0.8 km 5 min @ 6:00-6:30/km FC 129-146 bpm | — |
| SEXTA | CONTINUO | 13.17 km | 75:00 | 5:30-5:45/km | 146-153 bpm | 4 | AQUECIMENTO 1.67 km 10 min @ 5:45-6:15/km FC 129-146 bpm<br>PRINCIPAL 10.67 km 60 min @ 5:30-5:45/km FC 146-153 bpm<br>DESAQUECIMENTO 0.83 km 5 min @ 5:45-6:15/km FC 129-146 bpm | — |
| SABADO | LONGO | 25.04 km | 148:00 | 5:45-6:00/km | 146-153 bpm | 5 | AQUECIMENTO 1.6 km 10 min @ 6:00-6:30/km FC 129-146 bpm<br>PRINCIPAL 22.64 km 133 min @ 5:45-6:00/km FC 146-153 bpm<br>DESAQUECIMENTO 0.8 km 5 min @ 6:00-6:30/km FC 129-146 bpm | — |

**Descanso:** DOMINGO (Descanso completo para quebrar a sequência recente de cinco dias de treino e iniciar a semana recuperado.), TERCA (Descanso completo entre as sessões de segunda e quarta, respeitando o limite de dias consecutivos.), QUINTA (Descanso completo para recuperação antes das sessões de sexta e do longo de sábado.)


### Plano B

**Objetivo da semana:** Manter volume atual e introduzir variabilidade com fartlek e tempo run.  
**Volume planejado:** 72.0 km (alvo 72.0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 11.76 km | 75:00 | 6:00-6:30/km | 146-153 bpm | 4 | AQUECIMENTO 1.48 km 10 min @ 6:30-7:00/km FC 129-146 bpm<br>PRINCIPAL 8.8 km 55 min @ 6:00-6:30/km FC 146-153 bpm<br>DESAQUECIMENTO 1.48 km 10 min @ 6:30-7:00/km FC 129-146 bpm | — |
| QUARTA | CONTINUO | 14.87 km | 90:00 | 5:45-6:00/km | 146-162 bpm | 5 | AQUECIMENTO 1.48 km 10 min @ 6:30-7:00/km FC 129-146 bpm<br>PRINCIPAL 11.91 km 70 min @ 5:45-6:00/km FC 146-162 bpm<br>DESAQUECIMENTO 1.48 km 10 min @ 6:30-7:00/km FC 129-146 bpm | — |
| SEXTA | REGENERATIVO | 11.76 km | 75:00 | 6:00-6:30/km | 146-153 bpm | 4 | AQUECIMENTO 1.48 km 10 min @ 6:30-7:00/km FC 129-146 bpm<br>PRINCIPAL 8.8 km 55 min @ 6:00-6:30/km FC 146-153 bpm<br>DESAQUECIMENTO 1.48 km 10 min @ 6:30-7:00/km FC 129-146 bpm | — |
| SABADO | LONGO | 24.53 km | 148:00 | 5:45-6:00/km | 146-153 bpm | 6 | AQUECIMENTO 2.22 km 15 min @ 6:30-7:00/km FC 129-146 bpm<br>PRINCIPAL 20.09 km 118 min @ 5:45-6:00/km FC 146-153 bpm<br>DESAQUECIMENTO 2.22 km 15 min @ 6:30-7:00/km FC 129-146 bpm | — |

**Descanso:** TERCA (Recuperação após 5 dias consecutivos de treino), QUINTA (Recuperação e preparação para treino longo no sábado)


**Sua avaliação:** preferido: ___ · barraria algo? ___


## Caso 2 — avancado-tsb-baixo, repetição 2


### Plano A

**Objetivo da semana:** Manter volume atual e introduzir estímulos ausentes, focando em recuperação.  
**Volume planejado:** 63.0 km (alvo 63.0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 11.82 km | 75:00 | 6:00-6:30/km | 146-153 bpm | 4 | AQUECIMENTO 1.48 km 10 min @ 6:30-7:00/km FC 129-146 bpm<br>PRINCIPAL 9.6 km 60 min @ 6:00-6:30/km FC 146-153 bpm<br>DESAQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 129-146 bpm | — |
| QUARTA | CONTINUO | 15.26 km | 90:00 | 5:30-6:00/km | 146-162 bpm | 5 | AQUECIMENTO 1.48 km 10 min @ 6:30-7:00/km FC 129-146 bpm<br>PRINCIPAL 13.04 km 75 min @ 5:30-6:00/km FC 146-162 bpm<br>DESAQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 129-146 bpm | — |
| SEXTA | REGENERATIVO | 11.82 km | 75:00 | 6:00-6:30/km | 146-153 bpm | 4 | AQUECIMENTO 1.48 km 10 min @ 6:30-7:00/km FC 129-146 bpm<br>PRINCIPAL 9.6 km 60 min @ 6:00-6:30/km FC 146-153 bpm<br>DESAQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 129-146 bpm | — |
| SABADO | LONGO | 24.58 km | 148:00 | 5:45-6:00/km | 146-153 bpm | 5 | AQUECIMENTO 2.22 km 15 min @ 6:30-7:00/km FC 129-146 bpm<br>PRINCIPAL 20.43 km 120 min @ 5:45-6:00/km FC 146-153 bpm<br>DESAQUECIMENTO 1.93 km 13 min @ 6:30-7:00/km FC 129-146 bpm | — |

**Descanso:** TERCA (Recuperação após 5 dias consecutivos de treino), QUINTA (Recuperação após treino contínuo)


### Plano B

**Objetivo da semana:** Manter a carga recente sem intensificar, priorizando recuperação e base aeróbica para a maratona sub-3h.  
**Volume planejado:** 65.6 km (alvo 66.0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | CONTINUO | 12.59 km | 75:00 | 5:55-6:00/km | 146-153 bpm | 3 | AQUECIMENTO 1.68 km 10 min @ 5:55-6:00/km FC 129-146 bpm<br>PRINCIPAL 10.07 km 60 min @ 5:55-6:00/km FC 146-153 bpm<br>DESAQUECIMENTO 0.84 km 5 min @ 5:55-6:00/km FC 129-146 bpm | — |
| QUARTA | CONTINUO | 15.11 km | 90:00 | 5:55-6:00/km | 146-162 bpm | 4 | AQUECIMENTO 1.68 km 10 min @ 5:55-6:00/km FC 129-146 bpm<br>PRINCIPAL 12.59 km 75 min @ 5:55-6:00/km FC 146-153 bpm<br>DESAQUECIMENTO 0.84 km 5 min @ 5:55-6:00/km FC 129-146 bpm | — |
| SEXTA | CONTINUO | 12.59 km | 75:00 | 5:55-6:00/km | 146-153 bpm | 3 | AQUECIMENTO 1.68 km 10 min @ 5:55-6:00/km FC 129-146 bpm<br>PRINCIPAL 10.07 km 60 min @ 5:55-6:00/km FC 146-153 bpm<br>DESAQUECIMENTO 0.84 km 5 min @ 5:55-6:00/km FC 129-146 bpm | — |
| SABADO | LONGO | 25.55 km | 148:00 | 5:45-5:50/km | 146-153 bpm | 5 | AQUECIMENTO 1.38 km 8 min @ 5:45-5:50/km FC 129-146 bpm<br>PRINCIPAL 23.31 km 135 min @ 5:45-5:50/km FC 146-153 bpm<br>DESAQUECIMENTO 0.86 km 5 min @ 5:45-5:50/km FC 129-146 bpm | — |

**Descanso:** TERCA (Descanso completo para respeitar o limite de dias consecutivos e aliviar a fadiga acumulada.), QUINTA (Descanso entre sessões para limitar a sequência de treinos e favorecer recuperação.), DOMINGO (Recuperação completa após o longo de sábado.)


**Sua avaliação:** preferido: ___ · barraria algo? ___


## Caso 3 — avancado-tsb-baixo, repetição 3


### Plano A

**Objetivo da semana:** Manter volume atual e introduzir variabilidade com fartlek e tempo run.  
**Volume planejado:** 58.0 km (alvo 58.0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 12.55 km | 75:00 | 5:45-6:00/km | 146-153 bpm | 3 | AQUECIMENTO 1.6 km 10 min @ 6:00-6:30/km FC 129-146 bpm<br>PRINCIPAL 10.21 km 60 min @ 5:45-6:00/km FC 146-153 bpm<br>DESAQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 129-146 bpm | — |
| QUARTA | CONTINUO | 15.67 km | 90:00 | 5:30-5:45/km | 146-162 bpm | 5 | AQUECIMENTO 1.6 km 10 min @ 6:00-6:30/km FC 129-146 bpm<br>PRINCIPAL 13.33 km 75 min @ 5:30-5:45/km FC 146-162 bpm<br>DESAQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 129-146 bpm | — |
| SEXTA | REGENERATIVO | 12.55 km | 75:00 | 5:45-6:00/km | 146-153 bpm | 3 | AQUECIMENTO 1.6 km 10 min @ 6:00-6:30/km FC 129-146 bpm<br>PRINCIPAL 10.21 km 60 min @ 5:45-6:00/km FC 146-153 bpm<br>DESAQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 129-146 bpm | — |
| SABADO | LONGO | 24.93 km | 148:00 | 5:45-6:00/km | 146-153 bpm | 6 | AQUECIMENTO 2.4 km 15 min @ 6:00-6:30/km FC 129-146 bpm<br>PRINCIPAL 21.79 km 128 min @ 5:45-6:00/km FC 146-153 bpm<br>DESAQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 129-146 bpm | — |

**Descanso:** TERCA (Recuperação após 5 dias consecutivos de treino), QUINTA (Recuperação após treino contínuo)


### Plano B

**Objetivo da semana:** Recuperar da fadiga acumulada e manter base aeróbica para a maratona, sem acrescentar intensidade nesta semana.  
**Volume planejado:** 33.4 km (alvo 33.4 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| QUARTA | CONTINUO | 10.4 km | 60:00 | 5:30-5:45/km | 146-153 bpm | 4 | AQUECIMENTO 1.6 km 10 min @ 6:00-6:30/km FC 129-146 bpm<br>PRINCIPAL 8.0 km 45 min @ 5:30-5:45/km FC 146-153 bpm<br>DESAQUECIMENTO 0.8 km 5 min @ 6:00-6:30/km FC 129-146 bpm | — |
| SEXTA | REGENERATIVO | 5.8 km | 40:00 | 6:30-7:00/km | 129-146 bpm | 2 | AQUECIMENTO 2.18 km 10 min<br>PRINCIPAL 2.88 km 19 min @ 6:30-7:00/km FC 129-146 bpm<br>DESAQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 129-146 bpm | — |
| SABADO | LONGO | 16.87 km | 100:00 | 5:45-6:00/km | 146-153 bpm | 4 | AQUECIMENTO 1.6 km 10 min @ 6:00-6:30/km FC 129-146 bpm<br>PRINCIPAL 14.47 km 85 min @ 5:45-6:00/km FC 146-153 bpm<br>DESAQUECIMENTO 0.8 km 5 min @ 6:00-6:30/km FC 129-146 bpm | — |

**Descanso:** SEGUNDA (Descanso completo obrigatório após 5 dias consecutivos de treino e com TSB em torno de -20.)


**Sua avaliação:** preferido: ___ · barraria algo? ___


---

# Arquétipo: com-lesao-ativa

<details><summary>Contexto do atleta</summary>

```
Nível: INTERMEDIARIO
Lesão ativa: Tendinite no tendão de Aquiles direito
Treinos recentes: 3 sessões
```

</details>


## Caso 4 — com-lesao-ativa, repetição 1


### Plano A

**Objetivo da semana:** Recuperação segura e progressiva da tendinite no tendão de Aquiles.  
**Volume planejado:** 27.0 km (alvo 27.0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 6.49 km | 45:00 | 6:52-7:00/km | 126-143 bpm | 3 | AQUECIMENTO 1.44 km 10 min @ 6:52-7:00/km FC 126-143 bpm<br>PRINCIPAL 4.33 km 30 min @ 6:52-7:00/km FC 126-143 bpm<br>DESAQUECIMENTO 0.72 km 5 min @ 6:52-7:00/km FC 126-143 bpm | — |
| QUARTA | CONTINUO | 7.89 km | 45:00 | 5:36-5:48/km | 143-150 bpm | 4 | AQUECIMENTO 1.75 km 10 min @ 5:36-5:48/km FC 143-150 bpm<br>PRINCIPAL 5.26 km 30 min @ 5:36-5:48/km FC 143-150 bpm<br>DESAQUECIMENTO 0.88 km 5 min @ 5:36-5:48/km FC 143-150 bpm | — |
| SEXTA | CONTINUO | 7.89 km | 45:00 | 5:36-5:48/km | 143-150 bpm | 4 | AQUECIMENTO 1.75 km 10 min @ 5:36-5:48/km FC 143-150 bpm<br>PRINCIPAL 5.26 km 30 min @ 5:36-5:48/km FC 143-150 bpm<br>DESAQUECIMENTO 0.88 km 5 min @ 5:36-5:48/km FC 143-150 bpm | — |
| SABADO | REGENERATIVO | 6.49 km | 45:00 | 6:52-7:00/km | 126-143 bpm | 3 | AQUECIMENTO 1.44 km 10 min @ 6:52-7:00/km FC 126-143 bpm<br>PRINCIPAL 4.33 km 30 min @ 6:52-7:00/km FC 126-143 bpm<br>DESAQUECIMENTO 0.72 km 5 min @ 6:52-7:00/km FC 126-143 bpm | — |

**Descanso:** TERCA (Recuperação ativa), QUINTA (Recuperação ativa), DOMINGO (Recuperação ativa)


### Plano B

**Objetivo da semana:** Manter quatro sessões leves em Z1-Z2, respeitando a tendinite do Aquiles e priorizando corrida sem dor.  
**Volume planejado:** 24.6 km (alvo 24.6 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 5.7 km | 39:00 | 6:52-7:30/km | 126-143 bpm | 2 | AQUECIMENTO 1.63 km 10 min<br>PRINCIPAL 3.37 km 24 min @ 6:52-7:30/km FC 126-143 bpm<br>DESAQUECIMENTO 0.7 km 5 min @ 6:52-7:30/km FC 126-143 bpm | — |
| QUARTA | CONTINUO | 6.96 km | 45:00 | 5:55-6:30/km | 143-150 bpm | 4 | AQUECIMENTO 1.11 km 8 min @ 6:52-7:30/km FC 126-143 bpm<br>PRINCIPAL 5.15 km 32 min @ 5:55-6:30/km FC 143-150 bpm<br>DESAQUECIMENTO 0.7 km 5 min @ 6:52-7:30/km FC 126-143 bpm | — |
| SEXTA | CONTINUO | 6.96 km | 45:00 | 5:55-6:30/km | 143-150 bpm | 4 | AQUECIMENTO 1.11 km 8 min @ 6:52-7:30/km FC 126-143 bpm<br>PRINCIPAL 5.15 km 32 min @ 5:55-6:30/km FC 143-150 bpm<br>DESAQUECIMENTO 0.7 km 5 min @ 6:52-7:30/km FC 126-143 bpm | — |
| SABADO | REGENERATIVO | 5.7 km | 39:00 | 6:52-7:30/km | 126-143 bpm | 2 | AQUECIMENTO 1.63 km 10 min<br>PRINCIPAL 3.37 km 24 min @ 6:52-7:30/km FC 126-143 bpm<br>DESAQUECIMENTO 0.7 km 5 min @ 6:52-7:30/km FC 126-143 bpm | — |

**Descanso:** TERCA (Descanso completo para favorecer a recuperação do tendão de Aquiles.), QUINTA (Descanso completo entre as sessões leves.), DOMINGO (Descanso após a sessão de sábado.)


**Sua avaliação:** preferido: ___ · barraria algo? ___


## Caso 5 — com-lesao-ativa, repetição 2


### Plano A

**Objetivo da semana:** Manter movimento leve sem agravar a tendinite do Aquiles, respeitando os quatro treinos prescritos e permanecendo em Z1-Z2.  
**Volume planejado:** 25.0 km (alvo 25.0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 5.5 km | 38:00 | 7:00-7:30/km | 126-143 bpm | 2 | AQUECIMENTO 1.63 km 10 min<br>PRINCIPAL 3.18 km 23 min @ 7:00-7:30/km FC 126-143 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 126-143 bpm | — |
| QUARTA | CONTINUO | 7.4 km | 45:00 | 5:55-6:00/km | 143-150 bpm | 4 | AQUECIMENTO 1.34 km 8 min @ 5:55-6:00/km FC 126-143 bpm<br>PRINCIPAL 5.37 km 32 min @ 5:55-6:00/km FC 143-150 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 126-143 bpm | — |
| SEXTA | CONTINUO | 7.4 km | 45:00 | 5:55-6:00/km | 143-150 bpm | 4 | AQUECIMENTO 1.34 km 8 min @ 5:55-6:00/km FC 126-143 bpm<br>PRINCIPAL 5.37 km 32 min @ 5:55-6:00/km FC 143-150 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 126-143 bpm | — |
| SABADO | REGENERATIVO | 5.5 km | 38:00 | 7:00-7:30/km | 126-143 bpm | 2 | AQUECIMENTO 1.63 km 10 min<br>PRINCIPAL 3.18 km 23 min @ 7:00-7:30/km FC 126-143 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 126-143 bpm | — |

**Descanso:** TERCA (Descanso completo para recuperação do tendão de Aquiles.), QUINTA (Descanso completo entre as sessões leves.), DOMINGO (Descanso após a sessão de sábado.)


### Plano B

**Objetivo da semana:** Recuperação e retorno seguro à corrida sem dor.  
**Volume planejado:** 27.0 km (alvo 27.0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 6.5 km | 45:00 | 6:52-7:00/km | 126-143 bpm | 3 | AQUECIMENTO 1.63 km 10 min<br>PRINCIPAL 4.05 km 28 min @ 6:52-7:00/km FC 126-143 bpm<br>DESAQUECIMENTO 0.82 km 5 min FC 126-143 bpm | — |
| QUARTA | CONTINUO | 7.0 km | 41:00 | 5:36-5:48/km | 143-150 bpm | 4 | AQUECIMENTO 1.63 km 10 min<br>PRINCIPAL 4.55 km 26 min @ 5:36-5:48/km FC 143-150 bpm<br>DESAQUECIMENTO 0.82 km 5 min FC 143-150 bpm | — |
| SEXTA | CONTINUO | 7.0 km | 41:00 | 5:36-5:48/km | 143-150 bpm | 4 | AQUECIMENTO 1.63 km 10 min<br>PRINCIPAL 4.55 km 26 min @ 5:36-5:48/km FC 143-150 bpm<br>DESAQUECIMENTO 0.82 km 5 min FC 143-150 bpm | — |
| SABADO | REGENERATIVO | 6.5 km | 45:00 | 6:52-7:00/km | 126-143 bpm | 3 | AQUECIMENTO 1.63 km 10 min<br>PRINCIPAL 4.05 km 28 min @ 6:52-7:00/km FC 126-143 bpm<br>DESAQUECIMENTO 0.82 km 5 min FC 126-143 bpm | — |

**Descanso:** TERCA (Recuperação ativa), QUINTA (Recuperação ativa), DOMINGO (Recuperação ativa)


**Sua avaliação:** preferido: ___ · barraria algo? ___


## Caso 6 — com-lesao-ativa, repetição 3


### Plano A

**Objetivo da semana:** Recuperação e retorno seguro à corrida sem dor.  
**Volume planejado:** 27.0 km (alvo 27.0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 6.5 km | 45:00 | 6:52-7:00/km | 126-143 bpm | 3 | AQUECIMENTO 1.63 km 10 min<br>PRINCIPAL 4.05 km 28 min @ 6:52-7:00/km FC 126-143 bpm<br>DESAQUECIMENTO 0.82 km 5 min FC 126-143 bpm | — |
| QUARTA | CONTINUO | 7.0 km | 41:00 | 5:36-5:48/km | 143-150 bpm | 4 | AQUECIMENTO 1.63 km 10 min<br>PRINCIPAL 4.55 km 26 min @ 5:36-5:48/km FC 143-150 bpm<br>DESAQUECIMENTO 0.82 km 5 min FC 143-150 bpm | — |
| SEXTA | CONTINUO | 7.0 km | 41:00 | 5:36-5:48/km | 143-150 bpm | 4 | AQUECIMENTO 1.63 km 10 min<br>PRINCIPAL 4.55 km 26 min @ 5:36-5:48/km FC 143-150 bpm<br>DESAQUECIMENTO 0.82 km 5 min FC 143-150 bpm | — |
| SABADO | REGENERATIVO | 6.5 km | 45:00 | 6:52-7:00/km | 126-143 bpm | 3 | AQUECIMENTO 1.63 km 10 min<br>PRINCIPAL 4.05 km 28 min @ 6:52-7:00/km FC 126-143 bpm<br>DESAQUECIMENTO 0.82 km 5 min FC 126-143 bpm | — |

**Descanso:** TERCA (Recuperação ativa e prevenção de sobrecarga), QUINTA (Recuperação ativa e prevenção de sobrecarga), DOMINGO (Recuperação ativa e prevenção de sobrecarga)


### Plano B

**Objetivo da semana:** Retomar corrida sem dor com quatro sessões leves, mantendo o volume conservador por causa da tendinite no Aquiles direito.  
**Volume planejado:** 6.0 km (alvo 6.3 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 1.5 km | 45:00 | 6:52-7:30/km | 126-143 bpm | 2 | AQUECIMENTO 1.63 km 10 min<br>PRINCIPAL 5.57 km 40 min @ 6:52-7:30/km FC 126-143 bpm<br>DESAQUECIMENTO 0.7 km 5 min @ 6:52-7:30/km FC 126-143 bpm | — |
| QUARTA | CONTINUO | 6.71 km | 45:00 | 5:55-7:30/km | 143-150 bpm | 3 | AQUECIMENTO 1.19 km 8 min @ 5:55-7:30/km FC 126-143 bpm<br>PRINCIPAL 4.77 km 32 min @ 5:55-7:30/km FC 143-150 bpm<br>DESAQUECIMENTO 0.75 km 5 min @ 5:55-7:30/km FC 126-143 bpm | — |
| SEXTA | CONTINUO | 6.71 km | 45:00 | 5:55-7:30/km | 143-150 bpm | 3 | AQUECIMENTO 1.19 km 8 min @ 5:55-7:30/km FC 126-143 bpm<br>PRINCIPAL 4.77 km 32 min @ 5:55-7:30/km FC 143-150 bpm<br>DESAQUECIMENTO 0.75 km 5 min @ 5:55-7:30/km FC 126-143 bpm | — |
| SABADO | REGENERATIVO | 1.5 km | 45:00 | 6:52-7:30/km | 126-143 bpm | 2 | AQUECIMENTO 1.63 km 10 min<br>PRINCIPAL 5.57 km 40 min @ 6:52-7:30/km FC 126-143 bpm<br>DESAQUECIMENTO 0.7 km 5 min @ 6:52-7:30/km FC 126-143 bpm | — |

**Descanso:** TERCA (Descanso e recuperação do tendão de Aquiles.), QUINTA (Descanso entre sessões leves.), DOMINGO (Recuperação após a sessão de sábado.)


**Sua avaliação:** preferido: ___ · barraria algo? ___


---

# Arquétipo: iniciante-sem-lesao

<details><summary>Contexto do atleta</summary>

```
Nível: INICIANTE
Treinos recentes: 3 sessões
```

</details>


## Caso 7 — iniciante-sem-lesao, repetição 1


### Plano A

**Objetivo da semana:** Completar a primeira 10k com segurança e progressão controlada.  
**Volume planejado:** 25.0 km (alvo 25.0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | CONTINUO | 7.0 km | 50:00 | 6:50-7:00/km | 140-147 bpm | 4 | AQUECIMENTO 1.32 km 10 min FC 124-140 bpm<br>PRINCIPAL 5.02 km 35 min @ 6:50-7:00/km FC 140-147 bpm<br>DESAQUECIMENTO 0.66 km 5 min | — |
| QUARTA | CONTINUO | 6.0 km | 42:00 | 6:30-6:50/km | 140-155 bpm | 5 | AQUECIMENTO 1.32 km 10 min FC 124-140 bpm<br>PRINCIPAL 4.02 km 27 min @ 6:30-6:50/km FC 140-155 bpm<br>DESAQUECIMENTO 0.66 km 5 min | — |
| SEXTA | REGENERATIVO | 4.0 km | 29:00 | 6:50-7:00/km | 140-147 bpm | 3 | AQUECIMENTO 1.32 km 10 min FC 124-140 bpm<br>PRINCIPAL 2.02 km 14 min @ 6:50-7:00/km FC 140-147 bpm<br>DESAQUECIMENTO 0.66 km 5 min | — |
| SABADO | LONGO | 8.0 km | 57:00 | 6:50-7:00/km | 140-147 bpm | 4 | AQUECIMENTO 1.32 km 10 min FC 124-140 bpm<br>PRINCIPAL 6.02 km 42 min @ 6:50-7:00/km FC 140-147 bpm<br>DESAQUECIMENTO 0.66 km 5 min | — |

**Descanso:** TERCA (Recuperação ativa após treino contínuo), QUINTA (Recuperação ativa após treino fácil)


### Plano B

**Objetivo da semana:** Assimilar a carga recente e consolidar a base para completar a primeira 10k, reduzindo o volume semanal em cerca de 20% após o alerta de progressão rápida.  
**Volume planejado:** 8.0 km (alvo 8.0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 2.5 km | 19:00 | 6:52-7:15/km | 140-147 bpm | 3 | AQUECIMENTO 1.32 km 10 min<br>PRINCIPAL 0.53 km 4 min @ 6:52-7:15/km FC 140-147 bpm<br>DESAQUECIMENTO 0.65 km 5 min @ 7:25-7:53/km FC 124-140 bpm | — |
| QUARTA | CONTINUO | 2.53 km | 18:00 | 6:37-7:15/km | 140-147 bpm | 4 | AQUECIMENTO 0.69 km 5 min @ 6:37-7:53/km FC 124-147 bpm<br>PRINCIPAL 1.15 km 8 min @ 6:37-7:15/km FC 140-147 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 6:37-7:53/km FC 124-147 bpm | — |
| SABADO | LONGO | 3.11 km | 22:00 | 6:37-7:15/km | 140-147 bpm | 4 | AQUECIMENTO 0.69 km 5 min @ 6:37-7:53/km FC 124-147 bpm<br>PRINCIPAL 1.73 km 12 min @ 6:37-7:15/km FC 140-147 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 6:37-7:53/km FC 124-147 bpm | — |

**Descanso:** SEXTA (Descanso completo para reduzir a carga e recuperar entre as sessões.)


**Sua avaliação:** preferido: ___ · barraria algo? ___


## Caso 8 — iniciante-sem-lesao, repetição 2


### Plano A

**Objetivo da semana:** Consolidar a base para completar a primeira 10K, reduzindo a carga em cerca de 25% diante do ramp rate elevado e aumentando o longo com cautela.  
**Volume planejado:** 17.2 km (alvo 17.2 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 5.0 km | 37:00 | 7:00-7:30/km | 140-147 bpm | 3 | AQUECIMENTO 1.32 km 10 min<br>PRINCIPAL 3.02 km 22 min @ 7:00-7:30/km FC 124-147 bpm<br>DESAQUECIMENTO 0.66 km 5 min @ 7:15-7:53/km FC 124-140 bpm | — |
| QUARTA | CONTINUO | 5.69 km | 40:00 | 6:30-7:15/km | 140-155 bpm | 4 | AQUECIMENTO 1.1 km 8 min @ 7:00-7:30/km FC 124-147 bpm<br>PRINCIPAL 3.93 km 27 min @ 6:30-7:15/km FC 140-155 bpm<br>DESAQUECIMENTO 0.66 km 5 min @ 7:15-7:53/km FC 124-140 bpm | — |
| SABADO | LONGO | 6.43 km | 45:00 | 6:37-7:15/km | 140-147 bpm | 4 | AQUECIMENTO 0.7 km 5 min @ 6:52-7:30/km FC 124-147 bpm<br>PRINCIPAL 5.05 km 35 min @ 6:37-7:15/km FC 140-147 bpm<br>DESAQUECIMENTO 0.68 km 5 min @ 6:52-7:53/km FC 124-147 bpm | — |

**Descanso:** SEXTA (Descanso completo para limitar a carga semanal e chegar recuperada ao longo de sábado.)


### Plano B

**Objetivo da semana:** Completar a primeira 10k com foco em base aeróbica e progressão segura.  
**Volume planejado:** 23.0 km (alvo 23.0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 7.15 km | 52:00 | 6:50-7:25/km | 140-147 bpm | 4 | AQUECIMENTO 1.32 km 10 min @ 7:25-7:45/km FC 124-140 bpm<br>PRINCIPAL 4.91 km 35 min @ 6:50-7:25/km FC 140-147 bpm<br>DESAQUECIMENTO 0.92 km 7 min @ 7:25-7:45/km FC 124-140 bpm | — |
| QUARTA | CONTINUO | 7.46 km | 52:00 | 6:30-7:00/km | 140-155 bpm | 5 | AQUECIMENTO 1.32 km 10 min @ 7:25-7:45/km FC 124-140 bpm<br>PRINCIPAL 5.48 km 37 min @ 6:30-7:00/km FC 140-155 bpm<br>DESAQUECIMENTO 0.66 km 5 min @ 7:25-7:45/km FC 124-140 bpm | — |
| SEXTA | REGENERATIVO | 4.1 km | 31:00 | 00:00-00:00/km | 00-00 bpm | 1 | AQUECIMENTO 1.32 km 10 min<br>PRINCIPAL 0.13 km 1 min FC 00-00 bpm<br>DESAQUECIMENTO 2.65 km 20 min FC 00-00 bpm | — |
| SABADO | LONGO | 8.3 km | 60:00 | 6:50-7:25/km | 140-147 bpm | 4 | AQUECIMENTO 1.32 km 10 min @ 7:25-7:45/km FC 124-140 bpm<br>PRINCIPAL 6.32 km 45 min @ 6:50-7:25/km FC 140-147 bpm<br>DESAQUECIMENTO 0.66 km 5 min @ 7:25-7:45/km FC 124-140 bpm | — |

**Descanso:** TERCA (Recuperação entre treinos), QUINTA (Recuperação entre treinos), DOMINGO (Recuperação após treino longo)


**Sua avaliação:** preferido: ___ · barraria algo? ___


## Caso 9 — iniciante-sem-lesao, repetição 3


### Plano A

**Objetivo da semana:** Completar a primeira 10k com foco em base aeróbica e progressão segura.  
**Volume planejado:** 21.0 km (alvo 21.0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 7.0 km | 50:00 | 6:45-7:00/km | 140-147 bpm | 4 | AQUECIMENTO 1.32 km 10 min FC 124-140 bpm<br>PRINCIPAL 5.02 km 35 min @ 6:45-7:00/km FC 140-147 bpm<br>DESAQUECIMENTO 0.66 km 5 min | — |
| QUARTA | CONTINUO | 6.0 km | 42:00 | 6:30-6:45/km | 140-155 bpm | 5 | AQUECIMENTO 1.32 km 10 min FC 124-140 bpm<br>PRINCIPAL 4.02 km 27 min @ 6:30-6:45/km FC 140-155 bpm<br>DESAQUECIMENTO 0.66 km 5 min | — |
| SABADO | LONGO | 8.0 km | 56:00 | 6:45-7:00/km | 140-147 bpm | 5 | AQUECIMENTO 1.32 km 10 min FC 124-140 bpm<br>PRINCIPAL 6.02 km 41 min @ 6:45-7:00/km FC 140-147 bpm<br>DESAQUECIMENTO 0.66 km 5 min | — |

**Descanso:** TERCA (Recuperação após treino fácil), QUINTA (Recuperação após treino contínuo)


### Plano B

**Objetivo da semana:** Assimilar a carga recente e manter consistência para a primeira 10k, reduzindo o volume para priorizar recuperação e evitar nova progressão rápida.  
**Volume planejado:** 6.6 km (alvo 6.5 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 1.8 km | 14:00 | 7:15-7:53/km | 140-147 bpm | 3 | AQUECIMENTO 1.32 km 10 min<br>PRINCIPAL 0.22 km 2 min @ 7:15-7:53/km FC 124-140 bpm<br>DESAQUECIMENTO 0.26 km 2 min @ 7:15-7:53/km FC 124-140 bpm | — |
| QUARTA | CONTINUO | 2.08 km | 15:00 | 6:37-7:15/km | 140-147 bpm | 4 | AQUECIMENTO 0.53 km 4 min @ 7:15-7:53/km FC 124-140 bpm<br>PRINCIPAL 1.15 km 8 min @ 6:37-7:15/km FC 140-147 bpm<br>DESAQUECIMENTO 0.4 km 3 min @ 7:15-7:53/km FC 124-140 bpm | — |
| SABADO | LONGO | 2.8 km | 20:00 | 6:37-7:15/km | 140-147 bpm | 4 | AQUECIMENTO 0.53 km 4 min @ 7:15-7:53/km FC 124-140 bpm<br>PRINCIPAL 1.87 km 13 min @ 6:37-7:15/km FC 140-147 bpm<br>DESAQUECIMENTO 0.4 km 3 min @ 7:15-7:53/km FC 124-140 bpm | — |

**Descanso:** SEXTA (Descanso completo para limitar a carga nesta semana de redução.)


**Sua avaliação:** preferido: ___ · barraria algo? ___


---

# Arquétipo: sem-dados

<details><summary>Contexto do atleta</summary>

```
Nível: INTERMEDIARIO
Treinos recentes: 0 sessões
```

</details>


## Caso 10 — sem-dados, repetição 1


### Plano A

**Objetivo da semana:** Melhorar condicionamento geral com foco em variabilidade de estímulos.  
**Volume planejado:** 24.0 km (alvo 24.0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 5.0 km | 36:00 | 7:00-7:30/km | 115-130 bpm | 3 | AQUECIMENTO 1.43 km 10 min<br>PRINCIPAL 2.14 km 16 min @ 7:00-7:30/km FC 115-130 bpm<br>DESAQUECIMENTO 1.43 km 10 min FC 115-130 bpm | — |
| QUARTA | FARTLEK | 6.0 km | 40:00 | 6:30-7:00/km | 130-136 bpm | 4 | AQUECIMENTO 1.43 km 10 min FC 115-136 bpm<br>INTERVALADO 1 min FC 65-75% FCmax<br>RECUPERACAO 0.25 km 2 min FC 60-70% FCmax<br>INTERVALADO 1 min FC 65-75% FCmax<br>RECUPERACAO 0.25 km 2 min FC 60-70% FCmax<br>INTERVALADO 1 min FC 65-75% FCmax<br>RECUPERACAO 0.25 km 2 min FC 60-70% FCmax<br>INTERVALADO 1 min FC 65-75% FCmax<br>RECUPERACAO 0.25 km 2 min FC 60-70% FCmax<br>INTERVALADO 1 min FC 65-75% FCmax<br>RECUPERACAO 0.25 km 2 min FC 60-70% FCmax<br>DESAQUECIMENTO 2.14 km 15 min FC 115-130 bpm | — |
| SEXTA | CONTINUO | 5.1 km | 35:00 | 6:30-7:00/km | 130-136 bpm | 4 | AQUECIMENTO 1.43 km 10 min FC 115-136 bpm<br>PRINCIPAL 2.96 km 20 min FC 130-136 bpm<br>DESAQUECIMENTO 0.71 km 5 min FC 115-130 bpm | — |
| SABADO | LONGO | 8.81 km | 60:00 | 6:30-7:00/km | 130-136 bpm | 5 | AQUECIMENTO 1.43 km 10 min FC 115-136 bpm<br>PRINCIPAL 6.67 km 45 min FC 130-136 bpm<br>DESAQUECIMENTO 0.71 km 5 min FC 115-130 bpm | — |

**Descanso:** TERCA (Recuperação entre treinos), QUINTA (Recuperação entre treinos), DOMINGO (Recuperação após treino longo)


### Plano B

**Objetivo da semana:** Retomar o condicionamento com uma sessão aeróbica controlada, após 28 dias sem treinos registrados.  
**Volume planejado:** 0.0 km (alvo 0.0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SABADO | LONGO | 8.559999999999999 km | 60:00 | 70:00-60:00/km | 130-136 bpm | 4 | AQUECIMENTO 1.14 km 8 min @ 70:00-60:00/km FC 115-130 bpm<br>PRINCIPAL 6.71 km 47 min @ 70:00-60:00/km FC 130-136 bpm<br>DESAQUECIMENTO 0.71 km 5 min @ 70:00-60:00/km FC 115-130 bpm | — |

**Descanso:** SEGUNDA (Descanso; retomar a corrida de forma conservadora após a pausa.), QUARTA (Descanso completo entre sessões.), SEXTA (Descanso antes do longo de sábado.)


**Sua avaliação:** preferido: ___ · barraria algo? ___


## Caso 11 — sem-dados, repetição 2


### Plano A

**Objetivo da semana:** Retomar a corrida com um longo fácil em Z2, aumentando a consistência sem acelerar a carga após 28 dias sem treinos.  
**Volume planejado:** 0.0 km (alvo 0.0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SABADO | LONGO | 8.79 km | 60:00 | 6:30-7:00/km | 130-136 bpm | 4 | AQUECIMENTO 1.14 km 8 min @ 11:00-7:00/km FC 115-130 bpm<br>PRINCIPAL 6.96 km 47 min @ 6:30-7:00/km FC 130-136 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 115-130 bpm | — |

**Descanso:** SEGUNDA (Descanso; a única sessão prescrita pelo planner é o longo de sábado.), QUARTA (Descanso para manter uma retomada conservadora.), SEXTA (Descanso antes da sessão-chave de sábado.)


### Plano B

**Objetivo da semana:** Melhorar condicionamento geral com foco em variabilidade de estímulos.  
**Volume planejado:** 23.0 km (alvo 23.0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | CONTINUO | 5.84 km | 40:00 | 6:30-7:00/km | 130-136 bpm | 4 | AQUECIMENTO 1.43 km 10 min FC 115-130 bpm<br>PRINCIPAL 3.7 km 25 min @ 6:30-7:00/km FC 130-136 bpm<br>DESAQUECIMENTO 0.71 km 5 min FC 115-130 bpm | — |
| QUARTA | INTERVALADO | 6.0 km | 39:00 | 4:30-5:00/km | 153-162 bpm | 8 | AQUECIMENTO 1.43 km 10 min FC 115-130 bpm<br>INTERVALADO 0.8 km 4 min @ 4:30-5:00/km FC 153-162 bpm<br>RECUPERACAO 0.2 km 2 min FC 130-136 bpm<br>INTERVALADO 0.8 km 4 min @ 4:30-5:00/km FC 153-162 bpm<br>RECUPERACAO 0.2 km 2 min FC 130-136 bpm<br>INTERVALADO 0.8 km 4 min @ 4:30-5:00/km FC 153-162 bpm<br>RECUPERACAO 0.2 km 2 min FC 130-136 bpm<br>INTERVALADO 0.8 km 4 min FC 90-95% FCmax<br>RECUPERACAO 0.2 km 2 min FC 70-80% FCmax<br>DESAQUECIMENTO 0.8 km 5 min FC 115-130 bpm | — |
| SEXTA | REGENERATIVO | 4.0 km | 28:00 | 7:00-7:30/km | 115-130 bpm | 3 | AQUECIMENTO 1.43 km 10 min<br>PRINCIPAL 1.14 km 8 min @ 7:00-7:30/km FC 115-130 bpm<br>DESAQUECIMENTO 1.43 km 10 min FC 115-130 bpm | — |
| SABADO | LONGO | 8.81 km | 60:00 | 6:30-7:00/km | 130-136 bpm | 5 | AQUECIMENTO 1.43 km 10 min FC 115-130 bpm<br>PRINCIPAL 6.67 km 45 min @ 6:30-7:00/km FC 130-136 bpm<br>DESAQUECIMENTO 0.71 km 5 min FC 115-130 bpm | — |

**Descanso:** TERCA (Recuperação após treino de segunda-feira.), QUINTA (Recuperação após treino de quarta-feira.)


**Sua avaliação:** preferido: ___ · barraria algo? ___


## Caso 12 — sem-dados, repetição 3


### Plano A

**Objetivo da semana:** Melhorar condicionamento geral com foco em variabilidade de estímulos.  
**Volume planejado:** 23.0 km (alvo 23.0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | CONTINUO | 5.84 km | 40:00 | 6:30-7:00/km | 130-136 bpm | 4 | AQUECIMENTO 1.43 km 10 min FC 115-130 bpm<br>PRINCIPAL 3.7 km 25 min FC 130-136 bpm<br>DESAQUECIMENTO 0.71 km 5 min FC 115-130 bpm | — |
| QUARTA | INTERVALADO | 6.0 km | 45:00 | 5:00-5:30/km | 153-162 bpm | 8 | AQUECIMENTO 1.0 km 10 min FC 115-130 bpm<br>INTERVALADO 3.0 km 15 min FC 153-162 bpm<br>RECUPERACAO 2.0 km 20 min FC 130-136 bpm | — |
| SEXTA | REGENERATIVO | 4.21 km | 30:00 | 7:00-7:30/km | 115-130 bpm | 3 | AQUECIMENTO 1.43 km 10 min FC 115-130 bpm<br>PRINCIPAL 2.07 km 15 min FC 115-130 bpm<br>DESAQUECIMENTO 0.71 km 5 min FC 115-130 bpm | — |
| SABADO | LONGO | 8.81 km | 60:00 | 6:30-7:00/km | 130-136 bpm | 5 | AQUECIMENTO 1.43 km 10 min FC 115-130 bpm<br>PRINCIPAL 6.67 km 45 min FC 130-136 bpm<br>DESAQUECIMENTO 0.71 km 5 min FC 115-130 bpm | — |

**Descanso:** TERCA (Recuperação ativa após treino de segunda-feira.), QUINTA (Recuperação ativa após treino de quarta-feira.)


### Plano B

**Objetivo da semana:** Retomar o condicionamento com uma sessão longa leve em Z2; como não há treinos nas últimas 4 semanas, priorizar controle por FC e retorno gradual.  
**Volume planejado:** 0.0 km (alvo 0.0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SABADO | LONGO | 3.0 km | 60:00 | 20:00-20:00/km | 130-136 bpm | 4 | AQUECIMENTO 0.25 km 5 min @ 20:00-20:00/km FC 115-130 bpm<br>PRINCIPAL 2.5 km 50 min @ 20:00-20:00/km FC 130-136 bpm<br>DESAQUECIMENTO 0.25 km 5 min @ 20:00-20:00/km FC 115-130 bpm | — |

**Descanso:** SEGUNDA (Descanso completo para favorecer uma retomada conservadora após 28 dias sem treinos.), QUARTA (Descanso completo; manter recuperação entre a retomada e o longo de sábado.), SEXTA (Descanso completo antes da sessão-chave de sábado.)


**Sua avaliação:** preferido: ___ · barraria algo? ___


---

# Arquétipo: taper-semana-prova

<details><summary>Contexto do atleta</summary>

```
Nível: AVANCADO
Prova-alvo: Meia Maratona da Cidade em 2026-01-25
Treinos recentes: 5 sessões
```

</details>


## Caso 13 — taper-semana-prova, repetição 1


### Plano A

**Objetivo da semana:** Preparar para a Meia Maratona da Cidade com foco em frescor e estratégia.  
**Volume planejado:** 36.1 km (alvo 36.1 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| TERCA | REGENERATIVO | 4.9 km | 34:00 | 6:52-7:00/km | 128-145 bpm | 3 | AQUECIMENTO 0.72 km 5 min @ 6:52-7:00/km FC 128-145 bpm<br>PRINCIPAL 3.46 km 24 min @ 6:52-7:00/km FC 128-145 bpm<br>DESAQUECIMENTO 0.72 km 5 min @ 6:52-7:00/km FC 128-145 bpm | — |
| QUINTA | CONTINUO | 5.12 km | 34:00 | 6:30-6:45/km | 145-152 bpm | 4 | AQUECIMENTO 0.75 km 5 min @ 6:30-6:45/km FC 145-152 bpm<br>PRINCIPAL 3.62 km 24 min @ 6:30-6:45/km FC 145-152 bpm<br>DESAQUECIMENTO 0.75 km 5 min @ 6:30-6:45/km FC 145-152 bpm | — |
| SABADO | CONTINUO | 5.12 km | 34:00 | 6:30-6:45/km | 145-152 bpm | 4 | AQUECIMENTO 0.75 km 5 min @ 6:30-6:45/km FC 145-152 bpm<br>PRINCIPAL 3.62 km 24 min @ 6:30-6:45/km FC 145-152 bpm<br>DESAQUECIMENTO 0.75 km 5 min @ 6:30-6:45/km FC 145-152 bpm | — |
| DOMINGO | LONGO | 21.1 km | 94:00 | 4:10-4:25/km | 152-161 bpm | 9 | AQUECIMENTO 2.06 km 10 min<br>PRINCIPAL 18.32 km 79 min @ 4:10-4:25/km FC 152-161 bpm<br>DESAQUECIMENTO 0.72 km 5 min @ 6:52-7:00/km FC 128-145 bpm | — |

**Descanso:** SEGUNDA (Recuperação antes da semana de prova), QUARTA (Recuperação ativa para manter frescor)


### Plano B

**Objetivo da semana:** Chegar fresco à Meia Maratona da Cidade: corridas curtas e fáceis antes da prova, sem estímulos intensos.  
**Volume planejado:** 35.4 km (alvo 35.4 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| TERCA | REGENERATIVO | 4.7 km | 34:00 | 7:00-7:30/km | 128-145 bpm | 2 | AQUECIMENTO 2.06 km 10 min<br>PRINCIPAL 1.95 km 14 min @ 7:00-7:30/km FC 128-145 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 128-145 bpm | — |
| QUINTA | CONTINUO | 5.04 km | 34:00 | 6:30-7:00/km | 145-152 bpm | 3 | AQUECIMENTO 0.89 km 6 min @ 6:30-7:00/km FC 128-145 bpm<br>PRINCIPAL 3.41 km 23 min @ 6:30-7:00/km FC 145-152 bpm<br>DESAQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 128-145 bpm | — |
| SABADO | CONTINUO | 5.04 km | 34:00 | 6:30-7:00/km | 145-152 bpm | 3 | AQUECIMENTO 0.89 km 6 min @ 6:30-7:00/km FC 128-145 bpm<br>PRINCIPAL 3.41 km 23 min @ 6:30-7:00/km FC 145-152 bpm<br>DESAQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 128-145 bpm | — |
| DOMINGO | CONTINUO | 21.5 km | 127:00 | 5:48-6:01/km | 152-171 bpm | 7 | AQUECIMENTO 0.17 km 1 min @ 5:48-6:01/km FC 128-145 bpm<br>PRINCIPAL 21.16 km 125 min @ 5:48-6:01/km FC 152-171 bpm<br>DESAQUECIMENTO 0.17 km 1 min @ 5:48-6:01/km FC 128-145 bpm | — |

**Descanso:** SEGUNDA (Descanso completo na semana da prova.), QUARTA (Descanso para preservar a recuperação e o frescor.), SEXTA (Descanso completo antes da ativação curta de sábado.)


**Sua avaliação:** preferido: ___ · barraria algo? ___


## Caso 14 — taper-semana-prova, repetição 2


### Plano A

**Objetivo da semana:** Chegar fresco à Meia Maratona da Cidade: manter corridas curtas e leves antes da prova de domingo.  
**Volume planejado:** 36.3 km (alvo 37.3 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| TERCA | REGENERATIVO | 4.0 km | 24:00 | 7:00-7:30/km | 128-145 bpm | 2 | AQUECIMENTO 2.06 km 10 min<br>PRINCIPAL 1.25 km 9 min @ 7:00-7:30/km FC 128-145 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 128-145 bpm | — |
| QUINTA | CONTINUO | 5.7 km | 34:00 | 5:55-6:00/km | 145-152 bpm | 3 | AQUECIMENTO 1.34 km 8 min @ 5:55-6:00/km FC 145-152 bpm<br>PRINCIPAL 3.52 km 21 min @ 5:55-6:00/km FC 145-152 bpm<br>DESAQUECIMENTO 0.84 km 5 min @ 5:55-6:00/km FC 145-152 bpm | — |
| SABADO | CONTINUO | 5.7 km | 34:00 | 5:55-6:00/km | 145-152 bpm | 3 | AQUECIMENTO 1.34 km 8 min @ 5:55-6:00/km FC 145-152 bpm<br>PRINCIPAL 3.52 km 21 min @ 5:55-6:00/km FC 145-152 bpm<br>DESAQUECIMENTO 0.84 km 5 min @ 5:55-6:00/km FC 145-152 bpm | — |
| DOMINGO | TEMPO_RUN | 30.48 km | 127:00 | 4:10-4:10/km | 152-171 bpm | 8 | AQUECIMENTO 1.92 km 8 min @ 4:10-4:10/km FC 152-171 bpm<br>PRINCIPAL 27.36 km 114 min @ 4:10-4:10/km FC 152-171 bpm<br>DESAQUECIMENTO 1.2 km 5 min @ 4:10-4:10/km FC 152-171 bpm | — |

**Descanso:** SEGUNDA (Descanso antes dos treinos leves de taper.), QUARTA (Recuperação entre as corridas curtas.), SEXTA (Descanso para preservar frescor para a prova.)


### Plano B

**Objetivo da semana:** Preparar para a Meia Maratona da Cidade, priorizando frescor e estratégia.  
**Volume planejado:** 37.1 km (alvo 37.1 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| TERCA | REGENERATIVO | 5.0 km | 34:00 | 6:52-7:00/km | 128-145 bpm | 3 | AQUECIMENTO 2.06 km 10 min<br>PRINCIPAL 1.91 km 13 min @ 6:52-7:00/km FC 128-145 bpm<br>DESAQUECIMENTO 1.03 km 5 min FC 128-145 bpm | — |
| QUINTA | CONTINUO | 5.5 km | 34:00 | 5:48-6:00/km | 145-152 bpm | 4 | AQUECIMENTO 2.06 km 10 min<br>PRINCIPAL 2.41 km 14 min @ 5:48-6:00/km FC 145-152 bpm<br>DESAQUECIMENTO 1.03 km 5 min FC 145-152 bpm | — |
| SABADO | CONTINUO | 5.5 km | 34:00 | 5:48-6:00/km | 145-152 bpm | 4 | AQUECIMENTO 2.06 km 10 min<br>PRINCIPAL 2.41 km 14 min @ 5:48-6:00/km FC 145-152 bpm<br>DESAQUECIMENTO 1.03 km 5 min FC 145-152 bpm | — |
| DOMINGO | LONGO | 21.1 km | 93:00 | 4:10-4:25/km | 152-171 bpm | 9 | AQUECIMENTO 2.06 km 10 min<br>PRINCIPAL 16.98 km 73 min @ 4:10-4:25/km FC 152-171 bpm<br>DESAQUECIMENTO 2.06 km 10 min FC 128-145 bpm | — |

**Descanso:** SEGUNDA (Recuperação antes da semana de prova), QUARTA (Recuperação ativa para manter frescor antes da prova)


**Sua avaliação:** preferido: ___ · barraria algo? ___


## Caso 15 — taper-semana-prova, repetição 3


### Plano A

**Objetivo da semana:** Preparar para a Meia Maratona da Cidade com foco em frescor e estratégia.  
**Volume planejado:** 36.1 km (alvo 36.1 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| TERCA | REGENERATIVO | 5.0 km | 34:00 | 6:52-7:00/km | 128-145 bpm | 3 | AQUECIMENTO 2.06 km 10 min<br>PRINCIPAL 1.91 km 13 min @ 6:52-7:00/km FC 128-145 bpm<br>DESAQUECIMENTO 1.03 km 5 min FC 128-145 bpm | — |
| QUINTA | CONTINUO | 5.0 km | 26:00 | 5:48-6:00/km | 145-152 bpm | 4 | AQUECIMENTO 2.06 km 10 min<br>PRINCIPAL 1.91 km 11 min @ 5:48-6:00/km FC 145-152 bpm<br>DESAQUECIMENTO 1.03 km 5 min FC 145-152 bpm | — |
| SABADO | CONTINUO | 5.0 km | 26:00 | 5:48-6:00/km | 145-152 bpm | 4 | AQUECIMENTO 2.06 km 10 min<br>PRINCIPAL 1.91 km 11 min @ 5:48-6:00/km FC 145-152 bpm<br>DESAQUECIMENTO 1.03 km 5 min FC 145-152 bpm | — |
| DOMINGO | LONGO | 21.1 km | 93:00 | 4:10-4:25/km | 152-161 bpm | 8 | AQUECIMENTO 2.06 km 10 min<br>PRINCIPAL 16.98 km 73 min @ 4:10-4:25/km FC 152-161 bpm<br>DESAQUECIMENTO 2.06 km 10 min FC 128-145 bpm | — |

**Descanso:** SEGUNDA (Recuperação antes da semana de prova), QUARTA (Recuperação ativa para manter frescor antes da prova)


### Plano B

**Objetivo da semana:** Chegar descansado à Meia Maratona da Cidade: manter apenas corridas fáceis antes da prova e respeitar os limites de pace disponíveis.  
**Volume planejado:** 35.4 km (alvo 35.4 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| TERCA | REGENERATIVO | 4.5 km | 34:00 | 7:00-7:30/km | 128-145 bpm | 2 | AQUECIMENTO 2.06 km 10 min<br>PRINCIPAL 1.75 km 13 min @ 7:00-7:30/km FC 128-145 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 128-145 bpm | — |
| QUINTA | CONTINUO | 4.99 km | 34:00 | 6:30-7:00/km | 145-152 bpm | 3 | AQUECIMENTO 1.19 km 8 min @ 6:30-7:00/km FC 145-152 bpm<br>PRINCIPAL 3.11 km 21 min @ 6:30-7:00/km FC 145-152 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 128-145 bpm | — |
| SABADO | CONTINUO | 4.99 km | 34:00 | 6:30-7:00/km | 145-152 bpm | 3 | AQUECIMENTO 1.19 km 8 min @ 6:30-7:00/km FC 145-152 bpm<br>PRINCIPAL 3.11 km 21 min @ 6:30-7:00/km FC 145-152 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 128-145 bpm | — |
| DOMINGO | LONGO | 21.02 km | 127:00 | 5:55-6:05/km | 152-171 bpm | 7 | AQUECIMENTO 1.33 km 8 min @ 5:55-6:05/km FC 145-152 bpm<br>PRINCIPAL 19.0 km 114 min @ 5:55-6:05/km FC 152-171 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 128-145 bpm | — |

**Descanso:** QUARTA (Descanso para absorver a corrida regenerativa e preservar frescor.), SEXTA (Descanso completo antes da ativação leve de sábado.)


**Sua avaliação:** preferido: ___ · barraria algo? ___


---

<details><summary>Chave (abrir só depois de avaliar)</summary>

| caso | arquétipo | | |
|---|---|---|---|
| 1 | avancado-tsb-baixo | A = gpt-6-luna:medium | B = gpt-4o |
| 2 | avancado-tsb-baixo | A = gpt-4o | B = gpt-6-luna:medium |
| 3 | avancado-tsb-baixo | A = gpt-4o | B = gpt-6-luna:medium |
| 4 | com-lesao-ativa | A = gpt-4o | B = gpt-6-luna:medium |
| 5 | com-lesao-ativa | A = gpt-6-luna:medium | B = gpt-4o |
| 6 | com-lesao-ativa | A = gpt-4o | B = gpt-6-luna:medium |
| 7 | iniciante-sem-lesao | A = gpt-4o | B = gpt-6-luna:medium |
| 8 | iniciante-sem-lesao | A = gpt-6-luna:medium | B = gpt-4o |
| 9 | iniciante-sem-lesao | A = gpt-4o | B = gpt-6-luna:medium |
| 10 | sem-dados | A = gpt-4o | B = gpt-6-luna:medium |
| 11 | sem-dados | A = gpt-6-luna:medium | B = gpt-4o |
| 12 | sem-dados | A = gpt-4o | B = gpt-6-luna:medium |
| 13 | taper-semana-prova | A = gpt-4o | B = gpt-6-luna:medium |
| 14 | taper-semana-prova | A = gpt-6-luna:medium | B = gpt-4o |
| 15 | taper-semana-prova | A = gpt-4o | B = gpt-6-luna:medium |

</details>
