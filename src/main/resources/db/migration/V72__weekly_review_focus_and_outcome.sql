-- =====================================================================
-- V72: Foco da proxima semana + desfecho da revisao consumida
--      (add-weekly-review-llm-focus, Fatia 2)
--
-- 1) tb_revisao_semanal ganha a narrativa e a sua origem:
--    next_week_focus  — texto proposto ao coach como foco da semana seguinte
--    focus_source     — LLM ou TEMPLATE. Sem esse campo os dois regimes ficam
--                       indistinguiveis depois de persistidos, e o gate de custo
--                       A1 teria custo de um lado e nenhuma medida de beneficio
--                       do outro (mesma razao de origem_aprovacao).
--
-- 2) tb_plano_semanal ganha o vinculo com a revisao que consumiu e o desfecho:
--    consumed_review_id      — FK para a revisao usada como insumo na geracao
--    consumed_review_outcome — o que este plano fez com ela
--    O desfecho vive no PLANO, nao na revisao: uma revisao e 1:N com os planos
--    que a consomem (rejeitar e regerar faz dois planos consumirem a mesma
--    revisao); num campo unico da revisao o segundo desfecho sobrescreveria o
--    primeiro, apagando a rejeicao que interessa ao moat. De quebra, a revisao
--    deixa de sofrer escrita pos-congelamento (ADR-0006).
--
-- 3) Renomeia os 2 campos PT da tb_revisao_semanal (ADR-0007 — identificador
--    novo em ingles; campo legado normalizado quando a change ja mexe naquela
--    entidade). NAO toca no percentual_realizacao do dominio de adesao
--    (SemanaAdesaoDto/MetricasAdesaoService), que e outro conceito.
--    ATENCAO: quebra o contrato lido pelo card do coach — exige PR coordenado
--    no menthoros-front (renomeia dadosSuficientes/percentualRealizacao no tipo,
--    adapter e testes).
--
-- Rollback: renomear as colunas de volta e DROP das 4 colunas novas.
-- Aditiva + rename; sem perda de dado (rename preserva os valores existentes).
-- =====================================================================

ALTER TABLE tb_revisao_semanal
    RENAME COLUMN dados_suficientes TO sufficient_data;

ALTER TABLE tb_revisao_semanal
    RENAME COLUMN percentual_realizacao TO completion_rate;

ALTER TABLE tb_revisao_semanal
    ADD COLUMN IF NOT EXISTS next_week_focus TEXT,
    ADD COLUMN IF NOT EXISTS focus_source    VARCHAR(10)
                             CHECK (focus_source IN ('LLM', 'TEMPLATE'));

ALTER TABLE tb_plano_semanal
    ADD COLUMN IF NOT EXISTS consumed_review_id UUID
                             REFERENCES tb_revisao_semanal(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS consumed_review_outcome VARCHAR(20)
                             CHECK (consumed_review_outcome IN (
                                 'PENDING', 'NO_ADJUSTMENT', 'ADJUSTED',
                                 'PLAN_REJECTED', 'NOT_CONSUMED', 'NO_COACH_IN_LOOP'));

-- Agregacao do sinal de aprendizado: desfecho por revisao consumida.
CREATE INDEX IF NOT EXISTS idx_plano_semanal_consumed_review
    ON tb_plano_semanal(consumed_review_id);

DO $$
BEGIN
    RAISE NOTICE '✅ V72 - foco da proxima semana + desfecho da revisao consumida';
END$$;
