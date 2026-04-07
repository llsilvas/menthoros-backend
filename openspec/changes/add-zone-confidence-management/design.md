## Context

O Menthoros já lida com limiares, fallbacks e zonas de treino, e inclusive já prevê cenários em que os dados fisiológicos estão incompletos ou desatualizados. Falta transformar isso em uma capability explícita de confiança das zonas.

## Goals / Non-Goals

**Goals:**
- sinalizar confiança das zonas usadas na prescrição
- detectar necessidade de reavaliação
- evitar prescrição precisa baseada em dado fraco

**Non-Goals:**
- implementar todos os protocolos de teste fisiológico nesta mudança

## Decisions

### D1: Status explícito de confiança

**Decisão:** As zonas do atleta devem ter status explícito:

- `confiável`
- `estimada`
- `desatualizada`

**Rationale:** O treinador precisa saber se está decidindo com dado bom ou com fallback.

---

### D2: Recomendação operacional de reteste

**Decisão:** Quando a confiança estiver comprometida, o sistema deve recomendar reavaliação antes de aumentar precisão da prescrição.

**Rationale:** Isso protege a qualidade do plano e melhora a honestidade do produto.

---

### D3: Confiança baseada em recência + consistência

**Decisão:** A classificação de confiança deve considerar pelo menos:

- recência do último teste/atualização
- uso de fallback versus dado medido
- consistência entre limiares configurados e histórico recente observado

**Rationale:** Um único critério temporal é insuficiente para estimar confiança real.

## Technical Notes

### Contrato mínimo sugerido

```text
ZoneConfidenceStatus
- atletaId
- confidenceStatus
- basedOnFallback
- lastAssessmentDate
- primaryReason
- retestRecommended
```

### Critérios mínimos sugeridos

- `confiável`: sem fallback crítico, dados recentes e sem inconsistência relevante com histórico observado
- `estimada`: uso de fallback ou ausência de teste válido, mas sem forte sinal de incoerência
- `desatualizada`: recência vencida e/ou inconsistência relevante com histórico recente

## Risks / Trade-offs

**[Risco] Critérios mal calibrados** → Pode marcar zonas como ruins cedo demais ou tarde demais. Mitigação: calibração progressiva com histórico real.

## Migration Plan

1. Definir status de confiança
2. Detectar inconsistência e vencimento
3. Expor status na prescrição
4. Sugerir reavaliação quando necessário

## Open Questions

- Qual será a janela inicial de recência para marcar dado como desatualizado: 90 dias, 120 dias ou configurável por nível?
- Quais sinais mínimos do histórico recente serão usados como incoerência prática nesta primeira versão?
