## Why

Separar o fluxo OAuth do Strava em um change dedicado reduz risco e facilita rollout incremental. Este change implementa apenas autorização, callback, status de conexão e desconexão, mantendo o escopo isolado para branch específica.

## What Changes

- Serviço de OAuth (`StravaOAuthService`) para autorização, troca e refresh de token
- Controller (`StravaAuthController`) com endpoints de auth/callback/status/disconnect
- Validação de tenant em operações de status e desconexão
- Persistência de tokens em `IntegracaoExterna` para `plataforma = STRAVA`

## Impact

- APIs: `GET /api/strava/auth`, `GET /api/strava/callback`, `GET /api/strava/status/{atletaId}`, `DELETE /api/strava/disconnect/{atletaId}`
- Banco: usa `tb_integracao_externa` já existente na branch base
- Segurança: endpoints protegidos por JWT (exceto webhook, fora deste change)
