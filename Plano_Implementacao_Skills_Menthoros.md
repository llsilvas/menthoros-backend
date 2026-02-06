# Plano de Implementação - Skills de Análise Menthoros

**Projeto:** Menthoros - Aplicativo de Análise de Corrida  
**Versão:** 2.0.0  
**Data:** 10 de Fevereiro de 2026  
**Autor:** Leandro - Senior Software Engineer  
**Status:** Planejamento

---

## Sumário Executivo

Este documento descreve o plano detalhado de implementação de um sistema de **Skills Especializadas** para análise de treinos de corrida no aplicativo Menthoros. As skills funcionam como módulos de conhecimento expert baseados em fisiologia do esporte, oferecendo análises automáticas e personalizadas baseadas em décadas de pesquisa científica em treinamento esportivo.

### Objetivos Principais

1. **Automatizar análises técnicas** de treinos usando conhecimento especializado
2. **Fornecer feedback educacional** baseado em fisiologia do esporte
3. **Criar base para IA/ML** com regras interpretáveis e auditáveis
4. **Diferenciar competitivamente** o Menthoros de apps genéricos
5. **Escalar conhecimento expert** para milhares de usuários

### Resultados Esperados

- ✅ Análises automáticas em 100% dos treinos elegíveis
- ✅ Feedback personalizado baseado em histórico do atleta
- ✅ Recomendações acionáveis para próximos treinos
- ✅ Base de conhecimento auditável e versionada
- ✅ Capacidade de adicionar novas skills sem alterar código core

---

## 1. Visão Geral das Skills

### 1.1 O que são Skills?

Skills são **módulos de conhecimento especializado** implementados como arquivos YAML que contêm:

- **Regras fisiológicas** - Interpretação baseada em ciência do esporte
- **Métricas e fórmulas** - Cálculos padronizados pela comunidade científica
- **Interpretações graduadas** - Classificação de resultados (excelente → ruim)
- **Recomendações contextuais** - Ajustes específicos para próximos treinos
- **Templates de feedback** - Mensagens educacionais para o atleta

### 1.2 Arquitetura do Sistema

```
┌─────────────────────────────────────────────────────────┐
│                   Menthoros API                          │
│  ┌────────────────────────────────────────────────────┐ │
│  │         TreinoCoreService                          │ │
│  │  - Registro de treinos                             │ │
│  │  - Publicação de eventos                           │ │
│  └──────────────────┬─────────────────────────────────┘ │
│                     │ TreinoRegistradoEvent              │
│                     ▼                                    │
│  ┌────────────────────────────────────────────────────┐ │
│  │      SkillBasedAnalysisService                     │ │
│  │  - Detecta skills aplicáveis                       │ │
│  │  - Executa análises                                │ │
│  │  - Gera feedback consolidado                       │ │
│  └──────────────────┬─────────────────────────────────┘ │
│                     │                                    │
│         ┌───────────┼───────────┬────────────┐          │
│         ▼           ▼           ▼            ▼          │
│    ┌────────┐ ┌─────────┐ ┌─────────┐ ┌──────────┐    │
│    │Skill   │ │ Skill   │ │ Skill   │ │  Skill   │    │
│    │Intervalo│ │LongRun │ │Recupera │ │Periodiza │    │
│    └────────┘ └─────────┘ └─────────┘ └──────────┘    │
└─────────────────────────────────────────────────────────┘
```

### 1.3 Skills Planejadas

| Skill | Descrição | Prioridade | Complexidade |
|-------|-----------|------------|--------------|
| **interval-analysis** | Análise de treinos intervalados (decaimento, consistência, recuperação FC) | 🔴 Alta | Média |
| **long-run-analysis** | Análise de long runs (drift cardíaco, negative split, eficiência) | 🔴 Alta | Média |
| **recovery-analysis** | Análise de capacidade de recuperação cardíaca | 🟡 Média | Baixa |
| **training-zones** | Cálculo e prescrição de zonas de FC/pace | 🟡 Média | Média |
| **periodization** | Sugestões de progressão de carga e volume | 🟢 Baixa | Alta |
| **injury-prevention** | Detecção de sinais de overtraining | 🟢 Baixa | Alta |

---

## 2. Skill: Interval Analysis

### 2.1 Objetivo

Analisar treinos intervalados (ex: 10x400m, 5x1000m, fartlek) focando em:
- **Decaimento de performance** - queda de pace ao longo das repetições
- **Consistência de ritmo** - variabilidade entre repetições
- **Recuperação cardíaca** - capacidade de reduzir FC entre esforços

### 2.2 Base Fisiológica

**Decaimento de Performance:**
- Reflete a capacidade do sistema aeróbico de ressintetizar ATP
- Quanto menor o decaimento, maior a eficiência oxidativa
- Atletas de elite: <3% | Bem treinados: 3-5% | Iniciantes: >8%

**Consistência de Pace:**
- Indica controle neuromuscular e percepção de esforço
- CV < 2%: excelente | CV 2-4%: bom | CV > 6%: necessita ajuste

**Recuperação FC:**
- Queda >30 bpm em 90s: excelente capacidade aeróbica
- Queda <20 bpm: base aeróbica insuficiente

### 2.3 Estrutura de Arquivos

```
/src/main/resources/skills/interval-analysis/
├── skill.yml                    # Configuração principal
├── performance-decay-rules.yml  # Regras de interpretação de decaimento
├── pace-consistency-rules.yml   # Regras de consistência de pace
├── hr-recovery-rules.yml        # Regras de recuperação cardíaca
└── feedback-templates.yml       # Templates de mensagens
```

### 2.4 Métricas Calculadas

| Métrica | Fórmula | Interpretação |
|---------|---------|---------------|
| **Performance Decay %** | `((pace_last - pace_first) / pace_first) * 100` | <3%: excellent, 3-5%: good, 5-8%: fair, >8%: poor |
| **Coefficient of Variation** | `(std_deviation / mean) * 100` | <2%: excellent, 2-4%: good, 4-6%: fair, >6%: poor |
| **Average HR Drop** | `avg(hr_end_effort - hr_end_recovery)` | >30 bpm: excellent, 25-30: good, 20-25: fair, <20: poor |
| **Outliers** | Reps `> 2 std deviations from mean` | Identifica repetições inconsistentes |

### 2.5 Exemplo de Regra (performance-decay-rules.yml)

```yaml
performance_decay:
  description: "Analysis of performance drop across repetitions"
  
  physiology:
    concept: |
      Performance decay in intervals indicates the aerobic system's capacity
      to resynthesize ATP and clear lactate between repetitions.
  
  metrics:
    formula: "(pace_last_rep - pace_first_rep) / pace_first_rep * 100"
    unit: "percentage"
    
  interpretation:
    excellent:
      range: [0, 3]
      meaning: "Highly efficient aerobic system"
      athlete_level: "Elite or very well trained"
      recommendation: "Maintain volume, can gradually increase intensity"
      
    very_good:
      range: [3, 5]
      meaning: "Good aerobic capacity"
      athlete_level: "Well trained"
      recommendation: "Adequate progression, maintain current strategy"
      
    good:
      range: [5, 8]
      meaning: "Adequate aerobic capacity"
      athlete_level: "Intermediate/Advanced"
      recommendation: "Focus on aerobic base (Z2), maintain interval volume"
      
    fair:
      range: [8, 12]
      meaning: "Limited aerobic system"
      athlete_level: "Beginner/Intermediate"
      recommendation: |
        1. Reduce intensity (pace 5-10s/km slower)
        2. Increase recovery time between reps
        3. Prioritize Z2 workouts (60-70% HRmax)
      
    poor:
      range: [12, 100]
      meaning: "Insufficient aerobic system or overtraining"
      athlete_level: "Beginner or fatigued"
      recommendation: |
        ⚠️ WARNING: Very high decay!
        1. REDUCE interval volume by 50%
        2. INCREASE Z2 volume
        3. Check sleep quality
        4. If persists, consult coach/doctor
```

### 2.6 Exemplo de Feedback Gerado

```
📊 ANÁLISE TÉCNICA - TREINO INTERVALADO

Perfil do Treino: 10x1000m
Data: 10/02/2026

---

🔻 DECAIMENTO DE PERFORMANCE
Resultado: 4.2% (MUITO BOM)
Pace inicial: 4:15 → Pace final: 4:23

Interpretação Fisiológica:
Boa capacidade aeróbica, sistema oxidativo respondendo bem. 
Seu corpo está conseguindo ressintetizar ATP de forma eficiente 
entre as repetições.

Comparação Histórica:
- Média últimos treinos: 5.8%
- Tendência: ✅ MELHORA de 1.6%

---

📈 CONSISTÊNCIA DE PACE
Coeficiente de Variação: 2.1% (BOM)
Amplitude: 4:13 - 4:25

Análise de Padrão:
Pace bem controlado, com variação mínima entre repetições.
Primeira repetição foi ligeiramente mais rápida (padrão comum).

---

❤️ RECUPERAÇÃO CARDÍACA
Queda média FC: 28 bpm em 90s (BOM)
Avaliação: Sistema cardiovascular eficiente

Tendência intra-treino:
Repetições 1-3: 30 bpm de queda
Repetições 4-7: 28 bpm de queda
Repetições 8-10: 25 bpm de queda
→ Leve fadiga ao final (esperado)

---

💡 RECOMENDAÇÕES PARA PRÓXIMO TREINO

Com base na sua performance:
- Progressão adequada, manter estratégia atual
- Pode tentar aumentar 1-2 repetições OU
- Reduzir pace em 2-3s/km mantendo volume

Ajustes Sugeridos:
- Pace-alvo: 4:12-4:15 /km
- Tempo de recuperação: manter 90s
- Volume: 10-12 repetições

---

📚 CONTEXTO FISIOLÓGICO

O decaimento moderado (4.2%) indica que seu sistema aeróbico está
trabalhando bem, mas ainda há espaço para desenvolvimento. Continue
priorizando treinos longos em zona 2 (60-70% FCmax) para construir
base aeróbica sólida. Isso permitirá manter paces rápidos por mais
tempo com menor acúmulo de fadiga.
```

---

## 3. Skill: Long Run Analysis

### 3.1 Objetivo

Analisar treinos longos (>10km, >60min) focando em:
- **Drift cardíaco** - aumento de FC em pace constante
- **Negative split** - distribuição de esforço (segunda metade mais rápida)
- **Eficiência aeróbica** - Pa:HR ratio (pace/FC)

### 3.2 Base Fisiológica

**Drift Cardíaco:**
- FC aumenta mesmo mantendo pace devido a:
  - Desidratação (↓ volume plasmático)
  - Aumento temperatura corporal
  - Depleção de glicogênio
  - Fadiga neuromuscular
- Drift <5%: excelente acoplamento aeróbico
- Drift >8%: pace muito alto ou desidratação severa

**Negative Split:**
- Segunda metade mais rápida = estratégia ideal
- Preserva glicogênio, reduz lactato, melhora mental
- Positive split indica saída muito agressiva

### 3.3 Estrutura de Arquivos

```
/src/main/resources/skills/long-run-analysis/
├── skill.yml
├── cardiac-drift-rules.yml
├── negative-split-rules.yml
├── efficiency-rules.yml
└── feedback-templates.yml
```

### 3.4 Métricas Calculadas

| Métrica | Fórmula | Interpretação |
|---------|---------|---------------|
| **Cardiac Drift %** | `((HR_2nd_half - HR_1st_half) / HR_1st_half) * 100` | <3%: excellent, 3-5%: good, 5-8%: moderate, >8%: high |
| **Negative Split** | `pace_2nd_half - pace_1st_half` | <0: negative (optimal), 0-3s: even (good), >3s: positive (poor) |
| **Efficiency Factor** | `(normalized_pace / avg_HR) * 100` | Higher = more efficient |
| **Pa:HR Ratio** | `pace_km / HR` | Track evolution over time |

### 3.5 Exemplo de Regra (cardiac-drift-rules.yml)

```yaml
cardiac_drift:
  description: "Coupling between HR and pace throughout the workout"
  
  physiology:
    concept: |
      Cardiac drift is the progressive increase in HR at constant pace,
      reflecting:
      1. DEHYDRATION: ↓ plasma volume → ↑ compensatory HR
      2. TEMPERATURE: Peripheral vasodilation → ↓ venous return
      3. GLYCOGEN DEPLETION: Substrate shift → ↓ efficiency
      4. NEUROMUSCULAR FATIGUE: ↓ biomechanical efficiency
  
  metrics:
    calculation:
      method: "half_comparison"
      formula: |
        HR_first_half_avg = avg HR of first 50% distance
        HR_second_half_avg = avg HR of last 50% distance
        drift% = ((HR_second - HR_first) / HR_first) * 100
  
  interpretation:
    excellent:
      range: [0, 3]
      meaning: "Perfect aerobic coupling - 'well-coupled'"
      recommendation: |
        Can progress:
        - Increase volume (+10% distance)
        - Increase intensity (pace 5-10s/km faster)
        
    good:
      range: [3, 5]
      meaning: "Good aerobic coupling"
      recommendation: "Maintain current strategy"
        
    moderate:
      range: [5, 8]
      meaning: "Beginning of uncoupling"
      recommendation: |
        ⚠️ Adjustments needed:
        HYDRATION: Drink 150-250ml every 15-20min
        PACE: Reduce 10-15s/km next long run
        RECOVERY: Check sleep quality
        
    high:
      range: [8, 100]
      meaning: "Significant uncoupling"
      recommendation: |
        🚨 WARNING: Very high drift!
        1. REDUCE PACE drastically (-20 to -30s/km)
        2. HYDRATE properly (500ml before, 200ml/15min)
        3. INCREASE AEROBIC BASE (80% workouts in Z2)
        4. ASSESS RECOVERY (resting HR, HRV, sleep)
  
  contextual_factors:
    ambient_temperature:
      moderate_heat:
        temp_range: [25, 30]
        interpretation_adjustment: "+1-2% drift tolerance"
        recommendation: "Enhanced hydration"
        
      high_heat:
        temp_range: [30, 100]
        interpretation_adjustment: "+2-4% drift tolerance"
        recommendation: |
          - Reduce pace by 10-20s/km
          - Hydrate every 10-15min
          - Train in cooler hours
```

### 3.6 Exemplo de Feedback Gerado

```
📊 ANÁLISE TÉCNICA - LONG RUN

Perfil do Treino: 21km
Data: 10/02/2026
Condições: 22°C, umidade 65%

---

❤️ DRIFT CARDÍACO
Resultado: 4.2% (BOM)
FC primeira metade: 152 bpm → FC segunda metade: 158 bpm

Interpretação Fisiológica:
Bom acoplamento aeróbico. Pequeno aumento de FC é normal em
treinos longos e pode estar relacionado a:
- Leve desidratação (esperado em 21km)
- Aumento gradual de temperatura corporal
- Uso progressivo de gordura como substrato

Seu sistema cardiovascular está respondendo adequadamente.

---

⏱️ NEGATIVE SPLIT
Segunda metade: 8s/km MAIS RÁPIDA ✅
1ª metade (0-10.5km): 5:45 /km
2ª metade (10.5-21km): 5:37 /km

EXCELENTE! Estratégia de pacing perfeita:
- Início controlado preservou energia
- Finish forte demonstra reservas aeróbicas
- Mental bem treinado para "sofrer no final"

Tática ideal para provas!

---

💡 RECOMENDAÇÕES PARA PRÓXIMO TREINO

Seu desempenho foi muito bom! Estratégias para progressão:

OPÇÃO 1 - Aumentar Volume:
- Distância: 23-24km (aumento de 10%)
- Manter pace atual: 5:41 /km médio
- Mesmo padrão: início 5:45, final 5:35

OPÇÃO 2 - Aumentar Intensidade:
- Distância: manter 21km
- Pace: 5:35-5:38 /km médio
- Negative split: início 5:40, final 5:30

OPÇÃO 3 - Progressive Run:
- A cada 5km acelerar 5s/km
- 0-5km: 5:50, 5-10km: 5:45, 10-15km: 5:40, 15-21km: 5:35

---

📚 CONTEXTO FISIOLÓGICO

O negative split que você executou é FISIOLOGICAMENTE SUPERIOR
ao positive split porque:
1. Preserva glicogênio muscular (combustível premium)
2. Usa mais gordura no início (fonte ilimitada)
3. Reduz acúmulo de lactato
4. Treina mental para "correr forte quando está difícil"

Continue praticando essa estratégia - é exatamente o que atletas
de elite fazem em maratonas!
```

---

## 4. Implementação Técnica

### 4.1 Estrutura de Pastas

```
menthoros/
├── src/main/
│   ├── java/com/menthoros/
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   │   ├── TreinoRealizado.java
│   │   │   │   ├── EtapaTreinoRealizada.java
│   │   │   │   └── RepeticaoRealizada.java
│   │   │   └── repository/
│   │   │       └── TreinoRealizadoRepository.java
│   │   │
│   │   ├── skill/
│   │   │   ├── model/
│   │   │   │   ├── Skill.java
│   │   │   │   ├── TriggerConditions.java
│   │   │   │   ├── SkillRule.java
│   │   │   │   └── FeedbackTemplate.java
│   │   │   │
│   │   │   ├── loader/
│   │   │   │   └── SkillLoader.java
│   │   │   │
│   │   │   ├── service/
│   │   │   │   ├── SkillBasedAnalysisService.java
│   │   │   │   ├── IntervalAnalysisService.java
│   │   │   │   ├── LongRunAnalysisService.java
│   │   │   │   └── TemplateEngine.java
│   │   │   │
│   │   │   └── dto/
│   │   │       ├── CompleteAnalysisDTO.java
│   │   │       ├── SkillAnalysisResult.java
│   │   │       ├── PerformanceDecayDTO.java
│   │   │       ├── PaceConsistencyDTO.java
│   │   │       └── CardiacDriftDTO.java
│   │   │
│   │   └── listener/
│   │       └── WorkoutRegisteredListener.java
│   │
│   └── resources/
│       └── skills/
│           ├── interval-analysis/
│           │   ├── skill.yml
│           │   ├── performance-decay-rules.yml
│           │   ├── pace-consistency-rules.yml
│           │   ├── hr-recovery-rules.yml
│           │   └── feedback-templates.yml
│           │
│           ├── long-run-analysis/
│           │   ├── skill.yml
│           │   ├── cardiac-drift-rules.yml
│           │   ├── negative-split-rules.yml
│           │   ├── efficiency-rules.yml
│           │   └── feedback-templates.yml
│           │
│           └── recovery-analysis/
│               ├── skill.yml
│               ├── hr-recovery-rules.yml
│               └── feedback-templates.yml
│
└── pom.xml
```

### 4.2 Classes Principais

#### SkillLoader.java

```java
@Component
@Slf4j
public class SkillLoader {
    
    private final Map<String, Skill> skills = new ConcurrentHashMap<>();
    private final ObjectMapper yamlMapper;
    
    @PostConstruct
    public void loadSkills() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:skills/*/skill.yml");
            
            for (Resource resource : resources) {
                Skill skill = yamlMapper.readValue(
                    resource.getInputStream(), 
                    Skill.class
                );
                
                loadSkillRules(skill, extractSkillPath(resource));
                skills.put(skill.getName(), skill);
                
                log.info("Skill carregada: {} v{}", 
                    skill.getName(), skill.getVersion());
            }
        } catch (IOException e) {
            log.error("Erro ao carregar skills", e);
            throw new SkillLoadException("Falha ao carregar skills", e);
        }
    }
    
    public List<Skill> getApplicableSkills(TreinoRealizado treino) {
        return skills.values().stream()
            .filter(skill -> skill.isApplicable(treino))
            .collect(Collectors.toList());
    }
}
```

#### SkillBasedAnalysisService.java

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillBasedAnalysisService {
    
    private final SkillLoader skillLoader;
    private final TreinoRealizadoRepository treinoRepository;
    
    @EventListener
    @Async
    @Transactional
    public void onTreinoRegistrado(TreinoRegistradoEvent event) {
        TreinoRealizado treino = treinoRepository
            .findByIdWithEtapas(event.getTreinoId())
            .orElseThrow();
        
        // Detectar skills aplicáveis
        List<Skill> applicableSkills = skillLoader
            .getApplicableSkills(treino);
        
        if (applicableSkills.isEmpty()) {
            return;
        }
        
        // Executar análises
        List<ResultadoAnaliseSkill> resultados = applicableSkills.stream()
            .map(skill -> executarSkill(skill, treino))
            .collect(Collectors.toList());
        
        // Salvar resultados
        salvarAnalise(treino.getId(), resultados);
        
        log.info("Análises concluídas para treino {}: {} skills aplicadas",
            treino.getId(), applicableSkills.size());
    }
    
    private ResultadoAnaliseSkill executarSkill(
        Skill skill, 
        TreinoRealizado treino
    ) {
        switch (skill.getName()) {
            case "analise-intervalado":
                return analiseIntervaladoService.analisar(skill, treino);
            case "analise-longrun":
                return analiseLongRunService.analisar(skill, treino);
            default:
                return ResultadoAnaliseSkill.naoImplementado(skill.getName());
        }
    }
}
```

#### IntervalAnalysisService.java

```java
@Service
@RequiredArgsConstructor
public class IntervalAnalysisService {
    
    public SkillAnalysisResult analyze(Skill skill, TreinoRealizado workout) {
        
        // 1. Find interval stage
        EtapaTreinoRealizada intervalStage = workout.getEtapas().stream()
            .filter(e -> e.getTipo() == TipoEtapa.INTERVALO)
            .filter(e -> e.getRepeticoes().size() >= 3)
            .findFirst()
            .orElseThrow();
        
        // 2. Calculate metrics
        PerformanceDecayMetrics decay = calculateDecay(intervalStage);
        PaceConsistencyMetrics consistency = calculateConsistency(intervalStage);
        HRRecoveryMetrics recovery = calculateRecovery(intervalStage);
        
        // 3. Interpret based on skill rules
        PerformanceDecayInterpretation decayInterpretation = 
            interpretDecay(decay, skill);
        PaceConsistencyInterpretation consistencyInterpretation = 
            interpretConsistency(consistency, skill);
        
        // 4. Generate feedback
        String feedback = generateFeedback(
            decayInterpretation, 
            consistencyInterpretation, 
            recovery,
            skill
        );
        
        return SkillAnalysisResult.builder()
            .skillName(skill.getName())
            .metrics(Map.of(
                "performance_decay", decay,
                "pace_consistency", consistency,
                "hr_recovery", recovery
            ))
            .interpretations(Map.of(
                "performance_decay", decayInterpretation,
                "pace_consistency", consistencyInterpretation
            ))
            .feedback(feedback)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    private PerformanceDecayMetrics calculateDecay(
        EtapaTreinoRealizada stage
    ) {
        List<RepeticaoRealizada> reps = stage.getRepeticoes();
        
        double paceFirst = convertPaceToSeconds(reps.get(0).getPace());
        double paceLast = convertPaceToSeconds(
            reps.get(reps.size() - 1).getPace()
        );
        
        double percentage = ((paceLast - paceFirst) / paceFirst) * 100;
        
        return PerformanceDecayMetrics.builder()
            .percentage(percentage)
            .initialPace(reps.get(0).getPace())
            .finalPace(reps.get(reps.size() - 1).getPace())
            .numberOfReps(reps.size())
            .build();
    }
    
    private PerformanceDecayInterpretation interpretDecay(
        PerformanceDecayMetrics metrics,
        Skill skill
    ) {
        // Get decay rules
        Map<String, Object> rules = skill.getRules("performance_decay", Map.class);
        Map<String, Object> interpretationMap = 
            (Map<String, Object>) rules.get("interpretation");
        
        double percentage = metrics.getPercentage();
        
        // Find matching range
        for (Map.Entry<String, Object> entry : interpretationMap.entrySet()) {
            Map<String, Object> range = (Map<String, Object>) entry.getValue();
            List<Double> bounds = (List<Double>) range.get("range");
            
            if (percentage >= bounds.get(0) && percentage < bounds.get(1)) {
                return PerformanceDecayInterpretation.builder()
                    .level(entry.getKey())
                    .meaning((String) range.get("meaning"))
                    .athleteLevel((String) range.get("athlete_level"))
                    .recommendation((String) range.get("recommendation"))
                    .decayPercentage(percentage)
                    .build();
            }
        }
        
        return PerformanceDecayInterpretation.unknown();
    }
}
```

### 4.3 Formato dos Arquivos YAML

#### skill.yml (interval-analysis)

```yaml
skill:
  name: "interval-analysis"
  version: "1.0.0"
  description: "Interval training analysis based on exercise physiology"
  expertise_areas:
    - "Anaerobic resistance"
    - "Lactate threshold"
    - "VO2max"
    - "Running economy"
  
  trigger_conditions:
    workout_type: ["INTERVAL"]
    min_repetitions: 3
  
  analysis_modules:
    - performance_decay
    - pace_consistency
    - hr_recovery
```

#### performance-decay-rules.yml

```yaml
performance_decay:
  description: "Analysis of performance drop across repetitions"
  
  metrics:
    formula: "(pace_last_rep - pace_first_rep) / pace_first_rep * 100"
    unit: "percentage"
    
  interpretation:
    excellent:
      range: [0, 3]
      meaning: "Highly efficient aerobic system"
      athlete_level: "Elite or very well trained"
      recommendation: "Maintain volume, can increase intensity"
      
    very_good:
      range: [3, 5]
      meaning: "Good aerobic capacity"
      athlete_level: "Well trained"
      recommendation: "Adequate progression"
      
    # ... other ranges
```

---

## 5. Cronograma de Implementação

### Fase 1: Infraestrutura Base (Sprint 1 - 2 semanas)

**Objetivos:**
- ✅ Implementar sistema de loading de skills
- ✅ Criar estrutura de pastas e arquivos YAML
- ✅ Implementar event listener para treinos registrados
- ✅ Criar DTOs e models base

**Entregas:**
- `SkillLoader.java` funcional
- `Skill.java`, `TriggerConditions.java`, `SkillRule.java`
- Sistema de eventos configurado
- Testes unitários do loader

**Estimativa:** 40 horas

---

### Fase 2: Skill Interval Analysis (Sprint 2 - 2 semanas)

**Objetivos:**
- ✅ Implementar cálculo de performance decay
- ✅ Implementar cálculo de pace consistency
- ✅ Implementar análise de HR recovery
- ✅ Criar regras YAML completas
- ✅ Implementar templates de feedback

**Entregas:**
- `/skills/interval-analysis/` completo
- `IntervalAnalysisService.java`
- Testes de integração
- Documentação de uso

**Estimativa:** 48 horas

---

### Fase 3: Skill Long Run Analysis (Sprint 3 - 2 semanas)

**Objetivos:**
- ✅ Implementar cálculo de cardiac drift
- ✅ Implementar detecção de negative split
- ✅ Implementar efficiency factor
- ✅ Criar regras YAML completas
- ✅ Implementar templates de feedback

**Entregas:**
- `/skills/long-run-analysis/` completo
- `LongRunAnalysisService.java`
- Testes de integração
- Documentação de uso

**Estimativa:** 48 horas

---

### Fase 4: Skill Recovery & Polimento (Sprint 4 - 1 semana)

**Objetivos:**
- ✅ Implementar skill de recovery analysis
- ✅ Refinar feedbacks baseado em testes
- ✅ Otimizar performance
- ✅ Documentação completa

**Entregas:**
- `/skills/recovery-analysis/` completo
- Guia do desenvolvedor
- API documentation
- Apresentação para stakeholders

**Estimativa:** 24 horas

---

### Resumo do Cronograma

| Fase | Duração | Horas | Início | Fim |
|------|---------|-------|--------|-----|
| Fase 1: Infraestrutura | 2 semanas | 40h | 17/02 | 28/02 |
| Fase 2: Intervalado | 2 semanas | 48h | 03/03 | 14/03 |
| Fase 3: Long Run | 2 semanas | 48h | 17/03 | 28/03 |
| Fase 4: Polimento | 1 semana | 24h | 31/03 | 04/04 |
| **TOTAL** | **7 semanas** | **160h** | **17/02** | **04/04** |

---

## 6. Estratégia de Testes

### 6.1 Testes Unitários

**SkillLoaderTest.java**
```java
@Test
void shouldLoadAllSkillsFromClasspath() {
    List<Skill> skills = skillLoader.getAllSkills();
    assertThat(skills).hasSize(3);
    assertThat(skills)
        .extracting(Skill::getName)
        .containsExactlyInAnyOrder(
            "interval-analysis",
            "long-run-analysis",
            "recovery-analysis"
        );
}

@Test
void shouldDetectApplicableSkillForInterval() {
    TreinoRealizado workout = createIntervalWorkout();
    List<Skill> applicable = skillLoader.getApplicableSkills(workout);
    
    assertThat(applicable).hasSize(1);
    assertThat(applicable.get(0).getName())
        .isEqualTo("interval-analysis");
}
```

**IntervalAnalysisServiceTest.java**
```java
@Test
void shouldCalculatePerformanceDecayCorrectly() {
    EtapaTreinoRealizada stage = createStageWithReps(
        "4:15", "4:16", "4:17", "4:18", "4:20"
    );
    
    PerformanceDecayMetrics result = service.calculateDecay(stage);
    
    assertThat(result.getPercentage()).isCloseTo(1.96, within(0.01));
    assertThat(result.getInitialPace()).isEqualTo("4:15");
    assertThat(result.getFinalPace()).isEqualTo("4:20");
}

@Test
void shouldInterpretDecayAsExcellent() {
    PerformanceDecayMetrics metrics = PerformanceDecayMetrics.builder()
        .percentage(2.5)
        .build();
    
    PerformanceDecayInterpretation interpretation = 
        service.interpretDecay(metrics, skill);
    
    assertThat(interpretation.getLevel()).isEqualTo("excellent");
    assertThat(interpretation.getMeaning())
        .contains("Highly efficient aerobic system");
}
```

### 6.2 Testes de Integração

**SkillIntegrationTest.java**
```java
@SpringBootTest
@AutoConfigureMockMvc
class SkillIntegrationTest {
    
    @Test
    void shouldAnalyzeIntervalWorkoutCompletely() {
        // Given
        TreinoRealizadoDTO dto = createIntervalWorkoutDTO();
        
        // When
        TreinoRealizado workout = workoutCoreService.registerWorkout(dto);
        
        // Then - wait for async processing
        await().atMost(5, SECONDS).until(() -> {
            Optional<WorkoutAnalysis> analysis = 
                analysisRepository.findByWorkoutId(workout.getId());
            return analysis.isPresent();
        });
        
        WorkoutAnalysis analysis = analysisRepository
            .findByWorkoutId(workout.getId())
            .orElseThrow();
        
        assertThat(analysis.getAppliedSkills())
            .contains("interval-analysis");
        assertThat(analysis.getAutomatedFeedback())
            .contains("INTERVAL TRAINING ANALYSIS");
    }
}
```

### 6.3 Testes de Performance

```java
@Test
void analysisShouldExecuteInLessThan500ms() {
    TreinoRealizado workout = createComplexWorkout();
    
    StopWatch stopWatch = new StopWatch();
    stopWatch.start();
    
    SkillAnalysisResult result = service.analyze(skill, workout);
    
    stopWatch.stop();
    
    assertThat(stopWatch.getTotalTimeMillis()).isLessThan(500);
}
```

---

## 7. Métricas de Sucesso

### 7.1 Métricas Técnicas

| Métrica | Meta | Método de Medição |
|---------|------|-------------------|
| **Cobertura de Testes** | >85% | JaCoCo |
| **Tempo de Análise** | <500ms | Performance tests |
| **Taxa de Erro** | <1% | Logs + Monitoring |
| **Skills Aplicadas** | 100% dos treinos elegíveis | Database queries |

### 7.2 Métricas de Produto

| Métrica | Meta | Método de Medição |
|---------|------|-------------------|
| **Engagement com Feedback** | >70% usuários leem análises | Analytics |
| **Satisfação** | NPS >8/10 | Survey in-app |
| **Ajustes de Treino** | >40% seguem recomendações | Tracking próximos treinos |
| **Retenção** | +15% após implementação | Cohort analysis |

### 7.3 Métricas de Negócio

| Métrica | Meta | Método de Medição |
|---------|------|-------------------|
| **Conversão Free→Pro** | +20% | Conversion funnel |
| **Churn Reduction** | -15% | Retention analysis |
| **Share Features** | 30% usuários compartilham análises | Social sharing tracking |
| **Referrals** | +25% recomendações | Referral program |

---

## 8. Riscos e Mitigações

### 8.1 Riscos Técnicos

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| **Performance degrada com volume** | Média | Alto | Cache de análises, processamento assíncrono, índices otimizados |
| **Regras YAML ficam complexas demais** | Alta | Médio | Code review rigoroso, documentação clara, testes extensivos |
| **Bugs nas interpretações** | Média | Alto | Peer review com treinadores, validação com dados reais, A/B testing |
| **Dependência de estrutura de dados** | Baixa | Alto | Interface abstrata, versionamento de skills |

### 8.2 Riscos de Produto

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| **Usuários ignoram feedback** | Média | Alto | Tornar feedback acionável, usar notificações, gamificação |
| **Feedback muito técnico** | Média | Médio | A/B test de linguagem, opções de detalhamento (básico/avançado) |
| **Recomendações inadequadas** | Baixa | Alto | Validação com treinadores certificados, disclaimers, ajustes progressivos |

### 8.3 Riscos de Negócio

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| **Concorrentes copiam feature** | Alta | Médio | Focar em qualidade superior, adicionar skills continuamente |
| **Custo computacional alto** | Baixa | Médio | Otimização contínua, cache agressivo |
| **Liability (recomendações erradas)** | Baixa | Alto | Disclaimers legais, revisão de conteúdo, seguro |

---

## 9. Próximas Skills (Roadmap Futuro)

### 9.1 Fase 2 (Q3 2026)

**Skill: Training Zones**
- Cálculo automático de zonas de FC baseado em FC máx ou LTHR
- Prescrição de intensidade para cada tipo de treino
- Monitoramento de tempo em cada zona

**Skill: Periodization**
- Sugestão de progressão de carga (volume x intensidade)
- Detecção de plateau ou overtraining
- Recomendação de semanas de recuperação

### 9.2 Fase 3 (Q4 2026)

**Skill: Injury Prevention**
- Detecção de sinais de overtraining (FC repouso elevada, HRV reduzida)
- Análise de carga acumulada (acute:chronic workload ratio)
- Alertas preventivos

**Skill: Performance Prediction**
- Estimativa de tempo em provas (5km, 10km, 21km, 42km)
- Cálculo de VDOT (Daniels' Running Formula)
- Sugestão de pace de prova

---

## 10. Dependências e Requisitos

### 10.1 Dependências Maven

```xml
<!-- YAML Processing -->
<dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-yaml</artifactId>
</dependency>

<!-- Template Engine -->
<dependency>
    <groupId>org.apache.velocity</groupId>
    <artifactId>velocity-engine-core</artifactId>
    <version>2.3</version>
</dependency>

<!-- Async Processing -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-async</artifactId>
</dependency>
```

### 10.2 Requisitos de Infraestrutura

- **JDK:** 17+
- **Spring Boot:** 3.2.x
- **PostgreSQL:** 14+
- **Redis:** (opcional, para cache)
- **Memória:** +512MB heap para processamento de skills

### 10.3 Requisitos de Dados

Para skills funcionarem adequadamente, os treinos precisam ter:

**Obrigatório:**
- Etapas com tipo definido (INTERVALO, LONG_RUN, etc.)
- Pace realizado por etapa
- Duração por etapa

**Opcional (melhora análises):**
- FC média e máxima por etapa
- Cadência média
- Repetições individuais (para intervalados)
- Dados de recuperação entre repetições

---

## 11. Documentação e Treinamento

### 11.1 Documentação Técnica

**Para Desenvolvedores:**
- Guia de criação de novas skills
- API documentation (Swagger/OpenAPI)
- Exemplos de regras YAML
- Troubleshooting guide

**Para QA:**
- Casos de teste por skill
- Cenários de edge cases
- Validação de interpretações

### 11.2 Documentação de Produto

**Para Usuários:**
- Explicação do que são as análises
- Como interpretar feedback
- Glossário de termos técnicos
- FAQs

**Para Treinadores:**
- Base científica das análises
- Como usar insights para ajustar treinos
- Casos de uso práticos

---

## 12. Conclusão

### 12.1 Benefícios Esperados

**Técnicos:**
- ✅ Sistema modular e extensível
- ✅ Regras auditáveis e versionadas
- ✅ Fácil adição de novas skills
- ✅ Performance otimizada com processamento assíncrono

**Produto:**
- ✅ Diferencial competitivo significativo
- ✅ Feedback personalizado automaticamente
- ✅ Educação contínua do usuário
- ✅ Base para features premium

**Negócio:**
- ✅ Aumento de engagement e retenção
- ✅ Redução de churn
- ✅ Oportunidade de monetização
- ✅ Posicionamento como app "expert"

### 12.2 Próximos Passos

1. **Aprovação:** Revisar e aprovar este documento
2. **Kick-off:** Alinhar equipe e iniciar Sprint 1
3. **Setup:** Configurar ambiente e estrutura base
4. **Desenvolvimento:** Seguir cronograma das fases
5. **Deploy:** Lançamento gradual com feature flag

---

## 13. Anexos

### Anexo A: Glossário de Termos

| Termo | Definição |
|-------|-----------|
| **Skill** | Módulo de conhecimento especializado em YAML |
| **Drift Cardíaco** | Aumento de FC em pace constante ao longo do treino |
| **Negative Split** | Segunda metade do treino mais rápida que a primeira |
| **Decaimento** | Queda de performance ao longo das repetições |
| **CV (Coeficiente de Variação)** | Desvio-padrão dividido pela média, em % |
| **Pa:HR Ratio** | Relação entre pace e frequência cardíaca |
| **LTHR** | Lactate Threshold Heart Rate (FC no limiar de lactato) |
| **Z2** | Zona 2 de treino (60-70% FCmax, base aeróbica) |

### Anexo B: Referências Científicas

1. **Daniels, Jack.** "Daniels' Running Formula." 3rd ed. Human Kinetics, 2013.
2. **Seiler, Stephen.** "What is Best Practice for Training Intensity and Duration Distribution in Endurance Athletes?" International Journal of Sports Physiology and Performance, 2010.
3. **Esteve-Lanao, J., et al.** "Impact of Training Intensity Distribution on Performance in Endurance Athletes." Journal of Strength and Conditioning Research, 2007.
4. **Foster, C., et al.** "A New Approach to Monitoring Exercise Training." Journal of Strength and Conditioning Research, 2001.

---

**Documento aprovado por:**

_________________________  
Leandro - Senior Software Engineer  
Data: ___/___/2026

---

**Versão:** 1.0  
**Última atualização:** 10/02/2026  
**Próxima revisão:** 17/02/2026 (início Sprint 1)
