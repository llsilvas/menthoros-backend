## Context

O Menthoros caminha para um modelo com mais domain skills e recomendações orientadas por sinais fisiológicos e históricos. Quanto mais o produto passa a sugerir ajustes e bloqueios, mais precisa explicar de onde essas decisões vieram.

## Goals / Non-Goals

**Goals:**
- explicar recomendações relevantes
- mostrar dados e regras principais usados
- apoiar a confiança do treinador

**Non-Goals:**
- expor toda a lógica interna em nível bruto
- transformar cada recomendação em relatório longo

## Decisions

### D1: Explicabilidade estruturada

**Decisão:** A explicabilidade deve expor, no mínimo:

- motivo principal
- dados/evidências centrais
- regra ou skill acionada
- ação sugerida ou restrição aplicada

**Rationale:** Esse é o nível de transparência que gera valor operacional sem poluir a experiência.

---

### D2: Explicabilidade separada da recomendação textual

**Decisão:** A explicabilidade deve ser modelada como estrutura própria e não apenas concatenada na recomendação em texto livre.

**Rationale:** Isso permite reuso em fila de atenção, revisão semanal, UI e auditoria.

## Technical Notes

### Contrato mínimo sugerido

```text
RecommendationExplanation
- explanationType
- primaryReason
- evidence[]
- sourceRules[]
- suggestedAction
- confidence
```

### Fontes válidas de explicação

- domain skills
- regras determinísticas
- limites de prescrição
- métricas resumidas do atleta

## Risks / Trade-offs

**[Trade-off] Mais esforço de modelagem** → Exige contrato específico, mas reduz resistência ao uso do sistema.

## Migration Plan

1. Definir estrutura de explicabilidade
2. Integrar a recomendações e bloqueios
3. Expor explicabilidade ao treinador

## Open Questions

- O mesmo payload de explicabilidade servirá para treinador e atleta ou haverá níveis diferentes de detalhe?
