## 1. OAuth Service

- [ ] 1.1 Criar `StravaOAuthService.java` com métodos: `getAuthorizationUrl(UUID atletaId)`, `exchangeCodeForToken(String code, Atleta atleta)`, `refreshAccessToken(IntegracaoExterna integracao)`, `getValidToken(UUID atletaId)`, `isConnected(UUID atletaId)`, `disconnect(UUID atletaId)`
- [ ] 1.2 Implementar lógica de verificação de expiração de token em `getValidToken` (5 minutos de margem)
- [ ] 1.3 Implementar desativação de integração (`ativo = false`, limpeza de tokens) em `disconnect`
- [ ] 1.4 Garantir associação de `tenant_id` ao salvar `IntegracaoExterna`

## 2. OAuth Controller

- [ ] 2.1 Criar `StravaAuthController.java` com endpoints: `GET /api/strava/auth`, `GET /api/strava/callback`, `GET /api/strava/status/{atletaId}`, `DELETE /api/strava/disconnect/{atletaId}`
- [ ] 2.2 Adicionar anotações OpenAPI (`@Tag`, `@Operation`) no controller
- [ ] 2.3 Implementar redirecionamento de callback com `strava=success|error`

## 3. Testes

- [ ] 3.1 Criar `StravaOAuthServiceTest.java` cobrindo: geração de URL, refresh, detecção de expiração e desconexão

## 4. Segurança

- [ ] 4.1 Verificar proteção JWT dos endpoints de OAuth em `SecurityConfig`
