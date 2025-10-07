-- V5: Adiciona constantes de tempo adaptativas e campos de elevação
-- Autor: Claude Code
-- Data: 2025-10-07
-- Propósito:
--   1. Adicionar constantes de tempo CTL/ATL personalizáveis por atleta (Item 2)
--   2. Adicionar campos de elevação para TSS ajustado por terreno (Item 1)

-- ========================================
-- ITEM 2: Constantes de tempo adaptativas
-- ========================================

-- Adiciona constantes de tempo CTL/ATL personalizáveis na tabela tb_atleta
-- Valores padrão serão calculados dinamicamente baseados em nivel_experiencia:
-- - INICIANTE: CTL=30, ATL=5 (adapta rápido, recupera rápido)
-- - INTERMEDIARIO: CTL=35, ATL=6
-- - AVANCADO: CTL=42, ATL=7 (padrão clássico)
-- - ELITE: CTL=50, ATL=8 (adapta lento, maior resiliência)

ALTER TABLE tb_atleta
    ADD COLUMN IF NOT EXISTS ctl_time_constant INTEGER,
    ADD COLUMN IF NOT EXISTS atl_time_constant INTEGER;

COMMENT ON COLUMN tb_atleta.ctl_time_constant IS
'Constante de tempo para CTL (Chronic Training Load) em dias.
Define velocidade de adaptação ao treinamento crônico.
NULL = usar valor padrão baseado em nivel_experiencia';

COMMENT ON COLUMN tb_atleta.atl_time_constant IS
'Constante de tempo para ATL (Acute Training Load) em dias.
Define velocidade de recuperação da fadiga aguda.
NULL = usar valor padrão baseado em nivel_experiencia';


-- ========================================
-- ITEM 1: Campos de elevação para TSS
-- ========================================

-- Adiciona campos de elevação na tabela tb_treino_planejado
-- elevacao_ganho_metros: Subida acumulada (D+)
-- elevacao_perda_metros: Descida acumulada (D-)

ALTER TABLE tb_treino_planejado
    ADD COLUMN IF NOT EXISTS elevacao_ganho_metros INTEGER,
    ADD COLUMN IF NOT EXISTS elevacao_perda_metros INTEGER;

COMMENT ON COLUMN tb_treino_planejado.elevacao_ganho_metros IS
'Elevação acumulada (D+) em metros.
Usado para ajustar TSS baseado em dificuldade do terreno.
Ex: 600m em 20km (30m/km) = fator 1.20x';

COMMENT ON COLUMN tb_treino_planejado.elevacao_perda_metros IS
'Elevação perdida (D-) em metros.
Importante para calcular fadiga muscular em descidas longas
(contração excêntrica causa maior DOMS)';


-- Adiciona campos de elevação na tabela tb_treino_realizado
ALTER TABLE tb_treino_realizado
    ADD COLUMN IF NOT EXISTS elevacao_ganho_metros INTEGER,
    ADD COLUMN IF NOT EXISTS elevacao_perda_metros INTEGER;

COMMENT ON COLUMN tb_treino_realizado.elevacao_ganho_metros IS
'Elevação acumulada (D+) em metros.
Usado para ajustar TSS baseado em dificuldade do terreno.
Ex: 600m em 20km (30m/km) = fator 1.20x';

COMMENT ON COLUMN tb_treino_realizado.elevacao_perda_metros IS
'Elevação perdida (D-) em metros.
Importante para calcular fadiga muscular em descidas longas
(contração excêntrica causa maior DOMS)';


-- ========================================
-- ÍNDICES (Opcional, para performance)
-- ========================================

-- Índice para facilitar queries por constantes personalizadas
CREATE INDEX IF NOT EXISTS idx_atleta_custom_constants
ON tb_atleta (ctl_time_constant, atl_time_constant)
WHERE ctl_time_constant IS NOT NULL OR atl_time_constant IS NOT NULL;

COMMENT ON INDEX idx_atleta_custom_constants IS
'Índice para atletas com constantes de tempo personalizadas (não default)';