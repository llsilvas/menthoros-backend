## Context

Este change assume que `strava-activity-sync` está funcional — atividades são sincronizadas, mapeadas para `TreinoRealizado`, e `metodoCalculoTss` é preenchido automaticamente. Este change repousa sobre esses dados para gerar insights e alertas.

## Goals

- Reduzir invoke do LLM em 60-80% usando regras leves de filtro antes de análise narrativa
- Gerar alertas estruturados quando atividade realizada desvia do planejado em dimensões-chave (TSS, zona FC, cadência)
- Habilitar análise LLM condicional apenas quando há sinal relevante
- Armazenar contexto pré-computado para reutilização em análise LLM

## Non-Goals

- UI/Dashboard para exibir alertas
- Notificações ao treinador (email, push, WebSocket)
- Evolução de modelo LLM ou fine-tuning
- Scoring de confiança do mapeamento (covered by strava-activity-sync)

## Decisions

### D1: Alertas como entidade persistida (`tb_alerta_atividade`)

**Decisão:** Criar tabela `tb_alerta_atividade` com uma linha por alerta gerado, FK para `tb_treino_realizado`, categoria (enum), valor real, threshold esperado, contexto JSON.

**Rationale:** Alertas são sinais estruturados que o treinador e o LLM precisam entender. Persistir como tabela permite auditoria, rastreamento de padrões históricos ("este atleta sempre desvia em cadência") e reprocessamento futuro com regras atualizadas.

**Alternativa descartada:** Armazenar como campo JSON em `TreinoRealizado` — descartado porque múltiplos alertas por atividade exigem normalização para queries eficientes.

---

### D2: Regras leves de filtro em serviço Java vs. banco

**Decisão:** Implementar detecção de desvio em Java (`StravaInsightsService`) — comparando `TreinoRealizado` realizado com `TreinoPlanejado` correspondente e `MetricasThresholds` do atleta.

**Rationale:** Lógica de negócio não pertence ao SQL. Java permite unit tests mais simples e permite evolução das regras sem migrações de banco. A latência de comparação em Java é negligenciável (<10ms).

---

### D3: Categorias de alerta predefinidas (enum `TipoAlerta`)

**Decisão:** Enum `TipoAlerta` com valores: `DESVIO_TSS`, `DESVIO_ZONA_FC`, `DESVIO_CADENCIA`, `DESVIO_VELOCIDADE`, `DADOS_INCOMPLETOS`.

**Rationale:** Categorias predefinidas permitem que o treinador e o LLM entendam rapidamente o tipo de desvio. Enum garante que não há typos ou categorias inesperadas. Fácil de estender futuro.

---

### D4: Thresholds configuráveis por assessoria ou global

**Decisão:** Thresholds são **globais no `application.yml`** como propriedades estruturadas:
```yaml
app.strava-insights:
  thresholds:
    tss-deviation-percent: 15          # desvio % em relação ao planejado
    fc-out-of-zone-minutes: 5          # minutos fora da zona
    cadencia-deviation-percent: 10     # desvio % em relação ao histórico
    velocity-deviation-percent: 8
```

**Rationale:** MVP tem threshold global. Suportar por-assessoria exigiria estrutura de configuração mais complexa e persiste como tarefa futura. Global é suficiente para detectar desvios significativos.

---

### D5: Invocação condicional do LLM — regra `OR`

**Decisão:** Se **qualquer** alerta foi gerado, invocar o LLM. Se nenhum alerta, pular análise LLM inteiramente.

**Rationale:** Simplicidade e redução máxima de custos. Um alerta é sinal suficiente de que há contexto relevante para o LLM analisar. Evita micro-calls em dias rotineiros.

**Alternativa considerada:** Invocar LLM se múltiplos alertas (`AND`), mas é mais restritivo e perde oportunidades de insight quando um único desvio é significativo.

---

### D6: Cache de contexto do atleta em Caffeine

**Decisão:** Antes de invocar o LLM, montar contexto completo do atleta:
- Dados demográficos e fisiológicos
- Plano semanal corrente
- TSB/CTL/ATL atual
- Últimas 3 atividades sincronizadas
- Histórico de tipos de alerta para este atleta

Cachear esse contexto por 30 minutos em Caffeine. Se o LLM é invocado múltiplas vezes para o mesmo atleta no mesmo dia, reutiliza cache.

**Rationale:** O contexto é caro de computar (múltiplas queries) e o LLM precisa dele todo. Cacheá-lo reduz latência e I/O de banco. Caffeine já está no stack para cache de dados.

---

### D7: Processamento assíncrono via `@Async`

**Decisão:** Detecção de alerta é **síncrona** (imediata durante sync da atividade). Análise LLM é **assíncrona** — se alertas foram gerados, delegar para método `@Async` que invoca LLM e persiste resultado.

**Rationale:** Detecção de alerta é rápida (<50ms) e deve ser parte da transação de sync. Análise LLM é lenta (2–5seg) e não deve bloquear o callback de webhook. Async permite que webhook responda rapidamente.

---

### D8: Persistência de análise LLM

**Decisão:** Resultado da análise LLM (narrativa estruturada, recomendações, contexto) é persistido em nova tabela `tb_insights_atividade` com FK para `tb_alerta_atividade`.

**Rationale:** Permite rastreamento de recomendações históricas do LLM, auditoria e evolução do modelo. Não perder a inteligência gerada.

---

### D9: Multi-tenancy em alertas e insights

**Decisão:** Ambas as tabelas (`tb_alerta_atividade`, `tb_insights_atividade`) possuem coluna `tenant_id` não nula com índice (tenant_id, atleta_id, criado_em). Todas as queries filtram por TenantContext.

**Rationale:** Padrão já estabelecido no projeto. Sem isolamento, atletas de diferentes assessorias poderiam consultar alertas de outros.

---

## Risks / Trade-offs

**[Risco] Thresholds globais não se adaptam a diferentes perfis** → Um atleta de elite pode ter desvio TSS maior que um amador sem indicar problema. Mitigação: usar percentuais em relação ao plano, não valores absolutos. Futuro: suportar thresholds por nível de experiência do atleta.

**[Risco] LLM invocado em dia com múltiplos alertas consome muitos tokens** → Se um atleta sincroniza 10 atividades com desvio, 10 chamadas LLM. Mitigação: agrupar análise de múltiplas atividades em uma chamada única. Tarefa futura.

**[Trade-off] Cache de 30 minutos não reflete mudanças no plano em tempo real** → Se o treinador atualiza o plano, next sync do atleta ainda usa contexto cacheado antigo. Aceitável porque atualizações de plano são raras intra-dia.

---

## Migration Plan

1. Criar migrations `V29__Create_alerta_atividade_table.sql` e `V30__Create_insights_atividade_table.sql`
2. Criar entidades `AlertaAtividade.java`, `InsightsAtividade.java`
3. Criar repositories `AlertaAtividadeRepository`, `InsightsAtividadeRepository`
4. Implementar `StravaInsightsService` com detecção de alertas e geração de análise LLM
5. Integrar no callback de `strava-activity-sync` — após persistir `TreinoRealizado`, invocar detecção de alertas
6. Adicionar configuração de thresholds no `application.yml`

---

## Open Questions Resolved

1. **Thresholds:** 15% desvio TSS, 5 min fora da zona FC, 10% cadência, 8% velocidade (global, configurável)
2. **Alertas compostos:** Múltiplos alertas na mesma atividade — LLM vê todos e prioriza automaticamente
3. **Armazenamento:** Tabela `tb_alerta_atividade` + `tb_insights_atividade` (persistência normalizada)
