-- Initial schema for Menthoros application
-- This migration creates the basic tables structure

-- Atleta table
CREATE TABLE IF NOT EXISTS tb_atleta (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(100) NOT NULL,
    idade INTEGER NOT NULL CHECK (idade >= 10 AND idade <= 100),
    peso_kg DECIMAL(5,2) CHECK (peso_kg > 0 AND peso_kg <= 300),
    altura_cm DECIMAL(5,2) CHECK (altura_cm >= 100 AND altura_cm <= 250),
    objetivo VARCHAR(500) NOT NULL,
    nivel_experiencia VARCHAR(20) NOT NULL,
    dia_preferido_longo VARCHAR(20),
    tem_lesao BOOLEAN DEFAULT FALSE,
    descricao_lesao VARCHAR(1000),
    ativo VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
    embedding vector(1536), -- Para embeddings da OpenAI
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Dias disponíveis table
CREATE TABLE IF NOT EXISTS tb_dias_disponiveis (
    atleta_id UUID NOT NULL,
    dia VARCHAR(20) NOT NULL,
    PRIMARY KEY (atleta_id, dia),
    FOREIGN KEY (atleta_id) REFERENCES tb_atleta(id) ON DELETE CASCADE
);

-- Prova table
CREATE TABLE IF NOT EXISTS tb_prova (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    atleta_id UUID NOT NULL,
    nome VARCHAR(100) NOT NULL,
    tipo_prova VARCHAR(20) NOT NULL,
    data_prova DATE,
    distancia DECIMAL(8,2),
    tempo_meta TIME,
    status_prova VARCHAR(20) NOT NULL DEFAULT 'PLANEJADA',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (atleta_id) REFERENCES tb_atleta(id) ON DELETE CASCADE
);

-- Plano Meta Dados table
CREATE TABLE IF NOT EXISTS tb_plano_meta_dados (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    atleta_id UUID NOT NULL,
    contexto TEXT,
    embedding vector(1536),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (atleta_id) REFERENCES tb_atleta(id) ON DELETE CASCADE
);

-- Plano Treino table
CREATE TABLE IF NOT EXISTS tb_plano_treino (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    atleta_id UUID NOT NULL,
    prova_id UUID NOT NULL,
    contexto_id UUID,
    prova_alvo_id UUID,
    nome VARCHAR(100) NOT NULL,
    descricao TEXT NOT NULL,
    data_inicio DATE NOT NULL,
    objetivo VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (atleta_id) REFERENCES tb_atleta(id) ON DELETE CASCADE,
    FOREIGN KEY (prova_id) REFERENCES tb_prova(id) ON DELETE CASCADE,
    FOREIGN KEY (contexto_id) REFERENCES tb_plano_meta_dados(id),
    FOREIGN KEY (prova_alvo_id) REFERENCES tb_prova(id)
);

-- Plano Semanal table
CREATE TABLE IF NOT EXISTS tb_plano_semanal (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    atleta_id UUID NOT NULL,
    plano_treino_id UUID,
    semana INTEGER NOT NULL,
    data_inicio DATE NOT NULL,
    data_fim DATE NOT NULL,
    observacoes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (atleta_id) REFERENCES tb_atleta(id) ON DELETE CASCADE,
    FOREIGN KEY (plano_treino_id) REFERENCES tb_plano_treino(id) ON DELETE CASCADE
);

-- Treino Base (abstract table)
-- This represents the common fields for TreinoBase class

-- Treino Planejado table
CREATE TABLE IF NOT EXISTS tb_treino_planejado (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    atleta_id UUID NOT NULL,
    plano_semanal_id UUID NOT NULL,
    
    -- TreinoBase fields
    tipo_treino VARCHAR(20) NOT NULL,
    dia_semana VARCHAR(20) NOT NULL,
    distancia_km DECIMAL(8,2),
    duracao_minutos INTEGER,
    intensidade VARCHAR(20),
    descricao TEXT,
    
    -- TreinoPlanejado specific fields
    observacao TEXT,
    data_treino DATE,
    percepcao_esforco_esperada INTEGER CHECK (percepcao_esforco_esperada >= 1 AND percepcao_esforco_esperada <= 10),
    status_treino VARCHAR(20),
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (atleta_id) REFERENCES tb_atleta(id) ON DELETE CASCADE,
    FOREIGN KEY (plano_semanal_id) REFERENCES tb_plano_semanal(id) ON DELETE CASCADE
);

-- Etapa Treino table
CREATE TABLE IF NOT EXISTS tb_etapa_treino (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    treino_planejado_id UUID NOT NULL,
    ordem INTEGER NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    distancia_metros INTEGER,
    duracao_segundos INTEGER,
    ritmo_segundos_por_km INTEGER,
    repeticoes INTEGER DEFAULT 1,
    descanso_segundos INTEGER DEFAULT 0,
    intensidade VARCHAR(20),
    observacoes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (treino_planejado_id) REFERENCES tb_treino_planejado(id) ON DELETE CASCADE
);

-- Treino Realizado table
CREATE TABLE IF NOT EXISTS tb_treino_realizado (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    atleta_id UUID NOT NULL,
    
    -- TreinoBase fields
    tipo_treino VARCHAR(20) NOT NULL,
    dia_semana VARCHAR(20) NOT NULL,
    distancia_km DECIMAL(8,2),
    duracao_minutos INTEGER,
    intensidade VARCHAR(20),
    descricao TEXT,
    
    -- TreinoRealizado specific fields
    data_realizacao DATE NOT NULL,
    percepcao_esforco INTEGER CHECK (percepcao_esforco >= 1 AND percepcao_esforco <= 10),
    observacoes_atleta TEXT,
    condicoes_climaticas VARCHAR(100),
    frequencia_cardiaca_media INTEGER,
    frequencia_cardiaca_maxima INTEGER,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (atleta_id) REFERENCES tb_atleta(id) ON DELETE CASCADE
);

-- Indexes for better performance
CREATE INDEX IF NOT EXISTS idx_atleta_ativo ON tb_atleta(ativo);
CREATE INDEX IF NOT EXISTS idx_atleta_nome ON tb_atleta(nome);
CREATE INDEX IF NOT EXISTS idx_prova_atleta ON tb_prova(atleta_id);
CREATE INDEX IF NOT EXISTS idx_prova_data ON tb_prova(data_prova);
CREATE INDEX IF NOT EXISTS idx_plano_treino_atleta ON tb_plano_treino(atleta_id);
CREATE INDEX IF NOT EXISTS idx_plano_semanal_atleta ON tb_plano_semanal(atleta_id);
CREATE INDEX IF NOT EXISTS idx_treino_planejado_atleta ON tb_treino_planejado(atleta_id);
CREATE INDEX IF NOT EXISTS idx_treino_planejado_data ON tb_treino_planejado(data_treino);
CREATE INDEX IF NOT EXISTS idx_treino_realizado_atleta ON tb_treino_realizado(atleta_id);
CREATE INDEX IF NOT EXISTS idx_treino_realizado_data ON tb_treino_realizado(data_realizacao);
CREATE INDEX IF NOT EXISTS idx_etapa_treino_ordem ON tb_etapa_treino(treino_planejado_id, ordem);

-- Enable pgvector extension if not already enabled
CREATE EXTENSION IF NOT EXISTS vector;