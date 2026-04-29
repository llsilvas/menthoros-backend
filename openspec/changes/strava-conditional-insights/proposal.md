## Title

Análise LLM Condicional e Alertas de Desvio de Carga para Strava

## Summary

Implementar uma camada de análise inteligente que governa quando invocar o LLM (reduzindo custos em 60-80%) e gera alertas proativos para o treinador quando atividades Strava revelam desvios significativos no treino realizado vs. planejado.

## Problem Statement

Depois que a sincronização Strava está em produção, dois problemas emergem:

1. **Custo de LLM descontrolado** — Toda atividade sincronizada dispara uma chamada LLM, consumindo tokens mesmo em dias rotineiros sem insights relevantes. O LLM é caro (US$ 5–15 por M tokens) e lento (2–5 seg/chamada) — invocar indiscriminadamente não escala.

2. **Treinador fica passivo** — O sync importa dados, mas o treinador ainda precisa revisar manualmente cada atividade para perceber que algo saiu do planejado. A integração é uma fonte de dados, não uma ferramenta de decisão.

## Why This Matters

- **Scaling multi-tenant** — Com 100+ assessorias e 5000+ atletas sincronizando, cada atividade desnecessária ao LLM soma milhares de dólares/mês.
- **Diferencial competitivo** — Um coach que recebe alerta automático quando algo sai do plano toma decisão 10x mais rápido.
- **Aderência de dados** — Alertas estruturados realimentam o modelo do atleta com contexto real de desvios.

## Scope

Implementar:

1. **Regras leves de filtro** — Detectar desvios em TSS (%), FC (zone %), cadência, velocidade sem invocar LLM
2. **Gerador de alertas estruturados** — Quando regras detectam desvio, gerar alerta categorizado (DESVIO_TSS, DESVIO_ZONA_FC, DESVIO_CADENCIA) com sugestões de ação
3. **Análise condicional do LLM** — Chamar LLM apenas quando um ou mais alertas foram gerados, para análise narrativa e recomendação
4. **Armazenamento de contexto pré-processado** — Cache de contexto do atleta antes de invocar LLM

## Non-Goals

- Dashboard ou UI para exibir alertas (é responsabilidade de um change futuro ou outro sistema)
- Notificações em tempo real ao treinador (integração com email/push é futura)
- Fine-tuning de modelo ou evolução de IA (covered by separate changes)

## Acceptance Criteria

- [ ] Atividades com desvio TSS ≤ 10% não disparam análise LLM
- [ ] Atividades com FC dentro da zona esperada não geram alerta (mesmo com desvio TSS)
- [ ] Alerta estruturado é persistido com categoria, valor, threshold e contexto da atividade
- [ ] Análise LLM é invocada apenas se um ou mais alertas foram gerados na atividade
- [ ] Consumo de tokens do LLM reduz em mínimo 50% em dias rotineiros vs. linha de base

---

## References

- **Ideia #11 (Brainstorming):** Alerta Proativo de Desvio de Carga — geração automática de alerta ao treinador quando atividade Strava revela desvio significativo
- **Ideia #36 (Brainstorming):** Análise LLM Assíncrona e Condicional — redução de 60-80% no consumo de tokens ao invocar LLM apenas quando há sinal real
- **Brainstorming Review:** Recomendação de incorporar #11 e #36 em change dedicado pós-MVP de Strava

---

## Open Questions

1. **Thresholds de desvio:** Qual % de desvio em TSS, FC (em minutos fora da zona), cadência (%) considera significativo?
2. **Alertas compostos:** Se desvio TSS é baixo mas FC está alta, ainda gera alerta? Há priorização entre tipos de alerta?
3. **Armazenamento de alertas:** Alertas são persistidos em nova tabela `tb_alerta_atividade` ou como campo JSON em `TreinoRealizado`?
