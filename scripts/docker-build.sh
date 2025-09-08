#!/bin/bash

# Script para build do Docker com verificações

set -e

echo "🔍 Verificando estrutura do projeto..."

# Verificar se estamos no diretório correto
if [ ! -f "pom.xml" ]; then
    echo "❌ Erro: pom.xml não encontrado. Execute este script do diretório raiz do projeto."
    exit 1
fi

if [ ! -d "src" ]; then
    echo "❌ Erro: diretório src/ não encontrado."
    exit 1
fi

echo "✅ Estrutura do projeto OK"

# Verificar Dockerfile
DOCKERFILE="docker/Dockerfile.dev"
if [ ! -f "$DOCKERFILE" ]; then
    echo "❌ Erro: $DOCKERFILE não encontrado."
    exit 1
fi

echo "✅ Dockerfile encontrado: $DOCKERFILE"

# Build da imagem
IMAGE_NAME="${1:-menthoros:dev}"
echo "🚀 Iniciando build da imagem: $IMAGE_NAME"

docker build -f "$DOCKERFILE" -t "$IMAGE_NAME" .

echo "✅ Build concluído com sucesso!"
echo "📋 Para executar: docker run -p 8099:8099 $IMAGE_NAME"