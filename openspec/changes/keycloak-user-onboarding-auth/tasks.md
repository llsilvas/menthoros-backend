## 1. Configuração e Infra

- [ ] 1.1 Adicionar propriedades de integração administrativa do Keycloak (`url`, `realm`, `admin-client-id`, `admin-client-secret`) em `application.yml` e `.env.example`
- [ ] 1.2 Criar cliente HTTP dedicado para chamadas ao Keycloak (token admin, criação de usuário, atribuição de roles/grupos)
- [ ] 1.3 Definir política de timeout/retry para chamadas externas ao Keycloak

## 2. Login Backend

- [ ] 2.1 Criar DTOs `LoginRequest`, `LoginResponse` e mapeamento de erro de autenticação
- [ ] 2.2 Implementar `AuthController` com `POST /api/public/auth/login`
- [ ] 2.3 Implementar `AuthService` que chama endpoint de token do Keycloak e retorna payload normalizado
- [ ] 2.4 Atualizar `SecurityConfig` para liberar `POST /api/public/auth/login`
- [ ] 2.5 Garantir que senha/token não sejam registrados em logs

## 3. Provisionamento de Usuário

- [ ] 3.1 Criar DTOs de entrada/saída para `POST /api/admin/usuarios`
- [ ] 3.2 Implementar serviço de provisionamento: cria usuário no Keycloak, define senha, roles e vínculo de tenant
- [ ] 3.3 Persistir/sincronizar registro em `tb_usuario` com `tenant_id` e identificador do Keycloak
- [ ] 3.4 Tratar conflito de email/username já existente com retorno `409`
- [ ] 3.5 Implementar estratégia compensatória em falha parcial (rollback no Keycloak quando aplicável)

## 4. Autorização e Multi-Tenancy

- [ ] 4.1 Restringir `POST /api/admin/usuarios` para role administrativa
- [ ] 4.2 Validar `tenantId` de destino e impedir criação fora da política definida
- [ ] 4.3 Garantir que claims/roles emitidos pelo Keycloak permaneçam compatíveis com `JwtTenantFilter`

## 5. Testes

- [ ] 5.1 Testes unitários do `AuthService` (sucesso, 401, falha externa)
- [ ] 5.2 Testes unitários do provisionamento (sucesso, conflito, falha parcial com compensação)
- [ ] 5.3 Testes de controller para `/api/public/auth/login` e `/api/admin/usuarios`
- [ ] 5.4 Teste de integração mínimo com Keycloak (ou mock contratual) para criação e login

## 6. Documentação e Operação

- [ ] 6.1 Atualizar README com fluxo de criação de usuário e login
- [ ] 6.2 Documentar variáveis obrigatórias de Keycloak para ambientes local/cloud
- [ ] 6.3 Documentar respostas de erro padronizadas para frontend

## 7. Critérios de Aceite

- [ ] 7.1 Login válido retorna `200` com `accessToken` utilizável nas APIs protegidas
- [ ] 7.2 Login inválido retorna `401` com mensagem funcional sem expor detalhes sensíveis
- [ ] 7.3 Criação de usuário com dados válidos retorna `201` e usuário autenticável no Keycloak
- [ ] 7.4 Criação duplicada (email/username) retorna `409`
- [ ] 7.5 Usuário criado contém vínculo correto de `tenant_id` para uso nos endpoints multi-tenant
