-- =====================================================================
-- V74: Zona, intensidade e inclinação por etapa realizada
--
-- Três colunas que o treinador já enxerga no drilldown de voltas do
-- intervals.icu (colunas "Zona", "Intensidade" e "Inclinação média" do
-- export CSV) e que o Menthoros descartava por não ter onde guardar.
-- O dado vem no mesmo payload da activity que a ingestão de etapas
-- (intervals-icu-activity-laps) já passa a buscar — custo zero de rede.
--
-- Nomes em PORTUGUÊS, seguindo as colunas vizinhas desta tabela
-- (distancia_km, fc_media, cadencia_media, elevacao_ganho_metros).
-- Desvio deliberado do ADR-0007 ("código novo nasce em inglês"),
-- decidido em 2026-08-02: numa tabela inteiramente PT, três colunas
-- isoladas em inglês custam mais em legibilidade do que ganham em
-- convergência. Ver design.md D10 da change.
--
-- Unidades (verificadas contra payload real, atleta i641775,
-- activity i171415754, 2026-08-02):
--   zona                  zona de FC do intervalo, inteiro (1..N conforme o perfil)
--   intensidade_pct       % do limiar — a fonte entrega inteiro (75, 82, 93);
--                         NUMERIC(5,2) para não truncar caso passe a vir fracionado
--   inclinacao_media_pct  inclinação em PERCENTUAL; a fonte entrega FRAÇÃO
--                         (0.0011977 = 0,1%), o mapper multiplica por 100
--
-- Colunas nullable: dependem de zonas configuradas e de barômetro/GPS,
-- e nenhuma fonte além do intervals.icu as preenche hoje.
--
-- Rollback:
--   ALTER TABLE tb_etapa_realizada
--       DROP COLUMN IF EXISTS zona,
--       DROP COLUMN IF EXISTS intensidade_pct,
--       DROP COLUMN IF EXISTS inclinacao_media_pct;
-- Feature aditiva — nenhum dado existente é alterado ou removido.
-- =====================================================================

ALTER TABLE tb_etapa_realizada
    ADD COLUMN IF NOT EXISTS zona                 INTEGER,
    ADD COLUMN IF NOT EXISTS intensidade_pct      NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS inclinacao_media_pct NUMERIC(4,1);

DO $$
BEGIN
    RAISE NOTICE '✅ V74 - zona, intensidade_pct e inclinacao_media_pct adicionados a tb_etapa_realizada';
END$$;
