## Context

O Menthoros já possui dados suficientes ou em evolução para consolidar a semana do atleta: carga, treinos realizados, aderência, métricas de fadiga e análises de treino. Falta uma capability dedicada que organize isso em revisão semanal útil para a assessoria.

## Goals / Non-Goals

**Goals:**
- gerar revisão semanal estruturada do atleta
- consolidar execução e risco em uma única leitura
- usar a revisão como insumo para a próxima prescrição

**Non-Goals:**
- substituir toda interpretação humana do treinador
- produzir relatório longo sem ação prática

## Decisions

### D1: Revisão semanal estruturada

**Decisão:** A revisão semanal deve resumir carga, aderência, fadiga, evolução e foco recomendado.

**Rationale:** Esses são os elementos mínimos para fechar a semana de forma útil.

---

### D2: Revisão como insumo da próxima prescrição

**Decisão:** A geração do próximo plano deve poder consumir a revisão semanal mais recente.

**Rationale:** O valor da revisão aumenta quando ela muda a próxima decisão.

---

### D3: Revisão com janela semanal fechada

**Decisão:** A revisão deve ser calculada sobre uma janela semanal explícita (`semanaInicio`/`semanaFim`) para evitar ambiguidade temporal.

**Rationale:** Isso facilita persistência, reprocessamento e comparação histórica.

## Technical Notes

### Contrato mínimo sugerido

```text
WeeklyAthleteReview
- atletaId
- semanaInicio
- semanaFim
- adherenceStatus
- trainingLoadSummary
- fatigueSummary
- progressionSummary
- nextWeekFocus
- risks[]
- generatedAt
```

### Fontes mínimas da revisão

- treinos planejados da semana
- treinos realizados da semana
- debriefs pós-treino quando existirem
- métricas de carga/fadiga
- contexto de provas e fase da periodização

## Risks / Trade-offs

**[Risco] Resumo superficial demais** → Mitigação: usar sinais estruturados vindos das skills.

## Migration Plan

1. Definir snapshot semanal
2. Consolidar sinais e resumos
3. Persistir ou disponibilizar revisão
4. Integrar ao fluxo de geração de plano

## Open Questions

- A revisão será recalculada automaticamente ao final da semana ou sob demanda?
- O produto precisa persistir histórico de revisões fechadas para comparação entre semanas?
