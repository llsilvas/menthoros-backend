## Context

O Menthoros já possui `TreinoRealizado`, `TreinoPlanejado`, `EtapaRealizada` e cálculo de métricas fisiológicas. Também há uma direção clara de uso de domain skills para transformar dados do treino em interpretação estruturada.

O que falta é uma capability explícita que traduza a sessão realizada em uma leitura técnica acionável.

## Goals / Non-Goals

**Goals:**
- interpretar treino realizado de forma estruturada
- comparar planejado versus realizado
- produzir recomendação operacional para a sequência do ciclo
- reaproveitar o debrief na revisão semanal e na próxima prescrição

**Non-Goals:**
- substituir revisão humana do treinador
- cobrir todas as modalidades de treino nesta primeira fase com a mesma profundidade

## Decisions

### D1: Debrief estruturado, não apenas texto livre

**Decisão:** O resultado do debrief deve conter campos estruturados como score de execução, resumo, riscos e recomendação de sequência.

**Rationale:** Isso permite reaproveitamento por outras capabilities e evita prender o produto a uma saída puramente narrativa.

---

### D2: Priorizar análise por etapa

**Decisão:** Quando houver `EtapaRealizada`, a análise deve usar os blocos do treino como fonte principal.

**Rationale:** Sessões como intervalados, progressivos e longões variáveis são mal representadas por média geral.

---

### D3: Debrief deve influenciar a sequência

**Decisão:** O resultado precisa indicar impacto operacional sobre o próximo estímulo ou sobre a semana.

**Rationale:** O valor do debrief está em mudar ação, não apenas descrever o passado.

---

### D4: Persistência orientada a reuso

**Decisão:** O debrief deve ser persistido associado ao `TreinoRealizado`, com pelo menos:

- `executionScore`
- `executionStatus`
- `summary`
- `mainRisk`
- `nextStepRecommendation`
- `payloadJson` opcional para detalhes

**Rationale:** O resultado precisa ser consumível por revisão semanal, fila do treinador e próxima prescrição sem recalcular tudo sempre.

---

### D5: Classificação padronizada de execução

**Decisão:** O debrief deve classificar a execução em categorias operacionais estáveis, por exemplo:

- `ABAIXO_DO_ESPERADO`
- `DENTRO_DO_ESPERADO`
- `ACIMA_DO_ESPERADO`
- `INCONCLUSIVO`

**Rationale:** Isso facilita filtros, analytics e regras downstream.

## Technical Notes

### Contrato mínimo sugerido

```text
PostWorkoutDebrief
- treinoRealizadoId
- executionScore (1..10)
- executionStatus
- summary
- mainRisk nullable
- nextStepRecommendation
- comparedAgainstPlan boolean
- basedOnEtapas boolean
- generatedAt
```

### Fontes mínimas da análise

Prioridade de dados:

1. `EtapaRealizada`
2. `TreinoPlanejado` vinculado
3. métricas agregadas do `TreinoRealizado`
4. `PlanoMetaDados` do atleta no contexto

### Regras mínimas de fallback

- sem `TreinoPlanejado`: gerar debrief apenas com execução observada, sem comparação de aderência ao planejado
- sem `EtapaRealizada`: usar análise degradada por métricas agregadas
- sem dados suficientes para conclusão: marcar `executionStatus = INCONCLUSIVO`

## Risks / Trade-offs

**[Risco] Interpretação fraca sem etapas** → Em treinos sem `EtapaRealizada`, a análise será mais limitada. Mitigação: fallback degradado claro.

**[Trade-off] Mais modelagem para fechar o ciclo** → Exige novos DTOs e persistência, mas gera um dos maiores diferenciais do produto.

## Migration Plan

1. Definir modelo estruturado de debrief
2. Implementar comparação planejado versus realizado
3. Priorizar análise por etapa
4. Persistir resultado
5. Integrar ao fluxo de revisão e prescrição

## Open Questions

- O debrief será recalculável sob demanda ou considerado snapshot imutável da leitura daquele momento?
- A persistência ficará em colunas próprias de `TreinoRealizado` ou em tabela dedicada de análise?
