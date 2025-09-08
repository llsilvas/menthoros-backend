-- Menthoros Database Schema
-- PostgreSQL Init Script for Docker Container
-- Compatible with pgvector/pgvector:pg17

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "vector";

-- Create database and user (if not exists)
-- Note: In Docker, these are typically handled by environment variables
-- but we include them here for completeness

-- Set timezone
SET timezone = 'America/Sao_Paulo';

-- Create the main tables
-- 1. tb_atleta - Athletes table
CREATE TABLE IF NOT EXISTS tb_atleta (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nome VARCHAR(255) NOT NULL,
    idade INTEGER NOT NULL CHECK (idade > 0),
    peso_kg DECIMAL(5,2),
    altura_cm DECIMAL(5,2),
    objetivo VARCHAR(500) NOT NULL,
    nivel_experiencia VARCHAR(50) NOT NULL CHECK (nivel_experiencia IN ('INICIANTE', 'INTERMEDIARIO', 'AVANCADO')),
    dia_preferido_longo VARCHAR(20) CHECK (dia_preferido_longo IN ('DOMINGO', 'SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA', 'SABADO')),
    tem_lesao BOOLEAN DEFAULT FALSE,
    descricao_lesao TEXT,
    ativo VARCHAR(20) NOT NULL DEFAULT 'ATIVO' CHECK (ativo IN ('ATIVO', 'INATIVO')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. tb_dias_disponiveis - Available days for athletes (collection table)
CREATE TABLE IF NOT EXISTS tb_dias_disponiveis (
    atleta_id UUID NOT NULL,
    dia VARCHAR(20) NOT NULL CHECK (dia IN ('DOMINGO', 'SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA', 'SABADO')),
    PRIMARY KEY (atleta_id, dia),
    FOREIGN KEY (atleta_id) REFERENCES tb_atleta(id) ON DELETE CASCADE
);

-- 3. tb_prova - Races/Events table
CREATE TABLE IF NOT EXISTS tb_prova (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nome_prova VARCHAR(255) NOT NULL,
    data_prova DATE NOT NULL,
    distancia_km DECIMAL(10,3) NOT NULL CHECK (distancia_km > 0),
    tipo_prova VARCHAR(50) NOT NULL CHECK (tipo_prova IN ('CORRIDA_RUA', 'TRAIL', 'MEIA', 'MARATONA')),
    status_prova VARCHAR(50) NOT NULL CHECK (status_prova IN ('PLANEJADA', 'CONFIRMADA', 'CONCLUIDA', 'CANCELADA')),
    prova_alvo BOOLEAN DEFAULT FALSE,
    objetivo VARCHAR(500),
    atleta_id UUID NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (atleta_id) REFERENCES tb_atleta(id) ON DELETE CASCADE
);

-- 4. tb_plano_metadados - Training plan metadata
CREATE TABLE IF NOT EXISTS tb_plano_metadados (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    atleta_id UUID NOT NULL,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    volume_semanal_anterior DECIMAL(10,3),
    tsb_inicial INTEGER,
    dia_preferido_longo VARCHAR(20) NOT NULL CHECK (dia_preferido_longo IN ('DOMINGO', 'SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA', 'SABADO')),
    atl DECIMAL(10,3) DEFAULT 0.0,
    ctl DECIMAL(10,3) DEFAULT 0.0,
    tsb DECIMAL(10,3) DEFAULT 0.0,
    -- embedding VECTOR(1536), -- Uncomment if using vector embeddings
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (atleta_id) REFERENCES tb_atleta(id) ON DELETE CASCADE
);

-- 5. tb_plano_treino - Training plans table
CREATE TABLE IF NOT EXISTS tb_plano_treino (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    atleta_id UUID NOT NULL,
    prova_id UUID NOT NULL,
    nome VARCHAR(255) NOT NULL,
    descricao TEXT NOT NULL,
    data_inicio DATE NOT NULL,
    objetivo VARCHAR(500) NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('PLANEJADO', 'INICIADO', 'EM_ANDAMENTO', 'ATIVO', 'CONCLUIDO')),
    contexto_id UUID,
    prova_alvo_id UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (atleta_id) REFERENCES tb_atleta(id) ON DELETE CASCADE,
    FOREIGN KEY (prova_id) REFERENCES tb_prova(id) ON DELETE RESTRICT,
    FOREIGN KEY (contexto_id) REFERENCES tb_plano_metadados(id) ON DELETE SET NULL,
    FOREIGN KEY (prova_alvo_id) REFERENCES tb_prova(id) ON DELETE SET NULL
);

-- 6. tb_plano_semanal - Weekly plans table
CREATE TABLE IF NOT EXISTS tb_plano_semanal (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    atleta_id UUID NOT NULL,
    plano_treino_id UUID,
    plano_metadados_id UUID NOT NULL,
    semana_inicio DATE NOT NULL,
    semana_fim DATE NOT NULL,
    volume_planejado_km DECIMAL(10,3) NOT NULL CHECK (volume_planejado_km >= 0),
    volume_realizado_km DECIMAL(10,3),
    volume_alvo_km DECIMAL(10,3),
    tsb_inicio DECIMAL(10,3),
    tsb_fim DECIMAL(10,3),
    status VARCHAR(50) NOT NULL CHECK (status IN ('PLANEJADO', 'INICIADO', 'EM_ANDAMENTO', 'ATIVO', 'CONCLUIDO')),
    objetivo_semana VARCHAR(500),
    observacoes TEXT,
    versao BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (atleta_id) REFERENCES tb_atleta(id) ON DELETE CASCADE,
    FOREIGN KEY (plano_treino_id) REFERENCES tb_plano_treino(id) ON DELETE SET NULL,
    FOREIGN KEY (plano_metadados_id) REFERENCES tb_plano_metadados(id) ON DELETE CASCADE
);

-- 7. tb_treino_planejado - Planned training sessions
CREATE TABLE IF NOT EXISTS tb_treino_planejado (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    plano_semanal_id UUID NOT NULL,
    atleta_id UUID NOT NULL,
    dia_semana VARCHAR(20) NOT NULL CHECK (dia_semana IN ('DOMINGO', 'SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA', 'SABADO')),
    tipo_treino VARCHAR(100) NOT NULL,
    descricao TEXT,
    fc_alvo VARCHAR(50),
    duracao_min INTEGER,
    distancia_km DECIMAL(10,3),
    ritmo_alvo VARCHAR(50),
    observacao TEXT,
    data_treino DATE,
    percepcao_esforco_esperada INTEGER CHECK (percepcao_esforco_esperada BETWEEN 1 AND 10),
    status_treino VARCHAR(50) CHECK (status_treino IN ('REALIZADO', 'PERDIDO', 'PARCIAL', 'LIVRE')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (plano_semanal_id) REFERENCES tb_plano_semanal(id) ON DELETE CASCADE,
    FOREIGN KEY (atleta_id) REFERENCES tb_atleta(id) ON DELETE CASCADE
);

-- 8. tb_etapa_treino - Training stages/phases
CREATE TABLE IF NOT EXISTS tb_etapa_treino (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    treino_planejado_id UUID NOT NULL,
    ordem INTEGER NOT NULL,
    tipo_etapa VARCHAR(100) NOT NULL,
    descricao_etapa TEXT,
    duracao_min INTEGER,
    distancia_km DECIMAL(10,3),
    fc_alvo_etapa VARCHAR(50),
    repeticoes INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (treino_planejado_id) REFERENCES tb_treino_planejado(id) ON DELETE CASCADE
);

-- 9. tb_treino_realizado - Completed training sessions
CREATE TABLE IF NOT EXISTS tb_treino_realizado (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    atleta_id UUID NOT NULL,
    treino_planejado_id UUID,
    plano_semanal_id UUID,
    dia_semana VARCHAR(20) NOT NULL CHECK (dia_semana IN ('DOMINGO', 'SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA', 'SABADO')),
    tipo_treino VARCHAR(100) NOT NULL,
    descricao TEXT,
    fc_alvo VARCHAR(50),
    duracao_min INTEGER,
    distancia_km DECIMAL(10,3),
    ritmo_alvo VARCHAR(50),
    observacao TEXT,
    data_treino DATE,
    fc_media INTEGER,
    fc_max INTEGER,
    ritmo_medio VARCHAR(50),
    potencia_media INTEGER,
    cadencia_media INTEGER,
    comentario TEXT,
    fonte_dados VARCHAR(50) CHECK (fonte_dados IN ('GARMIN', 'STRAVA', 'MANUAL')),
    status VARCHAR(50) CHECK (status IN ('REALIZADO', 'PERDIDO', 'PARCIAL', 'LIVRE')),
    percepcao_esforco INTEGER CHECK (percepcao_esforco BETWEEN 1 AND 10),
    external_id VARCHAR(255),
    elevacao_total INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (atleta_id) REFERENCES tb_atleta(id) ON DELETE CASCADE,
    FOREIGN KEY (treino_planejado_id) REFERENCES tb_treino_planejado(id) ON DELETE SET NULL,
    FOREIGN KEY (plano_semanal_id) REFERENCES tb_plano_semanal(id) ON DELETE SET NULL
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_atleta_ativo ON tb_atleta(ativo);
CREATE INDEX IF NOT EXISTS idx_atleta_nome ON tb_atleta(nome);

CREATE INDEX IF NOT EXISTS idx_prova_atleta ON tb_prova(atleta_id);
CREATE INDEX IF NOT EXISTS idx_prova_data ON tb_prova(data_prova);
CREATE INDEX IF NOT EXISTS idx_prova_status ON tb_prova(status_prova);

CREATE INDEX IF NOT EXISTS idx_plano_metadados_atleta ON tb_plano_metadados(atleta_id);
CREATE INDEX IF NOT EXISTS idx_plano_metadados_data ON tb_plano_metadados(data_criacao);

CREATE INDEX IF NOT EXISTS idx_plano_treino_atleta ON tb_plano_treino(atleta_id);
CREATE INDEX IF NOT EXISTS idx_plano_treino_status ON tb_plano_treino(status);

CREATE INDEX IF NOT EXISTS idx_plano_semanal_atleta ON tb_plano_semanal(atleta_id);
CREATE INDEX IF NOT EXISTS idx_plano_semanal_semana ON tb_plano_semanal(semana_inicio, semana_fim);
CREATE INDEX IF NOT EXISTS idx_plano_semanal_status ON tb_plano_semanal(status);

CREATE INDEX IF NOT EXISTS idx_treino_planejado_atleta ON tb_treino_planejado(atleta_id);
CREATE INDEX IF NOT EXISTS idx_treino_planejado_plano ON tb_treino_planejado(plano_semanal_id);
CREATE INDEX IF NOT EXISTS idx_treino_planejado_data ON tb_treino_planejado(data_treino);

CREATE INDEX IF NOT EXISTS idx_etapa_treino_planejado ON tb_etapa_treino(treino_planejado_id);
CREATE INDEX IF NOT EXISTS idx_etapa_treino_ordem ON tb_etapa_treino(treino_planejado_id, ordem);

CREATE INDEX IF NOT EXISTS idx_treino_realizado_atleta ON tb_treino_realizado(atleta_id);
CREATE INDEX IF NOT EXISTS idx_treino_realizado_data ON tb_treino_realizado(data_treino);
CREATE INDEX IF NOT EXISTS idx_treino_realizado_external ON tb_treino_realizado(external_id);

-- Create triggers for updated_at timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
   NEW.updated_at = CURRENT_TIMESTAMP;
   RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply triggers to all tables with updated_at
CREATE OR REPLACE TRIGGER update_atleta_updated_at BEFORE UPDATE ON tb_atleta
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_prova_updated_at BEFORE UPDATE ON tb_prova
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_plano_metadados_updated_at BEFORE UPDATE ON tb_plano_metadados
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_plano_treino_updated_at BEFORE UPDATE ON tb_plano_treino
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_plano_semanal_updated_at BEFORE UPDATE ON tb_plano_semanal
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_treino_planejado_updated_at BEFORE UPDATE ON tb_treino_planejado
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_etapa_treino_updated_at BEFORE UPDATE ON tb_etapa_treino
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_treino_realizado_updated_at BEFORE UPDATE ON tb_treino_realizado
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert sample data for testing (optional)
-- Uncomment the following if you want sample data for development


-- Sample athlete
INSERT INTO tb_atleta (id, nome, idade, peso_kg, altura_cm, objetivo, nivel_experiencia, dia_preferido_longo, tem_lesao, ativo)
VALUES
    (uuid_generate_v4(), 'Jo�o Silva', 35, 75.5, 175.0, 'Completar meia maratona em menos de 2h', 'INTERMEDIARIO', 'SABADO', false, 'ATIVO'),
    (uuid_generate_v4(), 'Maria Santos', 28, 62.0, 165.0, 'Melhorar tempo nos 10K', 'AVANCADO', 'DOMINGO', false, 'ATIVO');

-- Sample available days
INSERT INTO tb_dias_disponiveis (atleta_id, dia)
SELECT id, 'SEGUNDA' FROM tb_atleta WHERE nome = 'Jo�o Silva'
UNION ALL
SELECT id, 'QUARTA' FROM tb_atleta WHERE nome = 'Jo�o Silva'
UNION ALL
SELECT id, 'SEXTA' FROM tb_atleta WHERE nome = 'Jo�o Silva'
UNION ALL
SELECT id, 'SABADO' FROM tb_atleta WHERE nome = 'Jo�o Silva';

-- Sample race
INSERT INTO tb_prova (id, nome_prova, data_prova, distancia_km, tipo_prova, status_prova, prova_alvo, objetivo, atleta_id)
SELECT uuid_generate_v4(), 'Meia Maratona S�o Paulo', '2024-12-15', 21.097, 'MEIA', 'CONFIRMADA', true, 'Sub 2h', id
FROM tb_atleta WHERE nome = 'Jo�o Silva';


-- Grant permissions (if needed)
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO menthoros;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO menthoros;

-- Final message
SELECT 'Menthoros database schema created successfully!' as message;