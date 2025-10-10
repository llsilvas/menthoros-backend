-- V6: Adiciona campos fisiológicos e de perfil completo do atleta
-- Autor: Claude Code
-- Data: 2025-10-09
-- Propósito:
--   1. Adicionar campos de identificação e perfil pessoal
--   2. Adicionar campos fisiológicos para cálculo de TSS (FC, Pace, VO2max)
--   3. Adicionar campos de capacidade e limites de treinamento
--   4. Adicionar campos de histórico de lesões

-- ========================================
-- PARTE 1: Campos de Identificação
-- ========================================

-- Remove coluna 'idade' (será calculada a partir de data_nascimento)
ALTER TABLE tb_atleta
    DROP COLUMN IF EXISTS idade;

-- Adiciona campos de identificação
ALTER TABLE tb_atleta
    ADD COLUMN IF NOT EXISTS sobrenome VARCHAR(100) NULL DEFAULT 'A definir',
    ADD COLUMN IF NOT EXISTS data_nascimento DATE NULL DEFAULT CURRENT_DATE,
    ADD COLUMN IF NOT EXISTS email VARCHAR(255) NULL DEFAULT 'temp@example.com',
    ADD COLUMN IF NOT EXISTS sexo VARCHAR(1) NULL DEFAULT 'M';

-- Adiciona constraint unique no email
ALTER TABLE tb_atleta
    ADD CONSTRAINT uk_atleta_email UNIQUE (email);

-- Adiciona constraint check para sexo
ALTER TABLE tb_atleta
    ADD CONSTRAINT ck_atleta_sexo CHECK (sexo IN ('M', 'F', 'O'));

COMMENT ON COLUMN tb_atleta.sobrenome IS 'Sobrenome do atleta';
COMMENT ON COLUMN tb_atleta.data_nascimento IS 'Data de nascimento do atleta. Idade será calculada dinamicamente.';
COMMENT ON COLUMN tb_atleta.email IS 'Email único do atleta para login e comunicação';
COMMENT ON COLUMN tb_atleta.sexo IS 'Sexo do atleta: M (Masculino), F (Feminino), O (Outro)';


-- ========================================
-- PARTE 2: Dados Fisiológicos (FC)
-- ========================================

ALTER TABLE tb_atleta
    ADD COLUMN IF NOT EXISTS fc_maxima INTEGER,
    ADD COLUMN IF NOT EXISTS fc_repouso INTEGER,
    ADD COLUMN IF NOT EXISTS fc_limiar INTEGER,
    ADD COLUMN IF NOT EXISTS data_ultimo_teste_fc DATE;

-- Constraints para FC
ALTER TABLE tb_atleta
    ADD CONSTRAINT ck_atleta_fc_maxima CHECK (fc_maxima IS NULL OR (fc_maxima >= 120 AND fc_maxima <= 220)),
    ADD CONSTRAINT ck_atleta_fc_repouso CHECK (fc_repouso IS NULL OR (fc_repouso >= 30 AND fc_repouso <= 100)),
    ADD CONSTRAINT ck_atleta_fc_limiar CHECK (fc_limiar IS NULL OR (fc_limiar >= 100 AND fc_limiar <= 200));

COMMENT ON COLUMN tb_atleta.fc_maxima IS
'Frequência Cardíaca Máxima (bpm).
Pode ser calculada (220 - idade) ou testada.
NULL = calcular automaticamente';

COMMENT ON COLUMN tb_atleta.fc_repouso IS
'Frequência Cardíaca em Repouso (bpm).
Medida pela manhã antes de levantar.
Indica nível de condicionamento cardiovascular';

COMMENT ON COLUMN tb_atleta.fc_limiar IS
'Frequência Cardíaca no Limiar Anaeróbico (bpm).
Aproximadamente 85-90% da FC máxima.
Usado como referência para zonas de FC e cálculo de TSS';

COMMENT ON COLUMN tb_atleta.data_ultimo_teste_fc IS
'Data do último teste de FC.
Recomenda-se refazer a cada 3 meses';


-- ========================================
-- PARTE 3: Dados Fisiológicos (Pace/Velocidade)
-- ========================================

ALTER TABLE tb_atleta
    ADD COLUMN IF NOT EXISTS pace_limiar DECIMAL(5,2),
    ADD COLUMN IF NOT EXISTS velocidade_limiar DECIMAL(5,2),
    ADD COLUMN IF NOT EXISTS data_ultimo_teste_pace DATE;

-- Constraints para Pace/Velocidade
ALTER TABLE tb_atleta
    ADD CONSTRAINT ck_atleta_pace_limiar CHECK (pace_limiar IS NULL OR (pace_limiar >= 2.5 AND pace_limiar <= 12.0)),
    ADD CONSTRAINT ck_atleta_velocidade_limiar CHECK (velocidade_limiar IS NULL OR (velocidade_limiar >= 5.0 AND velocidade_limiar <= 25.0));

COMMENT ON COLUMN tb_atleta.pace_limiar IS
'Pace no Limiar Anaeróbico em min/km.
Exemplo: 4.5 = 4min30s/km.
Usado para calcular zonas de treino e TSS baseado em ritmo';

COMMENT ON COLUMN tb_atleta.velocidade_limiar IS
'Velocidade no Limiar Anaeróbico em km/h.
Exemplo: 13.3 km/h.
Inverso do pace_limiar (velocidade = 60 / pace)';

COMMENT ON COLUMN tb_atleta.data_ultimo_teste_pace IS
'Data do último teste de pace/velocidade.
Recomenda-se refazer a cada 3 meses ou após mudanças significativas no condicionamento';


-- ========================================
-- PARTE 4: VO2max
-- ========================================

ALTER TABLE tb_atleta
    ADD COLUMN IF NOT EXISTS vo2max_estimado DECIMAL(5,2);

-- Constraint para VO2max
ALTER TABLE tb_atleta
    ADD CONSTRAINT ck_atleta_vo2max CHECK (vo2max_estimado IS NULL OR (vo2max_estimado >= 20 AND vo2max_estimado <= 90));

COMMENT ON COLUMN tb_atleta.vo2max_estimado IS
'VO2max estimado em ml/kg/min.
Estimado a partir de testes de corrida (Cooper, etc).
Valores típicos:
- Sedentário: 25-35
- Iniciante: 35-45
- Intermediário: 45-55
- Avançado: 55-65
- Elite: 65-85+';


-- ========================================
-- PARTE 5: Capacidades de Treinamento
-- ========================================

ALTER TABLE tb_atleta
    ADD COLUMN IF NOT EXISTS distancia_maxima_longo INTEGER,
    ADD COLUMN IF NOT EXISTS volume_semanal_max INTEGER;

-- Constraints para capacidades
ALTER TABLE tb_atleta
    ADD CONSTRAINT ck_atleta_distancia_longo CHECK (distancia_maxima_longo IS NULL OR (distancia_maxima_longo >= 5 AND distancia_maxima_longo <= 100)),
    ADD CONSTRAINT ck_atleta_volume_semanal CHECK (volume_semanal_max IS NULL OR (volume_semanal_max >= 10 AND volume_semanal_max <= 300));

COMMENT ON COLUMN tb_atleta.distancia_maxima_longo IS
'Distância máxima confortável do long run em km.
Usado pela IA para planejar o treino longo sem exageros.
Exemplo: 20km para maratonistas iniciantes, 35km para experientes';

COMMENT ON COLUMN tb_atleta.volume_semanal_max IS
'Volume semanal máximo confortável em km/semana.
Usado para evitar overtraining e calcular progressão segura.
Respeita regra dos 10% de aumento';


-- ========================================
-- PARTE 6: Histórico de Lesões
-- ========================================

ALTER TABLE tb_atleta
    ADD COLUMN IF NOT EXISTS data_ultima_lesao DATE,
    ADD COLUMN IF NOT EXISTS historico_lesoes TEXT;

COMMENT ON COLUMN tb_atleta.data_ultima_lesao IS
'Data da última lesão relatada.
Usado para calcular período de recuperação e ajustar cautela nos planos';

COMMENT ON COLUMN tb_atleta.historico_lesoes IS
'Histórico completo de lesões anteriores em formato texto livre.
Usado pela IA para contexto e prevenção de recorrências.
Exemplo: "2023-05: Fascite plantar (3 semanas afastado)"';


-- ========================================
-- PARTE 7: Índices para Performance
-- ========================================

-- Índice para facilitar queries por nível de experiência (já existe na V1)
-- CREATE INDEX IF NOT EXISTS idx_atleta_nivel_experiencia ON tb_atleta(nivel_experiencia);

-- Índice para facilitar queries de testes desatualizados
-- Nota: Removemos a condição WHERE com CURRENT_DATE pois não é IMMUTABLE
-- A filtragem por data será feita na query SQL em vez do índice
CREATE INDEX IF NOT EXISTS idx_atleta_testes_desatualizados
ON tb_atleta (data_ultimo_teste_fc, data_ultimo_teste_pace);

COMMENT ON INDEX idx_atleta_testes_desatualizados IS
'Índice para facilitar queries de atletas com testes fisiológicos desatualizados';

-- Índice para email lookup (importante para autenticação)
CREATE INDEX IF NOT EXISTS idx_atleta_email ON tb_atleta(email);


-- ========================================
-- PARTE 8: Limpeza de Defaults Temporários
-- ========================================

-- Remove defaults temporários após migração inicial
-- ATENÇÃO: Executar apenas DEPOIS de popular os dados reais
-- Descomentar quando os dados estiverem migrados:

-- ALTER TABLE tb_atleta ALTER COLUMN sobrenome DROP DEFAULT;
-- ALTER TABLE tb_atleta ALTER COLUMN data_nascimento DROP DEFAULT;
-- ALTER TABLE tb_atleta ALTER COLUMN email DROP DEFAULT;
-- ALTER TABLE tb_atleta ALTER COLUMN sexo DROP DEFAULT;