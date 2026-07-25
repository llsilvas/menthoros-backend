# Revisão Semanal persistida e congelada — fidelidade do sinal proposto ao moat de aprendizado

Ao desenhar a `weekly-athlete-review` (revisão semanal do atleta), constatamos que quase tudo dela é função determinística do que o `PlanoSemanal` + os treinos da janela já persistem: aderência (contagem realizados/planejados), fadiga e delta (`tsb_inicio`/`tsb_fim`), e o `recommendationType` (derivado de aderência + TSB). Consideramos, por isso, computar a revisão **on-the-fly** no endpoint de leitura, sem tabela nova.

Decidimos **persistir** uma projeção fina — `tb_revisao_semanal`, 1:1 com `PlanoSemanal` — congelando no momento da geração o **sinal estruturado do que foi proposto ao coach**: `recommendationType`, `adherenceSummary.status` e `dadosSuficientes`. O `weekOverWeekDelta` permanece **computado** (é só um diff contra o `PlanoSemanal` anterior).

A razão não é performance, é **fidelidade da história**. Os limiares da árvore de `recommendationType` (`TSB ≤ −25`, `TSB ≥ −10`, aderência `≥ 90%`) são defaults v1 explicitamente ajustáveis. Um read-model recomputaria a história com as regras vigentes na leitura, **reescrevendo retroativamente "o que foi recomendado ao coach" naquela semana**. O moat do produto é o loop de aprendizado "proposta da IA vs. edição do coach" (`focusOutcome`, na fatia de LLM): se o lado "proposta" é recalculado sob regras novas, o sinal é corrompido — passa-se a comparar a edição do coach contra uma recomendação que nunca foi feita. Congelar o sinal preserva o ground-truth para o moat e para análises LLM/RAG futuras.

O contraste com os resumos numéricos derivados (TSB, volumes) é deliberado: esses ficam **congelados no próprio `PlanoSemanal`** (`tsb_fim`, `volume_realizado_km` não mudam depois do `CONCLUIDO`), então recomputá-los é estável — por isso o `weekOverWeekDelta` não precisa ser persistido. Só o que depende de **regra mutável** (o `recommendationType` e os campos que o determinam) é congelado.

Consequências:
- A revisão é **1:1 com `PlanoSemanal`** — não inventa um conceito paralelo de "semana" (o glossário já avisa contra isso). É gerada **event-driven no encerramento da semana** (`EncerramentoSemanaService`, no fechamento manual do coach ou no fallback automático), quando os dados ficam finais — não há scheduler novo nem geração sob demanda.
- O conjunto congelado é **mínimo por design**: só o que constitui a proposta como-exibida. A narrativa `nextWeekFocus` (não-determinística, por LLM) e o `focusOutcome` (edição do coach) são persistidos na fatia de LLM (`add-weekly-review-llm-focus`), não aqui.
- Regeneração da revisão só por ação explícita (upsert idempotente por `plano_semanal_id`), preservando o congelamento.
