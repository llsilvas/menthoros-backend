## ADDED Requirements

### Requirement: Criar prova de atleta
O sistema SHALL permitir o cadastro de uma nova prova vinculada a um atleta existente, desde que o atleta pertença ao tenant do usuário autenticado.

#### Scenario: Cadastro com dados válidos
- **WHEN** uma requisição POST é feita para `/atleta/{atletaId}/provas` com body válido e token JWT com tenant_id
- **THEN** o sistema SHALL criar a prova, associá-la ao atleta e retornar HTTP 201 com o `ProvaOutputDto`

#### Scenario: Atleta não encontrado
- **WHEN** uma requisição POST é feita com `atletaId` inexistente no tenant
- **THEN** o sistema SHALL retornar HTTP 404 com mensagem de erro

#### Scenario: Atleta de outro tenant
- **WHEN** o `atletaId` existe mas pertence a outro tenant
- **THEN** o sistema SHALL retornar HTTP 404 (não revelar existência de dados de outro tenant)

#### Scenario: Body inválido
- **WHEN** campos obrigatórios estão ausentes ou inválidos (ex: `nomeProva` em branco, `dataProva` nula)
- **THEN** o sistema SHALL retornar HTTP 400 com detalhes de validação

### Requirement: Listar provas de atleta
O sistema SHALL retornar todas as provas de um atleta, ordenadas por data ascendente.

#### Scenario: Listagem com sucesso
- **WHEN** uma requisição GET é feita para `/atleta/{atletaId}/provas` com token JWT válido
- **THEN** o sistema SHALL retornar HTTP 200 com lista de `ProvaOutputDto` ordenada por `dataProva` ascendente

#### Scenario: Atleta sem provas
- **WHEN** o atleta existe mas não possui provas cadastradas
- **THEN** o sistema SHALL retornar HTTP 200 com lista vazia

#### Scenario: Atleta de outro tenant
- **WHEN** o `atletaId` existe mas pertence a outro tenant
- **THEN** o sistema SHALL retornar HTTP 404

### Requirement: Buscar prova por ID
O sistema SHALL retornar os dados de uma prova específica de um atleta.

#### Scenario: Prova encontrada
- **WHEN** uma requisição GET é feita para `/atleta/{atletaId}/provas/{provaId}` com token válido
- **THEN** o sistema SHALL retornar HTTP 200 com o `ProvaOutputDto`

#### Scenario: Prova não pertence ao atleta
- **WHEN** a `provaId` existe mas está vinculada a outro atleta
- **THEN** o sistema SHALL retornar HTTP 404

#### Scenario: Prova não encontrada
- **WHEN** a `provaId` não existe
- **THEN** o sistema SHALL retornar HTTP 404

### Requirement: Atualizar prova de atleta
O sistema SHALL permitir a atualização dos dados de uma prova existente do atleta.

#### Scenario: Atualização com dados válidos
- **WHEN** uma requisição PUT é feita para `/atleta/{atletaId}/provas/{provaId}` com body válido
- **THEN** o sistema SHALL atualizar a prova e retornar HTTP 200 com o `ProvaOutputDto` atualizado

#### Scenario: Prova não encontrada ou de outro atleta
- **WHEN** a `provaId` não existe ou pertence a outro atleta do mesmo ou diferente tenant
- **THEN** o sistema SHALL retornar HTTP 404

#### Scenario: Body inválido
- **WHEN** campos obrigatórios estão ausentes
- **THEN** o sistema SHALL retornar HTTP 400

### Requirement: Deletar prova de atleta
O sistema SHALL permitir a remoção permanente de uma prova de um atleta.

#### Scenario: Deleção com sucesso
- **WHEN** uma requisição DELETE é feita para `/atleta/{atletaId}/provas/{provaId}` com token válido
- **THEN** o sistema SHALL remover a prova e retornar HTTP 204

#### Scenario: Prova não encontrada ou de outro atleta
- **WHEN** a `provaId` não existe ou pertence a outro atleta
- **THEN** o sistema SHALL retornar HTTP 404

### Requirement: Isolamento multi-tenancy
Todas as operações sobre provas SHALL respeitar o `tenant_id` extraído do JWT pelo `JwtTenantFilter`.

#### Scenario: Acesso a dados do próprio tenant
- **WHEN** o usuário autenticado opera sobre provas de atletas do seu tenant
- **THEN** o sistema SHALL processar a requisição normalmente

#### Scenario: Tentativa de acesso a dados de outro tenant
- **WHEN** o usuário tenta acessar ou modificar provas de um atleta de outro tenant
- **THEN** o sistema SHALL retornar HTTP 404 sem revelar a existência dos dados
