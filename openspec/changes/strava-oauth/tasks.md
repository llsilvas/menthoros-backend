## 1. OAuth Service

- [ ] 1.1 Criar `StravaOAuthService.java` com métodos: `getAuthorizationUrl(UUID atletaId)`, `exchangeCodeForToken(String code, Atleta atleta)`, `refreshAccessToken(IntegracaoExterna integracao)`, `getValidToken(UUID atletaId)`, `isConnected(UUID atletaId)`, `disconnect(UUID atletaId)`
- [ ] 1.2 Implementar lógica de verificação de expiração de token em `getValidToken` (5 minutos de margem)
- [ ] 1.3 Implementar desativação de integração (`ativo = false`, limpeza de tokens) em `disconnect`
- [ ] 1.4 Garantir associação de `tenant_id` ao salvar `IntegracaoExterna`
- [ ] 1.5 Implementar retry/backoff para refresh de token (ex.: 3 tentativas com atraso progressivo)
- [ ] 1.6 Registrar falha persistente de refresh com log estruturado (`atletaId`, `tenantId`, `plataforma`, `motivo`)

## 2. OAuth Controller

- [ ] 2.1 Criar `StravaAuthController.java` com endpoints: `GET /api/strava/auth`, `GET /api/strava/callback`, `GET /api/strava/status/{atletaId}`, `DELETE /api/strava/disconnect/{atletaId}`
- [ ] 2.2 Adicionar anotações OpenAPI (`@Tag`, `@Operation`) no controller
- [ ] 2.3 Implementar redirecionamento de callback com `strava=success|error`
- [ ] 2.4 Garantir `404` para atleta inexistente e para atleta fora do tenant em status/disconnect

## 3. Testes

- [ ] 3.1 Criar `StravaOAuthServiceTest.java` cobrindo: geração de URL, refresh, detecção de expiração e desconexão
- [ ] 3.2 Adicionar teste de falha de refresh com desativação de integração e limpeza de tokens
- [ ] 3.3 Adicionar teste de retry/backoff (mockando falhas transitórias no endpoint de token)

## 4. Segurança

- [ ] 4.1 Verificar proteção JWT dos endpoints de OAuth em `SecurityConfig`

## 5. Critérios de Aceite

- [ ] 5.1 Callback válido persiste `access_token`, `refresh_token`, `token_expira_em`, `external_athlete_id` e `tenant_id`
- [ ] 5.2 Token próximo de expirar dispara refresh automático antes de chamadas de API
- [ ] 5.3 Falha persistente de refresh desativa integração e força reautorização

## 6. Review Gate (OpenSpec)

- [ ] 6.1 Executar `openspec status --change "strava-oauth" --json` e confirmar artifacts `done`
- [ ] 6.2 Executar `openspec instructions apply --change "strava-oauth" --json` e revisar tasks pendentes
- [ ] 6.3 Registrar resultado da revisão no PR antes de merge
