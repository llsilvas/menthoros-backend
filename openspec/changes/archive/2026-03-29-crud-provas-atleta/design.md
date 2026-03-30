## Context

A entidade `Prova` e o `ProvaRepository` já existem. O repositório possui queries para buscar provas por atleta, por intervalo de datas e por prova alvo. Falta a camada de serviço e o controller REST. O isolamento de tenant é feito via `Atleta.tenantId`, ou seja, ao acessar provas de um atleta, o serviço deve validar que o atleta pertence ao tenant atual (`TenantContext.getRequiredTenantId()`).

## Goals / Non-Goals

**Goals:**
- Expor endpoints REST para criar, listar, buscar, atualizar e deletar provas de um atleta
- Garantir isolamento multi-tenancy via validação do atleta
- Seguir os padrões já estabelecidos no projeto (Controller → Service → Repository, DTOs separados, MapStruct, validação com `@Valid`)

**Non-Goals:**
- Recalcular métricas ou planos ao criar/atualizar uma prova (isso é responsabilidade de outros serviços)
- Lógica de IA ou embeddings relacionados a provas
- Paginação na listagem (MVP usa lista completa, como no `AtletaController`)

## Decisions

### 1. URL aninhada sob `/atleta/{atletaId}/provas`
Provas são entidades subordinadas a um atleta. Usar URL aninhada (`/atleta/{atletaId}/provas`) deixa clara a relação de propriedade e evita que um endpoint de provas precise receber `atletaId` no corpo.
- **Alternativa considerada:** endpoint flat `/provas` com `atletaId` no body — rejeitado por ser menos RESTful e exigir validação extra no DTO.

### 2. Isolamento via atleta (não campo tenant_id direto na Prova)
A entidade `Prova` não possui campo `tenant_id` próprio. O tenant é herdado via `Atleta`. Portanto, o `ProvaServiceImpl` deve:
1. Buscar o `Atleta` pelo `atletaId`
2. Verificar que `atleta.getTenantId()` é igual a `TenantContext.getRequiredTenantId()`
3. Só então prosseguir com a operação
- **Alternativa considerada:** adicionar `tenant_id` à `Prova` — rejeitado por introduzir redundância e necessidade de migration.

### 3. Delete físico (não soft delete)
`Prova` não possui campo de status de exclusão lógica (como `AtletaStatus.INATIVO`). O delete será físico, mantendo consistência com o modelo de dados existente.

### 4. ProvaRepository já existe — não recriar
O `ProvaRepository` já está implementado com os métodos necessários. O serviço usará os métodos existentes: `findByAtletaOrderByDataProvaAsc` para listagem e `findById` para busca individual.

## Risks / Trade-offs

- **Sem paginação na listagem** → um atleta com muitas provas retorna tudo de uma vez. Mitigação: aceitável no MVP; pode ser adicionado depois sem quebrar contrato.
- **Validação de propriedade do atleta no serviço** → adiciona uma query extra para buscar o atleta antes de operar na prova. Mitigação: custo baixo; atletas já são cacheados.
- **Delete físico** → não há histórico de provas deletadas. Mitigação: aceitável para o domínio atual.

## Migration Plan

Nenhuma migration de banco de dados necessária. A tabela `tb_prova` e índices já existem. Apenas novos arquivos Java serão adicionados.
