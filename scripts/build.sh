#!/bin/bash

# Script de build automatizado para diferentes ambientes
# Uso: ./scripts/build.sh [dev|staging|prod] [--no-cache]

set -e

ENV=${1:-dev}
NO_CACHE=""

if [ "$2" == "--no-cache" ]; then
    NO_CACHE="--no-cache"
fi

echo "🚀 Building Menthoros para ambiente: $ENV"

case $ENV in
    "dev")
        echo "📝 Ambiente de desenvolvimento"
        docker-compose -f docker-compose.multi-env.yml --profile dev build $NO_CACHE app-dev
        ;;
    "staging")
        echo "🧪 Ambiente de staging"
        docker-compose -f docker-compose.multi-env.yml --profile staging build $NO_CACHE app-staging
        ;;
    "prod")
        echo "🏭 Ambiente de produção"
        docker-compose -f docker-compose.multi-env.yml --profile prod build $NO_CACHE app-prod
        ;;
    *)
        echo "❌ Ambiente inválido: $ENV"
        echo "Uso: ./scripts/build.sh [dev|staging|prod] [--no-cache]"
        exit 1
        ;;
esac

echo "✅ Build concluído para ambiente: $ENV"