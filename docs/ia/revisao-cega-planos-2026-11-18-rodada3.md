# Revisão cega de planos — gpt-4o × sucessor (migrate-openai-retired-models, task 3.5)

Cada caso é um arquétipo do eval set com o contexto do atleta e dois planos, A e B, gerados pelo
mesmo prompt de produção e passados pela normalização de produção (o que o coach recebe). A ordem é
embaralhada por caso; a chave está no fim.

**Para cada caso, anote:** (1) qual você mandaria ao atleta: A, B ou empate; (2) se algum dos dois
tem algo que você barraria (risco de lesão, carga errada, dia proibido, ritmo incoerente).

**Nota da rodada 3:** os planos passam pela normalização e pelo que o persist faz em produção: o dia
da prova é o treino PROVA do sistema (distância e ritmo da prova-alvo) e o volume da semana é a soma
dos treinos.

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

**Objetivo da semana:** Reduzir a carga para recuperar da fadiga acumulada, mantendo corrida fácil e um longo reduzido como base para a maratona sub-3h.  
**Volume planejado:** 31.29 km (alvo 66 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 7.2 km | 45:00 | 6:00-6:30/km | 129-146 bpm | 2 | AQUECIMENTO 1.6 km 10 min<br>PRINCIPAL 4.8 km 30 min @ 6:00-6:30/km FC 129-146 bpm<br>DESAQUECIMENTO 0.8 km 5 min @ 6:00-6:30/km FC 129-146 bpm | — |
| SEXTA | REGENERATIVO | 7.2 km | 45:00 | 6:00-6:30/km | 129-146 bpm | 2 | AQUECIMENTO 1.6 km 10 min<br>PRINCIPAL 4.8 km 30 min @ 6:00-6:30/km FC 129-146 bpm<br>DESAQUECIMENTO 0.8 km 5 min @ 6:00-6:30/km FC 129-146 bpm | — |
| SABADO | LONGO | 16.89 km | 100:00 | 5:45-6:00/km | 146-153 bpm | 4 | AQUECIMENTO 1.28 km 8 min @ 6:00-6:30/km FC 129-146 bpm<br>PRINCIPAL 14.81 km 87 min @ 5:45-6:00/km FC 146-153 bpm<br>DESAQUECIMENTO 0.8 km 5 min @ 6:00-6:30/km FC 129-146 bpm | — |

**Descanso:** QUARTA (Descanso obrigatório após 5 dias consecutivos de treino e TSB próximo de -20; retomar apenas se estiver recuperado.)


### Plano B

**Objetivo da semana:** Manter volume atual e introduzir fartlek e tempo run para variabilidade.  
**Volume planejado:** 62.91 km (alvo 60 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 10.5 km | 58:00 | 5:23-5:38/km | 146-153 bpm | 4 | AQUECIMENTO 1.82 km 10 min<br>PRINCIPAL 6.86 km 38 min @ 5:23-5:38/km FC 146-153 bpm<br>DESAQUECIMENTO 1.82 km 10 min FC 115-130 bpm | — |
| QUARTA | CONTINUO | 16.07 km | 90:00 | 5:23-5:35/km | 146-162 bpm | 6 | AQUECIMENTO 1.7 km 10 min @ 5:45-6:00/km FC 129-146 bpm<br>PRINCIPAL 12.77 km 70 min @ 5:23-5:35/km FC 146-162 bpm<br>DESAQUECIMENTO 1.6 km 10 min @ 6:00-6:30/km FC 129-146 bpm | — |
| SEXTA | REGENERATIVO | 10.5 km | 58:00 | 5:23-5:38/km | 146-153 bpm | 4 | AQUECIMENTO 1.82 km 10 min<br>PRINCIPAL 6.86 km 38 min @ 5:23-5:38/km FC 146-153 bpm<br>DESAQUECIMENTO 1.82 km 10 min FC 115-130 bpm | — |
| SABADO | LONGO | 25.84 km | 148:00 | 5:38-5:45/km | 146-153 bpm | 5 | AQUECIMENTO 2.55 km 15 min @ 5:45-6:00/km FC 129-146 bpm<br>PRINCIPAL 22.49 km 128 min @ 5:38-5:45/km FC 146-153 bpm<br>DESAQUECIMENTO 0.8 km 5 min @ 6:00-6:30/km FC 129-146 bpm | — |

**Descanso:** TERCA (Recuperação após 5 dias consecutivos de treino), QUINTA (Recuperação após treino contínuo)


**Sua avaliação:** preferido: ___ · barraria algo? ___


## Caso 2 — avancado-tsb-baixo, repetição 2


### Plano A

**Objetivo da semana:** Manter volume atual e introduzir fartlek e tempo run para variabilidade.  
**Volume planejado:** 65.11 km (alvo 71 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 12.44 km | 75:00 | 5:45-6:00/km | 146-153 bpm | 5 | AQUECIMENTO 1.6 km 10 min @ 6:00-6:30/km FC 129-146 bpm<br>PRINCIPAL 9.36 km 55 min @ 5:45-6:00/km FC 146-153 bpm<br>DESAQUECIMENTO 1.48 km 10 min @ 6:30-7:00/km FC 129-146 bpm | — |
| QUARTA | CONTINUO | 15.52 km | 90:00 | 5:30-5:45/km | 146-162 bpm | 6 | AQUECIMENTO 1.6 km 10 min @ 6:00-6:30/km FC 129-146 bpm<br>PRINCIPAL 12.44 km 70 min @ 5:30-5:45/km FC 146-162 bpm<br>DESAQUECIMENTO 1.48 km 10 min @ 6:30-7:00/km FC 129-146 bpm | — |
| SEXTA | REGENERATIVO | 12.44 km | 75:00 | 5:45-6:00/km | 146-153 bpm | 5 | AQUECIMENTO 1.6 km 10 min @ 6:00-6:30/km FC 129-146 bpm<br>PRINCIPAL 9.36 km 55 min @ 5:45-6:00/km FC 146-153 bpm<br>DESAQUECIMENTO 1.48 km 10 min @ 6:30-7:00/km FC 129-146 bpm | — |
| SABADO | LONGO | 24.71 km | 148:00 | 5:45-6:00/km | 146-153 bpm | 7 | AQUECIMENTO 2.4 km 15 min @ 6:00-6:30/km FC 129-146 bpm<br>PRINCIPAL 20.09 km 118 min @ 5:45-6:00/km FC 146-153 bpm<br>DESAQUECIMENTO 2.22 km 15 min @ 6:30-7:00/km FC 129-146 bpm | — |

**Descanso:** TERCA (Recuperação após 5 dias consecutivos de treino), QUINTA (Recuperação após treino contínuo)


### Plano B

**Objetivo da semana:** Recuperar da fadiga acumulada e manter a base aeróbica para sub-3h; reduzir carga e evitar intensidade nesta semana.  
**Volume planejado:** 30.72 km (alvo 66 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 6 km | 41:00 | 6:30-7:00/km | 129-146 bpm | 2 | AQUECIMENTO 1.48 km 10 min<br>PRINCIPAL 3.78 km 26 min @ 6:30-7:00/km FC 129-146 bpm<br>DESAQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 129-146 bpm | — |
| SEXTA | REGENERATIVO | 6 km | 41:00 | 6:30-7:00/km | 129-146 bpm | 2 | AQUECIMENTO 1.48 km 10 min<br>PRINCIPAL 3.78 km 26 min @ 6:30-7:00/km FC 129-146 bpm<br>DESAQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 129-146 bpm | — |
| SABADO | LONGO | 18.72 km | 110:00 | 5:45-6:00/km | 146-153 bpm | 4 | AQUECIMENTO 1.7 km 10 min @ 5:45-6:00/km FC 129-153 bpm<br>PRINCIPAL 16.17 km 95 min @ 5:45-6:00/km FC 146-153 bpm<br>DESAQUECIMENTO 0.85 km 5 min @ 5:45-6:00/km FC 129-146 bpm | — |

**Descanso:** QUARTA (Descanso obrigatório pela sequência recente de 5 dias e TSB próximo de -20.)


**Sua avaliação:** preferido: ___ · barraria algo? ___


## Caso 3 — avancado-tsb-baixo, repetição 3


### Plano A

**Objetivo da semana:** Priorizar recuperação da fadiga e manter corrida fácil, preservando a consistência para a meta sub-3h na maratona.  
**Volume planejado:** 26.36 km (alvo 66 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 5.5 km | 34:00 | 6:00-6:30/km | 129-146 bpm | 2 | AQUECIMENTO 1.6 km 10 min<br>PRINCIPAL 3.1 km 19 min @ 6:00-6:30/km FC 129-146 bpm<br>DESAQUECIMENTO 0.8 km 5 min @ 6:00-6:30/km FC 129-146 bpm | — |
| SEXTA | CONTINUO | 6.75 km | 40:00 | 5:40-6:00/km | 146-153 bpm | 4 | AQUECIMENTO 1.32 km 8 min @ 5:50-6:20/km FC 129-146 bpm<br>PRINCIPAL 4.63 km 27 min @ 5:40-6:00/km FC 146-153 bpm<br>DESAQUECIMENTO 0.8 km 5 min @ 6:00-6:30/km FC 129-146 bpm | — |
| SABADO | LONGO | 14.11 km | 85:00 | 5:50-6:10/km | 146-153 bpm | 4 | AQUECIMENTO 1.31 km 8 min @ 5:55-6:20/km FC 129-153 bpm<br>PRINCIPAL 12 km 72 min @ 5:50-6:10/km FC 146-153 bpm<br>DESAQUECIMENTO 0.8 km 5 min @ 6:00-6:30/km FC 129-146 bpm | — |

**Descanso:** TERCA (Descanso para limitar a carga acumulada.), QUARTA (Descanso obrigatório diante do alerta de dias consecutivos e TSB -20.), QUINTA (Descanso completo entre as sessões leves.), DOMINGO (Recuperação após o longo de sábado.)


### Plano B

**Objetivo da semana:** Manter volume atual e introduzir fartlek e tempo run para variabilidade.  
**Volume planejado:** 70.37 km (alvo 63 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 13.62 km | 75:00 | 5:23-5:38/km | 146-153 bpm | 4 | AQUECIMENTO 1.82 km 10 min FC 129-146 bpm<br>PRINCIPAL 10.89 km 60 min @ 5:23-5:38/km FC 146-153 bpm<br>DESAQUECIMENTO 0.91 km 5 min FC 129-146 bpm | — |
| QUARTA | CONTINUO | 16.54 km | 90:00 | 5:23-5:30/km | 146-162 bpm | 5 | AQUECIMENTO 1.84 km 10 min FC 129-146 bpm<br>PRINCIPAL 13.78 km 75 min @ 5:23-5:30/km FC 146-162 bpm<br>DESAQUECIMENTO 0.92 km 5 min FC 129-146 bpm | — |
| SEXTA | REGENERATIVO | 13.62 km | 75:00 | 5:23-5:38/km | 146-153 bpm | 4 | AQUECIMENTO 1.82 km 10 min FC 129-146 bpm<br>PRINCIPAL 10.89 km 60 min @ 5:23-5:38/km FC 146-153 bpm<br>DESAQUECIMENTO 0.91 km 5 min FC 129-146 bpm | — |
| SABADO | LONGO | 26.59 km | 148:00 | 5:30-5:38/km | 146-153 bpm | 5 | AQUECIMENTO 2.69 km 15 min FC 129-146 bpm<br>PRINCIPAL 21.56 km 120 min @ 5:30-5:38/km FC 146-153 bpm<br>DESAQUECIMENTO 2.34 km 13 min FC 129-146 bpm | — |

**Descanso:** TERCA (Recuperação após 5 dias consecutivos de treino), QUINTA (Recuperação para evitar fadiga acumulada)


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

**Objetivo da semana:** Manter corrida leve e sem dor, respeitando a tendinite de Aquiles e o limite de esforço até Z2.  
**Volume planejado:** 24.34 km (alvo 24.4 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 5.5 km | 40:00 | 7:00-7:30/km | 126-143 bpm | 2 | AQUECIMENTO 1.38 km 10 min<br>PRINCIPAL 3.43 km 25 min @ 7:00-7:30/km FC 126-143 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 126-143 bpm | — |
| QUARTA | CONTINUO | 6.67 km | 45:00 | 6:30-7:00/km | 143-150 bpm | 4 | AQUECIMENTO 1.19 km 8 min @ 6:30-7:00/km FC 143-150 bpm<br>PRINCIPAL 4.74 km 32 min @ 6:30-7:00/km FC 143-150 bpm<br>DESAQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 143-150 bpm | — |
| SEXTA | CONTINUO | 6.67 km | 45:00 | 6:30-7:00/km | 143-150 bpm | 4 | AQUECIMENTO 1.19 km 8 min @ 6:30-7:00/km FC 143-150 bpm<br>PRINCIPAL 4.74 km 32 min @ 6:30-7:00/km FC 143-150 bpm<br>DESAQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 143-150 bpm | — |
| SABADO | REGENERATIVO | 5.5 km | 40:00 | 7:00-7:30/km | 126-143 bpm | 2 | AQUECIMENTO 1.38 km 10 min<br>PRINCIPAL 3.43 km 25 min @ 7:00-7:30/km FC 126-143 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 126-143 bpm | — |

**Descanso:** TERCA (Descanso completo para recuperação do tendão de Aquiles.), QUINTA (Descanso completo entre as sessões leves.), DOMINGO (Recuperação após a sessão de sábado.)


### Plano B

**Objetivo da semana:** Recuperação segura e progressiva da lesão no tendão de Aquiles.  
**Volume planejado:** 28.4 km (alvo 27 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 6.49 km | 45:00 | 6:52-7:00/km | 126-143 bpm | 3 | AQUECIMENTO 1.44 km 10 min FC 126-143 bpm<br>PRINCIPAL 4.33 km 30 min @ 6:52-7:00/km FC 126-143 bpm<br>DESAQUECIMENTO 0.72 km 5 min FC 126-143 bpm | — |
| QUARTA | CONTINUO | 7.71 km | 45:00 | 5:36-5:48/km | 143-150 bpm | 4 | AQUECIMENTO 1.63 km 10 min FC 143-150 bpm<br>PRINCIPAL 5.26 km 30 min @ 5:36-5:48/km FC 143-150 bpm<br>DESAQUECIMENTO 0.82 km 5 min FC 143-150 bpm | — |
| SEXTA | CONTINUO | 7.71 km | 45:00 | 5:36-5:48/km | 143-150 bpm | 4 | AQUECIMENTO 1.63 km 10 min FC 143-150 bpm<br>PRINCIPAL 5.26 km 30 min @ 5:36-5:48/km FC 143-150 bpm<br>DESAQUECIMENTO 0.82 km 5 min FC 143-150 bpm | — |
| SABADO | REGENERATIVO | 6.49 km | 45:00 | 6:52-7:00/km | 126-143 bpm | 3 | AQUECIMENTO 1.44 km 10 min FC 126-143 bpm<br>PRINCIPAL 4.33 km 30 min @ 6:52-7:00/km FC 126-143 bpm<br>DESAQUECIMENTO 0.72 km 5 min FC 126-143 bpm | — |

**Descanso:** TERCA (Recuperação e prevenção de sobrecarga), QUINTA (Recuperação e prevenção de sobrecarga)


**Sua avaliação:** preferido: ___ · barraria algo? ___


## Caso 5 — com-lesao-ativa, repetição 2


### Plano A

**Objetivo da semana:** Retomar a corrida sem dor, mantendo quatro sessões leves em Z1-Z2 e interrompendo se a dor no Aquiles aumentar.  
**Volume planejado:** 23.9 km (alvo 18.6 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 4.5 km | 33:00 | 7:00-7:30/km | 126-143 bpm | 2 | AQUECIMENTO 1.38 km 10 min<br>PRINCIPAL 2.43 km 18 min @ 7:00-7:30/km FC 126-143 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 126-143 bpm | — |
| QUARTA | CONTINUO | 7.45 km | 45:00 | 5:55-6:00/km | 143-150 bpm | 3 | AQUECIMENTO 1.28 km 8 min @ 6:00-6:30/km FC 126-143 bpm<br>PRINCIPAL 5.37 km 32 min @ 5:55-6:00/km FC 143-150 bpm<br>DESAQUECIMENTO 0.8 km 5 min @ 6:00-6:30/km FC 126-143 bpm | — |
| SEXTA | CONTINUO | 7.45 km | 45:00 | 5:55-6:00/km | 143-150 bpm | 3 | AQUECIMENTO 1.28 km 8 min @ 6:00-6:30/km FC 126-143 bpm<br>PRINCIPAL 5.37 km 32 min @ 5:55-6:00/km FC 143-150 bpm<br>DESAQUECIMENTO 0.8 km 5 min @ 6:00-6:30/km FC 126-143 bpm | — |
| SABADO | REGENERATIVO | 4.5 km | 33:00 | 7:00-7:30/km | 126-143 bpm | 2 | AQUECIMENTO 1.38 km 10 min<br>PRINCIPAL 2.43 km 18 min @ 7:00-7:30/km FC 126-143 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 126-143 bpm | — |

**Descanso:** TERCA (Descanso completo para recuperação do tendão de Aquiles.), QUINTA (Descanso completo entre as sessões leves.), DOMINGO (Recuperação após a sessão de sábado.)


### Plano B

**Objetivo da semana:** Recuperação segura e progressiva da tendinite no tendão de Aquiles.  
**Volume planejado:** 27.72 km (alvo 27 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 6.27 km | 45:00 | 6:52-7:30/km | 126-143 bpm | 3 | AQUECIMENTO 1.39 km 10 min @ 6:52-7:30/km FC 126-143 bpm<br>PRINCIPAL 4.18 km 30 min @ 6:52-7:30/km FC 126-143 bpm<br>DESAQUECIMENTO 0.7 km 5 min @ 6:52-7:30/km FC 126-143 bpm | — |
| QUARTA | CONTINUO | 7.59 km | 45:00 | 5:36-6:00/km | 143-150 bpm | 4 | AQUECIMENTO 1.72 km 10 min @ 5:36-6:00/km FC 143-150 bpm<br>PRINCIPAL 5.17 km 30 min @ 5:36-6:00/km FC 143-150 bpm<br>DESAQUECIMENTO 0.7 km 5 min @ 6:52-7:30/km FC 126-143 bpm | — |
| SEXTA | CONTINUO | 7.59 km | 45:00 | 5:36-6:00/km | 143-150 bpm | 4 | AQUECIMENTO 1.72 km 10 min @ 5:36-6:00/km FC 143-150 bpm<br>PRINCIPAL 5.17 km 30 min @ 5:36-6:00/km FC 143-150 bpm<br>DESAQUECIMENTO 0.7 km 5 min @ 6:52-7:30/km FC 126-143 bpm | — |
| SABADO | REGENERATIVO | 6.27 km | 45:00 | 6:52-7:30/km | 126-143 bpm | 3 | AQUECIMENTO 1.39 km 10 min @ 6:52-7:30/km FC 126-143 bpm<br>PRINCIPAL 4.18 km 30 min @ 6:52-7:30/km FC 126-143 bpm<br>DESAQUECIMENTO 0.7 km 5 min @ 6:52-7:30/km FC 126-143 bpm | — |

**Descanso:** TERCA (Recuperação entre treinos), QUINTA (Recuperação entre treinos), DOMINGO (Recuperação após semana de treinos)


**Sua avaliação:** preferido: ___ · barraria algo? ___


## Caso 6 — com-lesao-ativa, repetição 3


### Plano A

**Objetivo da semana:** Retomar a corrida sem dor, mantendo quatro sessões leves em Z1-Z2 e respeitando a tendinite ativa do Aquiles.  
**Volume planejado:** 22.06 km (alvo 19 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 4.5 km | 33:00 | 7:00-7:30/km | 126-143 bpm | 2 | AQUECIMENTO 1.38 km 10 min<br>PRINCIPAL 2.43 km 18 min @ 7:00-7:30/km FC 126-143 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 126-143 bpm | — |
| QUARTA | CONTINUO | 6.53 km | 45:00 | 6:30-7:00/km | 143-150 bpm | 3 | AQUECIMENTO 1.1 km 8 min @ 7:00-7:30/km FC 126-143 bpm<br>PRINCIPAL 4.74 km 32 min @ 6:30-7:00/km FC 143-150 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 126-143 bpm | — |
| SEXTA | CONTINUO | 6.53 km | 45:00 | 6:30-7:00/km | 143-150 bpm | 3 | AQUECIMENTO 1.1 km 8 min @ 7:00-7:30/km FC 126-143 bpm<br>PRINCIPAL 4.74 km 32 min @ 6:30-7:00/km FC 143-150 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 126-143 bpm | — |
| SABADO | REGENERATIVO | 4.5 km | 33:00 | 7:00-7:30/km | 126-143 bpm | 2 | AQUECIMENTO 1.38 km 10 min<br>PRINCIPAL 2.43 km 18 min @ 7:00-7:30/km FC 126-143 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 126-143 bpm | — |

**Descanso:** TERCA (Descanso entre sessões para proteger o tendão de Aquiles.), QUINTA (Descanso entre sessões para permitir recuperação.), DOMINGO (Descanso após a sessão de sábado.)


### Plano B

**Objetivo da semana:** Recuperação segura e progressiva da tendinite no tendão de Aquiles.  
**Volume planejado:** 28.44 km (alvo 27 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 6.49 km | 45:00 | 6:52-7:00/km | 126-143 bpm | 3 | AQUECIMENTO 1.44 km 10 min @ 6:52-7:00/km FC 126-143 bpm<br>PRINCIPAL 4.33 km 30 min @ 6:52-7:00/km FC 126-143 bpm<br>DESAQUECIMENTO 0.72 km 5 min @ 6:52-7:00/km FC 126-143 bpm | — |
| QUARTA | CONTINUO | 7.73 km | 45:00 | 5:36-5:48/km | 143-150 bpm | 4 | AQUECIMENTO 1.75 km 10 min @ 5:36-5:48/km FC 143-150 bpm<br>PRINCIPAL 5.26 km 30 min @ 5:36-5:48/km FC 143-150 bpm<br>DESAQUECIMENTO 0.72 km 5 min @ 6:52-7:00/km FC 126-143 bpm | — |
| SEXTA | CONTINUO | 7.73 km | 45:00 | 5:36-5:48/km | 143-150 bpm | 4 | AQUECIMENTO 1.75 km 10 min @ 5:36-5:48/km FC 143-150 bpm<br>PRINCIPAL 5.26 km 30 min @ 5:36-5:48/km FC 143-150 bpm<br>DESAQUECIMENTO 0.72 km 5 min @ 6:52-7:00/km FC 126-143 bpm | — |
| SABADO | REGENERATIVO | 6.49 km | 45:00 | 6:52-7:00/km | 126-143 bpm | 3 | AQUECIMENTO 1.44 km 10 min @ 6:52-7:00/km FC 126-143 bpm<br>PRINCIPAL 4.33 km 30 min @ 6:52-7:00/km FC 126-143 bpm<br>DESAQUECIMENTO 0.72 km 5 min @ 6:52-7:00/km FC 126-143 bpm | — |

**Descanso:** TERCA (Recuperação ativa), QUINTA (Recuperação ativa), DOMINGO (Recuperação ativa)


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

**Objetivo da semana:** Consolidar a base aeróbica para completar a primeira 10k, com o longo fácil como sessão-chave e sem estímulos intensos nesta semana.  
**Volume planejado:** 22.73 km (alvo 22.9 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 6.6 km | 48:00 | 7:00-7:15/km | 140-147 bpm | 4 | AQUECIMENTO 1.32 km 10 min<br>PRINCIPAL 4.63 km 33 min @ 7:00-7:15/km FC 140-147 bpm<br>DESAQUECIMENTO 0.65 km 5 min @ 7:25-7:52/km FC 124-140 bpm | — |
| QUARTA | CONTINUO | 7.69 km | 52:00 | 6:30-6:50/km | 140-155 bpm | 5 | AQUECIMENTO 1.19 km 8 min @ 6:40-6:50/km FC 140-147 bpm<br>PRINCIPAL 5.85 km 39 min @ 6:30-6:50/km FC 140-155 bpm<br>DESAQUECIMENTO 0.65 km 5 min @ 7:25-7:52/km FC 124-140 bpm | — |
| SABADO | LONGO | 8.44 km | 60:00 | 6:50-7:15/km | 140-147 bpm | 4 | AQUECIMENTO 1.12 km 8 min @ 6:50-7:25/km FC 124-147 bpm<br>PRINCIPAL 6.67 km 47 min @ 6:50-7:15/km FC 140-147 bpm<br>DESAQUECIMENTO 0.65 km 5 min @ 7:25-7:52/km FC 124-140 bpm | — |

**Descanso:** SEXTA (Descanso completo entre o contínuo de quarta e o longo de sábado.)


### Plano B

**Objetivo da semana:** Completar a primeira 10k com segurança e progressão controlada.  
**Volume planejado:** 26 km (alvo 26 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | CONTINUO | 7 km | 51:00 | 6:52-7:25/km | 140-147 bpm | 4 | AQUECIMENTO 1.32 km 10 min FC 124-140 bpm<br>PRINCIPAL 5.02 km 36 min @ 6:52-7:25/km FC 140-147 bpm<br>DESAQUECIMENTO 0.66 km 5 min | — |
| QUARTA | CONTINUO | 7 km | 49:00 | 6:30-6:52/km | 140-155 bpm | 5 | AQUECIMENTO 1.32 km 10 min FC 124-140 bpm<br>PRINCIPAL 5.02 km 34 min @ 6:30-6:52/km FC 140-155 bpm<br>DESAQUECIMENTO 0.66 km 5 min | — |
| SEXTA | REGENERATIVO | 4 km | 29:00 | 7:00-7:30/km | 124-147 bpm | 3 | AQUECIMENTO 0.66 km 5 min FC 124-140 bpm<br>PRINCIPAL 2.68 km 19 min @ 7:00-7:30/km FC 124-147 bpm<br>DESAQUECIMENTO 0.66 km 5 min | — |
| SABADO | LONGO | 8 km | 58:00 | 6:52-7:25/km | 140-147 bpm | 5 | AQUECIMENTO 1.32 km 10 min FC 124-140 bpm<br>PRINCIPAL 6.02 km 43 min @ 6:52-7:25/km FC 140-147 bpm<br>DESAQUECIMENTO 0.66 km 5 min | — |

**Descanso:** TERCA (Recuperação entre treinos), QUINTA (Recuperação entre treinos), DOMINGO (Recuperação após treino longo)


**Sua avaliação:** preferido: ___ · barraria algo? ___


## Caso 8 — iniciante-sem-lesao, repetição 2


### Plano A

**Objetivo da semana:** Construir base aeróbica para completar a primeira 10K, mantendo as três sessões prescritas em Z2-Z3 e sem acrescentar intensidade diante do alerta de progressão rápida.  
**Volume planejado:** 22.39 km (alvo 22.2 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 6.4 km | 46:00 | 6:52-7:15/km | 140-147 bpm | 4 | AQUECIMENTO 1.32 km 10 min<br>PRINCIPAL 4.16 km 29 min @ 6:52-7:15/km FC 140-147 bpm<br>DESAQUECIMENTO 0.92 km 7 min @ 7:25-7:52/km FC 124-140 bpm | — |
| QUARTA | CONTINUO | 7.44 km | 52:00 | 6:30-7:15/km | 140-155 bpm | 5 | AQUECIMENTO 1.09 km 8 min @ 6:52-7:52/km FC 124-147 bpm<br>PRINCIPAL 5.67 km 39 min @ 6:30-7:15/km FC 140-155 bpm<br>DESAQUECIMENTO 0.68 km 5 min @ 6:52-7:52/km FC 124-147 bpm | — |
| SABADO | LONGO | 8.55 km | 60:00 | 6:37-7:15/km | 140-147 bpm | 4 | AQUECIMENTO 1.09 km 8 min @ 6:52-7:52/km FC 124-147 bpm<br>PRINCIPAL 6.78 km 47 min @ 6:37-7:15/km FC 140-147 bpm<br>DESAQUECIMENTO 0.68 km 5 min @ 6:52-7:52/km FC 124-147 bpm | — |

**Descanso:** SEXTA (Descanso completo para recuperar entre o contínuo de quarta e o longo de sábado.)


### Plano B

**Objetivo da semana:** Completar a primeira 10k com foco em base aeróbica e progressão segura.  
**Volume planejado:** 25.5 km (alvo 25.5 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | CONTINUO | 7 km | 50:00 | 6:45-7:00/km | 140-147 bpm | 4 | AQUECIMENTO 1.32 km 10 min FC 124-140 bpm<br>PRINCIPAL 5.02 km 35 min @ 6:45-7:00/km FC 140-147 bpm<br>DESAQUECIMENTO 0.66 km 5 min | — |
| QUARTA | CONTINUO | 6.5 km | 45:00 | 6:30-6:45/km | 140-155 bpm | 5 | AQUECIMENTO 1.32 km 10 min FC 124-140 bpm<br>PRINCIPAL 4.52 km 30 min @ 6:30-6:45/km FC 140-155 bpm<br>DESAQUECIMENTO 0.66 km 5 min | — |
| SEXTA | REGENERATIVO | 4 km | 30:00 | 7:00-7:30/km | 124-140 bpm | 3 | AQUECIMENTO 1.32 km 10 min<br>PRINCIPAL 2.02 km 15 min @ 7:00-7:30/km FC 124-140 bpm<br>DESAQUECIMENTO 0.66 km 5 min FC 124-140 bpm | — |
| SABADO | LONGO | 8 km | 56:00 | 6:45-7:00/km | 140-147 bpm | 5 | AQUECIMENTO 1.32 km 10 min FC 124-140 bpm<br>PRINCIPAL 6.02 km 41 min @ 6:45-7:00/km FC 140-147 bpm<br>DESAQUECIMENTO 0.66 km 5 min | — |

**Descanso:** TERCA (Recuperação ativa após treino de segunda-feira), QUINTA (Recuperação ativa após treino de quarta-feira), DOMINGO (Recuperação após treino longo de sábado)


**Sua avaliação:** preferido: ___ · barraria algo? ___


## Caso 9 — iniciante-sem-lesao, repetição 3


### Plano A

**Objetivo da semana:** Consolidar a base aeróbica para completar a primeira 10K, introduzindo o longo com esforço controlado em Z2.  
**Volume planejado:** 22.52 km (alvo 22.5 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 6.6 km | 48:00 | 6:52-7:15/km | 140-147 bpm | 4 | AQUECIMENTO 1.32 km 10 min<br>PRINCIPAL 4.62 km 33 min @ 6:52-7:15/km FC 140-147 bpm<br>DESAQUECIMENTO 0.66 km 5 min @ 7:25-7:50/km FC 124-140 bpm | — |
| QUARTA | CONTINUO | 7.39 km | 52:00 | 6:30-7:15/km | 140-155 bpm | 5 | AQUECIMENTO 1.06 km 8 min @ 7:15-7:50/km FC 124-147 bpm<br>PRINCIPAL 5.67 km 39 min @ 6:30-7:15/km FC 140-155 bpm<br>DESAQUECIMENTO 0.66 km 5 min @ 7:25-7:50/km FC 124-140 bpm | — |
| SABADO | LONGO | 8.53 km | 60:00 | 6:37-7:15/km | 140-147 bpm | 4 | AQUECIMENTO 0.66 km 5 min @ 7:15-7:50/km FC 124-147 bpm<br>PRINCIPAL 7.21 km 50 min @ 6:37-7:15/km FC 140-147 bpm<br>DESAQUECIMENTO 0.66 km 5 min @ 7:15-7:50/km FC 124-147 bpm | — |

**Descanso:** SEXTA (Descanso completo para recuperação antes do longo de sábado.)


### Plano B

**Objetivo da semana:** Completar a primeira 10k com foco em base aeróbica e variabilidade de estímulos.  
**Volume planejado:** 22.95 km (alvo 22.5 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 7.14 km | 52:00 | 6:50-7:25/km | 140-147 bpm | 4 | AQUECIMENTO 1.31 km 10 min @ 7:25-7:50/km FC 124-140 bpm<br>PRINCIPAL 4.91 km 35 min @ 6:50-7:25/km FC 140-147 bpm<br>DESAQUECIMENTO 0.92 km 7 min @ 7:25-7:50/km FC 124-140 bpm | — |
| QUARTA | CONTINUO | 7.52 km | 52:00 | 6:30-6:50/km | 140-155 bpm | 5 | AQUECIMENTO 1.31 km 10 min @ 7:25-7:50/km FC 124-140 bpm<br>PRINCIPAL 5.55 km 37 min @ 6:30-6:50/km FC 140-155 bpm<br>DESAQUECIMENTO 0.66 km 5 min @ 7:25-7:50/km FC 124-140 bpm | — |
| SEXTA | REGENERATIVO | 0 km | 00:00 | 00:00-00:00/km | 00-00 bpm | 1 | PRINCIPAL 1 min FC 00-00 bpm<br>PRINCIPAL 20 min FC 00-00 bpm | — |
| SABADO | LONGO | 8.29 km | 60:00 | 6:50-7:25/km | 140-147 bpm | 4 | AQUECIMENTO 1.31 km 10 min @ 7:25-7:50/km FC 124-140 bpm<br>PRINCIPAL 6.32 km 45 min @ 6:50-7:25/km FC 140-147 bpm<br>DESAQUECIMENTO 0.66 km 5 min @ 7:25-7:50/km FC 124-140 bpm | — |

**Descanso:** TERCA (Recuperação após treino contínuo), QUINTA (Recuperação após treino fácil)


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

**Objetivo da semana:** Retomar a corrida com uma sessão conservadora em Z2 após 28 dias sem treinos e iniciar a construção de condicionamento. Zonas estimadas; recomenda-se teste de limiar.  
**Volume planejado:** 8.89 km (alvo 0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SABADO | LONGO | 8.89 km | 60:00 | 6:30-7:00/km | 130-136 bpm | 4 | AQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 115-130 bpm<br>PRINCIPAL 7.41 km 50 min @ 6:30-7:00/km FC 130-136 bpm<br>DESAQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 115-130 bpm | — |

**Descanso:** SEGUNDA (Descanso para retomar gradualmente após período sem treinos.), QUARTA (Descanso completo; manter a progressão conservadora nesta semana.), SEXTA (Descanso antes do longo de sábado.)


### Plano B

**Objetivo da semana:** Melhorar condicionamento geral com foco em variabilidade de estímulos.  
**Volume planejado:** 25.87 km (alvo 24 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 5 km | 36:00 | 7:00-7:30/km | 115-130 bpm | 3 | AQUECIMENTO 1.43 km 10 min<br>PRINCIPAL 2.14 km 16 min FC 115-130 bpm<br>DESAQUECIMENTO 1.43 km 10 min FC 115-130 bpm | — |
| QUARTA | FARTLEK | 6 km | 45:00 | 6:30-7:00/km | 136-144 bpm | 5 | AQUECIMENTO 1.43 km 10 min FC 115-130 bpm<br>INTERVALADO 1 min FC 70-80% FCmax<br>RECUPERACAO 0.25 km 2 min FC 60-70% FCmax<br>INTERVALADO 1 min FC 70-80% FCmax<br>RECUPERACAO 0.25 km 2 min FC 60-70% FCmax<br>INTERVALADO 1 min FC 70-80% FCmax<br>RECUPERACAO 0.25 km 2 min FC 60-70% FCmax<br>INTERVALADO 1 min FC 70-80% FCmax<br>RECUPERACAO 0.25 km 2 min FC 60-70% FCmax<br>INTERVALADO 1 min FC 70-80% FCmax<br>RECUPERACAO 0.25 km 2 min FC 60-70% FCmax<br>DESAQUECIMENTO 2.86 km 20 min FC 115-130 bpm | — |
| SEXTA | TEMPO_RUN | 6.06 km | 40:00 | 6:00-6:30/km | 136-144 bpm | 6 | AQUECIMENTO 1.43 km 10 min FC 115-130 bpm<br>PRINCIPAL 3.2 km 20 min FC 136-144 bpm<br>DESAQUECIMENTO 1.43 km 10 min FC 115-130 bpm | — |
| SABADO | LONGO | 8.81 km | 60:00 | 6:30-7:00/km | 130-136 bpm | 4 | AQUECIMENTO 1.43 km 10 min FC 115-130 bpm<br>PRINCIPAL 6.67 km 45 min FC 130-136 bpm<br>DESAQUECIMENTO 0.71 km 5 min FC 115-130 bpm | — |

**Descanso:** TERCA (Recuperação), QUINTA (Recuperação)


**Sua avaliação:** preferido: ___ · barraria algo? ___


## Caso 11 — sem-dados, repetição 2


### Plano A

**Objetivo da semana:** Melhorar condicionamento geral com foco em variabilidade de estímulos.  
**Volume planejado:** 25.55 km (alvo 26 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | REGENERATIVO | 5 km | 36:00 | 7:00-7:30/km | 115-130 bpm | 2 | AQUECIMENTO 1.43 km 10 min<br>PRINCIPAL 2.14 km 16 min FC 115-130 bpm<br>DESAQUECIMENTO 1.43 km 10 min FC 115-130 bpm | — |
| QUARTA | INTERVALADO | 6 km | 36:00 | 5:30-6:00/km | 153-162 bpm | 8 | AQUECIMENTO 1.43 km 10 min FC 115-130 bpm<br>INTERVALADO 0.54 km 2 min FC 153-162 bpm<br>RECUPERACAO 0.25 km 2 min FC 130-136 bpm<br>INTERVALADO 0.54 km 2 min FC 153-162 bpm<br>RECUPERACAO 0.25 km 2 min FC 130-136 bpm<br>INTERVALADO 0.54 km 2 min FC 153-162 bpm<br>RECUPERACAO 0.25 km 2 min FC 130-136 bpm<br>INTERVALADO 0.54 km 2 min FC 153-162 bpm<br>RECUPERACAO 0.25 km 2 min FC 130-136 bpm<br>DESAQUECIMENTO 1.43 km 10 min FC 115-130 bpm | — |
| SEXTA | REGENERATIVO | 5 km | 36:00 | 7:00-7:30/km | 115-130 bpm | 2 | AQUECIMENTO 1.43 km 10 min<br>PRINCIPAL 2.14 km 16 min FC 115-130 bpm<br>DESAQUECIMENTO 1.43 km 10 min FC 115-130 bpm | — |
| SABADO | LONGO | 9.55 km | 65:00 | 6:30-7:00/km | 130-136 bpm | 4 | AQUECIMENTO 1.43 km 10 min FC 115-130 bpm<br>PRINCIPAL 7.41 km 50 min FC 130-136 bpm<br>DESAQUECIMENTO 0.71 km 5 min FC 115-130 bpm | — |

**Descanso:** TERCA (Recuperação ativa), QUINTA (Recuperação ativa)


### Plano B

**Objetivo da semana:** Retomar a corrida com um longo controlado de 60 min em Z2; zonas estimadas, recomenda-se teste de limiar antes de prescrever pace.  
**Volume planejado:** 8.79 km (alvo 0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SABADO | LONGO | 8.79 km | 60:00 | 6:30-7:00/km | 130-136 bpm | 4 | AQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 115-130 bpm<br>PRINCIPAL 7.41 km 50 min @ 6:30-7:00/km FC 130-136 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 115-130 bpm | — |

**Descanso:** SEGUNDA (Descanso para retorno conservador após 28 dias sem treinos registrados.), QUARTA (Descanso entre sessões; não há histórico recente para sustentar carga adicional.), SEXTA (Descanso antes do longo-chave de sábado.)


**Sua avaliação:** preferido: ___ · barraria algo? ___


## Caso 12 — sem-dados, repetição 3


### Plano A

**Objetivo da semana:** Retomar com segurança após 28 dias sem treinos; manter o longo de sábado em Z2 e usar FC estimada até realizar teste de limiar.  
**Volume planejado:** 8.89 km (alvo 0 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SABADO | LONGO | 8.89 km | 60:00 | 6:30-7:00/km | 130-136 bpm | 4 | AQUECIMENTO 1.19 km 8 min @ 6:30-7:00/km FC 115-130 bpm<br>PRINCIPAL 6.96 km 47 min @ 6:30-7:00/km FC 130-136 bpm<br>DESAQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 115-130 bpm | — |

**Descanso:** SEGUNDA (Descanso; semana de retomada após período sem treinos.), QUARTA (Descanso para manter a retomada conservadora.), SEXTA (Descanso antes da sessão-chave de sábado.)


### Plano B

**Objetivo da semana:** Iniciar com volume conservador para melhorar condicionamento geral.  
**Volume planejado:** 23.63 km (alvo 18 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| SEGUNDA | CONTINUO | 5.84 km | 40:00 | 6:30-7:00/km | 130-136 bpm | 4 | AQUECIMENTO 1.43 km 10 min FC 115-130 bpm<br>PRINCIPAL 3.7 km 25 min FC 130-136 bpm<br>DESAQUECIMENTO 0.71 km 5 min FC 115-130 bpm | — |
| QUARTA | INTERVALADO | 3.14 km | 39:00 | 5:00-5:30/km | 153-162 bpm | 8 | AQUECIMENTO 1.43 km 10 min FC 115-130 bpm<br>INTERVALADO 4 min FC 153-162 bpm<br>RECUPERACAO 0.25 km 2 min FC 130-136 bpm<br>INTERVALADO 4 min FC 153-162 bpm<br>RECUPERACAO 0.25 km 2 min FC 130-136 bpm<br>INTERVALADO 4 min FC 153-162 bpm<br>RECUPERACAO 0.25 km 2 min FC 130-136 bpm<br>INTERVALADO 4 min FC 153-162 bpm<br>RECUPERACAO 0.25 km 2 min FC 130-136 bpm<br>DESAQUECIMENTO 0.71 km 5 min FC 115-130 bpm | — |
| SEXTA | CONTINUO | 5.84 km | 40:00 | 6:30-7:00/km | 130-136 bpm | 4 | AQUECIMENTO 1.43 km 10 min FC 115-130 bpm<br>PRINCIPAL 3.7 km 25 min FC 130-136 bpm<br>DESAQUECIMENTO 0.71 km 5 min FC 115-130 bpm | — |
| SABADO | LONGO | 8.81 km | 60:00 | 6:30-7:00/km | 130-136 bpm | 5 | AQUECIMENTO 1.43 km 10 min FC 115-130 bpm<br>PRINCIPAL 6.67 km 45 min FC 130-136 bpm<br>DESAQUECIMENTO 0.71 km 5 min FC 115-130 bpm | — |

**Descanso:** TERCA (Recuperação entre treinos), QUINTA (Recuperação entre treinos), DOMINGO (Recuperação após treino longo)


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

**Objetivo da semana:** Preparar para a Meia Maratona da Cidade, garantindo frescor e confiança.  
**Volume planejado:** 37.54 km (alvo 36.1 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| TERCA | REGENERATIVO | 4.9 km | 34:00 | 6:52-7:00/km | 128-145 bpm | 3 | AQUECIMENTO 0.72 km 5 min @ 6:52-7:00/km FC 128-145 bpm<br>PRINCIPAL 3.46 km 24 min @ 6:52-7:00/km FC 128-145 bpm<br>DESAQUECIMENTO 0.72 km 5 min @ 6:52-7:00/km FC 128-145 bpm | — |
| QUINTA | CONTINUO | 5.77 km | 34:00 | 5:48-6:00/km | 145-152 bpm | 4 | AQUECIMENTO 0.85 km 5 min @ 5:48-6:00/km FC 145-152 bpm<br>PRINCIPAL 4.07 km 24 min @ 5:48-6:00/km FC 145-152 bpm<br>DESAQUECIMENTO 0.85 km 5 min @ 5:48-6:00/km FC 145-152 bpm | — |
| SABADO | CONTINUO | 5.77 km | 34:00 | 5:48-6:00/km | 145-152 bpm | 4 | AQUECIMENTO 0.85 km 5 min @ 5:48-6:00/km FC 145-152 bpm<br>PRINCIPAL 4.07 km 24 min @ 5:48-6:00/km FC 145-152 bpm<br>DESAQUECIMENTO 0.85 km 5 min @ 5:48-6:00/km FC 145-152 bpm | — |
| DOMINGO | PROVA | 21.1 km | 01:28:00 | 4:10 | — | — | — | Meia Maratona da Cidade |

**Descanso:** SEGUNDA (Recuperação após treino contínuo), QUARTA (Recuperação após treino regenerativo), SEXTA (Recuperação antes da prova)


### Plano B

**Objetivo da semana:** Priorizar frescor para a Meia Maratona da Cidade, manter apenas corridas leves antes da prova e buscar PR nos 21 km.  
**Volume planejado:** 34.89 km (alvo 33.4 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| TERCA | REGENERATIVO | 4.1 km | 29:00 | 7:00-7:10/km | 128-145 bpm | 2 | AQUECIMENTO 1.41 km 10 min<br>PRINCIPAL 1.98 km 14 min @ 7:00-7:10/km FC 128-145 bpm<br>DESAQUECIMENTO 0.71 km 5 min @ 7:00-7:10/km FC 128-145 bpm | — |
| QUINTA | CONTINUO | 4.85 km | 34:00 | 6:55-7:05/km | 145-152 bpm | 3 | AQUECIMENTO 1.14 km 8 min @ 6:55-7:05/km FC 145-152 bpm<br>PRINCIPAL 3 km 21 min @ 6:55-7:05/km FC 145-152 bpm<br>DESAQUECIMENTO 0.71 km 5 min @ 7:00-7:10/km FC 128-145 bpm | — |
| SABADO | CONTINUO | 4.84 km | 34:00 | 6:55-7:05/km | 145-152 bpm | 3 | AQUECIMENTO 1.13 km 8 min @ 7:00-7:10/km FC 128-145 bpm<br>PRINCIPAL 3 km 21 min @ 6:55-7:05/km FC 145-152 bpm<br>DESAQUECIMENTO 0.71 km 5 min @ 7:00-7:10/km FC 128-145 bpm | — |
| DOMINGO | PROVA | 21.1 km | 01:28:00 | 4:10 | — | — | — | Meia Maratona da Cidade |

**Descanso:** SEGUNDA (Descanso antes das sessões leves de taper.), QUARTA (Recuperação entre as corridas curtas.), SEXTA (Descanso para chegar descansado à ativação de sábado.)


**Sua avaliação:** preferido: ___ · barraria algo? ___


## Caso 14 — taper-semana-prova, repetição 2


### Plano A

**Objetivo da semana:** Preparação final para a Meia Maratona da Cidade, priorizando frescor e confiança.  
**Volume planejado:** 37.1 km (alvo 37.1 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| TERCA | REGENERATIVO | 5 km | 35:00 | 6:52-7:00/km | 128-145 bpm | 3 | AQUECIMENTO 1.44 km 10 min<br>PRINCIPAL 2.84 km 20 min @ 6:52-7:00/km FC 128-145 bpm<br>DESAQUECIMENTO 0.72 km 5 min FC 128-145 bpm | — |
| QUINTA | CONTINUO | 5.5 km | 32:00 | 5:48-6:00/km | 145-152 bpm | 4 | AQUECIMENTO 1.69 km 10 min<br>PRINCIPAL 2.96 km 17 min @ 5:48-6:00/km FC 145-152 bpm<br>DESAQUECIMENTO 0.85 km 5 min FC 145-152 bpm | — |
| SABADO | CONTINUO | 5.5 km | 32:00 | 5:48-6:00/km | 145-152 bpm | 4 | AQUECIMENTO 1.69 km 10 min<br>PRINCIPAL 2.96 km 17 min @ 5:48-6:00/km FC 145-152 bpm<br>DESAQUECIMENTO 0.85 km 5 min FC 145-152 bpm | — |
| DOMINGO | PROVA | 21.1 km | 01:28:00 | 4:10 | — | — | — | Meia Maratona da Cidade |

**Descanso:** SEGUNDA (Recuperação ativa após treino longo), QUARTA (Recuperação ativa após treino leve)


### Plano B

**Objetivo da semana:** Chegar descansado à Meia Maratona da Cidade: apenas corridas fáceis antes da prova e competição no domingo, sem exceder os limites de pace informados.  
**Volume planejado:** 35.97 km (alvo 35.5 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| TERCA | REGENERATIVO | 4.8 km | 35:00 | 7:00-7:30/km | 128-145 bpm | 2 | AQUECIMENTO 1.38 km 10 min<br>PRINCIPAL 2.73 km 20 min @ 7:00-7:30/km FC 128-145 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 128-145 bpm | — |
| QUINTA | CONTINUO | 5.04 km | 34:00 | 6:30-7:00/km | 145-152 bpm | 3 | AQUECIMENTO 1.19 km 8 min @ 6:30-7:00/km FC 145-152 bpm<br>PRINCIPAL 3.11 km 21 min @ 6:30-7:00/km FC 145-152 bpm<br>DESAQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 145-152 bpm | — |
| SABADO | CONTINUO | 5.03 km | 34:00 | 6:30-7:00/km | 145-152 bpm | 3 | AQUECIMENTO 1.48 km 10 min @ 6:30-7:00/km FC 145-152 bpm<br>PRINCIPAL 2.81 km 19 min @ 6:30-7:00/km FC 145-152 bpm<br>DESAQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 145-152 bpm | — |
| DOMINGO | PROVA | 21.1 km | 01:28:00 | 4:10 | — | — | — | Meia Maratona da Cidade |

**Descanso:** SEGUNDA (Descanso para preservar frescor na semana da prova.), QUARTA (Recuperação entre as sessões leves.), SEXTA (Descanso completo antes da ativação de sábado.)


**Sua avaliação:** preferido: ___ · barraria algo? ___


## Caso 15 — taper-semana-prova, repetição 3


### Plano A

**Objetivo da semana:** Semana de prova: preservar o frescor para a Meia Maratona da Cidade no domingo, com ativações curtas e sem estímulos intensos.  
**Volume planejado:** 35.88 km (alvo 35.8 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| TERCA | REGENERATIVO | 4.7 km | 34:00 | 7:00-7:30/km | 128-145 bpm | 2 | AQUECIMENTO 1.38 km 10 min<br>PRINCIPAL 2.63 km 19 min @ 7:00-7:30/km FC 128-145 bpm<br>DESAQUECIMENTO 0.69 km 5 min @ 7:00-7:30/km FC 128-145 bpm | — |
| QUINTA | CONTINUO | 5.04 km | 34:00 | 6:30-7:00/km | 145-152 bpm | 3 | AQUECIMENTO 1.19 km 8 min @ 6:30-7:00/km FC 128-145 bpm<br>PRINCIPAL 3.11 km 21 min @ 6:30-7:00/km FC 145-152 bpm<br>DESAQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 128-145 bpm | — |
| SABADO | CONTINUO | 5.04 km | 34:00 | 6:30-7:00/km | 145-152 bpm | 3 | AQUECIMENTO 1.19 km 8 min @ 6:30-7:00/km FC 128-145 bpm<br>PRINCIPAL 3.11 km 21 min @ 6:30-7:00/km FC 145-152 bpm<br>DESAQUECIMENTO 0.74 km 5 min @ 6:30-7:00/km FC 128-145 bpm | — |
| DOMINGO | PROVA | 21.1 km | 01:28:00 | 4:10 | — | — | — | Meia Maratona da Cidade |

**Descanso:** SEGUNDA (Descanso para iniciar a semana da prova com recuperação.), QUARTA (Recuperação entre a corrida leve de terça e a ativação de quinta.), SEXTA (Descanso antes da ativação curta de sábado e da prova.)


### Plano B

**Objetivo da semana:** Preparação final para a Meia Maratona da Cidade, priorizando frescor e estratégia.  
**Volume planejado:** 37.1 km (alvo 37.1 km)

| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |
|---|---|---|---|---|---|---|---|---|
| TERCA | REGENERATIVO | 5 km | 35:00 | 6:52-7:00/km | 128-145 bpm | 3 | AQUECIMENTO 1.44 km 10 min<br>PRINCIPAL 2.84 km 20 min @ 6:52-7:00/km FC 128-145 bpm<br>DESAQUECIMENTO 0.72 km 5 min FC 128-145 bpm | — |
| QUINTA | CONTINUO | 5.5 km | 32:00 | 5:48-6:00/km | 145-152 bpm | 4 | AQUECIMENTO 1.69 km 10 min<br>PRINCIPAL 2.96 km 17 min @ 5:48-6:00/km FC 145-152 bpm<br>DESAQUECIMENTO 0.85 km 5 min FC 145-152 bpm | — |
| SABADO | CONTINUO | 5.5 km | 32:00 | 5:48-6:00/km | 145-152 bpm | 4 | AQUECIMENTO 1.69 km 10 min<br>PRINCIPAL 2.96 km 17 min @ 5:48-6:00/km FC 145-152 bpm<br>DESAQUECIMENTO 0.85 km 5 min FC 145-152 bpm | — |
| DOMINGO | PROVA | 21.1 km | 01:28:00 | 4:10 | — | — | — | Meia Maratona da Cidade |

**Descanso:** SEGUNDA (Recuperação após treino contínuo), QUARTA (Recuperação ativa para frescor antes da prova), SEXTA (Recuperação ativa para frescor antes da prova)


**Sua avaliação:** preferido: ___ · barraria algo? ___


---

<details><summary>Chave (abrir só depois de avaliar)</summary>

| caso | arquétipo | | |
|---|---|---|---|
| 1 | avancado-tsb-baixo | A = gpt-6-luna:medium | B = gpt-4o |
| 2 | avancado-tsb-baixo | A = gpt-4o | B = gpt-6-luna:medium |
| 3 | avancado-tsb-baixo | A = gpt-6-luna:medium | B = gpt-4o |
| 4 | com-lesao-ativa | A = gpt-6-luna:medium | B = gpt-4o |
| 5 | com-lesao-ativa | A = gpt-6-luna:medium | B = gpt-4o |
| 6 | com-lesao-ativa | A = gpt-6-luna:medium | B = gpt-4o |
| 7 | iniciante-sem-lesao | A = gpt-6-luna:medium | B = gpt-4o |
| 8 | iniciante-sem-lesao | A = gpt-6-luna:medium | B = gpt-4o |
| 9 | iniciante-sem-lesao | A = gpt-6-luna:medium | B = gpt-4o |
| 10 | sem-dados | A = gpt-6-luna:medium | B = gpt-4o |
| 11 | sem-dados | A = gpt-4o | B = gpt-6-luna:medium |
| 12 | sem-dados | A = gpt-6-luna:medium | B = gpt-4o |
| 13 | taper-semana-prova | A = gpt-4o | B = gpt-6-luna:medium |
| 14 | taper-semana-prova | A = gpt-4o | B = gpt-6-luna:medium |
| 15 | taper-semana-prova | A = gpt-6-luna:medium | B = gpt-4o |

</details>
