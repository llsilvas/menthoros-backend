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
-- CHECKs seguem o padrão das colunas vizinhas desta tabela (ck_*_fc_media,
-- ck_*_potencia, ck_*_cadencia, da V7): o dado vem de terceiro e alimenta
-- cálculo de zona e carga — um valor fora de faixa entrando em silêncio
-- contamina a análise rio abaixo.
--
-- Colunas nullable: dependem de zonas configuradas e de barômetro/GPS,
-- e nenhuma fonte além do intervals.icu as preenche hoje.
--
-- Rollback:
--   ALTER TABLE tb_etapa_realizada
--       DROP CONSTRAINT IF EXISTS ck_etapa_realizada_zona,
--       DROP CONSTRAINT IF EXISTS ck_etapa_realizada_intensidade_pct,
--       DROP CONSTRAINT IF EXISTS ck_etapa_realizada_inclinacao_media_pct,
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
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_etapa_realizada_zona') THEN
        ALTER TABLE tb_etapa_realizada
            ADD CONSTRAINT ck_etapa_realizada_zona
            CHECK (zona IS NULL OR zona BETWEEN 1 AND 10);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_etapa_realizada_intensidade_pct') THEN
        ALTER TABLE tb_etapa_realizada
            ADD CONSTRAINT ck_etapa_realizada_intensidade_pct
            CHECK (intensidade_pct IS NULL OR intensidade_pct BETWEEN 0 AND 200);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_etapa_realizada_inclinacao_media_pct') THEN
        ALTER TABLE tb_etapa_realizada
            ADD CONSTRAINT ck_etapa_realizada_inclinacao_media_pct
            CHECK (inclinacao_media_pct IS NULL OR inclinacao_media_pct BETWEEN -100 AND 100);
    END IF;
END$$;

DO $$
BEGIN
    RAISE NOTICE '✅ V74 - zona, intensidade_pct e inclinacao_media_pct adicionados a tb_etapa_realizada';
END$$;
