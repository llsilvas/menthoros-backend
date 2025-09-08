#!/bin/bash

# Script de deploy automatizado
# Uso: ./scripts/deploy.sh [dev|staging|prod]

set -e

ENV=${1:-dev}

echo "🚀 Deploy do Menthoros para ambiente: $ENV"

# Verificar se o .env existe
if [ ! -f .env ]; then
    echo "❌ Arquivo .env não encontrado!"
    echo "Copie .env.example para .env e configure as variáveis"
    exit 1
fi

# Parar serviços existentes
echo "🛑 Parando serviços existentes..."
docker-compose -f docker-compose.multi-env.yml --profile $ENV down

case $ENV in
    "dev")
        echo "📝 Iniciando ambiente de desenvolvimento..."
        docker-compose -f docker-compose.multi-env.yml --profile dev up -d
        echo "🌐 Aplicação disponível em: http://localhost:8099"
        echo "🐞 Debug na porta: 5005"
        echo "🔄 LiveReload na porta: 35729"
        ;;
    "staging")
        echo "🧪 Iniciando ambiente de staging..."
        docker-compose -f docker-compose.multi-env.yml --profile staging up -d
        echo "🌐 Aplicação disponível em: http://localhost:8099"
        ;;
    "prod")
        echo "🏭 Iniciando ambiente de produção..."
        docker-compose -f docker-compose.multi-env.yml --profile prod up -d
        echo "🌐 Aplicação disponível em: http://localhost:8099"
        echo "🔒 Nginx disponível em: http://localhost:80"
        ;;
    *)
        echo "❌ Ambiente inválido: $ENV"
        echo "Uso: ./scripts/deploy.sh [dev|staging|prod]"
        exit 1
        ;;
esac

echo "✅ Deploy concluído para ambiente: $ENV"

# Mostrar logs
echo "📋 Últimos logs:"
docker-compose -f docker-compose.multi-env.yml --profile $ENV logs --tail=20 app-$ENV