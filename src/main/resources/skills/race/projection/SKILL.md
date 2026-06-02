---
name: race-projection-narrative
description: Generates progression narrative, key assumptions and coach note for race time projections
version: 1.0.0
language: en-US
tags: [race, projection, narrative, coach]
---

# Race Projection Narrative Generator

## Purpose

Generate three structured text outputs for a race time projection:
1. **progression_narrative** — concise description of the athlete's training trajectory and what the projection reflects (max 500 chars)
2. **key_assumptions** — explicit list of the assumptions behind the projection (max 5 items, each max 120 chars)
3. **coach_note** — actionable coaching recommendation for the weeks leading to race day (max 400 chars)

The language must match the athlete's locale. Default to **pt-BR**.

## Input Schema

```json
{
  "athlete": {
    "nome": "string",
    "nivel_experiencia": "INICIANTE | INTERMEDIARIO | AVANCADO | ELITE",
    "fc_maxima": "integer | null",
    "fc_limiar": "integer | null"
  },
  "projections": {
    "<distance_m>": {
      "projected_time_seconds": "integer",
      "projected_pace_sec_per_km": "integer",
      "time_range_optimistic_sec": "integer",
      "time_range_conservative_sec": "integer",
      "confidence": "LOW | MEDIUM | HIGH",
      "pr_potential": "boolean"
    }
  },
  "regression": {
    "slope": "double | null",
    "r_squared": "double | null",
    "sessions_used": "integer",
    "fallback_used": "boolean",
    "confidence_layer1": "LOW | MEDIUM | HIGH"
  },
  "ctl_forecast": {
    "current_ctl": "double",
    "projected_ctl_race_day": "double",
    "ctl_trend": "BUILDING | STABLE | DECLINING",
    "weeks_to_peak": "integer | null"
  },
  "periodization": {
    "adjustment_rationale_key": "TAPER_OPTIMAL | BUILD_PEAK | ESPECIFICO_FRESH | BASE_CONSERVATIVE | FATIGUED | OVERTAPERED",
    "adjustment_factor": "double"
  },
  "weeks_to_race": "integer"
}
```

## Output Schema

Respond ONLY with valid JSON. No markdown, no explanation outside the JSON block.

```json
{
  "progression_narrative": "string (max 500 chars, pt-BR)",
  "key_assumptions": ["string (max 120 chars each)", "..."],
  "coach_note": "string (max 400 chars, pt-BR)"
}
```

## Rules

- **progression_narrative**: Describe the athlete's trend objectively. Reference the regression direction (improving/stable/declining) and CTL trajectory. Do NOT include specific numbers from projections — those are shown separately in the UI.
- **key_assumptions**: List only limitations or conditions that affect projection reliability. When `confidence = LOW`, the first assumption MUST acknowledge data limitations explicitly (e.g., insufficient training sessions or missing HR data). Max 5 items.
- **coach_note**: Give one clear, actionable recommendation for the weeks until race day based on periodization phase and TSB. Avoid generic advice.
- **Uncertainty language**: When `confidence = LOW`, use hedging language in the narrative (e.g., "com base nos dados disponíveis", "estimativa preliminar"). When `confidence = HIGH`, be assertive.
- **No PII leakage**: Do not include athlete ID, tenant ID, or any system-internal identifier in the output.

## Examples

### HIGH confidence — TAPER_OPTIMAL

Input context: 12 weeks of consistent training, R²=0.85, TAPER phase, TSB=+2

```json
{
  "progression_narrative": "João mantém progressão consistente há 10 semanas, com pace normalizado melhorando de forma linear. A carga está bem distribuída e o taper está produzindo os efeitos esperados de recuperação. A projeção reflete a forma atual com alta confiança.",
  "key_assumptions": [
    "Treinos realizados em condições normais de clima e terreno",
    "Sem lesões ou interrupções nas próximas semanas",
    "Prova em percurso plano ou levemente ondulado"
  ],
  "coach_note": "O atleta está em janela ideal de taper. Manter apenas treinos leves (Z1-Z2) nos próximos 7 dias e garantir sono e hidratação adequados. Nenhum treino de qualidade adicional necessário."
}
```

### LOW confidence — dados insuficientes

Input context: 3 sessions used, fallback_used=true, missing HR data

```json
{
  "progression_narrative": "Com base nos dados disponíveis — histórico de treinos limitado e sem monitoramento de frequência cardíaca — esta é uma estimativa preliminar. A precisão da projeção aumentará com mais sessões registradas.",
  "key_assumptions": [
    "Dados insuficientes: menos de 6 sessões qualificadas para regressão confiável",
    "Sem dados de FC: pace bruto usado como proxy — ignora eficiência cardiovascular",
    "Estimativa pode ter margem de erro superior a 5%"
  ],
  "coach_note": "Priorize registrar treinos de Tempo Run e Longão com monitor cardíaco nas próximas semanas. Com mais dados, a próxima projeção será significativamente mais precisa."
}
```

### MEDIUM confidence — meta STRETCH com goal_gap_analysis

Input context: R²=0.62, BUILD phase, TSB=-8, coach_goal_override provided (gap=6.3%, STRETCH)

```json
{
  "progression_narrative": "Maria apresenta melhora consistente nos últimos 2 meses, mas ainda em fase de construção de base. A forma atual projeta um tempo próximo ao objetivo, porém com margem de incerteza relevante. A evolução nas próximas semanas será determinante.",
  "key_assumptions": [
    "Regressão baseada em dados de qualidade média (R²=0.62) — mais sessões melhorariam a precisão",
    "Fase BUILD: atleta ainda acumulando carga, fator de ajuste neutro aplicado",
    "TSB levemente negativo indica fadiga moderada — recuperação influencia o resultado",
    "Meta de 1h45 exige 6.3% de melhora sobre a projeção atual (classificação: STRETCH)"
  ],
  "coach_note": "Projeção atual: 1h52. Meta: 1h45 (gap 6.3% — STRETCH). A meta é alcançável se o taper for bem executado e as últimas semanas de qualidade renderem. Não ajuste a meta agora — reavalie na projeção das 4 semanas finais."
}
```
