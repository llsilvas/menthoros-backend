# Plano de Implementacao - Spring AI Agent Skills para Analise de Treinos

**Projeto:** Menthoros - Aplicativo de Analise de Corrida
**Versao:** 2.1.0
**Data:** 12 de Fevereiro de 2026
**Autor:** Leandro + Claude
**Status:** Planejamento
**Base:** Plano_Implementacao_Skills_Menthoros.md (v1.0)

---

## 1. Visao Estrategica: O Diferencial do Menthoros

### 1.1 O Problema que Resolvemos

O corredor amador termina seu treino, olha o relogio e ve: pace 5:41/km, FC media 155 bpm,
21.1 km. E dai? **O que esses numeros significam para a evolucao dele?** Ele melhorou?
Piorou? O que deveria ajustar no proximo treino?

Hoje, a interpretacao desses dados depende de:
- **Treinador humano** ($200-500/mes) - inacessivel para maioria
- **Conhecimento proprio** - exige anos de estudo em fisiologia
- **Intuicao** - subjetiva e propensa a erros

### 1.2 O que Existe no Mercado

| App | O que faz | O que NAO faz |
|-----|-----------|---------------|
| **Strava** | Mostra splits, mapa, pace. Social. | Nao interpreta fisiologicamente. Nao diz "seu drift cardiaco de 7% indica desidratacao". |
| **Garmin Connect** | Metricas avancadas (VO2max, Training Effect, Body Battery). | Generico. Mesma analise para um iniciante e um elite. Nao personaliza pelo historico. |
| **TrainingPeaks** | TSS, IF, CTL/ATL/TSB. Gold standard para coaches. | Exige coach para interpretar. Atleta sozinho ve numeros sem entender. |
| **Nike Run Club** | Planos guiados, audio coaching. | Zero analise pos-treino. Sem metricas fisiologicas. |
| **COROS/Polar** | Hardware excelente, metricas no relogio. | Analise confinada ao ecossistema do relogio. Sem visao holistica. |

### 1.3 O Diferencial Menthoros: "Treinador de IA que Conhece Voce"

```
┌─────────────────────────────────────────────────────────────────────────┐
│                                                                         │
│    Garmin/Strava/Polar                    Menthoros                      │
│                                                                         │
│    "Seu drift cardiaco foi 4.2%"   vs    "Maria, seu drift cardiaco     │
│                                           de 4.2% MELHOROU 1.8% vs      │
│    Fim.                                   seu ultimo longao de 18km.     │
│                                           Isso indica que sua base       │
│                                           aerobica esta respondendo      │
│                                           aos treinos em Z2 das ultimas  │
│                                           3 semanas. Voce esta pronta    │
│                                           para aumentar o longao para    │
│                                           23km mantendo o pace de 5:40.  │
│                                           Hidrate 200ml a cada 15min     │
│                                           ja que a temperatura esta      │
│                                           subindo nessa epoca."          │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.4 Os 5 Diferenciais Competitivos Concretos

**1. Interpretacao Fisiologica Personalizada (ninguem faz)**
- Nao e "drift cardiaco: 4.2%". E "seu drift cardiaco de 4.2% indica que seu acoplamento
  aerobico melhorou. Isso acontece porque seus treinos longos em Z2 estao fortalecendo o
  ventriculo esquerdo, aumentando o volume de ejecao."
- O LLM CRUZA o resultado com o nivel do atleta, historico, lesoes, e objetivo.

**2. Tendencia Temporal (Garmin faz, mas sem contexto)**
- "Seu decaimento em intervalados caiu de 8.2% para 4.2% nos ultimos 5 treinos. Essa
  melhoria de 49% indica que a periodizacao esta funcionando."
- Nao sao numeros isolados. E a HISTORIA do atleta contada com dados.

**3. Recomendacoes Acionaveis (TrainingPeaks depende do coach)**
- "Para o proximo longao: aumente 10% (23km), mantenha pace 5:40, hidrate 200ml/15min.
  OPCAO B: mantenha 21km mas reduza pace para 5:30."
- O atleta sabe EXATAMENTE o que fazer na proxima semana.

**4. Ciclo Fechado: Analise → Plano (ninguem faz automatizado)**
- A analise do treino realizado alimenta a geracao do PROXIMO plano semanal.
- "Maria completou o longao com drift de 4.2% (bom). Na proxima semana, o plano
  gerado pela IA ja considera essa informacao para calibrar volume e intensidade."

**5. Educacao Continua (ninguem faz)**
- Cada analise inclui um paragrafo educacional sobre fisiologia.
- O atleta nao apenas recebe numeros - ele APRENDE por que esses numeros importam.
- Apos 3 meses, o atleta entende drift cardiaco, decaimento, negative split.

---

## 2. Arquitetura: Skills.md + @Tool (Complementares)

### 2.1 O Papel de Cada Componente

```
┌─────────────────────────────────────────────────────────────────┐
│                     SKILLS (.md)                                 │
│          "CEREBRO" - O que o agente SABE                        │
│                                                                  │
│  ┌─────────────────┐  ┌──────────────────┐  ┌───────────────┐  │
│  │ interval-        │  │ long-run-         │  │ recovery-      │  │
│  │ analysis.md      │  │ analysis.md       │  │ analysis.md    │  │
│  │                  │  │                   │  │                │  │
│  │ Quando usar?     │  │ Quando usar?      │  │ Quando usar?   │  │
│  │ Quais tools?     │  │ Quais tools?      │  │ Quais tools?   │  │
│  │ Como interpretar?│  │ Como interpretar? │  │ Como interp.?  │  │
│  │ Ranges por nivel │  │ Ranges por nivel  │  │ Ranges p/nivel │  │
│  │ Recomendacoes    │  │ Recomendacoes     │  │ Recomendacoes  │  │
│  └────────┬─────────┘  └────────┬──────────┘  └───────┬───────┘  │
│           │                     │                      │          │
└───────────┼─────────────────────┼──────────────────────┼──────────┘
            │    Carregado dinamicamente por tipo         │
            └─────────────────────┼──────────────────────┘
                                  │
                    ┌─────────────▼──────────────┐
                    │    AnaliseSkillService       │
                    │    (Orquestrador)            │
                    │                              │
                    │  1. Identifica tipo treino    │
                    │  2. Carrega skill(.md)        │
                    │  3. Compoe system prompt      │
                    │  4. ChatClient + Tools        │
                    │  5. Structured Output         │
                    │  6. Persiste resultado        │
                    └─────────────┬──────────────┘
                                  │
┌─────────────────────────────────┼──────────────────────────────────┐
│                     TOOLS (@Tool Java)                              │
│          "MAOS" - O que o agente PODE FAZER                        │
│                                                                     │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐ │
│  │ IntervalAnalysis  │  │ LongRunAnalysis   │  │ AtletaContext     │ │
│  │ Tools             │  │ Tools             │  │ Tools             │ │
│  │                   │  │                   │  │                   │ │
│  │ - decaimento()    │  │ - driftCardiaco() │  │ - historico()     │ │
│  │ - consistencia()  │  │ - negativeSplit() │  │ - perfil()        │ │
│  │ - recuperacaoFC() │  │ - efficiency()    │  │ - tendencias()    │ │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘ │
│                                                                     │
│  Calculos DETERMINISTICOS em Java. Precisao garantida. Testaveis.  │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 Por que Ambos e nao Apenas Um?

| Somente @Tool | Somente Skills.md | @Tool + Skills.md |
|---------------|-------------------|-------------------|
| LLM calcula E interpreta | LLM so interpreta texto | LLM interpreta resultados EXATOS |
| Risco de alucinacao em calculos | Sem calculos automaticos | Calculos Java + Interpretacao IA |
| Prompt monolitico gigante | Conhecimento modular mas sem acao | Modular + Acionavel |
| Mesmo prompt para todo tipo | Seleciona skill por tipo | Skill certa + Tools certos |
| Nao testavel facilmente | Nao testavel (so texto) | Tools testaveis unitariamente |

---

## 3. Estrutura de Arquivos Completa

```
src/main/
├── java/com/menthoros/
│   └── skill/
│       ├── config/
│       │   └── SkillChatClientConfig.java        # ChatClient dedicado com tools
│       │
│       ├── loader/
│       │   ├── SkillDefinition.java               # Record: metadata do frontmatter YAML
│       │   └── SkillLoader.java                    # Carrega .md, parseia frontmatter, seleciona
│       │
│       ├── tools/
│       │   ├── IntervalAnalysisTools.java          # @Tool: decaimento, CV, recuperacao FC
│       │   ├── LongRunAnalysisTools.java           # @Tool: drift, negative split, EF
│       │   ├── RecoveryAnalysisTools.java          # @Tool: recuperacao cardiaca geral
│       │   └── AtletaContextTools.java             # @Tool: historico, perfil, tendencias
│       │
│       ├── dto/
│       │   ├── AnaliseCompletaOutputDto.java       # Structured output do agente
│       │   ├── MetricaDecaimentoDto.java
│       │   ├── MetricaConsistenciaDto.java
│       │   ├── MetricaDriftCardiacoDto.java
│       │   ├── MetricaNegativeSplitDto.java
│       │   ├── MetricaRecuperacaoFcDto.java
│       │   └── HistoricoAtletaDto.java
│       │
│       └── service/
│           └── AnaliseSkillService.java            # Orquestrador principal
│
├── resources/
│   ├── skills/                                     # SKILLS.md (conhecimento modular)
│   │   ├── base-system.md                          # Prompt base compartilhado
│   │   ├── interval-analysis.md                    # Skill: treinos intervalados
│   │   ├── long-run-analysis.md                    # Skill: treinos longos
│   │   └── recovery-analysis.md                    # Skill: recuperacao
│   │
│   ├── prompts/
│   │   └── (existentes - nao mudam)
│   │
│   └── db/migration/
│       └── V17__Create_analise_treino_table.sql
```

---

## 4. Skills.md: Modulos de Conhecimento

### 4.1 base-system.md (Carregado SEMPRE)

```markdown
---
name: base-system
description: Instrucoes base para todas as analises de treino
version: 1.0.0
always_load: true
---

# Menthoros - Agente de Analise de Treinos de Corrida

Voce e um especialista em fisiologia do esporte e treinamento de corrida.

## Principios Fundamentais

1. **NUNCA inventar numeros.** Use APENAS dados retornados pelos tools.
2. **SEMPRE buscar o perfil do atleta** antes de interpretar qualquer metrica.
3. **SEMPRE buscar historico** para comparar com treinos anteriores do mesmo tipo.
4. **Adaptar linguagem** ao nivel: INICIANTE (simples), AVANCADO (tecnico).
5. **Ser construtivo.** Mesmo resultados ruins devem motivar.
6. **Linguagem**: portugues brasileiro, tecnica mas acessivel.

## Ajuste de Ranges por Nivel do Atleta

Ao interpretar resultados, ajuste a tolerancia:
- **INICIANTE**: Ranges +30% mais tolerantes. Celebrar qualquer consistencia.
- **INTERMEDIARIO**: Ranges padrao. Incentivar progressao.
- **AVANCADO**: Ranges padrao. Sugerir ajustes finos.
- **ELITE**: Ranges -20% mais exigentes. Foco em otimizacao marginal.

## Formato da Resposta

Sempre preencha o AnaliseCompletaOutputDto com TODOS os campos.
Inclua 2-4 recomendacoes ACIONAVEIS (com numeros concretos de pace, distancia, etc).
```

### 4.2 interval-analysis.md

```markdown
---
name: interval-analysis
description: Analise especializada de treinos intervalados de corrida
version: 1.0.0
trigger:
  tipo_treino: [INTERVALADO, TIRO, FARTLEK]
  min_etapas_tipo_intervalado: 3
tools_required:
  - calcularDecaimentoPerformance
  - calcularConsistenciaPace
  - calcularRecuperacaoFC
  - buscarHistoricoTreinos
  - buscarPerfilAtleta
---

# Skill: Analise de Treino Intervalado

## Contexto Fisiologico

Treinos intervalados desenvolvem o VO2max e a velocidade no limiar anaerobico.
A qualidade do treino e medida por 3 pilares: decaimento, consistencia e recuperacao.

## Instrucoes Passo a Passo

### Passo 1: Conhecer o Atleta
Chame `buscarPerfilAtleta(atletaId)`.
Anote: nivel de experiencia, FC maxima, lesoes.

### Passo 2: Extrair Dados das Etapas
Das etapas do treino, separe:
- Etapas tipo INTERVALADO → extraia paces (em seg/km) na ordem
- Etapas tipo RECUPERACAO → extraia FC maxima (pico do esforco anterior)
  e FC ao final da recuperacao

### Passo 3: Calcular Metricas
Chame os 3 tools com os dados extraidos:

1. `calcularDecaimentoPerformance(pacesPorRepeticao)`
   - Input: lista de paces em seg/km das repeticoes INTERVALADO
   - Mede: queda de performance ao longo das repeticoes

2. `calcularConsistenciaPace(pacesPorRepeticao)`
   - Input: mesma lista de paces
   - Mede: variabilidade (controle neuromuscular)

3. `calcularRecuperacaoFC(fcPicoEsforcos, fcFimRecuperacoes)`
   - Input: FC pico de cada esforco + FC fim de cada recuperacao
   - Mede: capacidade cardiovascular de recuperacao

### Passo 4: Interpretar Resultados

#### Decaimento de Performance
Reflete a capacidade do sistema aerobico de ressintetizar ATP e remover lactato
entre repeticoes. Quanto menor, mais eficiente o metabolismo oxidativo.

| Range    | Classificacao | Significado | Recomendacao |
|----------|--------------|-------------|--------------|
| < 3%     | EXCELENTE | Sistema oxidativo altamente eficiente. Nivel elite. | Manter volume. Pode aumentar intensidade gradualmente. |
| 3% - 5%  | MUITO BOM | Boa capacidade aerobica. Bem treinado. | Progressao adequada. Manter estrategia atual. |
| 5% - 8%  | BOM | Capacidade aerobica adequada. | Focar em base aerobica (Z2). Manter volume de intervalos. |
| 8% - 12% | REGULAR | Sistema aerobico limitado. | Reduzir intensidade 5-10s/km. Aumentar recuperacao. Priorizar Z2. |
| > 12%    | RUIM | Insuficiente ou overtraining. | ALERTA: Reduzir volume 50%. Checar sono. Se persistir, consultar medico. |

#### Consistencia de Pace (Coeficiente de Variacao)
Indica controle neuromuscular e percepcao de esforco. Treino com CV baixo
significa que o atleta dosou bem o esforco.

| Range    | Classificacao | Significado |
|----------|--------------|-------------|
| < 2%     | EXCELENTE | Controle neuromuscular preciso. Excelente percepcao de esforco. |
| 2% - 4%  | BOM | Variabilidade aceitavel. Bom controle. |
| 4% - 6%  | REGULAR | Saida rapida demais ou fadiga prematura. Ajustar percepcao. |
| > 6%     | RUIM | Pacing inadequado. Iniciar mais conservador. |

Padroes comuns a observar:
- 1a rep muito rapida, depois estabiliza → "fast start syndrome" (comum, alertar)
- Queda progressiva → fadiga fisiologica normal
- Rep do meio fora → possivel distracno ou terreno diferente

#### Recuperacao de FC
Indicador DIRETO da capacidade aerobica. Queda rapida = coracao eficiente.

| Range    | Classificacao | Significado |
|----------|--------------|-------------|
| > 30 bpm | EXCELENTE | Excelente capacidade cardiovascular. |
| 25-30 bpm | BOM | Boa recuperacao. Sistema cardiovascular eficiente. |
| 20-25 bpm | REGULAR | Base aerobica em desenvolvimento. Mais Z2 necessario. |
| < 20 bpm | RUIM | Base aerobica insuficiente. Focar 80% do volume em Z2. |

Observar tendencia INTRA-TREINO:
- Queda mantida ao longo das reps → boa resistencia
- Queda diminuindo (30→25→20) → fadiga acumulada (normal nas ultimas reps)
- Queda aumentando → raro, pode indicar aquecimento insuficiente no inicio

### Passo 5: Comparar com Historico
Chame `buscarHistoricoTreinos(atletaId, "INTERVALADO", 5)`.
Compare cada metrica com a media dos ultimos treinos.
Identifique: MELHORA (>5% melhor), ESTAVEL (<5% variacao), PIORA (>5% pior).

### Passo 6: Gerar Recomendacoes
Baseado nos resultados, gere 2-4 recomendacoes com NUMEROS CONCRETOS:
- Se decaimento bom: "Pode aumentar 1-2 reps OU reduzir pace em X seg/km"
- Se decaimento ruim: "Reduzir pace em X seg/km E aumentar recuperacao para Y seg"
- Se consistencia ruim: "Iniciar 3-5s/km mais lento que o pace medio atual"
- Se recuperacao ruim: "Priorizar 3+ treinos/semana em Z2 nas proximas 4 semanas"
```

### 4.3 long-run-analysis.md

```markdown
---
name: long-run-analysis
description: Analise especializada de treinos longos e continuos
version: 1.0.0
trigger:
  tipo_treino: [LONGO, CONTINUO]
  min_distancia_km: 8
  min_duracao_min: 40
tools_required:
  - calcularDriftCardiaco
  - calcularNegativeSplit
  - calcularEfficiencyFactor
  - buscarHistoricoTreinos
  - buscarPerfilAtleta
---

# Skill: Analise de Treino Longo

## Contexto Fisiologico

O treino longo e o pilar da preparacao para provas de fundo. Desenvolve:
- Capacidade oxidativa (mitocondrias, capilarizacao)
- Eficiencia na oxidacao de gordura (preserva glicogenio)
- Resistencia musculoesqueletica
- Resiliencia mental

A qualidade e medida por: drift cardiaco, estrategia de pacing, eficiencia.

## Instrucoes Passo a Passo

### Passo 1: Conhecer o Atleta
Chame `buscarPerfilAtleta(atletaId)`.

### Passo 2: Dividir o Treino em Metades
Se o treino tem etapas:
- 1a metade: etapas 1 ate N/2 (media ponderada por distancia de pace e FC)
- 2a metade: etapas N/2+1 ate N

Se nao tem etapas detalhadas, use os dados gerais do treino
(nesse caso, drift cardiaco nao pode ser calculado com precisao - informar).

### Passo 3: Calcular Metricas

1. `calcularDriftCardiaco(fcPrimeiraMetade, fcSegundaMetade, pacePrimeiraMetade, paceSegundaMetade)`
   - Mede: desacoplamento entre FC e pace ao longo do treino

2. `calcularNegativeSplit(pacePrimeiraMetade, paceSegundaMetade)`
   - Mede: estrategia de distribuicao de esforco

3. `calcularEfficiencyFactor(paceMedia, fcMedia)`
   - Mede: relacao entre velocidade e esforco cardiaco

### Passo 4: Interpretar Resultados

#### Drift Cardiaco
O drift cardiaco e o aumento PROGRESSIVO de FC mesmo mantendo pace constante.
Causas fisiologicas:
1. **Desidratacao**: Menos plasma → menos volume ejetado → FC compensa
2. **Termorregulacao**: Vasodilatacao periferica → menos retorno venoso
3. **Deplecao de glicogenio**: Troca de substrato → menor eficiencia
4. **Fadiga neuromuscular**: Menos eficiencia biomecanica

IMPORTANTE: Drift so e valido se o pace se manteve relativamente constante (<5% variacao).
Se o atleta acelerou na 2a metade, aumento de FC e esperado (nao e drift).

| Range    | Classificacao | Significado | Recomendacao |
|----------|--------------|-------------|--------------|
| < 3%     | EXCELENTE | "Well-coupled". Acoplamento aerobico perfeito. | Pode progredir: +10% distancia OU pace 5-10s/km mais rapido. |
| 3% - 5%  | BOM | Bom acoplamento. Normal em longoes >15km. | Manter estrategia. Hidratar bem. |
| 5% - 8%  | MODERADO | Inicio de desacoplamento. | Hidratar 200ml/15min. Reduzir pace 10-15s/km. Checar sono. |
| > 8%     | ALTO | Desacoplamento significativo. | ALERTA: Reduzir pace 20-30s/km. Hidratar 250ml/15min. Avaliar se volume esta adequado. |

Fatores contextuais (se disponivel):
- Calor >25°C: adicionar +2% tolerancia ao drift
- Calor >30°C: adicionar +4% tolerancia + recomendar horario mais fresco
- Altitude >1500m: adicionar +3% tolerancia

#### Negative Split
A estrategia IDEAL fisiologicamente e o negative split (2a metade mais rapida).

| Resultado | Tipo | Classificacao | Interpretacao |
|-----------|------|--------------|---------------|
| 2a metade 3+ seg/km mais rapida | NEGATIVE SPLIT | EXCELENTE | Estrategia perfeita. Preservou glicogenio, usou gordura no inicio, finish forte. |
| Diferenca < 3 seg/km | EVEN SPLIT | BOM | Pacing consistente. Aceitavel para longoes. |
| 2a metade 3-10 seg/km mais lenta | POSITIVE SPLIT LEVE | REGULAR | Saida ligeiramente agressiva. Iniciar 5-10s/km mais lento. |
| 2a metade 10+ seg/km mais lenta | POSITIVE SPLIT FORTE | RUIM | Pacing inadequado. "Explodiu" no final. Iniciar MUITO mais conservador. |

Por que negative split e superior:
1. Preserva glicogenio muscular (combustivel premium)
2. Usa mais gordura no inicio (fonte "ilimitada")
3. Reduz acumulo de lactato
4. Treina o mental: "correr forte quando esta dificil"

#### Efficiency Factor (EF)
Acompanhar evolucao ao longo de SEMANAS. Nao tem range absoluto.
- EF aumentando → atleta ficando mais eficiente (mesmo pace com menos FC)
- EF estavel → manutencao
- EF caindo → fadiga acumulada, overreaching, ou perda de forma

### Passo 5: Comparar com Historico
Chame `buscarHistoricoTreinos(atletaId, "LONGO", 5)`.
Comparar ESPECIALMENTE o Efficiency Factor (tendencia ao longo de semanas).

### Passo 6: Gerar Recomendacoes
Com numeros concretos:
- Opcao A: Aumentar volume (distancia exata + pace sugerido)
- Opcao B: Aumentar intensidade (mesma distancia + pace sugerido)
- Opcao C: Progressive run (faixas de pace por km)
- Hidratacao e nutricao se drift > 5%
```

### 4.4 recovery-analysis.md

```markdown
---
name: recovery-analysis
description: Analise de capacidade de recuperacao cardiaca do atleta
version: 1.0.0
trigger:
  tipo_treino: [REGENERATIVO, FACIL, CONTINUO]
  fc_media_disponivel: true
tools_required:
  - calcularEfficiencyFactor
  - buscarHistoricoTreinos
  - buscarPerfilAtleta
---

# Skill: Analise de Recuperacao

## Contexto Fisiologico

Treinos regenerativos e faceis sao tao importantes quanto os intensos.
Monitorar a eficiencia em treinos leves revela:
- Estado de recuperacao do sistema nervoso
- Fadiga acumulada (overreaching)
- Evolucao da base aerobica

## Instrucoes

### Indicadores de Alerta
- FC media em treino facil > 75% FCmax → possivel fadiga acumulada
- EF caindo em treinos faceis → overreaching
- RPE alto (>5) em treino regenerativo → corpo nao recuperou

### Recomendacoes Tipicas
- Se indicadores normais: "Recuperacao adequada. Manter programacao."
- Se indicadores de fadiga: "Considerar semana de descarga (-30% volume)."
- Se persistir por 2+ semanas: "Avaliar overtraining. Recomendado dia de descanso total."
```

---

## 5. Exemplo Completo: A Jornada da Maria (21km Long Run)

### 5.1 Perfil da Maria

```
Nome: Maria Silva
Nivel: INTERMEDIARIO
Idade: 32 anos
Objetivo: Meia maratona sub-2h (pace alvo: 5:40/km)
FC Max: 188 bpm (testada)
FC Repouso: 58 bpm
FC Limiar: 168 bpm
Dias disponiveis: SEG, QUA, SEX, SAB
Dia preferido longao: SABADO
Volume semanal atual: 40-45 km
Prova alvo: Meia Maratona de Floripa - 15/03/2026 (4 semanas)
```

### 5.2 Fluxo Completo: Semana a Semana

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         CICLO SEMANAL MENTHOROS                         │
│                                                                         │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────────────┐ │
│  │ DOMINGO   │    │ SEG-SEX  │    │ SABADO    │    │ DOMINGO          │ │
│  │           │    │          │    │           │    │                  │ │
│  │ Gera      │───>│ Maria    │───>│ Maria     │───>│ Analise Skills   │ │
│  │ Plano     │    │ executa  │    │ faz o     │    │ avalia treinos   │ │
│  │ Semanal   │    │ treinos  │    │ longao    │    │ da semana        │ │
│  │ (IA)      │    │ e lanca  │    │ 21km      │    │                  │ │
│  │           │    │ no app   │    │           │    │ Resultado:       │ │
│  │ PlanoCtrl │    │ TreinoCtrl    │ TreinoCtrl│    │ AnaliseSkillSvc  │ │
│  │ /gerar    │    │ /lancar  │    │ /lancar   │    │ /analise         │ │
│  └──────────┘    └──────────┘    └──────────┘    └────────┬─────────┘ │
│                                                            │           │
│                                                            ▼           │
│                                                   ┌──────────────┐    │
│                                                   │ Alimenta o   │    │
│                                                   │ PROXIMO plano│───┐│
│                                                   │ semanal      │   ││
│                                                   └──────────────┘   ││
│                                                                      ││
│  Proxima semana <────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────┘
```

### 5.3 Passo 1: Geracao do Plano Semanal (ja existe no Menthoros)

```http
POST /planos/atletas/{mariaId}/gerar?modoGeracaoPlano=PROXIMA_SEMANA
```

A IA (IaServiceImpl) gera o plano da semana:

```json
{
  "semanaInicio": "2026-02-16",
  "semanaFim": "2026-02-22",
  "volumePlanejadoKm": 44.0,
  "objetivoSemanal": "Ultimo longao forte antes do taper para a meia maratona",
  "treinosPlanejados": [
    {
      "diaSemana": "SEGUNDA",
      "tipoTreino": "REGENERATIVO",
      "distanciaKm": 6.0,
      "duracaoMin": "35:00",
      "ritmoAlvo": "6:30-7:00/km",
      "descricao": "Recuperacao ativa pos-fim de semana"
    },
    {
      "diaSemana": "QUARTA",
      "tipoTreino": "INTERVALADO",
      "distanciaKm": 10.0,
      "duracaoMin": "55:00",
      "ritmoAlvo": "5:00-5:20/km",
      "descricao": "6x1000m em Z4 com 90s recuperacao",
      "etapas": [
        {"ordem": 1, "tipoEtapa": "AQUECIMENTO", "distanciaKm": 2.0, "duracaoMin": 12},
        {"ordem": 2, "tipoEtapa": "INTERVALADO", "distanciaKm": 1.0, "duracaoMin": 5},
        {"ordem": 3, "tipoEtapa": "RECUPERACAO", "distanciaKm": 0.3, "duracaoMin": 2},
        {"ordem": 4, "tipoEtapa": "INTERVALADO", "distanciaKm": 1.0, "duracaoMin": 5},
        {"ordem": 5, "tipoEtapa": "RECUPERACAO", "distanciaKm": 0.3, "duracaoMin": 2},
        {"ordem": 6, "tipoEtapa": "INTERVALADO", "distanciaKm": 1.0, "duracaoMin": 5},
        {"ordem": 7, "tipoEtapa": "RECUPERACAO", "distanciaKm": 0.3, "duracaoMin": 2},
        {"ordem": 8, "tipoEtapa": "INTERVALADO", "distanciaKm": 1.0, "duracaoMin": 5},
        {"ordem": 9, "tipoEtapa": "RECUPERACAO", "distanciaKm": 0.3, "duracaoMin": 2},
        {"ordem": 10, "tipoEtapa": "INTERVALADO", "distanciaKm": 1.0, "duracaoMin": 5},
        {"ordem": 11, "tipoEtapa": "RECUPERACAO", "distanciaKm": 0.3, "duracaoMin": 2},
        {"ordem": 12, "tipoEtapa": "INTERVALADO", "distanciaKm": 1.0, "duracaoMin": 5},
        {"ordem": 13, "tipoEtapa": "DESAQUECIMENTO", "distanciaKm": 1.5, "duracaoMin": 10}
      ]
    },
    {
      "diaSemana": "SEXTA",
      "tipoTreino": "FACIL",
      "distanciaKm": 7.0,
      "duracaoMin": "42:00",
      "ritmoAlvo": "6:00-6:30/km",
      "descricao": "Pre-longao, soltar as pernas"
    },
    {
      "diaSemana": "SABADO",
      "tipoTreino": "LONGO",
      "distanciaKm": 21.0,
      "duracaoMin": "120:00",
      "ritmoAlvo": "5:30-5:50/km",
      "descricao": "Simulado meia maratona. Negative split: inicio 5:50, final 5:30.",
      "etapas": [
        {"ordem": 1, "tipoEtapa": "AQUECIMENTO", "distanciaKm": 2.0, "duracaoMin": 13},
        {"ordem": 2, "tipoEtapa": "PRINCIPAL", "distanciaKm": 17.0, "duracaoMin": 97},
        {"ordem": 3, "tipoEtapa": "DESAQUECIMENTO", "distanciaKm": 2.0, "duracaoMin": 13}
      ]
    }
  ]
}
```

### 5.4 Passo 2: Maria Executa os Treinos (SEG a SEX)

Maria corre e registra via Garmin sync ou lancamento manual:

```http
POST /treinos/{mariaId}/lancar-treino
```

### 5.5 Passo 3: Maria Faz o Longao de 21km (SABADO)

Sabado de manha, Maria corre 21km. O Garmin registra tudo.
Ela sincroniza e o Menthoros recebe:

```http
POST /treinos/{mariaId}/lancar-treino

{
  "atletaId": "maria-uuid",
  "dataTreino": "2026-02-22",
  "diaSemana": "SABADO",
  "tipoTreino": "LONGO",
  "distanciaKm": 21.1,
  "duracaoMin": "01:59:30",
  "ritmoMedio": "5:41",
  "fcMedia": 155,
  "fcMax": 175,
  "percepcaoEsforco": 7,
  "feedbackAtleta": "Me senti bem, consegui acelerar no final. Calor forte apos km 15.",
  "fonteDados": "GARMIN",
  "externalId": "garmin-987654321",
  "etapasRealizadas": [
    {
      "ordem": 1,
      "tipoEtapa": "AQUECIMENTO",
      "descricao": "Aquecimento leve",
      "distanciaKm": 2.0,
      "duracao": "13:20",
      "paceMedia": "06:40",
      "fcMedia": 135,
      "fcMax": 148
    },
    {
      "ordem": 2,
      "tipoEtapa": "PRINCIPAL",
      "descricao": "Bloco principal 0-10.5km",
      "distanciaKm": 8.5,
      "duracao": "49:00",
      "paceMedia": "05:46",
      "fcMedia": 152,
      "fcMax": 165
    },
    {
      "ordem": 3,
      "tipoEtapa": "PRINCIPAL",
      "descricao": "Bloco principal 10.5-19km",
      "distanciaKm": 8.5,
      "duracao": "46:45",
      "paceMedia": "05:30",
      "fcMedia": 161,
      "fcMax": 173
    },
    {
      "ordem": 4,
      "tipoEtapa": "DESAQUECIMENTO",
      "descricao": "Desaquecimento",
      "distanciaKm": 2.1,
      "duracao": "14:25",
      "paceMedia": "06:52",
      "fcMedia": 138,
      "fcMax": 155
    }
  ]
}
```

### 5.6 Passo 4: Analise com Skills (O MOMENTO MAGICO)

Maria (ou o sistema automaticamente) solicita a analise:

```http
POST /treinos/{mariaId}/treinos-realizados/{treinoId}/analise
```

#### O que acontece por dentro:

```
1. AnaliseSkillService recebe o request
   │
2. Busca TreinoRealizado com etapas do banco
   │
3. SkillLoader identifica: tipoTreino=LONGO, distancia=21.1km
   │  → Carrega: base-system.md + long-run-analysis.md
   │
4. Compoe system prompt:
   │  [base-system.md] + [long-run-analysis.md]
   │
5. Monta user message com dados do treino da Maria
   │
6. ChatClient.prompt()
   │  .system(promptComposto)
   │  .user(dadosTreino)
   │  .tools(longRunTools, atletaContextTools)
   │  .call()
   │
7. GPT-4o le as instrucoes do skill e decide chamar tools:
   │
   ├─ Tool Call 1: buscarPerfilAtleta("maria-uuid")
   │  → Retorna: {nivel: INTERMEDIARIO, fcMax: 188, objetivo: "sub-2h meia"}
   │
   ├─ Tool Call 2: calcularDriftCardiaco(152, 161, 346, 330)
   │  → Retorna: {driftPercentual: 5.92, paceConstante: false,
   │              variacaoPace: 4.62%, classificacao: "MODERADO"}
   │  (!) pace NAO foi constante - Maria acelerou. LLM vai contextualizar.
   │
   ├─ Tool Call 3: calcularNegativeSplit(346, 330)
   │  → Retorna: {diferenca: -16, tipo: "NEGATIVE_SPLIT",
   │              classificacao: "EXCELENTE"}
   │
   ├─ Tool Call 4: calcularEfficiencyFactor(341, 155)
   │  → Retorna: 1.13
   │
   ├─ Tool Call 5: buscarHistoricoTreinos("maria-uuid", "LONGO", 5)
   │  → Retorna: {treinos: [
   │       {data: "2026-02-08", dist: 18km, drift: 7.8%, ef: 1.08},
   │       {data: "2026-02-01", dist: 16km, drift: 6.2%, ef: 1.10},
   │       {data: "2026-01-25", dist: 18km, drift: 8.1%, ef: 1.07}
   │     ]}
   │
8. GPT-4o interpreta TODOS os resultados usando o skill long-run-analysis.md
   │  e retorna AnaliseCompletaOutputDto (structured output)
   │
9. AnaliseSkillService persiste em tb_analise_treino
   │
10. Retorna para Maria
```

### 5.7 Passo 5: O Feedback que Maria Recebe

```json
{
  "resumoGeral": "Excelente simulado de meia maratona, Maria! Seu negative split de 16s/km mostra maturidade no pacing. O drift cardiaco de 5.9% esta dentro do esperado para 21km com aceleracao na segunda metade - importante notar que voce NAO manteve pace constante (acelerou 4.6%), entao parte do aumento de FC e esperado, nao e drift puro. Seu Efficiency Factor subiu de 1.08 para 1.13 em 4 semanas.",

  "nivelGeral": "BOM",

  "metricas": [
    {
      "nome": "Drift Cardiaco",
      "valor": "5.9%",
      "classificacao": "MODERADO (com ressalva)",
      "interpretacao": "Seu FC subiu de 152 para 161 bpm entre as metades. POREM, voce tambem acelerou de 5:46 para 5:30/km (negative split de 16s). Parte desse aumento de FC e resposta NORMAL a aceleracao, nao drift verdadeiro. O drift 'puro' estimado e menor que 5.9%. Considerando o calor que voce reportou apos km 15, esse valor esta adequado para seu nivel."
    },
    {
      "nome": "Negative Split",
      "valor": "16 seg/km mais rapida na 2a metade",
      "classificacao": "EXCELENTE",
      "interpretacao": "Primeira metade em 5:46/km, segunda em 5:30/km. Essa e EXATAMENTE a estrategia que atletas de elite usam em maratonas. Voce preservou glicogenio no inicio usando mais gordura como substrato, e teve reservas para acelerar no final. Isso mostra resiliencia mental e boa percepcao de esforco."
    },
    {
      "nome": "Efficiency Factor",
      "valor": "1.13",
      "classificacao": "MELHORA",
      "interpretacao": "Seu EF era 1.07-1.08 ha 4 semanas e agora e 1.13. Isso significa que voce esta correndo MAIS RAPIDO com MENOS esforco cardiaco. E o sinal mais claro de que sua base aerobica esta evoluindo."
    }
  ],

  "comparacaoHistorico": "Comparando com seus ultimos 3 longoes: o drift caiu de 8.1% (25/jan, 18km) para 5.9% (hoje, 21km com aceleracao). Seu EF subiu consistentemente: 1.07 → 1.08 → 1.10 → 1.13. A tendencia e claramente positiva. Voce esta atingindo distancias maiores com melhor eficiencia.",

  "tendencia": "MELHORA",

  "recomendacoes": [
    {
      "tipo": "PROXIMO_TREINO",
      "descricao": "Proximo sabado e a ultima semana antes da prova (taper). Fazer longao CURTO de 12-14km em pace confortavel (5:50-6:00/km). O objetivo e manter a maquina rodando sem criar fadiga.",
      "prioridade": "ALTA"
    },
    {
      "tipo": "HIDRATACAO",
      "descricao": "Voce mencionou calor apos km 15. Para a prova dia 15/03, leve gel com sal e hidrate 200ml a cada 15min. Comece a hidratar ANTES de sentir sede (km 5).",
      "prioridade": "ALTA"
    },
    {
      "tipo": "ESTRATEGIA_PROVA",
      "descricao": "Baseado neste simulado, sua estrategia para a meia maratona: 0-5km em 5:50/km (controlando adrenalina), 5-15km em 5:40/km (ritmo alvo), 15-21km em 5:30/km ou menos (se sentir bem). Pace medio alvo: 5:38/km = 1h59min.",
      "prioridade": "ALTA"
    },
    {
      "tipo": "VOLUME",
      "descricao": "Semana de taper: reduzir volume total para 28-30km (vs 44km desta semana). Manter 1 treino de qualidade (quarta: 4x800m em pace de prova) e 2 treinos faceis.",
      "prioridade": "MEDIA"
    }
  ],

  "contextoFisiologico": "O negative split que voce executou e fisiologicamente superior ao positive split. Nos primeiros 10km, seu corpo priorizou a oxidacao de gordura (via beta-oxidacao), preservando glicogenio muscular. A partir do km 15, quando voce acelerou, o glicogenio preservado permitiu sustentar pace mais rapido sem 'bater o muro'. Seu Efficiency Factor crescente (1.07→1.13) confirma que 4 semanas de treinos longos em Z2 estao aumentando a densidade mitocondrial e a capilarizacao muscular. Voce esta no ponto ideal para a prova."
}
```

### 5.8 Passo 6: O Ciclo Fecha (Proximo Plano)

Quando o Menthoros gera o plano da PROXIMA semana (semana de taper), o
`PlanoTreinoPromptBuilder` ja existente passa os treinos recentes como contexto.
A analise do longao da Maria (drift, negative split, EF) esta nos dados que alimentam
a geracao do proximo plano.

O prompt de geracao de plano ja recebe:
- "Maria fez longao de 21km com drift 5.9%, negative split excelente, EF 1.13"
- A IA de geracao de plano (IaServiceImpl) ajusta automaticamente:
  - Volume reduzido (taper)
  - Mantem 1 treino de qualidade
  - Pace calibrado pelo simulado

**Ciclo fechado: Analise → Plano → Execucao → Analise → Plano → ...**

---

## 6. Implementacao Passo a Passo

### 6.1 Fase 1: Infraestrutura (3-4 dias)

**O que fazer:**

1. Criar pacote `com.menthoros.skill` com sub-pacotes
2. Criar `SkillDefinition.java` (record para o frontmatter YAML)
3. Criar `SkillLoader.java` (carrega .md do classpath, parseia YAML frontmatter)
4. Criar todos os DTOs de metricas (records simples)
5. Criar `AnaliseCompletaOutputDto.java` (structured output)
6. Criar migration `V17__Create_analise_treino_table.sql`
7. Criar entidade `AnaliseTreino.java` e repository
8. Criar `SkillChatClientConfig.java` (ChatClient dedicado)
9. Escrever os 3 arquivos `.md` de skills + base-system.md

**SkillLoader.java - implementacao:**

```java
@Component
@Slf4j
public class SkillLoader {

    private final Map<String, SkillDefinition> skills = new ConcurrentHashMap<>();
    private final ObjectMapper yamlMapper;

    @PostConstruct
    public void loadSkills() throws IOException {
        yamlMapper = new ObjectMapper(new YAMLFactory());

        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:skills/*.md");

        for (Resource resource : resources) {
            String content = new String(resource.getInputStream().readAllBytes(), UTF_8);
            SkillDefinition skill = parseSkillFile(content, resource.getFilename());
            skills.put(skill.name(), skill);
            log.info("Skill carregada: {} v{}", skill.name(), skill.version());
        }
    }

    private SkillDefinition parseSkillFile(String content, String filename) {
        // Separa frontmatter YAML (entre ---) do corpo markdown
        String[] parts = content.split("---", 3);
        String yamlFrontmatter = parts[1].trim();
        String markdownBody = parts.length > 2 ? parts[2].trim() : "";

        Map<String, Object> metadata = yamlMapper.readValue(yamlFrontmatter, Map.class);

        return new SkillDefinition(
            (String) metadata.get("name"),
            (String) metadata.get("description"),
            (String) metadata.getOrDefault("version", "1.0.0"),
            (Map<String, Object>) metadata.get("trigger"),
            (List<String>) metadata.get("tools_required"),
            (Boolean) metadata.getOrDefault("always_load", false),
            markdownBody
        );
    }

    /**
     * Retorna skills aplicaveis ao tipo de treino.
     * Sempre inclui skills com always_load=true.
     */
    public List<SkillDefinition> findApplicable(TreinoRealizado treino) {
        String tipoTreino = treino.getTipoTreino().getValue();
        BigDecimal distancia = treino.getDistanciaKm();

        return skills.values().stream()
            .filter(skill -> skill.alwaysLoad() || matchesTrigger(skill, tipoTreino, distancia))
            .toList();
    }

    public String composePrompt(List<SkillDefinition> skills) {
        return skills.stream()
            .map(SkillDefinition::markdownBody)
            .collect(Collectors.joining("\n\n---\n\n"));
    }

    private boolean matchesTrigger(SkillDefinition skill, String tipoTreino, BigDecimal dist) {
        Map<String, Object> trigger = skill.trigger();
        if (trigger == null) return false;

        List<String> tipos = (List<String>) trigger.get("tipo_treino");
        if (tipos != null && !tipos.contains(tipoTreino)) return false;

        Number minDist = (Number) trigger.get("min_distancia_km");
        if (minDist != null && dist != null && dist.doubleValue() < minDist.doubleValue())
            return false;

        return true;
    }
}
```

**SkillDefinition.java:**

```java
public record SkillDefinition(
    String name,
    String description,
    String version,
    Map<String, Object> trigger,
    List<String> toolsRequired,
    boolean alwaysLoad,
    String markdownBody
) {}
```

### 6.2 Fase 2: Tools de Calculo (3-4 dias)

Implementar os @Tool conforme detalhado no plano anterior (secao 4.1, 4.2, 4.3).
Cada tool e um metodo Java puro, deterministico, testavel.

**Prioridade de implementacao:**
1. `LongRunAnalysisTools` (drift, negative split, EF) - usado no exemplo da Maria
2. `IntervalAnalysisTools` (decaimento, consistencia, recuperacao FC)
3. `AtletaContextTools` (perfil, historico, tendencias)
4. `RecoveryAnalysisTools` (EF em treinos faceis, alertas)

### 6.3 Fase 3: Service Orquestrador (3-4 dias)

```java
@Service
@Slf4j
public class AnaliseSkillService {

    private final ChatClient.Builder chatClientBuilder;
    private final SkillLoader skillLoader;
    private final TreinoRealizadoRepository treinoRepository;
    private final AnaliseTreinoRepository analiseRepository;

    // Tools injetados
    private final IntervalAnalysisTools intervalTools;
    private final LongRunAnalysisTools longRunTools;
    private final RecoveryAnalysisTools recoveryTools;
    private final AtletaContextTools atletaContextTools;

    @Value("classpath:skills/base-system.md")
    private Resource baseSystemPrompt;

    public AnaliseCompletaOutputDto analisarTreino(UUID atletaId, UUID treinoId) {

        // 1. Buscar treino com etapas
        TreinoRealizado treino = treinoRepository.findById(treinoId)
                .orElseThrow(() -> new ResourceNotFoundException("Treino nao encontrado"));

        // 2. Verificar se ja existe analise (cache)
        Optional<AnaliseTreino> existente = analiseRepository
                .findByTreinoRealizadoId(treinoId);
        if (existente.isPresent()) {
            return deserialize(existente.get().getAnaliseCompletaJson());
        }

        // 3. Selecionar skills aplicaveis
        List<SkillDefinition> skills = skillLoader.findApplicable(treino);
        String systemPrompt = skillLoader.composePrompt(skills);

        log.info("Analise treino {} - Skills: {}", treinoId,
                skills.stream().map(SkillDefinition::name).toList());

        // 4. Montar mensagem com dados do treino
        String userMessage = buildUserMessage(treino);

        // 5. Chamar o agente
        long startTime = System.currentTimeMillis();

        AnaliseCompletaOutputDto analise = chatClientBuilder
                .defaultSystem(systemPrompt)
                .defaultTools(intervalTools, longRunTools,
                              recoveryTools, atletaContextTools)
                .defaultAdvisors(
                    ToolCallAdvisor.builder()
                        .conversationHistoryEnabled(false)
                        .build())
                .build()
                .prompt()
                .user(userMessage)
                .call()
                .entity(AnaliseCompletaOutputDto.class);

        long duration = System.currentTimeMillis() - startTime;

        // 6. Persistir
        salvarAnalise(treino, analise, skills, duration);

        log.info("Analise concluida em {}ms para treino {}: {}",
                duration, treinoId, analise.nivelGeral());

        return analise;
    }

    private String buildUserMessage(TreinoRealizado treino) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analise o seguinte treino:\n\n");
        sb.append("Atleta ID: ").append(treino.getAtleta().getId()).append("\n");
        sb.append("Tipo: ").append(treino.getTipoTreino().getValue()).append("\n");
        sb.append("Data: ").append(treino.getDataTreino()).append("\n");
        sb.append("Distancia: ").append(treino.getDistanciaKm()).append(" km\n");
        sb.append("Duracao: ").append(treino.getDuracaoMin()).append("\n");
        sb.append("FC Media: ").append(treino.getFcMedia()).append(" bpm\n");
        sb.append("FC Max: ").append(treino.getFcMax()).append(" bpm\n");
        sb.append("Pace Media: ").append(treino.getPaceMedia()).append("\n");
        sb.append("RPE: ").append(treino.getPercepcaoEsforco()).append("/10\n");

        if (treino.getFeedbackAtleta() != null) {
            sb.append("Feedback do atleta: ").append(treino.getFeedbackAtleta()).append("\n");
        }

        if (treino.getEtapasRealizadas() != null && !treino.getEtapasRealizadas().isEmpty()) {
            sb.append("\nEtapas realizadas:\n");
            for (EtapaRealizada etapa : treino.getEtapasRealizadas()) {
                sb.append(String.format(
                    "  %d. [%s] %s - Dist: %s km, Duracao: %s, Pace: %s, FC: %d/%d bpm, RPE: %s\n",
                    etapa.getOrdem(),
                    etapa.getTipoEtapa(),
                    etapa.getDescricao() != null ? etapa.getDescricao() : "",
                    etapa.getDistanciaKm(),
                    etapa.getDuracao(),
                    etapa.getPaceMedia(),
                    etapa.getFcMedia() != null ? etapa.getFcMedia() : 0,
                    etapa.getFcMax() != null ? etapa.getFcMax() : 0,
                    etapa.getPercepcaoEsforco() != null ? etapa.getPercepcaoEsforco() : "-"
                ));
            }
        }

        return sb.toString();
    }

    private void salvarAnalise(TreinoRealizado treino, AnaliseCompletaOutputDto dto,
                                List<SkillDefinition> skills, long duration) {
        AnaliseTreino analise = AnaliseTreino.builder()
                .treinoRealizado(treino)
                .resumoGeral(dto.resumoGeral())
                .nivelGeral(dto.nivelGeral())
                .tendencia(dto.tendencia())
                .analiseCompletaJson(serialize(dto))
                .skillsAplicadas(skills.stream()
                        .map(SkillDefinition::name)
                        .collect(Collectors.joining(",")))
                .tempoProcessamentoMs(duration)
                .modeloLlm("gpt-4o")
                .criadoEm(LocalDateTime.now())
                .build();

        analiseRepository.save(analise);
    }
}
```

### 6.4 Fase 4: Controller e Integracao (2-3 dias)

Adicionar ao `TreinoRealizadoController`:

```java
@Operation(summary = "Analisar treino realizado",
           description = "Gera analise fisiologica do treino usando AI Skills")
@PostMapping("{atletaId}/treinos-realizados/{treinoId}/analise")
public ResponseEntity<AnaliseCompletaOutputDto> analisarTreino(
        @PathVariable UUID atletaId,
        @PathVariable UUID treinoId) {
    AnaliseCompletaOutputDto analise = analiseSkillService.analisarTreino(atletaId, treinoId);
    return ResponseEntity.ok(analise);
}

@Operation(summary = "Buscar analise existente de treino",
           description = "Retorna analise previamente gerada (cache)")
@GetMapping("{atletaId}/treinos-realizados/{treinoId}/analise")
public ResponseEntity<AnaliseCompletaOutputDto> buscarAnalise(
        @PathVariable UUID atletaId,
        @PathVariable UUID treinoId) {
    // Busca do banco, retorna 404 se nao existe
}
```

### 6.5 Fase 5: Testes e Polimento (3-4 dias)

**Testes unitarios dos Tools:**
```java
@Test
void deveCalcularNegativeSplitCorreto() {
    // 1a metade: 5:46/km = 346 seg/km
    // 2a metade: 5:30/km = 330 seg/km
    var result = longRunTools.calcularNegativeSplit(346, 330);

    assertThat(result.tipo()).isEqualTo("NEGATIVE_SPLIT");
    assertThat(result.classificacao()).isEqualTo("EXCELENTE");
    assertThat(result.diferencaSegundos()).isEqualTo(-16.0);
}

@Test
void deveCalcularDriftCardiacoComPaceVariavel() {
    var result = longRunTools.calcularDriftCardiaco(152, 161, 346, 330);

    assertThat(result.driftPercentual()).isCloseTo(5.92, within(0.1));
    assertThat(result.paceConstante()).isFalse(); // pace variou >5%
    assertThat(result.classificacao()).isEqualTo("MODERADO");
}
```

**Teste de integracao com ChatClient:**
```java
@SpringBootTest
class AnaliseSkillServiceIntegrationTest {

    @Test
    void deveAnalisarLongRunDaMariaComNegativeSplit() {
        // Given: treino da Maria com etapas
        UUID treinoId = criarTreinoLongo21km();

        // When
        AnaliseCompletaOutputDto analise = analiseService.analisarTreino(mariaId, treinoId);

        // Then
        assertThat(analise.nivelGeral()).isIn("BOM", "EXCELENTE");
        assertThat(analise.metricas()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(analise.recomendacoes()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(analise.contextoFisiologico()).isNotBlank();
    }
}
```

---

## 7. Cronograma Atualizado

| Fase | Duracao | Horas | Entregas |
|------|---------|-------|----------|
| **Fase 1**: Infraestrutura | 3-4 dias | ~14h | SkillLoader, DTOs, Skills.md, Migration, Config |
| **Fase 2**: Tools | 3-4 dias | ~16h | 4 classes @Tool + testes unitarios |
| **Fase 3**: Orquestrador | 3-4 dias | ~14h | AnaliseSkillService + testes integracao |
| **Fase 4**: Controller | 2-3 dias | ~8h | Endpoints + Swagger |
| **Fase 5**: Polimento | 3-4 dias | ~14h | Ajuste prompts, edge cases, metricas |
| **TOTAL** | **~3-4 semanas** | **~66h** | Sistema funcional com 3 skills |

---

## 8. Evolucao Futura (pos-MVP)

### 8.1 Analise Automatica pos-Lancamento

Apos lancar treino (`addTreino` / `lancarTreino`), disparar analise automaticamente:

```java
// Em TreinoServiceImpl.lancarTreino(), apos salvar:
applicationEventPublisher.publishEvent(new TreinoRegistradoEvent(treinoSalvo.getId()));

// Listener async:
@EventListener
@Async("llmTaskExecutor")
public void onTreinoRegistrado(TreinoRegistradoEvent event) {
    analiseSkillService.analisarTreino(event.getAtletaId(), event.getTreinoId());
}
```

### 8.2 Novas Skills (adicionar sem mudar codigo core)

| Skill | Arquivo | Trigger | Tools Novos |
|-------|---------|---------|-------------|
| **training-zones.md** | Calcula zonas de FC/pace | Qualquer treino | calcularZonas() |
| **periodization.md** | Avalia progressao de carga | Semanal | calcularCargaAcumulada() |
| **race-prediction.md** | Estima tempo de prova | Pre-prova | estimarVDOT(), estimarTempo() |
| **overtraining.md** | Detecta sinais de fadiga | FC repouso elevada | avaliarTendenciaFadiga() |

Cada nova skill = 1 arquivo .md + 1 classe @Tool (se necessario) + atualizar config.

### 8.3 Ciclo Fechado: Analise → Plano

Alimentar o prompt de geracao de plano com as analises mais recentes:

```java
// Em PlanoTreinoPromptBuilder, adicionar:
String analiseRecente = analiseRepository
    .findMostRecentByAtletaId(atletaId)
    .map(a -> "Analise do ultimo treino: " + a.getResumoGeral())
    .orElse("");
// Incluir no prompt de geracao de plano
```

---

## 9. Referencias

### Spring AI
- [Sub-Agent Orchestration with Spring AI (2026)](https://gaetanopiazzolla.github.io/java/ai/2026/02/09/sub-agent-pattern.html)
- [Spring AI Agent Utils (Community)](https://github.com/spring-ai-community/spring-ai-agent-utils)
- [Tool Calling with Spring AI - Piotr's TechBlog](https://piotrminkowski.com/2025/03/13/tool-calling-with-spring-ai/)
- [Function Calling Deep Dive - Alexis Segura](https://www.alexis-segura.com/notes/function-calling-ai-agents-deep-dive-with-spring-ai/)

### Fisiologia do Esporte
- Daniels, Jack. "Daniels' Running Formula." 3rd ed. Human Kinetics, 2013.
- Seiler, Stephen. "What is Best Practice for Training Intensity and Duration Distribution?"
- Foster, C., et al. "A New Approach to Monitoring Exercise Training." JSCR, 2001.
- Minetti et al. (2002) "Energy cost of walking and running at extreme gradients"

---

**Documento criado por:** Leandro + Claude
**Proxima revisao:** Inicio da implementacao
