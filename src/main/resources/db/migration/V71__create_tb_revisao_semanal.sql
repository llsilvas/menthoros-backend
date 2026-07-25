-- =====================================================================
-- V71: Cria tb_revisao_semanal (add-weekly-review-consolidation, Fatia 1)
--
-- Revisao semanal do atleta, 1:1 com tb_plano_semanal (uk_revisao_semanal_plano).
-- Congela no encerramento da semana o sinal ESTRUTURADO do que foi proposto ao
-- coach — recommendation_type, adherence_status, dados_suficientes — por
-- fidelidade ao moat de aprendizado (regras ajustaveis recomputariam a historia;
-- ver ADR-0006). Janela, TSB e volumes ficam no proprio tb_plano_semanal (join);
-- week_over_week_delta e computado na leitura, nao persistido.
--
-- next_week_focus / focus_outcome (narrativa LLM + edicao do coach) sao da
-- Fatia 2 (add-weekly-review-llm-focus) — colunas aditivas futuras nesta tabela.
--
-- Sem coluna tenant_id propria (excecao consciente ao Table Design Standard):
-- filho 1:1 de tb_plano_semanal, o isolamento vem do join plano_semanal->assessoria
-- nas queries do repositorio (findByPlanoSemanalIdAndTenant/findRecentesByAtletaAndTenant);
-- duplicar tenant_id aqui so criaria risco de divergencia. Ver ADR-0006.
--
-- Rollback: DROP TABLE IF EXISTS tb_revisao_semanal;
-- Feature aditiva pura -- sem impacto em dado existente; reversao segura.
-- =====================================================================

CREATE TABLE IF NOT EXISTS tb_revisao_semanal (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    plano_semanal_id        UUID         NOT NULL REFERENCES tb_plano_semanal(id) ON DELETE CASCADE,
    recommendation_type     VARCHAR(20)  NOT NULL
                            CHECK (recommendation_type IN ('RECOVERY', 'MAINTAIN', 'PROGRESS')),
    adherence_status        VARCHAR(10)  NOT NULL
                            CHECK (adherence_status IN ('ALTA', 'MEDIA', 'BAIXA')),
    percentual_realizacao   NUMERIC(5,2),
    dados_suficientes       BOOLEAN      NOT NULL,
    gerada_em               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_revisao_semanal_plano UNIQUE (plano_semanal_id)
);

DO $$
BEGIN
    RAISE NOTICE '✅ V71 - tb_revisao_semanal criada com sucesso';
END$$;
