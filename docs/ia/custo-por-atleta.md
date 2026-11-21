# Custo de LLM por atleta — modelo de estimativa

Criado em 2026-11-15 (change `migrate-openai-retired-models`, CA4). Serve para reajustar: cada número
abaixo é **medido** ou **premissa**, e a coluna "fonte" diz qual. Trocar uma premissa por dado real
é editar uma linha e refazer a conta; a consulta SQL do final entrega o dado real quando o
`develop` tiver volume.

Preços: sempre de `src/main/resources/llm-pricing.yml` (não duplicar aqui). Câmbio premissa:
**US$ 1 = R$ 5,50** (ajustar). Mês = 52/12 = **4,33 semanas**.

## O que gera custo (por atleta)

A temporada (provas) não chama LLM. Os geradores de custo são:

| Rota | Modelo hoje | Chamador | Frequência (premissa) |
|---|---|---|---|
| `plano` | gpt-4o | `IaServiceImpl` | 1 plano/semana (retry multiplica) |
| `simple` | gpt-4o-mini | `AthleteMessageGenerator`, `WeeklyFocusModelClient` | 4 chamadas/semana |
| `expert` | gpt-4o | **nenhum** (`TaskComplexity.EXPERT` sem uso em `src/main`) | 0 |
| `complex` | claude-sonnet-4-6 | `WorkoutAnalysisListener` | 4 análises/semana |
| `standard` | claude-haiku-4-5 | `WorkoutAnalysisTranslator`, classificador | 4 traduções/semana |

## Custo por plano (MEDIDO)

Bake-off real de 2026-11-15, 5 casos por candidato, 1 chamada por caso, **sem o juiz**
(`docs/ia/bakeoff-openai-sucessor-2026-11-15.md`). Custo ignora cache hit (superestima um pouco) e
conta tokens de reasoning como saída.

| Candidato | tokens in | tokens out | USD/plano | USD/mês (4,33 planos) | R$/mês |
|---|---|---|---|---|---|
| gpt-4o (baseline) | 11 499 | 1 134 | 0,0401 | 0,174 | 0,96 |
| gpt-6-luna:none | 11 493 | 1 160 | 0,0017 | 0,0075 | 0,04 |
| gpt-6-luna:low | 11 493 | 1 947 | 0,0021 | 0,0092 | 0,05 |
| gpt-6-luna:medium | 11 493 | 3 507 | 0,0029 | 0,0126 | 0,07 |

Só o `plano` do gpt-4o já consome ~87% do teto de R$ 1,10. Com Luna sobra mais de 90%.

## Demais rotas (PREMISSA — sem medição)

| Rota | Tokens in/out por chamada | USD/chamada | Chamadas/mês | USD/mês |
|---|---|---|---|---|
| `simple` gpt-4o-mini | 2 000 / 300 | 0,00048 | 17,3 | 0,0083 |
| `simple` luna (reasoning ~500 extra) | 2 000 / 800 | 0,0006 | 17,3 | 0,0104 |
| `complex` sonnet-4-6 | 4 000 / 1 000 | 0,027 | 17,3 | 0,468 |
| `standard` haiku-4-5 | 1 000 / 500 | 0,0035 | 17,3 | 0,061 |

## Total por atleta/mês

| Cenário | Rotas OpenAI (teto CA4) | R$ (OpenAI) | Anthropic | R$ (Anthropic) |
|---|---|---|---|---|
| Hoje (gpt-4o + gpt-4o-mini) | 0,182 | **1,00** | 0,529 | 2,91 |
| Luna `medium` em plano e simple | 0,023 | **0,13** | 0,529 | 2,91 |

- **CA4 cobre só rotas OpenAI** (texto da change): Luna cabe com folga; gpt-4o hoje está a R$ 0,10 do teto.
- **Anthropic é o maior custo real** (~R$ 2,91/atleta/mês, sobretudo `WorkoutAnalysisListener`) e fica fora do CA4. Vale decidir se o teto deve ser total.
- Retries de plano (`attempt > 1`) e planos regenerados pelo coach multiplicam o custo do `plano`; a estimativa assume 1 chamada por semana.

## Como atualizar

1. Preço mudou: editar `llm-pricing.yml` e refazer os USD acima (tokens não mudam).
2. Modelo/esforço mudou: rodar o bake-off real (`OpenAiSuccessorBakeoffRealTest`, ver Javadoc) e copiar tokens in/out.
3. Premissas de frequência/tokens: substituir pelos números reais da consulta abaixo.

## Consulta com dado real (rodar no `develop`/produção)

```sql
-- custo por atleta e por mês, por rota (tb_llm_call, V94)
SELECT date_trunc('month', created_at) AS mes,
       atleta_id,
       route,
       count(*)                          AS chamadas,
       round(avg(input_tokens))          AS tokens_in_medio,
       round(avg(output_tokens))         AS tokens_out_medio,
       round(sum(cost_usd), 4)           AS custo_usd
FROM tb_llm_call
WHERE atleta_id IS NOT NULL
GROUP BY 1, 2, 3
ORDER BY 1 DESC, custo_usd DESC;
```

Limite: `atleta_id` só é preenchido na rota `plano` (comentário da V94). Para as outras rotas, somar
por `tenant_id` e dividir pelo número de atletas ativos do mês. Se atribuir por atleta for
necessário nas demais rotas, é change própria.
