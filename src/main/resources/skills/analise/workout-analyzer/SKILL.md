---
name: workout-analyzer
description: Post-workout analysis comparing planned vs actual execution with technical interpretation
version: 1.0.0
language: en-US
tags: [analysis, post-workout, rpe, fatigue-detection]
---

# Workout Analyzer

## Purpose

Analyze completed workout compared to planned workout, providing structured feedback with:
- Technical interpretation of execution
- Root cause analysis of deviations
- Actionable recommendations
- Execution quality score (1-10)

This skill is designed to detect:
✅ Accumulated fatigue requiring recovery
✅ Environmental stress (heat, humidity, altitude)
✅ Pacing errors (starting too fast)
✅ CNS (Central Nervous System) fatigue
✅ Normal training adaptation

## Input Schema

```json
{
  "planned": {
    "type": "LONG_RUN | INTERVAL | TEMPO | RECOVERY | EASY",
    "distance_km": number,
    "target_pace": "MM:SS/km" (optional),
    "target_hr_zone": "Z1 | Z2 | Z3 | Z4 | Z5" (optional),
    "expected_rpe": 1-10
  },
  "actual": {
    "distance_km": number,
    "avg_pace": "MM:SS/km" (optional),
    "avg_hr": number (optional, bpm),
    "rpe": 1-10
  },
  "athlete_context": {
    "tsb": number,
    "ctl": number,
    "consecutive_load_days": number
  }
}
```

## Analysis Framework

### Step 1: RPE Delta Analysis

**Definition:** Difference between actual RPE and expected RPE.

```
RPE Delta = actual_rpe - expected_rpe
```

**Classification Table:**

| Delta Range | Classification | Interpretation | Action Required |
|-------------|----------------|----------------|-----------------|
| >= +3 | CONCERNING | Significantly harder than expected | Investigate immediately |
| +1 to +2 | MODERATE | Slightly harder, monitor trend | Check recovery status |
| -1 to +1 | NORMAL | As expected, good execution | Continue as planned |
| <= -2 | EASY | Easier than expected | Assess if undertraining or taper |

**Example:**
```
Planned RPE: 4 (easy long run)
Actual RPE: 7 (felt hard)
Delta: +3 → CONCERNING
```

### Step 2: Fatigue Context Integration

**Correlate RPE delta with Training Stress Balance (TSB):**

```python
# Decision Tree for Root Cause Analysis

IF rpe_delta >= +3 AND tsb < -20:
    PRIMARY_CAUSE = "ACCUMULATED_FATIGUE"
    SEVERITY = "HIGH"
    RECOMMENDATION = "Mandatory recovery 48-72 hours"
    TAGS = ["FATIGUE_DETECTED", "ACCUMULATED_FATIGUE", "RECOVERY_NEEDED"]

ELIF rpe_delta >= +3 AND tsb > -10:
    PRIMARY_CAUSE = "ENVIRONMENTAL_FACTORS or INADEQUATE_FUELING"
    SEVERITY = "MEDIUM"
    RECOMMENDATION = "Check nutrition/hydration strategy, weather conditions"
    TAGS = ["ENVIRONMENTAL_STRESS"]

ELIF rpe_delta >= +3 AND consecutive_load_days >= 5:
    PRIMARY_CAUSE = "CNS_FATIGUE"
    SEVERITY = "HIGH"
    RECOMMENDATION = "Active recovery or complete rest day"
    TAGS = ["CNS_FATIGUE", "RECOVERY_NEEDED"]

ELIF rpe_delta >= +3 AND actual.distance < planned.distance * 0.9:
    PRIMARY_CAUSE = "PACING_ERROR"
    SEVERITY = "MEDIUM"
    RECOMMENDATION = "Review pacing strategy, consider starting slower"
    TAGS = ["PACING_ERROR", "WORKOUT_INCOMPLETE"]

ELSE:
    PRIMARY_CAUSE = "NORMAL"
    SEVERITY = "LOW"
    RECOMMENDATION = "Continue current training load"
    TAGS = ["NORMAL"]
```

**TSB Reference:**

| TSB Range | Status | Training Recommendation |
|-----------|--------|------------------------|
| > +5 | Fresh | Can handle hard workouts |
| 0 to +5 | Good | Normal training |
| -10 to 0 | Fatigued | Monitor closely |
| -20 to -10 | Overreaching | Reduce load or rest |
| < -20 | High Risk | Mandatory recovery |

### Step 3: Heart Rate Drift Detection (if HR available)

**Scientific Threshold:**
- **Normal drift:** 3-5% over 90+ minutes (glycogen depletion + thermoregulation)
- **Concerning drift:** >10% (dehydration, overheating, inadequate fueling)

```python
# Calculate HR Drift
duration_minutes = actual.duration
first_half_avg_hr = actual.first_half_hr
second_half_avg_hr = actual.second_half_hr

hr_drift_percent = ((second_half_avg_hr - first_half_avg_hr) / first_half_avg_hr) * 100

IF duration_minutes > 90 AND hr_drift_percent > 10:
    TAGS.append("HEART_RATE_DRIFT_DETECTED")
    RECOMMENDATION += " Review hydration and fueling strategy for long runs."
```

**Example:**
```
90-minute long run:
- First 45min avg HR: 150 bpm
- Second 45min avg HR: 165 bpm
- Drift: 10% → Normal for duration
```

### Step 4: Execution Score Calculation

```python
def calculate_execution_score(rpe_delta, tsb, distance_completion_percent):
    """
    Calculate execution quality score (1-10).
    
    10 = Perfect execution
    1 = Severe problems, risk of injury
    """
    
    base_score = 10
    
    # Perfect execution
    if abs(rpe_delta) == 0:
        return 10
    
    # Excellent execution
    if abs(rpe_delta) == 1:
        return 9
    
    # Good execution (context-dependent)
    if abs(rpe_delta) == 2:
        if tsb > -10:
            return 8  # Normal fatigue
        else:
            return 7  # Acceptable given fatigue
    
    # Poor execution (investigate)
    if rpe_delta >= +3:
        score = max(1, 6 - rpe_delta)
        
        # Adjust for incomplete workout
        if distance_completion_percent < 0.9:
            score -= 1
        
        return max(1, score)
    
    # Easier than expected (could be good or concerning)
    if rpe_delta <= -2:
        if tsb > 0:
            return 7  # Good recovery, easy day
        else:
            return 6  # Possible undertraining
    
    return 5  # Default
```

## Output Schema

```json
{
  "summary": "string (max 100 chars, concise headline)",
  "technical_interpretation": "string (2-3 sentences, detailed analysis)",
  "primary_cause": "ACCUMULATED_FATIGUE | ENVIRONMENTAL_FACTORS | PACING_ERROR | CNS_FATIGUE | NORMAL | UNDERTRAINING",
  "recommendation": "string (actionable advice, specific)",
  "tags": ["array", "of", "tags"],
  "execution_score": 1-10,
  "rationale": "string (explain score calculation and key findings)"
}
```

## Available Tags

**Execution Quality:**
- `EXCELLENT_EXECUTION` - RPE delta 0
- `GOOD_EXECUTION` - RPE delta within ±1
- `POOR_EXECUTION` - RPE delta >= +3

**Fatigue Indicators:**
- `FATIGUE_DETECTED` - RPE delta +3 or more
- `ACCUMULATED_FATIGUE` - TSB < -20 + high RPE delta
- `CNS_FATIGUE` - Consecutive load days >= 5
- `RECOVERY_NEEDED` - Recommendation includes mandatory recovery

**Performance Issues:**
- `HEART_RATE_DRIFT` - HR drift > 10%
- `ENVIRONMENTAL_STRESS` - Heat, humidity, altitude impact
- `PACING_ERROR` - Started too fast or inconsistent pacing
- `WORKOUT_INCOMPLETE` - Distance < 90% of planned

**Positive Indicators:**
- `GOOD_RECOVERY` - Positive TSB + normal execution
- `NORMAL_ADAPTATION` - Expected fatigue for training phase

## Complete Examples

### Example 1: Perfect Execution

**Input:**
```json
{
  "planned": {
    "type": "LONG_RUN",
    "distance_km": 18,
    "target_pace": "5:30-5:45/km",
    "expected_rpe": 4
  },
  "actual": {
    "distance_km": 17.8,
    "avg_pace": "5:38/km",
    "rpe": 4
  },
  "athlete_context": {
    "tsb": 2,
    "ctl": 45,
    "consecutive_load_days": 2
  }
}
```

**Expected Output:**
```json
{
  "summary": "Excellent adherence to plan",
  "technical_interpretation": "Long run executed precisely as prescribed. Pace within target range (5:38/km vs 5:30-5:45/km target). RPE matched expectations (4/10), indicating good pacing and appropriate fitness for the workout. Positive TSB (+2) suggests good recovery status.",
  "primary_cause": "NORMAL",
  "recommendation": "Continue current training load. Consider slight progression next week.",
  "tags": ["EXCELLENT_EXECUTION", "GOOD_RECOVERY"],
  "execution_score": 10,
  "rationale": "RPE delta = 0, pace on target, positive TSB indicates well-recovered athlete executing prescribed stimulus correctly."
}
```

---

### Example 2: Accumulated Fatigue (Critical)

**Input:**
```json
{
  "planned": {
    "type": "INTERVAL",
    "distance_km": 12,
    "target_pace": "4:20/km",
    "expected_rpe": 6
  },
  "actual": {
    "distance_km": 10,
    "avg_pace": "4:45/km",
    "rpe": 9
  },
  "athlete_context": {
    "tsb": -28,
    "ctl": 52,
    "consecutive_load_days": 7
  }
}
```

**Expected Output:**
```json
{
  "summary": "Clear signs of accumulated fatigue - recovery needed",
  "technical_interpretation": "Interval workout significantly harder than expected. RPE delta of +3 (9 vs 6 expected) combined with TSB of -28 and 7 consecutive load days indicates accumulated fatigue. Workout terminated early (10km vs 12km planned) and pace slower than target (4:45/km vs 4:20/km). Classic signs of overreaching.",
  "primary_cause": "ACCUMULATED_FATIGUE",
  "recommendation": "Mandatory recovery: 2-3 days of active recovery (Z1-Z2 easy runs, 30-45 minutes) or complete rest. Do not attempt quality workouts until TSB > -15.",
  "tags": ["FATIGUE_DETECTED", "ACCUMULATED_FATIGUE", "CNS_FATIGUE", "RECOVERY_NEEDED", "WORKOUT_INCOMPLETE"],
  "execution_score": 3,
  "rationale": "RPE delta +3, negative TSB beyond -25, extended consecutive load period, and incomplete workout are strong indicators of overtraining risk. Immediate recovery required to prevent injury and allow adaptation."
}
```

---

### Example 3: Environmental Stress

**Input:**
```json
{
  "planned": {
    "type": "TEMPO",
    "distance_km": 10,
    "target_pace": "5:00/km",
    "expected_rpe": 5
  },
  "actual": {
    "distance_km": 10,
    "avg_pace": "5:15/km",
    "rpe": 8
  },
  "athlete_context": {
    "tsb": 5,
    "ctl": 48,
    "consecutive_load_days": 3
  }
}
```

**Expected Output:**
```json
{
  "summary": "Workout harder than expected despite good recovery",
  "technical_interpretation": "Tempo run felt significantly harder (RPE 8 vs 5 expected) and pace was slower (5:15/km vs 5:00/km target), but athlete has positive TSB (+5) and only 3 consecutive load days. This mismatch suggests external factors rather than accumulated fatigue.",
  "primary_cause": "ENVIRONMENTAL_FACTORS",
  "recommendation": "Check environmental conditions (temperature, humidity, wind). Ensure adequate hydration and fueling. If conditions were normal, monitor for early signs of illness. Rest day tomorrow may be beneficial.",
  "tags": ["FATIGUE_DETECTED", "ENVIRONMENTAL_STRESS"],
  "execution_score": 6,
  "rationale": "RPE delta +3 is concerning, but positive TSB and low consecutive load days rule out accumulated fatigue. Environmental stress or early illness most likely. Score penalized for missed pace target but not as severe as fatigue-related issues."
}
```

## Implementation Notes

### For LLM Processing

**Always follow this sequence:**

1. **Calculate RPE delta first** - This is the primary indicator
2. **Check TSB context** - Determines if fatigue is accumulated or acute
3. **Evaluate consecutive load days** - CNS fatigue indicator
4. **Consider workout completion** - Incomplete workouts are red flags
5. **Generate specific recommendation** - "Active recovery" is better than "rest"
6. **Explain your reasoning** - Rationale field should show the logic chain
7. **Use appropriate tags** - Helps with historical pattern analysis

### Key Principles

✅ **Be specific:** "2-3 days active recovery (Z1-Z2, 30-45 min)" > "Take it easy"
✅ **Consider context:** Same RPE delta has different meanings at different TSB levels
✅ **Prioritize safety:** When in doubt, recommend recovery
✅ **Explain clearly:** Athlete should understand WHY they felt that way
✅ **Tag accurately:** Tags enable trend detection across workouts

### Common Pitfalls to Avoid

❌ **Don't ignore TSB:** RPE delta +3 with TSB +5 is very different from TSB -25
❌ **Don't recommend hard workouts when TSB < -20:** This risks injury
❌ **Don't assume environmental factors without checking TSB first**
❌ **Don't give vague recommendations:** Be specific and actionable

## Scientific References

- **RPE (Rate of Perceived Exertion):** Borg CR10 Scale
- **TSB (Training Stress Balance):** CTL - ATL (Chronic - Acute Training Load)
- **Heart Rate Drift:** Cardiovascular drift during prolonged exercise
- **Functional Overreaching:** Temporary performance decrement with subsequent supercompensation (when managed properly)

---

**This skill should be invoked for ALL post-workout analysis where RPE data is available.**

For workouts without RPE, consider using pace and HR data only, but analysis will be less accurate.
