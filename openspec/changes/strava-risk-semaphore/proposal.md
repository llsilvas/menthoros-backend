## Title

Semáforo de Risco de Atletas — Dashboard de Triagem para Treinadores

## Summary

Implementar um score de risco que agrupa múltiplas dimensões de ameaça (sobrecarga, desvios de carga, inatividade, desvio de padrão) em um semáforo simples (🟢 verde / 🟡 amarelo / 🔴 vermelho) exibido no dashboard. O treinador consegue triagem rápida de múltiplos atletas e sabe em 5 segundos quem precisa atenção.

## Problem Statement

Um treinador que gerencia 20–50 atletas não consegue revisar cada um diariamente:

- Ler 50 perfis leva 30+ minutos
- Risco não é óbvio — exige análise cruzada de múltiplas métricas (TSB, alertas de desvio, aderência)
- Sem triagem rápida, o treinador revisa aleatoriamente e perde sinais de risco iminente

O resultado: tomadas de decisão reativas ("vejo que o atleta está sobrecarregado quando já está em overtraining") em vez de proativas.

## Why This Matters

- **Escalabilidade operacional** — O treinador não é um analista full-time; é um estrategista. Precisando de 5 segundos para ler "verde/amarelo/vermelho" vs. 5 minutos para cavar dados.
- **Diferencial de produto** — Nenhum app de coaching faz triagem automática baseada em inteligência. É competitividade pura.
- **Retenção de treinador** — Se o dashboard economiza 2 horas/dia, a plataforma vira indispensável.

## Scope

Implementar:

1. **Modelo de risco** — Cruzar TSB (forma), alertas de desvio (Strava), aderência, padrão histórico para calcular **score de risco de 0–100**
2. **Mapeamento score → semáforo** — Verde (< 25), Amarelo (25–60), Vermelho (> 60)
3. **Persistência de risco** — Tabela `tb_risco_atleta` com snapshot diário do score + dimensões que contribuíram
4. **Endpoint para dashboard** — `GET /api/strava/risk-semaphore` retorna lista de atletas com score, status, e motivos principais
5. **Recomendações ligadas ao risco** — Se vermelho, sugerir que treinador reduza carga próxima atividade ou aumentar recuperação

## Non-Goals

- UI/Dashboard (renderização é responsabilidade do frontend ou sistema externo)
- Notificações ao treinador
- Histórico de risco (apenas snapshot diário; histórico é tarefa futura)
- Customização per-assessoria de fórmula de risco (global para MVP)

## Acceptance Criteria

- [ ] Score de risco é calculado após cada sincronização Strava de um atleta
- [ ] Score correlaciona com sobrecarga real (validação manual com 3+ treinadores)
- [ ] Endpoint retorna lista de atletas ordenada por score descendente
- [ ] Cada atleta no endpoint inclui: `id`, `nome`, `risco_score`, `status_semaforo` (`RED`|`YELLOW`|`GREEN`), `motivos` (array: qual dimensão mais contribuiu)
- [ ] Atleta com TSB negativo (overtraining) sempre retorna `RED`
- [ ] Atleta com múltiplos alertas na semana retorna mínimo `YELLOW`

---

## References

- **Ideia #16 (Brainstorming):** Semáforo de Atletas por Risco — dashboard mostra todos os atletas por status com cálculo LLM cruzando dados Strava da semana com plano
- **Ideia #11 (Brainstorming):** Alertas gerados pelo change anterior (`strava-conditional-insights`) alimentam este modelo de risco

---

## Open Questions

1. **Dimensões de risco:** Quais são as 4-5 dimensões principais que contribuem para risco? (TSB, alertas, aderência, padrão histórico?)
2. **Fórmula de agregação:** Risco final é média ponderada das dimensões? Máximo (worst-case)? Regra de negócio com threshold booleano?
3. **Tempo de cálculo:** Score é recalculado em tempo real (lento) ou snapshot diário/por-hora (rápido)?
