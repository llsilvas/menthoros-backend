# Plano de Implementação - Menthoros com Spring AI Agent Skills

**Documento Técnico:** Arquitetura usando Spring AI Generic Agent Skills  
**Versão:** 2.0  
**Data:** 10 de Fevereiro de 2026  
**Autor:** Leandro - Senior Software Engineer  
**Referência:** https://spring.io/blog/2026/01/13/spring-ai-generic-agent-skills

---

## Sumário Executivo

Este documento substitui a abordagem anterior de **YAML-based skills** por **Spring AI Generic Agent Skills**, que oferece:
- ✅ Integração nativa com Spring AI
- ✅ Skills como beans Spring gerenciados
- ✅ Suporte a ferramentas (tools) no padrão do ecossistema Spring AI
- ✅ Composição de skills via anotações
- ✅ Observabilidade e monitoramento integrados

---

## 1. Visão Geral - Spring AI Agent Skills

### 1.1 O Que São Agent Skills?

Agent Skills no Spring AI são **capacidades executáveis** que podem ser:
1. Chamadas diretamente por código Java
2. Expostas como "tools" para LLMs (Claude, GPT, etc)
3. Compostas em workflows complexos
4. Gerenciadas pelo container Spring

### 1.2 Diferença da Abordagem Anterior

#### Antes (YAML-based):
```yaml
# performance-decay-rules.yml
performance_decay:
  interpretation:
    excellent:
      range: [0, 3]
      meaning: "..."
```

#### Agora (Spring AI Skills):
```java
@Component
public class PerformanceDecaySkill implements Skill {
    
    @Override
    public SkillExecution execute(SkillRequest request) {
        WorkoutData workout = request.getData(WorkoutData.class);
        
        // Lógica de cálculo
        double decay = calculateDecay(workout);
        
        // Interpretação com regras em código
        Interpretation interpretation = interpretDecay(decay);
        
        return SkillExecution.success(interpretation);
    }
}
```

---

## 2. Arquitetura Proposta

### 2.1 Visão Geral dos Componentes

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
│  │           Workout Analysis Skills                   │   │
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
│  │  │ WeeklyReviewSkill                            │ │   │
│  │  │  - aggregateWeekData()                       │ │   │
│  │  │  - detectPatterns()                          │ │   │
│  │  │  - generateRecommendations()                 │ │   │
│  │  └──────────────────────────────────────────────┘ │   │
│  └────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌────────────────────────────────────────────────────┐   │
│  │           Domain Services                          │   │
│  │  - WorkoutRepository                              │   │
│  │  - TrainingPlanRepository                         │   │
│  │  - AnalysisRepository                             │   │
│  └────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Fluxos de Uso

#### Fluxo 1: Análise Determinística (SEM IA)
```
Treino Registrado
    ↓
WorkoutAnalysisService
    ↓
IntervalAnalysisSkill.execute()
    ↓
Retorna análise estruturada
    ↓
Salva + Notifica usuário
```

#### Fluxo 2: Revisão Semanal (COM IA)
```
Domingo 20:00
    ↓
WeeklyReviewService
    ↓
ChatClient (Claude)
    ↓
Tools disponíveis:
  - getWeekWorkouts()
  - getWorkoutAnalysis(id)
  - getAthleteContext()
    ↓
Claude chama tools conforme necessário
    ↓
Gera revisão contextual
    ↓
Salva + Notifica usuário
```

#### Fluxo 3: Geração de Plano (COM IA + Skills)
```
Segunda 09:00
    ↓
TrainingPlanService
    ↓
ChatClient (Claude)
    ↓
Tools disponíveis:
  - getAthleteProfile()
  - getRecentWorkouts()
  - getLastWeekReview()
  - calculateTrainingLoad()
    ↓
Claude chama tools
    ↓
Gera plano estruturado
    ↓
Salva + Notifica usuário
```

---

## 3. Implementação Detalhada

### 3.1 Dependências Maven

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
</dependencies>
```

### 3.2 Configuração Spring AI

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
        List<Function<?, ?>> tools // ← Auto-injetado com todas as @Bean functions
    ) {
        return ChatClient.builder(chatModel)
            .defaultTools(tools) // Skills expostas como tools
            .build();
    }
}
```

### 3.3 Application Properties

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

## 4. Skills de Análise (Determinísticas)

### 4.1 IntervalAnalysisSkill

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

### 4.2 LongRunAnalysisSkill

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
            recommendations.add("⚠️ High cardiac drift indicates:");
            recommendations.add("1. Starting pace too aggressive");
            recommendations.add("2. Possible dehydration - drink 200ml every 15min");
            recommendations.add("3. Inadequate aerobic base");
            recommendations.add("Next long run: start 20-30s/km slower");
        }
        
        // Positive split severo
        if (split == SplitType.POSITIVE_SEVERE) {
            recommendations.add("🚨 Severe positive split - pacing error!");
            recommendations.add("You started " + (int)splitMetrics.getDifferenceSeconds() + "s/km too fast");
            recommendations.add("MANDATORY: Use pace alerts on watch");
            recommendations.add("Start at target pace + 15s/km, allow natural progression");
        }
        
        // Negative split (celebrar!)
        if (split == SplitType.NEGATIVE_STRONG || split == SplitType.NEGATIVE_MILD) {
            recommendations.add("✅ Excellent pacing discipline!");
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

## 5. Skills Como Tools (Para IA)

### 5.1 Expondo Skills como Tools

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

### 5.2 Como a IA Usa os Tools

```java
// WeeklyReviewService.java
@Service
public class WeeklyReviewService {
    
    private final ChatClient chatClient;
    
    public WeeklyReviewService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }
    
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
                .call()
                .content();
            
            // Salvar revisão
            saveWeeklyReview(user.getId(), review);
            
            // Notificar
            notificationService.send(user.getId(), "📊 Revisão semanal pronta!");
        }
    }
}
```

---

## 6. Comparação: YAML vs Spring AI Skills

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

**Veredito:** Spring AI Skills é **superior** para nossa arquitetura!

---

## 7. Cronograma Atualizado

### Sprint 1: Setup Spring AI (1 semana)
- [ ] Adicionar dependências Spring AI
- [ ] Configurar AnthropicChatModel
- [ ] Configurar ChatClient com tools
- [ ] Criar testes de integração básicos
- [ ] **Entrega:** ChatClient funcional

### Sprint 2: Interval Analysis Skill (1 semana)
- [ ] Implementar IntervalAnalysisSkill
- [ ] Calcular métricas (decay, CV, HR recovery)
- [ ] Implementar interpretação (levels)
- [ ] Gerar recomendações
- [ ] Testes unitários completos
- [ ] **Entrega:** Análise intervalos funcionando

### Sprint 3: Long Run Analysis Skill (1 semana)
- [ ] Implementar LongRunAnalysisSkill
- [ ] Calcular métricas (drift, split, efficiency)
- [ ] Implementar interpretação
- [ ] Gerar recomendações
- [ ] Testes unitários completos
- [ ] **Entrega:** Análise long runs funcionando

### Sprint 4: Tools Configuration (1 semana)
- [ ] Expor skills como @Bean functions
- [ ] Criar AnalysisToolsConfiguration
- [ ] Documentar cada tool com @Description
- [ ] Testar function calling com Claude
- [ ] **Entrega:** Tools disponíveis para IA

### Sprint 5: Weekly Review com IA (1 semana)
- [ ] Implementar WeeklyReviewService
- [ ] Criar prompt estruturado
- [ ] Testar chamadas de tools pela IA
- [ ] Implementar salvamento de review
- [ ] **Entrega:** Revisão semanal funcionando

### Sprint 6: Training Plan Generation (1 semana)
- [ ] Implementar TrainingPlanService
- [ ] Expor tools para contexto do atleta
- [ ] Criar prompts para geração de plano
- [ ] Validar planos gerados
- [ ] **Entrega:** Geração automática de planos

### Sprint 7: UI + Polimento (1 semana)
- [ ] Frontend para exibir análises
- [ ] Frontend para revisões semanais
- [ ] Notificações push
- [ ] Ajustes baseados em feedback
- [ ] **Entrega:** Sistema completo end-to-end

**Total:** 7 semanas (~160 horas)

---

## 8. Exemplo Completo End-to-End

### Cenário: Maria registra long run problemático

```java
// 1. Maria registra treino (09:00)
POST /api/workouts
{
  "userId": 123,
  "stages": [...]
}

// 2. WorkoutService salva + publica evento
@EventListener
public void onWorkoutRegistered(WorkoutRegisteredEvent event) {
    
    Workout workout = event.getWorkout();
    
    // 3. Detectar tipo e chamar skill apropriada
    if (isLongRun(workout)) {
        LongRunAnalysisResult analysis = longRunSkill.analyze(workout);
        
        // 4. Salvar análise
        analysisRepository.save(WorkoutAnalysis.from(analysis));
        
        // 5. Notificar usuário
        if (analysis.getDriftLevel() == DriftLevel.HIGH) {
            notificationService.sendCritical(
                workout.getUserId(),
                "⚠️ Problemas detectados no long run"
            );
        } else {
            notificationService.send(
                workout.getUserId(),
                "Análise do treino pronta! 🎯"
            );
        }
    }
}

// Custo até aqui: $0 (análise determinística!)
```

```java
// 6. Domingo 20:00 - Revisão semanal com IA
@Scheduled(cron = "0 0 20 * * SUN")
public void weeklyReview() {
    
    String prompt = """
        User ID: 123
        
        Analyze Maria's training week:
        1. Fetch workouts with getWeekWorkouts(123)
        2. For each workout, call appropriate analysis tool
        3. Get athlete profile with getAthleteProfile(123)
        4. Generate comprehensive review
        """;
    
    // IA VAI CHAMAR:
    // - getWeekWorkouts(123) → retorna 5 workouts
    // - analyzeLongRun(workoutId=456) → retorna drift 12.6%, positive split
    // - analyzeLongRun(workoutId=457) → outro long run
    // - getAthleteProfile(123) → retorna goals, level
    
    String review = chatClient.prompt()
        .user(prompt)
        .call()
        .content();
    
    // Review gerado pela IA mencionará:
    // "Saturday's long run showed severe pacing error (drift 12.6%)..."
    // "The tool analysis indicates you started 14s/km too fast..."
    
    saveReview(123, review);
}

// Custo: $0.05 (1 chamada IA, vários tools)
```

---

## 9. Vantagens da Abordagem

### 9.1 Para Desenvolvimento
- ✅ **Type safety:** Erros em compile-time, não runtime
- ✅ **Testabilidade:** JUnit puro, sem mocking complexo
- ✅ **Debugging:** Breakpoints funcionam normalmente
- ✅ **IDE support:** Autocomplete, refactoring
- ✅ **DI nativo:** Spring gerencia lifecycle

### 9.2 Para Observabilidade
- ✅ **Métricas:** Micrometer integrado
- ✅ **Tracing:** Spring Boot Actuator
- ✅ **Logs:** SLF4J padrão
- ✅ **Monitoring:** Prometheus/Grafana ready

### 9.3 Para IA Integration
- ✅ **Function calling:** Suporte nativo Spring AI
- ✅ **Tool discovery:** Automático via @Bean
- ✅ **Descrições:** @Description para documentar tools
- ✅ **Composição:** Skills podem chamar outras skills

### 9.4 Para Manutenção
- ✅ **Refactoring:** IDEs ajudam
- ✅ **Evolução:** Adicionar novos métodos facilmente
- ✅ **Versionamento:** Git padrão
- ✅ **Rollback:** Deploy padrão

---

## 10. Custos Finais

### Por Usuário/Mês (4 semanas)

```
Planos semanais (IA):        4 × $0.10 = $0.40
Análises skills:            20 × $0.00 = $0.00  ← Determinísticas!
Revisões semanais (IA):      4 × $0.05 = $0.20
─────────────────────────────────────────────
TOTAL:                                  $0.60/mês

Margem com plano $9.99:                $9.39 (94%)
```

**Para 1.000 usuários ativos: $600/mês**  
**Para 10.000 usuários ativos: $6.000/mês**

---

## 11. Próximos Passos

1. ✅ **Aprovar arquitetura Spring AI Skills**
2. 🚀 **Iniciar Sprint 1** - Setup Spring AI
3. 📝 **Documentar conventions** - Padrões de código
4. 🧪 **Setup CI/CD** - Testes automatizados
5. 👥 **Onboarding** - Treinar equipe em Spring AI

---

## 12. Referências

- Spring AI Documentation: https://docs.spring.io/spring-ai/reference/
- Spring AI Agent Skills Blog: https://spring.io/blog/2026/01/13/spring-ai-generic-agent-skills
- Anthropic Function Calling: https://docs.anthropic.com/claude/docs/tool-use
- Daniels' Running Formula (base fisiológica)

---

**Documento aprovado por:**

_________________________  
Leandro - Senior Software Engineer  
Data: ___/___/2026

**Próxima revisão:** Após Sprint 1 (setup completo)
