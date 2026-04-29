## Context

Este change repousa sobre:
- `strava-conditional-insights` para alertas estruturados de desvio
- `strava-activity-sync` para dados de atividades sincronizadas
- Sistema existente de `MetricasAlerta` (TSB, CTL, ATL) e `NivelAlerta` (que já tem semáforo interno)

O objetivo é expor um semáforo simples e acionável para o treinador gerenciar atletas em massa.

## Goals

- Calcular um score de risco agregado (0–100) por atleta baseado em múltiplas sinais
- Mapear score para semáforo tricolor (verde/amarelo/vermelho) para decisão rápida
- Persistir snapshots diários de risco para auditoria e análise histórica futura
- Habilitar recomendações automáticas de ação quando risco é elevado

## Non-Goals

- UI/Dashboard
- Notificações
- Fine-tuning de fórmula por perfil de atleta
- Histórico temporal (future)
- Personalização por assessoria

## Decisions

### D1: Dimensões de Risco Agregadas

**Decisão:** Score de risco é calculado a partir de **5 dimensões independentes**, cada uma contribuindo um peso:

1. **Dimensão TSB (Forma)** — Peso 30%
   - TSB > -10: risco baixo (0 pontos)
   - TSB -10 a -20: risco médio (50 pontos)
   - TSB < -20: risco alto (100 pontos)

2. **Dimensão Alertas de Desvio** — Peso 25%
   - Sem alertas na última semana: 0 pontos
   - 1-2 alertas: 40 pontos
   - 3+ alertas ou 1+ alerta crítico (DESVIO_TSS > 50%): 100 pontos

3. **Dimensão Aderência** — Peso 20%
   - > 80% de aderência: 0 pontos
   - 50-80%: 50 pontos
   - < 50%: 100 pontos

4. **Dimensão Padrão Histórico** — Peso 15%
   - Padrão consistente com últimas 4 semanas: 0 pontos
   - Desvio de padrão moderado (mudança > 15% na distribuição de tipos): 40 pontos
   - Desvio severo (mudança > 30%) ou queda abrupta de atividades: 100 pontos

5. **Dimensão Dados Incompletos** — Peso 10%
   - Todos os campos de FC, cadência, pace disponíveis: 0 pontos
   - 1-2 campos ausentes em 50%+ das atividades: 30 pontos
   - Maioria dos dados ausentes ou impossível calcular TSS: 100 pontos

**Fórmula Final:**
```
score_risco = 0.30 × tsb_score + 0.25 × alertas_score + 0.20 × aderencia_score + 0.15 × padrao_score + 0.10 × dados_score
```

**Rationale:** Divisão em dimensões permite que um atleta não seja marcado como "vermelho" apenas porque TSB está baixo — exige múltiplos sinais. TSB tem peso maior porque é o mais objetivo. Pesos podem ser ajustados por assessoria futuro.

---

### D2: Mapping Score → Semáforo

**Decisão:**
- 🟢 **Verde:** score < 25 (baixo risco, padrão esperado)
- 🟡 **Amarelo:** score 25–60 (atenção, monitorar próximo treino)
- 🔴 **Vermelho:** score > 60 (intervenção necessária, reduzir carga ou aumentar recuperação)

**Rationale:** Limites ajustados empiricamente para 3 estados perceptíveis. Verde é maioria (dia normal), amarelo é "não ignore", vermelho é "aja agora".

---

### D3: Snapshot vs. Cálculo em Tempo Real

**Decisão:** Snapshot diário. A cada sincronização de atividade, o score é **recalculado**, mas persistido como versão de "data do dia". Se múltiplas atividades são sincronizadas no mesmo dia, a última sobrescreve.

**Rationale:** Performance. Calcular em tempo real para 5000+ atletas é caro. Snapshot diário é suficiente para decisões — o treinador não revisa 50 atletas a cada atividade. Snapshot também habilita histórico futuro.

---

### D4: Recomendações Ligadas ao Score

**Decisão:** Se score_risco > 60, endpoint retorna recomendação estruturada:
- Se TSB < -20: "Aumentar recuperação — reduzir próxima atividade em 20%"
- Se alertas_score alta: "Revisar padrão de execução — últimas atividades desviaram do planejado"
- Se aderencia baixa: "Verificar entendimento do plano — atleta não está completando estímulos"

**Rationale:** Recomendação conecta score a ação concreta. Não é suficiente o treinador ver "vermelho" — precisa saber o quê fazer.

---

### D5: Persistência em `tb_risco_atleta` com snapshot diário

**Decisão:** Tabela `tb_risco_atleta` com uma linha por atleta por data:
- `id`, `atleta_id` (FK), `data` (date), `score_risco` (Integer 0-100), `status_semaforo` (enum RED|YELLOW|GREEN), `dimensoes_json` (detalhe de cada dimensão), `motivo_principal` (qual dimensão contribuiu mais), `recomendacao` (texto estruturado), `tenant_id`, `criado_em`

Se uma data já tem registro e nova sincronização acontece no mesmo dia, **update** em vez de insert.

**Rationale:** Auditoria e histórico simples. Uma linha por dia por atleta é escala viável mesmo com 10K+ atletas.

---

### D6: Filtros de Visibilidade

**Decisão:** Endpoint `GET /api/strava/risk-semaphore?status=RED,YELLOW&order=score_desc&limit=50` retorna:
- Todos os atletas se parâmetro `status` omitido
- Filtrados por status de semáforo se parâmetro fornecido
- Ordenados por score descendente (maior risco first)
- Paginado (padrão 50, máximo 100)

**Rationale:** Dashboard precisa de controle — treinador com 50 atletas quer ver "apenas os vermelhos" primeiro.

---

### D7: Atualização de Score — Timing

**Decisão:** Score é **recalculado e persistido** imediatamente após:
1. Nova atividade sincronizada do atleta
2. Manualmente, se treinador fizer update no plano
3. Snapshot noturno recalcula para todos os atletas (job assíncrono)

**Rationale:** Snapshot noturno garante que se algum dado foi atualizado fora do sync (e.g., feedback manual do treinador), o score reflete no dia seguinte. Sem isso, score fica stale se não há atividade por dias.

---

### D8: Multi-tenancy

**Decisão:** Tabela `tb_risco_atleta` tem `tenant_id`. Endpoint de semáforo retorna apenas atletas do tenant autenticado. Sem isolamento, um coach vê risco de atletas de outras assessorias.

**Rationale:** Padrão existente.

---

## Risks / Trade-offs

**[Risco] Fórmula de score não captures todas as dimensões de risco** → Há riscos que não são TSB, alertas ou aderência (lesão iminente, problemas pessoais do atleta). Mitigação: adicionar campo manual `risco_override` para treinador marcar risco fora de dimensões automáticas. Tarefa futura.

**[Risco] Score é simplista demais — não há "contexto"** → Um atleta em taper (TSB negativo proposital) é marcado vermelho. Mitigação: incluir `fase_periodizacao` na avaliação de TSB. Se em taper, TSB negativo é esperado.

**[Trade-off] Snapshot diário não reflete mudanças intra-dia** → Se múltiplas atividades chegam de madrugada, status final é visto no dia seguinte. Aceitável porque decisão de treino não é tomada em real-time.

---

## Migration Plan

1. Criar migration `V31__Create_risco_atleta_table.sql`
2. Criar entidade `RiscoAtleta.java`
3. Criar repository `RiscoAtletaRepository.java`
4. Implementar `StravaRiskSemaphoreService` com cálculo de score
5. Integrar job noturno de recalculation para todos os atletas
6. Criar controller com endpoint de semáforo
7. Adicionar testes

---

## Open Questions Resolved

1. **Dimensões:** TSB (30%), Alertas (25%), Aderência (20%), Padrão Histórico (15%), Dados (10%)
2. **Fórmula:** Média ponderada das 5 dimensões (0–100)
3. **Timing:** Atualizado após sync Strava + snapshot noturno
