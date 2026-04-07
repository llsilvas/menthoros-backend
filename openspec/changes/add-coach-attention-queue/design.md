## Context

O Menthoros já calcula ou tende a calcular sinais importantes como fadiga, sobrecarga, ausência de estímulos, execução ruim e baixa aderência. Hoje, porém, esses sinais não estão consolidados em uma visão operacional única para o treinador.

## Goals / Non-Goals

**Goals:**
- priorizar atletas que exigem revisão imediata
- consolidar motivo principal e ação sugerida
- ordenar itens por impacto operacional

**Non-Goals:**
- substituir um dashboard analítico completo
- notificar todos os microeventos do atleta

## Decisions

### D1: Fila de atenção como lista curta e acionável

**Decisão:** Cada item da fila deve conter atleta, motivo principal, severidade e ação sugerida.

**Rationale:** O treinador precisa agir rápido, não interpretar tabelas extensas.

---

### D2: Priorização por severidade, urgência e impacto

**Decisão:** A ordenação da fila deve combinar gravidade do sinal, urgência temporal e impacto provável na prescrição.

**Rationale:** Nem todo alerta precisa da mesma atenção.

---

### D3: Um item por atleta por motivo principal

**Decisão:** A fila deve evitar duplicação excessiva e consolidar, por padrão, um item principal por atleta/motivo agregado.

**Rationale:** Sem consolidação, a fila perde utilidade operacional e vira lista de alertas brutos.

## Technical Notes

### Contrato mínimo sugerido

```text
CoachAttentionItem
- atletaId
- athleteName
- priorityScore
- severity
- primaryReason
- suggestedAction
- generatedAt
- evidenceJson
```

### Fontes elegíveis de sinal

- fadiga e prontidão
- baixa aderência
- ausência de estímulo-chave
- debrief ruim/excessivo
- zonas desatualizadas
- risco de progressão indevida na semana vigente

### Ordenação sugerida

Ordenar por:

1. `severity`
2. `priorityScore`
3. data do sinal mais recente

### Deduplicação sugerida

- não gerar dois itens simultâneos para o mesmo atleta com o mesmo `primaryReason`
- se houver múltiplos sinais do mesmo tipo, consolidar evidências no mesmo item

## Risks / Trade-offs

**[Risco] Ruído excessivo** → Muitos itens podem reduzir confiança. Mitigação: score de priorização e limite por motivo principal.

## Migration Plan

1. Definir modelo de item de atenção
2. Consolidar sinais elegíveis
3. Implementar score/priorização
4. Expor consulta para o treinador

## Open Questions

- A fila será materializada periodicamente ou calculada on-demand?
- Haverá limite máximo de itens por treinador/tenant na resposta principal?
