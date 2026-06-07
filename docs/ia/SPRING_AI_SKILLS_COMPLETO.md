# Spring AI - Plano Completo de Implementação de Skills

**Nota Introdutória:** Este documento consolida múltiplos planos de implementação originais (Plano_SpringAI_Agent_Skills.md, plano_spring_ai_agent_skills.md, skills-implementacao.md e Plano_Implementacao_Skills_Menthoros.md), apresentando uma visão unificada e completa do sistema de Skills para o Menthoros com Spring AI Agent.

**Documento Técnico:** Arquitetura usando Spring AI Generic Agent Skills
**Versão:** 2.1.0
**Data:** 12 de Fevereiro de 2026
**Autor:** Leandro - Senior Software Engineer
**Referência:** https://spring.io/blog/2026/01/13/spring-ai-generic-agent-skills

---

## Sumário Executivo

Este documento descreve o plano detalhado de implementação de um sistema de **Skills Especializadas** para análise automática de treinos de corrida no aplicativo Menthoros, utilizando a abordagem moderna do Spring AI Generic Agent Skills.

### Objetivos Principais

1. **Migrar de YAML-based skills** para arquitetura nativa Spring AI com suporte a function calling
2. **Automatizar análises técnicas** de treinos usando conhecimento especializado baseado em fisiologia do esporte
3. **Integrar IA (Claude) com função calling** para análises contextualizadas e revisões semanais automáticas
4. **Criar base de conhecimento** interpretável, auditável e versionada
5. **Diferenciar competitivamente** o Menthoros de apps genéricos

### Resultados Esperados

- ✅ Análises automáticas determinísticas em 100% dos treinos elegíveis
- ✅ Integração nativa Spring AI com ChatClient
- ✅ Skills expostas como @Bean functions (tools para IA)
- ✅ Redução de 40-60% no token consumption com arquitetura de skills como tools
- ✅ Feedback personalizado baseado em contexto do atleta
- ✅ Revisões semanais geradas por IA com rastreabilidade total
- ✅ Capacidade de adicionar novas skills sem alterar código core

---

## 1. Problema Resolvido e Contexto do Mercado

### 1.1 O Problema

O corredor amador termina seu treino e vê: pace 5:41/km, FC média 155 bpm, 21.1 km. **O que esses números significam para a evolução dele?** Ele melhorou? Piorou? O que deveria ajustar no próximo treino?

A interpretação desses dados depende de:
- **Treinador humano** ($200-500/mês) - inacessível para maioria
- **Conhecimento próprio** - exige anos de estudo em fisiologia
- **Intuição** - subjetiva e propensa a erros

### 1.2 Soluções Existentes no Mercado

| App | O que faz | Limitações |
|-----|-----------|-----------|
| **Strava** | Mostra splits, mapa, pace, social | Não interpreta fisiologicamente |
| **Garmin Connect** | Métricas avançadas (VO2max, Training Effect, Body Battery) | Genérico - mesma análise para iniciante e elite |
| **TrainingPeaks** | TSS, IF, CTL/ATL/TSB (gold standard para coaches) | Exige coach para interpretar; atleta vê números sem entender |
| **Nike Run Club** | Planos guiados, audio coaching | Zero análise pós-treino |
| **COROS/Polar** | Hardware excelente, métricas no relógio | Análise confinada ao ecossistema do relógio |

### 1.3 O Diferencial Menthoros

**"Treinador de IA que Conhece Você"** — implementado com Spring AI Skills:

- Análises determinísticas baseadas em fisiologia (sem IA)
- IA contextualizada que "lê" suas análises e entende seu progresso
- Recomendações personalizadas que evoluem com o tempo
- Feedback educacional que treina o seu entendimento

---

## 2. Visão Geral - Spring AI Agent Skills

### 2.1 O Que São Agent Skills?

Agent Skills no Spring AI são **capacidades executáveis** que podem ser:

1. **Chamadas diretamente por código Java** - Como métodos normais em Services
2. **Expostas como "tools" para LLMs** - Claude, GPT, etc. usam via function calling
3. **Compostas em workflows complexos** - Skills podem chamar outras skills
4. **Gerenciadas pelo container Spring** - Ciclo de vida automático

### 2.2 Diferença da Abordagem Anterior (YAML)

#### Antes (YAML-based Skills)

```yaml
# performance-decay-rules.yml
performance_decay:
  interpretation:
    excellent:
      range: [0, 3]
      meaning: "..."
```

**Limitações:**
- Parsing overhead em runtime
- Difícil de testar
- Sem suporte nativo para function calling (IA)
- Versionamento complexo

#### Agora (Spring AI Generic Agent Skills)

```java
@Component
public class PerformanceDecaySkill {

    public PerformanceDecayResult analyze(Workout workout) {
        // Lógica pura em código
        double decay = calculateDecay(workout);

        // Interpretação em código estruturado
        Interpretation interpretation = interpretDecay(decay);

        return PerformanceDecayResult.success(interpretation);
    }
}
```

**Vantagens:**
- Type safety em compile-time
- Testabilidade com JUnit puro
- Suporte nativo Spring AI para function calling
- DI automático, observabilidade integrada
- Refactoring seguro com IDE

### 2.3 Dois Padrões Complementares

#### Padrão 1: Skills Determinísticas (Análises)

Executadas diretamente quando treino é registrado:

```
TreinoRegistrado → WorkoutAnalysisService → IntervalAnalysisSkill.execute()
                                          → LongRunAnalysisSkill.execute()
                                          ↓
                          Feedback estruturado + salvo
```

**Custo:** $0 (processamento local)

#### Padrão 2: Skills Como Tools (Para IA)

Expostas como functions para Claude usar com function calling:

```
ChatClient (Claude) com prompt
    ↓
Claude raciocina: "Preciso entender o contexto do atleta"
    ↓
Claude chama tool: getWeekWorkouts()
Claude chama tool: analyzeInterval(workoutId=123)
Claude chama tool: getAthleteProfile()
    ↓
Claude gera revisão contextualizada
```

**Custo:** ~$0.05 por revisão (1 chamada IA + vários tools)

---

## 3. Arquitetura Proposta

### 3.1 Visão Geral dos Componentes

```
┌─────────────────────────────────────────────────────────────┐
│                    MENTHOROS BACKEND                        │
│                                                             │
│  ┌────────────────────────────────────────────────────┐   │
│  │              Spring AI Agent                       │   │
│  │  ┌──────────────────────────────────────────────┐ │   │
│  │  │         ChatClient (Claude API)              │ │   │
│  │  └──────────────────────────────────────────────┘ │   │
│  │                        ↕                           │   │
│  │  ┌──────────────────────────────────────────────┐ │   │
│  │  │         Function Calling / Tools             │ │   │
│  │  └──────────────────────────────────────────────┘ │   │
│  └────────────────┬───────────────────────────────────┘   │
│                   │                                        │
│  ┌────────────────┴───────────────────────────────────┐   │
│  │           Workout Analysis Skills                  │   │
│  │  ┌──────────────────────────────────────────────┐ │   │
│  │  │ @Component                                   │ │   │
│  │  │ IntervalAnalysisSkill                        │ │   │
│  │  │  - calculatePerformanceDecay()               │ │   │
│  │  │  - calculatePaceConsistency()                │ │   │
│  │  │  - interpretResults()                        │ │   │
│  │  └──────────────────────────────────────────────┘ │   │
│  │                                                    │   │
│  │  ┌──────────────────────────────────────────────┐ │   │
│  │  │ @Component                                   │ │   │
│  │  │ LongRunAnalysisSkill                         │ │   │
│  │  │  - calculateCardiacDrift()                   │ │   │
│  │  │  - detectNegativeSplit()                     │ │   │
│  │  │  - interpretResults()                        │ │   │
│  │  └──────────────────────────────────────────────┘ │   │
│  │                                                    │   │
│  │  ┌──────────────────────────────────────────────┐ │   │
│  │  │ @Component                                   │ │   │
│  │  │ WeeklyReviewSkill / Tools                    │ │   │
│  │  │  - getWeekWorkouts()                         │ │   │
│  │  │  - getWorkoutAnalysis(id)                    │ │   │
│  │  │  - getAthleteContext()                       │ │   │
│  │  └──────────────────────────────────────────────┘ │   │
│  └────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌────────────────────────────────────────────────────┐   │
│  │           Domain Services & Repositories           │   │
│  │  - WorkoutRepository                              │   │
│  │  - TrainingPlanRepository                         │   │
│  │  - AnalysisRepository                             │   │
│  └────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 Fluxos de Uso

#### Fluxo 1: Análise Determinística (SEM IA)

```
Treino Registrado
    ↓
WorkoutAnalysisService escuta TreinoRegistradoEvent
    ↓
Detecta tipo e chama skill apropriada:
  - IntervalAnalysisSkill.execute()
  - LongRunAnalysisSkill.execute()
    ↓
Retorna análise estruturada com métricas + interpretações
    ↓
Salva análise + Notifica usuário
```

**Custo:** $0 (processamento local)

#### Fluxo 2: Revisão Semanal (COM IA)

```
Domingo 20:00
    ↓
WeeklyReviewService (agendado)
    ↓
ChatClient + prompt estruturado + Tools disponíveis
    ↓
Claude raciocina:
  - Chama getWeekWorkouts() → lista treinos semana
  - Chama analyzeInterval(id=456) → obtém análise
  - Chama getAthleteContext() → entende perfil
    ↓
Claude gera revisão contextualizada
    ↓
Salva + Notifica usuário
```

**Custo:** ~$0.05 (1 chamada IA, vários tools internos)

#### Fluxo 3: Geração de Plano Semanal (COM IA + Skills)

```
Segunda 09:00
    ↓
TrainingPlanService (agendado)
    ↓
ChatClient + system prompt + Tools disponíveis
    ↓
Claude raciocina:
  - Chama calcularMetricasAtuais() → TSB, CTL, fadiga
  - Chama buscarHistoricoTreinos() → padrão recente
  - Chama getMacrocycleContext() → fase do treino
    ↓
Claude gera plano com volume, intensidades, recomendações
    ↓
Salva + Notifica usuário
```

**Custo:** ~$0.10 (1 chamada IA, tools variável)

---

## 4. Implementação Técnica Detalhada

### 4.1 Dependências Maven

```xml
<dependencies>
    <!-- Spring AI Core -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-core</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- Spring AI Anthropic (Claude) -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-anthropic</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- Spring AI Agent Utils -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-agent-utils</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- YAML Processing (para interpretação de métricas) -->
    <dependency>
        <groupId>com.fasterxml.jackson.dataformat</groupId>
        <artifactId>jackson-dataformat-yaml</artifactId>
    </dependency>

    <!-- Template Engine (para feedback dinâmico) -->
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
</dependencies>
```

### 4.2 Configuração Spring AI

```java
// AIConfiguration.java
@Configuration
public class AIConfiguration {

    @Value("${spring.ai.anthropic.api-key}")
    private String anthropicApiKey;

    @Bean
    public AnthropicChatModel chatModel() {
        return AnthropicChatModel.builder()
            .apiKey(anthropicApiKey)
            .model("claude-sonnet-4-20250514")
            .build();
    }

    @Bean
    public ChatClient chatClient(
        AnthropicChatModel chatModel,
        List<FunctionCallback> tools // ← Auto-injetado com todas as tools
    ) {
        return ChatClient.builder(chatModel)
            .defaultTools(tools) // Skills expostas como tools
            .build();
    }
}
```

### 4.3 Application Properties

```properties
# Spring AI Configuration
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
spring.ai.anthropic.chat.options.model=claude-sonnet-4-20250514
spring.ai.anthropic.chat.options.max-tokens=2000
spring.ai.anthropic.chat.options.temperature=0.7

# Observability
management.tracing.sampling.probability=1.0
management.metrics.export.prometheus.enabled=true
```

---

## 5. Skills de Análise Determinísticas

### 5.1 IntervalAnalysisSkill

Analisa treinos intervalados (ex: 10x400m, 5x1000m, fartlek) focando em:
- **Decaimento de performance** - queda de pace ao longo das repetições
- **Consistência de ritmo** - variabilidade entre repetições
- **Recuperação cardíaca** - capacidade de reduzir FC entre esforços

**Base Fisiológica:**

- **Decaimento < 3%:** Elite | **3-5%:** Bem treinado | **5-8%:** Intermediário | **>8%:** Iniciante/Fatigado
- **Consistência (CV) < 2%:** Excelente | **2-4%:** Bom | **>6%:** Necessita ajuste
- **HR recovery > 30 bpm:** Excelente aeróbica | **< 20 bpm:** Base aeróbica insuficiente

```java
package com.menthoros.skill.analysis;

import org.springframework.stereotype.Component;
import com.menthoros.domain.model.*;
import java.util.List;

@Component
public class IntervalAnalysisSkill {

    /**
     * Analisa treino intervalado calculando métricas de performance
     */
    public IntervalAnalysisResult analyze(Workout workout) {

        // 1. Validar se é treino intervalado
        WorkoutStage intervalStage = findIntervalStage(workout);
        if (intervalStage == null) {
            throw new IllegalArgumentException("Workout has no interval stage");
        }

        // 2. Calcular métricas
        PerformanceDecayMetrics decay = calculatePerformanceDecay(intervalStage);
        PaceConsistencyMetrics consistency = calculatePaceConsistency(intervalStage);
        HRRecoveryMetrics recovery = calculateHRRecovery(intervalStage);

        // 3. Interpretar baseado em ranges
        PerformanceLevel decayLevel = interpretDecay(decay.getPercentage());
        ConsistencyLevel consistencyLevel = interpretConsistency(consistency.getCv());
        RecoveryLevel recoveryLevel = interpretRecovery(recovery.getAvgDropBpm());

        // 4. Gerar recomendações
        List<String> recommendations = generateRecommendations(
            decayLevel,
            consistencyLevel,
            recoveryLevel
        );

        // 5. Retornar resultado estruturado
        return IntervalAnalysisResult.builder()
            .workoutId(workout.getId())
            .decay(decay)
            .decayLevel(decayLevel)
            .consistency(consistency)
            .consistencyLevel(consistencyLevel)
            .recovery(recovery)
            .recoveryLevel(recoveryLevel)
            .recommendations(recommendations)
            .timestamp(LocalDateTime.now())
            .build();
    }

    // ===== CÁLCULO DE MÉTRICAS =====

    private PerformanceDecayMetrics calculatePerformanceDecay(WorkoutStage stage) {
        List<Repetition> reps = stage.getRepetitions();

        double paceFirstSec = convertPaceToSeconds(reps.get(0).getPace());
        double paceLastSec = convertPaceToSeconds(reps.get(reps.size() - 1).getPace());

        double percentage = ((paceLastSec - paceFirstSec) / paceFirstSec) * 100;

        return PerformanceDecayMetrics.builder()
            .percentage(percentage)
            .initialPace(reps.get(0).getPace())
            .finalPace(reps.get(reps.size() - 1).getPace())
            .differenceSeconds(paceLastSec - paceFirstSec)
            .numberOfReps(reps.size())
            .build();
    }

    private PaceConsistencyMetrics calculatePaceConsistency(WorkoutStage stage) {
        List<Repetition> reps = stage.getRepetitions();

        double[] paces = reps.stream()
            .mapToDouble(r -> convertPaceToSeconds(r.getPace()))
            .toArray();

        double mean = Arrays.stream(paces).average().orElse(0);
        double variance = Arrays.stream(paces)
            .map(p -> Math.pow(p - mean, 2))
            .average()
            .orElse(0);
        double stdDev = Math.sqrt(variance);
        double cv = (stdDev / mean) * 100;

        return PaceConsistencyMetrics.builder()
            .coefficientOfVariation(cv)
            .standardDeviation(stdDev)
            .avgPaceSeconds(mean)
            .build();
    }

    private HRRecoveryMetrics calculateHRRecovery(WorkoutStage stage) {
        double avgDrop = stage.getRepetitions().stream()
            .filter(r -> r.getRecoveryFinalHR() != null)
            .mapToDouble(r -> r.getMaxHR() - r.getRecoveryFinalHR())
            .average()
            .orElse(0);

        return HRRecoveryMetrics.builder()
            .avgDropBpm(avgDrop)
            .recoveryTimeSeconds(stage.getRepetitions().get(0).getRecoveryDurationSeconds())
            .build();
    }

    // ===== INTERPRETAÇÃO =====

    private PerformanceLevel interpretDecay(double percentage) {
        if (percentage < 3) return PerformanceLevel.EXCELLENT;
        if (percentage < 5) return PerformanceLevel.VERY_GOOD;
        if (percentage < 8) return PerformanceLevel.GOOD;
        if (percentage < 12) return PerformanceLevel.FAIR;
        return PerformanceLevel.POOR;
    }

    private ConsistencyLevel interpretConsistency(double cv) {
        if (cv < 2) return ConsistencyLevel.EXCELLENT;
        if (cv < 4) return ConsistencyLevel.GOOD;
        if (cv < 6) return ConsistencyLevel.FAIR;
        return ConsistencyLevel.POOR;
    }

    private RecoveryLevel interpretRecovery(double avgDrop) {
        if (avgDrop > 30) return RecoveryLevel.EXCELLENT;
        if (avgDrop > 25) return RecoveryLevel.GOOD;
        if (avgDrop > 20) return RecoveryLevel.FAIR;
        return RecoveryLevel.POOR;
    }

    // ===== RECOMENDAÇÕES =====

    private List<String> generateRecommendations(
        PerformanceLevel decay,
        ConsistencyLevel consistency,
        RecoveryLevel recovery
    ) {
        List<String> recommendations = new ArrayList<>();

        // Baseado em decay
        switch (decay) {
            case EXCELLENT, VERY_GOOD ->
                recommendations.add("Excellent decay control. Can increase volume or intensity.");
            case GOOD ->
                recommendations.add("Good performance. Focus on aerobic base (Z2 runs).");
            case FAIR, POOR -> {
                recommendations.add("High decay indicates inadequate aerobic base.");
                recommendations.add("Reduce interval intensity by 5-10s/km.");
                recommendations.add("Increase Z2 volume to 80% of weekly mileage.");
            }
        }

        // Baseado em consistency
        if (consistency == ConsistencyLevel.POOR) {
            recommendations.add("Use pace alerts on watch to improve consistency.");
            recommendations.add("Start 2-3s/km slower than target pace.");
        }

        // Baseado em recovery
        if (recovery == RecoveryLevel.FAIR || recovery == RecoveryLevel.POOR) {
            recommendations.add("Improve HR recovery with more Z2 aerobic work.");
            recommendations.add("Consider longer recovery intervals (120s instead of 90s).");
        }

        return recommendations;
    }

    // ===== HELPERS =====

    private WorkoutStage findIntervalStage(Workout workout) {
        return workout.getStages().stream()
            .filter(s -> s.getType() == StageType.INTERVAL)
            .filter(s -> s.getRepetitions() != null && s.getRepetitions().size() >= 3)
            .findFirst()
            .orElse(null);
    }

    private double convertPaceToSeconds(String pace) {
        // "4:15" → 255 seconds
        String[] parts = pace.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }
}
```

### 5.2 LongRunAnalysisSkill

Analisa treinos longos (>10km, >60min) focando em:
- **Drift cardíaco** - aumento de FC em pace constante
- **Negative split** - distribuição de esforço (segunda metade mais rápida)
- **Eficiência aeróbica** - Pa:HR ratio

**Base Fisiológica:**

- **Drift < 3%:** Excelente | **3-5%:** Bom | **5-8%:** Moderado | **>8%:** Alto (desidratação/pace agressivo)
- **Negative split:** Ideal para maratonas (preserva glicogênio, reduz lactato)
- **Efficiency factor:** Correlação pace/HR

```java
package com.menthoros.skill.analysis;

import org.springframework.stereotype.Component;

@Component
public class LongRunAnalysisSkill {

    public LongRunAnalysisResult analyze(Workout workout) {

        WorkoutStage longRunStage = findLongRunStage(workout);
        if (longRunStage == null) {
            throw new IllegalArgumentException("Not a long run workout");
        }

        // Calcular métricas
        CardiacDriftMetrics drift = calculateCardiacDrift(longRunStage);
        NegativeSplitMetrics split = calculateNegativeSplit(longRunStage);
        EfficiencyMetrics efficiency = calculateEfficiency(longRunStage);

        // Interpretar
        DriftLevel driftLevel = interpretDrift(drift.getPercentage());
        SplitType splitType = interpretSplit(split);

        // Gerar recomendações
        List<String> recommendations = generateRecommendations(
            driftLevel,
            splitType,
            drift,
            split
        );

        return LongRunAnalysisResult.builder()
            .workoutId(workout.getId())
            .drift(drift)
            .driftLevel(driftLevel)
            .split(split)
            .splitType(splitType)
            .efficiency(efficiency)
            .recommendations(recommendations)
            .timestamp(LocalDateTime.now())
            .build();
    }

    private CardiacDriftMetrics calculateCardiacDrift(WorkoutStage stage) {
        List<SegmentMetrics> segments = stage.getSegments();
        int midpoint = segments.size() / 2;

        double firstHalfHR = segments.subList(0, midpoint).stream()
            .mapToDouble(SegmentMetrics::getAvgHR)
            .average()
            .orElse(0);

        double secondHalfHR = segments.subList(midpoint, segments.size()).stream()
            .mapToDouble(SegmentMetrics::getAvgHR)
            .average()
            .orElse(0);

        double percentage = ((secondHalfHR - firstHalfHR) / firstHalfHR) * 100;

        return CardiacDriftMetrics.builder()
            .percentage(percentage)
            .firstHalfAvgHR(firstHalfHR)
            .secondHalfAvgHR(secondHalfHR)
            .build();
    }

    private NegativeSplitMetrics calculateNegativeSplit(WorkoutStage stage) {
        List<SegmentMetrics> segments = stage.getSegments();
        int midpoint = segments.size() / 2;

        double firstHalfPace = segments.subList(0, midpoint).stream()
            .mapToDouble(s -> convertPaceToSeconds(s.getAvgPace()))
            .average()
            .orElse(0);

        double secondHalfPace = segments.subList(midpoint, segments.size()).stream()
            .mapToDouble(s -> convertPaceToSeconds(s.getAvgPace()))
            .average()
            .orElse(0);

        double differenceSeconds = secondHalfPace - firstHalfPace;

        return NegativeSplitMetrics.builder()
            .isNegativeSplit(differenceSeconds < 0)
            .firstHalfPace(formatPace(firstHalfPace))
            .secondHalfPace(formatPace(secondHalfPace))
            .differenceSeconds(Math.abs(differenceSeconds))
            .build();
    }

    private DriftLevel interpretDrift(double percentage) {
        if (percentage < 3) return DriftLevel.EXCELLENT;
        if (percentage < 5) return DriftLevel.GOOD;
        if (percentage < 8) return DriftLevel.MODERATE;
        return DriftLevel.HIGH;
    }

    private SplitType interpretSplit(NegativeSplitMetrics split) {
        if (split.isNegativeSplit()) {
            return split.getDifferenceSeconds() > 10
                ? SplitType.NEGATIVE_STRONG
                : SplitType.NEGATIVE_MILD;
        } else {
            return split.getDifferenceSeconds() > 30
                ? SplitType.POSITIVE_SEVERE
                : SplitType.POSITIVE_MILD;
        }
    }

    private List<String> generateRecommendations(
        DriftLevel drift,
        SplitType split,
        CardiacDriftMetrics driftMetrics,
        NegativeSplitMetrics splitMetrics
    ) {
        List<String> recommendations = new ArrayList<>();

        // Drift alto
        if (drift == DriftLevel.HIGH) {
            recommendations.add("High cardiac drift indicates:");
            recommendations.add("1. Starting pace too aggressive");
            recommendations.add("2. Possible dehydration - drink 200ml every 15min");
            recommendations.add("3. Inadequate aerobic base");
            recommendations.add("Next long run: start 20-30s/km slower");
        }

        // Positive split severo
        if (split == SplitType.POSITIVE_SEVERE) {
            recommendations.add("Severe positive split - pacing error!");
            recommendations.add("You started " + (int)splitMetrics.getDifferenceSeconds() + "s/km too fast");
            recommendations.add("MANDATORY: Use pace alerts on watch");
            recommendations.add("Start at target pace + 15s/km, allow natural progression");
        }

        // Negative split (celebrar!)
        if (split == SplitType.NEGATIVE_STRONG || split == SplitType.NEGATIVE_MILD) {
            recommendations.add("Excellent pacing discipline!");
            recommendations.add("Negative splits train race-day mental strength");
            recommendations.add("Continue this strategy in future long runs");
        }

        return recommendations;
    }

    private WorkoutStage findLongRunStage(Workout workout) {
        return workout.getStages().stream()
            .filter(s -> s.getType() == StageType.LONG_RUN || s.getType() == StageType.EASY)
            .filter(s -> s.getDistanceKm() >= 10)
            .filter(s -> s.getDurationSeconds() >= 3600) // >= 60min
            .findFirst()
            .orElse(null);
    }
}
```

---

## 6. Skills Como Tools (Para IA com Function Calling)

### 6.1 Padrão: Tools em Classes Separadas (SEM @Tool)

O padrão recomendado é **separar Java puro de Spring AI**:

```java
// MetricasTools.java — Java puro, sem @Tool
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricasTools {
    private final PlanoMetaDadosRepository repo;

    // Record leve — não expõe entidade JPA ao LLM
    public record MetricasAtuaisResult(
        Double tsbAtual,
        Double ctlAtual,
        Double atlAtual,
        String interpretacaoTsb,
        Boolean alertaDiasConsecutivos
    ) {}

    // Método Java comum — sem Spring AI
    public MetricasAtuaisResult calcularMetricasAtuais(String atletaId) {
        log.info("[TOOL] calcularMetricasAtuais — atletaId={}", atletaId);

        PlanoMetaDados meta = repo
            .findFirstByAtletaIdOrderByDataAtualizacaoDesc(UUID.fromString(atletaId))
            .orElseThrow(() -> new IllegalArgumentException("Atleta sem metadados: " + atletaId));

        return new MetricasAtuaisResult(
            meta.getTsbAtual(),
            meta.getCtlAtual(),
            meta.getAtlAtual(),
            meta.getInterpretacaoTsb(),
            meta.getAlertaDiasConsecutivos()
        );
    }
}
```

### 6.2 Registro das Tools via Configuration

```java
@Configuration
@RequiredArgsConstructor
public class ToolsConfig {

    private final MetricasTools metricasTools;
    private final TreinoHistoricoTools treinoHistoricoTools;

    @Bean("toolsPlanoSemanal")
    public List<FunctionCallback> toolsPlanoSemanal() {
        return List.of(
            FunctionCallbackWrapper.builder(metricasTools::calcularMetricasAtuais)
                .withName("calcularMetricasAtuais")
                .withDescription("""
                    Retorna as métricas de carga e fadiga atuais do atleta:
                    TSB (forma), CTL (fitness), ATL (fadiga), ramp rate e interpretações.
                    Chame ANTES de definir TSS-alvo.
                    """)
                .withInputType(String.class)  // atletaId direto
                .build(),

            FunctionCallbackWrapper.builder(treinoHistoricoTools::buscarHistoricoTreinos)
                .withName("buscarHistoricoTreinos")
                .withDescription("""
                    Busca os treinos realizados pelo atleta nos últimos N dias.
                    Retorna: dataTreino, tipoTreino, duracaoMin, tssCalculado, percepcaoEsforco.
                    """)
                .withInputType(TreinoHistoricoTools.BuscarHistoricoInput.class)
                .build()
        );
    }
}
```

### 6.3 Exposição de Skills como Tools para IA

```java
package com.menthoros.skill.tools;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import java.util.function.Function;

@Configuration
public class AnalysisToolsConfiguration {

    private final IntervalAnalysisSkill intervalSkill;
    private final LongRunAnalysisSkill longRunSkill;
    private final WorkoutRepository workoutRepository;

    public AnalysisToolsConfiguration(
        IntervalAnalysisSkill intervalSkill,
        LongRunAnalysisSkill longRunSkill,
        WorkoutRepository workoutRepository
    ) {
        this.intervalSkill = intervalSkill;
        this.longRunSkill = longRunSkill;
        this.workoutRepository = workoutRepository;
    }

    /**
     * Tool: Buscar treino por ID
     */
    @Bean
    @Description("Get workout details by ID including all stages and repetitions")
    public Function<WorkoutRequest, Workout> getWorkout() {
        return request -> workoutRepository
            .findByIdWithStages(request.workoutId())
            .orElseThrow(() -> new RuntimeException("Workout not found: " + request.workoutId()));
    }

    /**
     * Tool: Analisar treino intervalado
     */
    @Bean
    @Description("""
        Analyze interval training workout calculating:
        - Performance decay (pace drop across reps)
        - Pace consistency (coefficient of variation)
        - HR recovery between intervals

        Returns structured analysis with levels and recommendations.
        """)
    public Function<AnalyzeIntervalRequest, IntervalAnalysisResult> analyzeInterval() {
        return request -> {
            Workout workout = workoutRepository
                .findByIdWithStages(request.workoutId())
                .orElseThrow();

            return intervalSkill.analyze(workout);
        };
    }

    /**
     * Tool: Analisar long run
     */
    @Bean
    @Description("""
        Analyze long run workout calculating:
        - Cardiac drift (HR increase at constant pace)
        - Negative/positive split
        - Aerobic efficiency

        Returns structured analysis with levels and recommendations.
        """)
    public Function<AnalyzeLongRunRequest, LongRunAnalysisResult> analyzeLongRun() {
        return request -> {
            Workout workout = workoutRepository
                .findByIdWithStages(request.workoutId())
                .orElseThrow();

            return longRunSkill.analyze(workout);
        };
    }

    /**
     * Tool: Buscar treinos da semana
     */
    @Bean
    @Description("Get all workouts from current week for a user")
    public Function<WeekWorkoutsRequest, List<Workout>> getWeekWorkouts() {
        return request -> workoutRepository.findThisWeek(request.userId());
    }

    /**
     * Tool: Buscar perfil do atleta
     */
    @Bean
    @Description("Get athlete profile including goals, level, preferences")
    public Function<AthleteProfileRequest, AthleteProfile> getAthleteProfile() {
        return request -> athleteRepository
            .findById(request.userId())
            .map(User::getAthleteProfile)
            .orElseThrow();
    }

    // ===== Request Records =====

    public record WorkoutRequest(Long workoutId) {}
    public record AnalyzeIntervalRequest(Long workoutId) {}
    public record AnalyzeLongRunRequest(Long workoutId) {}
    public record WeekWorkoutsRequest(Long userId) {}
    public record AthleteProfileRequest(Long userId) {}
}
```

### 6.4 Como a IA Usa os Tools

```java
// WeeklyReviewService.java
@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyReviewService {

    private final ChatClient chatClient;
    private final List<FunctionCallback> toolsPlanoSemanal;

    @Scheduled(cron = "0 0 20 * * SUN")
    public void generateWeeklyReviews() {

        List<User> activeUsers = userRepository.findActiveThisWeek();

        for (User user : activeUsers) {

            String prompt = String.format("""
                You are an expert running coach analyzing a complete training week.

                User ID: %d

                TASK:
                1. Use getWeekWorkouts() to fetch this week's workouts
                2. For each workout:
                   - Use analyzeInterval() if it's an interval workout
                   - Use analyzeLongRun() if it's a long run
                3. Use getAthleteProfile() to understand goals and context
                4. Generate comprehensive weekly review covering:
                   - Overall assessment
                   - Pattern analysis
                   - Wins to celebrate
                   - Adjustments for next week

                Be specific and actionable. Length: 300-400 words.
                """, user.getId());

            // ChatClient VAI CHAMAR OS TOOLS automaticamente!
            String review = chatClient.prompt()
                .user(prompt)
                .tools(toolsPlanoSemanal)  // Tools disponíveis para Claude
                .call()
                .content();

            // Salvar revisão
            saveWeeklyReview(user.getId(), review);

            // Notificar
            notificationService.send(user.getId(), "📊 Weekly review ready!");

            log.info("[WEEKLY_REVIEW] Generated for user {}", user.getId());
        }
    }
}
```

**O que acontece internamente:**

```
→ Claude recebe prompt + tools list

Turno 1 — Claude raciocina:
  "Preciso buscar os treinos da semana"
  → tool_use: getWeekWorkouts(userId=123)
  ← resultado: [Workout{id=456, type=INTERVAL}, ...]

Turno 2 — Claude:
  "Vou analisar cada um"
  → tool_use: analyzeInterval(workoutId=456)
  ← resultado: {decay: 4.2%, consistency: GOOD, ...}

Turno 3 — Claude:
  "Preciso do perfil do atleta para contexto"
  → tool_use: getAthleteProfile(userId=123)
  ← resultado: {level: INTERMEDIATE, goals: [Marathon], ...}

Turno 4 — Claude:
  "Tenho dados suficientes, gero revisão"
  → [STOP] → gera texto da revisão

Resultado:
  "Weekly Review: 5 workouts completed, average decay 4.2% indicates good
   aerobic capacity. Focus next week on long run with attention to pacing..."
```

---

## 7. Trace de Raciocínio do Agente (Exemplo: Semana Regenerativa)

**Cenário:** Atleta intermediário com TSB = -25, dias consecutivos = 6

```
→ Claude recebe:
  - Perfil: INTERMEDIARIO, FCmax=180, dias disponíveis: seg/qua/sex/sab
  - [PROIBIDO] treino intervalado — Gate 1 ativo
  - Macrociclo: semana 8/18, fase BASE, TSS-alvo: 180 (recuperação)

Turno 1 — Claude raciocina:
  "Preciso confirmar exatamente o estado de fadiga antes de prescrever volumes."
  → tool_use: calcularMetricasAtuais("uuid-atleta")
  ← resultado: { tsbAtual: -25, ctlAtual: 48,
                  interpretacaoTsb: "MUITO_FATIGADO",
                  alertaDiasConsecutivos: true, diasConsecutivosTreino: 6 }

Turno 2 — Claude raciocina:
  "6 dias seguidos, TSB=-25. Semana claramente regenerativa.
   Preciso ver o que foi feito para entender o que recuperar."
  → tool_use: buscarHistoricoTreinos({ atletaId: "uuid", diasJanela: 7 })
  ← resultado: [
      { INTERVALADO 50min TSS=85 RPE=9 },
      { CONTINUO 65min TSS=70 RPE=7 },
      { LONGO 90min TSS=110 RPE=8 },
      { CONTINUO 45min TSS=55 RPE=7 },
      { TIRO 40min TSS=75 RPE=9 }
    ]

Turno 3 — Claude raciocina:
  "RPE alto nos 5 últimos dias, TSS acumulado ~395.
   Semana anterior muito pesada. TSS-alvo desta semana: 120 máximo.
   Distribuição: 2 × REGENERATIVO + 1 × CONTINUO leve. Sem intervalados.
   Dados suficientes — sem necessidade de mais tool calls."
  → [STOP] → gera PlanoSemanalDto

Resultado:
  - Segunda: REGENERATIVO 25min Z1 — TSS=20
  - Quarta:  CONTINUO 40min Z2 — TSS=45
  - Sexta:   REGENERATIVO 20min Z1 — TSS=15
  - TSS total: 80 (abaixo do alvo 120 por margem de segurança com TSB=-25)

Token consumption:
  System prompt (restrições hard):  ~950 tokens
  Tool call 1 (request + response): ~280 tokens
  Tool call 2 (request + response): ~420 tokens
  Output (PlanoSemanalDto):         ~450 tokens
  ─────────────────────────────────────────────
  TOTAL:                           ~2.100 tokens

  ANTES (buildOptimizedPrompt completo): ~3.650 tokens
  REDUÇÃO: 43% neste cenário
```

---

## 8. Cronograma de Implementação

### Sprint 1: Setup Spring AI (1 semana)

- [ ] Adicionar dependências Spring AI ao pom.xml
- [ ] Configurar AnthropicChatModel
- [ ] Configurar ChatClient com suporte a tools
- [ ] Criar testes de integração básicos
- **Entrega:** ChatClient funcional com simple prompt/response

**Estimativa:** 20 horas

### Sprint 2: Interval Analysis Skill (1 semana)

- [ ] Implementar IntervalAnalysisSkill (completo)
- [ ] Calcular métricas (decay, CV, HR recovery)
- [ ] Implementar interpretação (levels/ranges)
- [ ] Gerar recomendações contextualizadas
- [ ] Testes unitários completos
- **Entrega:** Análise intervalados funcionando

**Estimativa:** 24 horas

### Sprint 3: Long Run Analysis Skill (1 semana)

- [ ] Implementar LongRunAnalysisSkill (completo)
- [ ] Calcular métricas (drift, split, efficiency)
- [ ] Implementar interpretação
- [ ] Gerar recomendações
- [ ] Testes unitários completos
- **Entrega:** Análise long runs funcionando

**Estimativa:** 24 horas

### Sprint 4: Tools Configuration (1 semana)

- [ ] Expor skills como @Bean functions
- [ ] Criar AnalysisToolsConfiguration
- [ ] Documentar cada tool com @Description
- [ ] Testar function calling com Claude
- **Entrega:** Tools disponíveis para IA chamar

**Estimativa:** 16 horas

### Sprint 5: Weekly Review com IA (1 semana)

- [ ] Implementar WeeklyReviewService
- [ ] Criar prompt estruturado
- [ ] Testar chamadas de tools pela IA
- [ ] Implementar salvamento de review
- **Entrega:** Revisão semanal funcionando

**Estimativa:** 20 horas

### Sprint 6: Training Plan Generation (1 semana)

- [ ] Implementar TrainingPlanService
- [ ] Expor tools para contexto do atleta
- [ ] Criar prompts para geração de plano
- [ ] Validar planos gerados
- **Entrega:** Geração automática de planos

**Estimativa:** 24 horas

### Sprint 7: UI + Polimento (1 semana)

- [ ] Frontend para exibir análises
- [ ] Frontend para revisões semanais
- [ ] Notificações push
- [ ] Ajustes baseados em feedback
- **Entrega:** Sistema completo end-to-end

**Estimativa:** 32 horas

**Total:** 7 semanas (~160 horas)

---

## 9. Custos Esperados

### Por Usuário/Mês (4 semanas)

```
Planos semanais (IA):        4 × $0.10 = $0.40
Análises skills:            20 × $0.00 = $0.00  ← Determinísticas!
Revisões semanais (IA):      4 × $0.05 = $0.20
─────────────────────────────────────────────
TOTAL:                                  $0.60/mês

Margem com plano $9.99:                $9.39 (94%)
```

**Para 1.000 usuários ativos:** $600/mês
**Para 10.000 usuários ativos:** $6.000/mês

---

## 10. Vantagens da Arquitetura Spring AI Skills

### 10.1 Para Desenvolvimento

- ✅ **Type safety:** Erros em compile-time, não runtime
- ✅ **Testabilidade:** JUnit puro, sem mocking complexo
- ✅ **Debugging:** Breakpoints funcionam normalmente
- ✅ **IDE support:** Autocomplete, refactoring
- ✅ **DI nativo:** Spring gerencia lifecycle

### 10.2 Para Observabilidade

- ✅ **Métricas:** Micrometer integrado
- ✅ **Tracing:** Spring Boot Actuator
- ✅ **Logs:** SLF4J padrão
- ✅ **Monitoring:** Prometheus/Grafana ready

### 10.3 Para IA Integration

- ✅ **Function calling:** Suporte nativo Spring AI
- ✅ **Tool discovery:** Automático via @Bean
- ✅ **Descrições:** @Description para documentar
- ✅ **Composição:** Skills podem chamar outras skills
- ✅ **Multi-turn:** Claude raciocina autonomamente

### 10.4 Para Manutenção

- ✅ **Refactoring:** IDEs ajudam
- ✅ **Evolução:** Adicionar novos métodos facilmente
- ✅ **Versionamento:** Git padrão
- ✅ **Rollback:** Deploy padrão
- ✅ **Auditoria:** Rastreamento total de tools chamados

---

## 11. Comparação: YAML vs Spring AI Skills

| Aspecto | YAML-based | Spring AI Skills |
|---------|------------|------------------|
| **Tipo** | Declarativo | Programático |
| **Flexibilidade** | Limitado | Total (Java) |
| **Testabilidade** | Difícil | Fácil (JUnit) |
| **Composição** | Manual | Spring DI |
| **Observabilidade** | Custom | Spring Boot Actuator |
| **Versionamento** | Arquivos | Git + Releases |
| **Hot Reload** | Possível | Requer restart |
| **Integração IA** | Via parsing | Nativo (tools) |
| **Manutenção** | Média | Fácil |
| **Performance** | Parsing overhead | Direto em memória |
| **Function Calling** | Não suportado | Suportado nativamente |
| **Token Usage** | ~3.650 tokens | ~2.100 tokens (-43%) |

**Veredito:** Spring AI Skills é **superior** para nossa arquitetura!

---

## 12. Estrutura de Pastas Final

```
menthoros/
├── src/main/
│   ├── java/com/menthoros/
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   │   ├── Workout.java
│   │   │   │   ├── WorkoutStage.java
│   │   │   │   └── Repetition.java
│   │   │   └── repository/
│   │   │       └── WorkoutRepository.java
│   │   │
│   │   ├── skill/
│   │   │   ├── analysis/
│   │   │   │   ├── IntervalAnalysisSkill.java
│   │   │   │   └── LongRunAnalysisSkill.java
│   │   │   │
│   │   │   ├── tools/
│   │   │   │   ├── MetricasTools.java
│   │   │   │   ├── TreinoHistoricoTools.java
│   │   │   │   ├── MacrocicloPlanejamentoTools.java
│   │   │   │   └── ToolsConfig.java
│   │   │   │
│   │   │   ├── service/
│   │   │   │   ├── SkillBasedAnalysisService.java
│   │   │   │   ├── WeeklyReviewService.java
│   │   │   │   └── TrainingPlanService.java
│   │   │   │
│   │   │   └── dto/
│   │   │       ├── IntervalAnalysisResult.java
│   │   │       ├── LongRunAnalysisResult.java
│   │   │       ├── PerformanceDecayMetrics.java
│   │   │       └── CardiacDriftMetrics.java
│   │   │
│   │   ├── config/
│   │   │   └── AIConfiguration.java
│   │   │
│   │   └── listener/
│   │       └── WorkoutRegisteredListener.java
│   │
│   └── resources/
│       └── (prompts, YAML rules se necessário, etc)
│
└── pom.xml
```

---

## 13. Próximas Skills (Roadmap Futuro)

### Fase 2 (Q3 2026)

- **Skill: Training Zones** - Cálculo automático de zonas de FC baseado em LTHR
- **Skill: Periodization** - Sugestão de progressão de carga (volume x intensidade)
- **Skill: Recovery Analysis** - Análise de capacidade de recuperação cardíaca

### Fase 3 (Q4 2026)

- **Skill: Injury Prevention** - Detecção de sinais de overtraining
- **Skill: Performance Prediction** - Estimativa de tempo em provas (VDOT calculator)

---

## 14. Riscos e Mitigações

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| **Performance degrada com volume** | Média | Alto | Cache de análises, async processing, índices otimizados |
| **Regras ficam complexas demais** | Alta | Médio | Code review rigoroso, documentação clara, testes extensivos |
| **Bugs nas interpretações** | Média | Alto | Peer review com treinadores, validação com dados reais, A/B testing |
| **IA gera recomendações inadequadas** | Baixa | Alto | Disclaimers legais, validação de safety, reviews humanas periódicas |
| **Token consumption sobe muito** | Baixa | Médio | Otimização de prompts, caching de resultados |

---

## 15. Próximos Passos

1. **Aprovação:** Revisar e aprovar este documento
2. **Kick-off:** Alinhar equipe e iniciar Sprint 1
3. **Setup:** Configurar ambiente Spring AI
4. **Desenvolvimento:** Seguir cronograma das 7 sprints
5. **Deploy:** Lançamento gradual com feature flags
6. **Monitoramento:** Rastrear usage, custos, qualidade de análises

---

## Referências

- Spring AI Documentation: https://docs.spring.io/spring-ai/reference/
- Spring AI Agent Skills Blog: https://spring.io/blog/2026/01/13/spring-ai-generic-agent-skills
- Anthropic Function Calling: https://docs.anthropic.com/claude/docs/tool-use
- Daniels' Running Formula (base fisiológica)
- Seiler's Threshold Concept (treinamento por zonas)

---

**Documento aprovado por:**

_________________________
Leandro - Senior Software Engineer
Data: ___/___/2026

**Versão:** 2.1.0
**Última atualização:** 12 de Fevereiro de 2026
**Próxima revisão:** Após Sprint 1 (setup completo)
