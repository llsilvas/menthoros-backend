## ADDED Requirements

### Requirement: Autenticação obrigatória em rotas de negócio
Toda rota de negócio SHALL exigir um JWT Bearer válido contendo `tenant_id`. Rotas públicas permitidas sem token: `/api/public/**`, `/swagger-ui/**`, `/api-docs/**`, `/actuator/health`.

#### Scenario: Request sem token em rota de negócio
- **WHEN** uma requisição é feita a qualquer endpoint de negócio sem cabeçalho `Authorization`
- **THEN** o sistema retorna HTTP 401

#### Scenario: Request com token sem tenant_id
- **WHEN** uma requisição é feita com JWT válido mas sem claim `tenant_id`
- **THEN** o sistema retorna HTTP 401 ou 403

#### Scenario: Rotas públicas acessíveis sem token
- **WHEN** uma requisição é feita para `/actuator/health` ou `/swagger-ui/**` sem token
- **THEN** o sistema retorna HTTP 200

---

### Requirement: Resolução de tenant sem fallback
O sistema SHALL usar `TenantContext.getRequiredTenantId()` para obter o tenant em todos os services de negócio. O sistema NOT SHALL usar qualquer fallback para "primeira assessoria ativa" ou tenant default em fluxo de request HTTP.

#### Scenario: Request de negócio sem contexto de tenant
- **WHEN** um service de negócio é invocado e `TenantContext` está vazio (sem JWT)
- **THEN** o sistema lança `IllegalStateException` e retorna HTTP 500

#### Scenario: Criação de atleta com tenant do JWT
- **WHEN** uma requisição `POST /atleta` é feita com JWT contendo `tenant_id` válido
- **THEN** o atleta é criado associado exclusivamente ao tenant do JWT

---

### Requirement: Acesso a entidades tenant-scoped filtrado por tenant
Toda consulta por ID a uma entidade tenant-scoped SHALL incluir `tenant_id` como critério de filtro no mesmo select do banco de dados. O sistema NOT SHALL retornar ou modificar entidades de um tenant diferente do tenant da request.

#### Scenario: Acesso a atleta de outro tenant por ID
- **WHEN** uma requisição busca um atleta por UUID que existe mas pertence a outro tenant
- **THEN** o sistema retorna HTTP 404

#### Scenario: Acesso a treino realizado de outro tenant por ID
- **WHEN** uma requisição busca um treino realizado por UUID que existe mas pertence a outro tenant
- **THEN** o sistema retorna HTTP 404

#### Scenario: Acesso a plano semanal de outro tenant por ID
- **WHEN** uma requisição busca um plano semanal por UUID que existe mas pertence a outro tenant
- **THEN** o sistema retorna HTTP 404

#### Scenario: Acesso a prova de outro tenant por ID
- **WHEN** uma requisição busca uma prova por UUID que existe mas pertence a outro tenant
- **THEN** o sistema retorna HTTP 404

#### Scenario: Acesso a metadados de atleta de outro tenant
- **WHEN** um service consulta `PlanoMetaDados` por ID e o registro pertence a outro tenant
- **THEN** o sistema retorna `Optional.empty()` ou lança `ResourceNotFoundException`

---

### Requirement: Cache segmentado por tenant
Toda entrada de cache de entidade ou lista tenant-scoped SHALL usar chave que inclua o `tenantId` como prefixo. O sistema NOT SHALL retornar um cache hit de tenant A para uma request de tenant B.

#### Scenario: Cache de atleta segmentado por tenant
- **WHEN** tenant A consulta atleta com ID X e o resultado é cacheado
- **THEN** uma consulta de tenant B ao mesmo ID X não retorna o cache de tenant A

#### Scenario: Cache de lista de atletas segmentado por tenant
- **WHEN** tenant A consulta a lista de atletas e o resultado é cacheado
- **THEN** uma consulta de tenant B não retorna a lista cacheada de tenant A

#### Scenario: Invalidação de cache por tenant
- **WHEN** tenant A atualiza um atleta
- **THEN** apenas as entradas de cache do tenant A são invalidadas

---

### Requirement: Entidade PlanoMetaDados com tenant mapeado
A entidade `PlanoMetaDados` SHALL mapear o campo `tenant_id` do banco de dados como relação `@ManyToOne Assessoria`. A criação de novos metadados SHALL persistir o `tenant_id` do contexto da request.

#### Scenario: Criação de metadados com tenant
- **WHEN** `PlanoMetadadosServiceImpl` cria um novo registro de `PlanoMetaDados`
- **THEN** o campo `assessoria` é populado com a assessoria do `TenantContext` atual

#### Scenario: Consulta de metadados filtrada por tenant
- **WHEN** `PlanoMetadadosRepository` busca metadados por atleta
- **THEN** apenas metadados do tenant atual são retornados

---

### Requirement: Índice único para deduplicação de treinos por tenant
A tabela `tb_treino_realizado` SHALL ter índice único composto `(tenant_id, fonte_dados, external_id)` quando `fonte_dados` e `external_id` são não nulos, garantindo que IDs externos de integrações não colidam entre tenants.

#### Scenario: Deduplicação de treino por tenant
- **WHEN** dois treinos do mesmo `fonte_dados` e `external_id` são importados para tenants diferentes
- **THEN** ambos são aceitos sem conflito de unicidade

#### Scenario: Rejeição de duplicata no mesmo tenant
- **WHEN** um treino do mesmo `fonte_dados`, `external_id` e `tenant_id` é inserido novamente
- **THEN** o banco rejeita com violação de constraint de unicidade

---

### Requirement: Extração de tenant_id com suporte a dois formatos de JWT
O `JwtTenantFilter` SHALL extrair o `tenant_id` a partir de dois formatos possíveis de JWT emitido pelo Keycloak:
1. **Claim flat** — `tenant_id` diretamente no payload do JWT (formato primário)
2. **Claim organization** — `organization.{orgId}.tenant_id` (formato Keycloak Organizations)

A extração SHALL tentar o claim flat primeiro; se ausente, SHALL tentar o claim `organization`.

#### Scenario: JWT com claim tenant_id flat
- **WHEN** uma requisição é feita com JWT contendo o claim `tenant_id` diretamente no payload
- **THEN** o sistema SHALL extrair o `tenant_id` desse claim e configurar o `TenantContext`

#### Scenario: JWT com claim organization (Keycloak Organizations)
- **WHEN** uma requisição é feita com JWT sem claim `tenant_id` flat mas com claim `organization` no formato `{ "<orgId>": { "tenant_id": "<uuid>" } }`
- **THEN** o sistema SHALL extrair o `tenant_id` do objeto aninhado e configurar o `TenantContext`

#### Scenario: JWT com múltiplas organizations
- **WHEN** uma requisição é feita com JWT cujo claim `organization` contém mais de uma entrada
- **THEN** o sistema SHALL rejeitar a requisição com HTTP 403
- **AND** NÃO SHALL configurar nenhum `TenantContext`

#### Scenario: JWT com tenant_id inválido (não-UUID)
- **WHEN** uma requisição é feita com JWT cujo `tenant_id` não é um UUID válido
- **THEN** o sistema SHALL rejeitar a requisição com HTTP 403

---

### Requirement: Sincronização automática de usuário do Keycloak na camada de filtro
A cada requisição autenticada com JWT válido e `tenant_id` resolvido, o `JwtTenantFilter` SHALL invocar o `UsuarioSyncService` para sincronizar o usuário do Keycloak com a tabela `tb_usuario`.

`tb_usuario` é um **cache local** dos dados do Keycloak — não armazena senhas. A fonte da verdade é sempre o Keycloak.

#### Scenario: Primeiro acesso do usuário cria registro local
- **GIVEN** uma requisição autenticada com JWT cujo `sub` não existe em `tb_usuario`
- **WHEN** o `JwtTenantFilter` processa a requisição
- **THEN** o sistema SHALL criar um novo registro em `tb_usuario` com `id = UUID.fromString(sub)`, associado à assessoria do `tenant_id`
- **AND** SHALL popular `email`, `nome`, `sobrenome`, `email_verificado` e `role` a partir dos claims do JWT

#### Scenario: Acesso subsequente atualiza dados do usuário local
- **GIVEN** uma requisição autenticada com JWT cujo `sub` já existe em `tb_usuario`
- **WHEN** o `JwtTenantFilter` processa a requisição
- **THEN** o sistema SHALL atualizar `email`, `nome`, `sobrenome`, `email_verificado`, `role`, `ultimo_acesso` e `ultima_sinc` no registro existente

#### Scenario: tenant_id do JWT sem assessoria correspondente no banco
- **GIVEN** uma requisição autenticada com JWT cujo `tenant_id` é um UUID válido mas não existe em `tb_assessoria`
- **WHEN** o `UsuarioSyncService` tenta criar o usuário local
- **THEN** o sistema SHALL lançar `IllegalArgumentException`
- **AND** a exceção SHALL ser capturada e logada pelo `JwtTenantFilter`
- **AND** a requisição SHALL continuar sem usuário sincronizado (falha não-bloqueante)

#### Scenario: Falha na sincronização não bloqueia a requisição
- **GIVEN** qualquer falha no `UsuarioSyncService` (ex: banco indisponível, constraint violation)
- **WHEN** o `JwtTenantFilter` captura a exceção
- **THEN** o sistema SHALL registrar o erro em log
- **AND** SHALL continuar o processamento da requisição normalmente (a autenticação e o `TenantContext` já estão configurados)
- **AND** NÃO SHALL retornar erro HTTP por causa da falha de sincronização

---

### Requirement: Mapeamento de roles do Keycloak para UserRole local
O sistema SHALL extrair roles do JWT a partir de dois caminhos e mapeá-las para o enum `UserRole` (ADMIN, TECNICO, VISUALIZADOR).

Caminhos de extração (aplicados em conjunto, com prioridade de hierarquia no mapeamento):
1. Claim flat `roles` (string única ou lista)
2. `resource_access.{app.security.roles-client-id}.roles` (Keycloak client roles)

Hierarquia de mapeamento: `ADMIN > TECNICO > VISUALIZADOR`.

#### Scenario: JWT com role ADMIN
- **WHEN** o JWT contém `"ADMIN"` no claim de roles (flat ou resource_access)
- **THEN** o usuário sincronizado SHALL ter `role = ADMIN` em `tb_usuario`

#### Scenario: JWT sem role reconhecida
- **WHEN** o JWT não contém nenhuma das roles reconhecidas (`ADMIN`, `TECNICO`, `VISUALIZADOR`)
- **THEN** o sistema SHALL atribuir `role = VISUALIZADOR` como padrão seguro (somente leitura)
- **AND** SHALL registrar aviso em log
