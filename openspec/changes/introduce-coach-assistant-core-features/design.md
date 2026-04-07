## Context

O Menthoros já possui recursos importantes para prescrição e análise, como:

- contexto rico montado em `PlanoTreinoPromptBuilder`
- cálculos fisiológicos e alertas em `MetricasAlertaService`
- gates determinísticos em `IntervaladoElegibilidadeService`
- granularidade por `EtapaRealizada`
- evolução arquitetural proposta para `domain skills`

O próximo salto de produto não depende apenas de mais métricas, mas de transformar essas métricas em fluxo operacional útil para o treinador.

## Goals / Non-Goals

**Goals:**
- fechar o ciclo planejado → realizado → revisado → próximo ajuste
- transformar dados em priorização operacional para o treinador
- tornar recomendações e ajustes explicáveis
- aumentar valor percebido para assessorias

**Non-Goals:**
- competir como app social de corrida
- substituir o treinador por automação completa
- ampliar escopo para outros esportes nesta fase

## Decisions

### D1: Priorizar fluxo do treinador sobre features genéricas do atleta

**Decisão:** As features essenciais desta mudança devem ser desenhadas primeiro para o treinador e para a assessoria.

**Rationale:** É o nicho onde o Menthoros tem maior chance de diferenciação e onde a inteligência do produto gera mais valor operacional.

---

### D2: Análise pós-treino deve ser estruturada e acionável

**Decisão:** A capability `post-workout-debrief` deve produzir resultado estruturado com interpretação, score de execução, riscos e recomendação de sequência.

**Rationale:** Só armazenar treino executado não gera vantagem competitiva. O valor está em interpretar o treino e conectar isso ao próximo passo.

---

### D3: Criar fila operacional de atenção

**Decisão:** A capability `coach-attention-queue` deve consolidar sinais de risco, aderência, fadiga e ausência de estímulos para priorizar atletas diariamente.

**Rationale:** O treinador precisa de uma lista curta do que exige ação, e não de mais um dashboard cheio de dados.

---

### D4: Revisão semanal deve consolidar múltiplos sinais

**Decisão:** A capability `weekly-athlete-review` deve gerar um resumo estruturado da semana do atleta, incluindo aderência, carga, fadiga, evolução e recomendação de foco.

**Rationale:** Essa é uma das atividades mais repetitivas e valiosas para o treinador, e o produto pode reduzir muito o esforço operacional aqui.

---

### D5: Explicabilidade é requisito de produto, não detalhe técnico

**Decisão:** Toda recomendação relevante deve poder ser explicada com:

- dados principais usados
- skill/regra acionada
- motivo do ajuste
- restrição aplicada

**Rationale:** Sem explainability, o sistema perde confiança e tende a ser percebido como caixa preta.

---

### D6: Zonas devem ter status de confiança

**Decisão:** O produto deve explicitar se a prescrição está baseada em zonas confiáveis, estimadas ou desatualizadas.

**Rationale:** A qualidade da prescrição depende diretamente da qualidade dos dados fisiológicos. Tornar isso visível evita falsa precisão.

## Risks / Trade-offs

**[Risco] Escopo amplo demais** → Essas features tocam análise, revisão, painel operacional e explicabilidade. Mitigação: entregar em fases e apoiar-se na camada de domain skills.

**[Risco] Excesso de ruído na fila de atenção** → Muitos alertas podem reduzir confiança. Mitigação: priorização por severidade, impacto e urgência.

**[Trade-off] Mais modelagem para gerar valor operacional** → Há custo extra de consolidar sinais e produzir resumos estruturados, mas esse é exatamente o diferencial de produto buscado.

## Migration Plan

1. Implementar capability de `post-workout-debrief`
2. Consolidar sinais para `coach-attention-queue`
3. Implementar `weekly-athlete-review`
4. Adicionar camada de `recommendation-explainability`
5. Introduzir `zone-confidence-management`
6. Integrar essas capabilities ao fluxo de prescrição e revisão

## Open Questions

- A fila de atenção será calculada on-demand, pré-processada ou híbrida?
- A revisão semanal será persistida como snapshot ou gerada sob demanda?
- O nível de explainability exposto ao atleta deve ser o mesmo do treinador?
