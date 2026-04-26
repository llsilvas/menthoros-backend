## Context

Este change assume a branch base com infraestrutura Strava (properties, entidade `IntegracaoExterna`, migrations e DTOs principais) já disponível.

## Goals

- Implementar fluxo OAuth2 fim a fim para conectar conta Strava por atleta
- Garantir refresh automático com margem de 5 minutos
- Permitir consulta de status e desconexão sem apagar histórico de treinos

## Non-Goals

- Sincronização de atividades
- Processamento de webhooks
- Criptografia de token em repouso

## Decisions

### D1: `state` no callback carrega `atletaId`

O callback usa `state=atletaId` para correlacionar autorização com atleta alvo.

### D2: Refresh com margem de segurança

`getValidToken` considera token vencido quando `token_expira_em <= now + 5min`.

### D3: Desconexão lógica

`disconnect` marca `ativo = false` e limpa tokens, preservando dados históricos já importados.

### D4: Tenant enforcement

Status e desconexão devem usar `TenantContext` e consultas filtradas por tenant para evitar vazamento entre assessorias.
