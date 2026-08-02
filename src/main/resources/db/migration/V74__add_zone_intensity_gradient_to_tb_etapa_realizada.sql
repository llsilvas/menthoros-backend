-- =====================================================================
-- V74: Zona, intensidade e inclinação por etapa realizada
--
-- Três colunas que o treinador já enxerga no drilldown de voltas do
-- intervals.icu (colunas "Zona", "Intensidade" e "Inclinação média" do
-- export CSV) e que o Menthoros descartava por não ter onde guardar.
-- O dado vem no mesmo payload da activity que a ingestão de etapas
-- (intervals-icu-activity-laps) já passa a buscar — custo zero de rede.
--
-- Nomes em inglês por ADR-0007 (código novo nasce em inglês), embora as
-- colunas legadas desta tabela estejam em PT — a base é bilíngue por
-- decisão, e renomear as antigas está fora do escopo desta change.
--
-- Unidades (verificadas contra payload real, atleta i641775,
-- activity i171415754, 2026-08-02):
--   zone              zona de FC do intervalo, inteiro (1..N conforme o perfil)
--   intensity_pct     % do limiar — a fonte entrega inteiro (75, 82, 93);
--                     NUMERIC(5,2) para não truncar caso passe a vir fracionado
--   avg_gradient_pct  inclinação em PERCENTUAL; a fonte entrega FRAÇÃO
--                     (0.0011977 = 0,1%), o mapper multiplica por 100
--
-- Colunas nullable: dependem de zonas configuradas e de barômetro/GPS,
-- e nenhuma fonte além do intervals.icu as preenche hoje.
--
-- Rollback:
--   ALTER TABLE tb_etapa_realizada
--       DROP COLUMN IF EXISTS zone,
--       DROP COLUMN IF EXISTS intensity_pct,
--       DROP COLUMN IF EXISTS avg_gradient_pct;
-- Feature aditiva — nenhum dado existente é alterado ou removido.
-- =====================================================================

ALTER TABLE tb_etapa_realizada
    ADD COLUMN IF NOT EXISTS zone             INTEGER,
    ADD COLUMN IF NOT EXISTS intensity_pct    NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS avg_gradient_pct NUMERIC(4,1);

DO $$
BEGIN
    RAISE NOTICE '✅ V74 - zone, intensity_pct e avg_gradient_pct adicionados a tb_etapa_realizada';
END$$;
