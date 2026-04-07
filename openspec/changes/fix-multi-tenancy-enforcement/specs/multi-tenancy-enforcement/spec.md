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
