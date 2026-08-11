---
status: accepted
---

# Provisionamento do auto-cadastro sem transação entre Postgres e Keycloak

O auto-cadastro público de assessoria (`keycloak-user-onboarding-auth`) cria recursos em **dois
sistemas que não compartilham transação**: a `Assessoria` e o `Usuario` no Postgres, e a organização
e o usuário no Keycloak. `@Transactional` não alcança o Keycloak — um `rollback` local não desfaz
nada do lado do provedor de identidade.

O modo de falha que importa não é "o cadastro falhou". É **conta que existe no Keycloak sem tenant
local**: o coach autentica com sucesso, entra, e encontra um produto quebrado. Isso é pior que falhar,
porque falhar é visível e recuperável — a conta órfã não é nem uma coisa nem outra.

**Decisão 1 — a ordem de criação não é escolha de design, é imposição do modelo.** `Usuario.id` **é**
o `sub` do JWT do Keycloak (`Usuario.java`, `UsuarioSyncServiceImpl` faz `UUID.fromString(keycloakId)`).
O usuário local não pode existir antes do usuário no Keycloak, porque sua chave primária vem de lá.
A sequência é `Assessoria → organização → usuário no Keycloak → Usuario local`, e a compensação é o
inverso literal. Quem tentar criar o `Usuario` antes descobre isso como violação de chave, não como
decisão.

**Decisão 2 — o usuário nasce desabilitado e só é habilitado depois do verify-email sair.** A ordem
oposta — habilitar e então disparar o e-mail — deixa, quando o envio falha, um usuário habilitado que
nunca recebe a confirmação. Ele consegue existir e não consegue ser confirmado, e ninguém é avisado.
Com o envio primeiro, falha de SMTP compensa o cadastro inteiro e o coach tenta de novo, que é um
desfecho ruim mas honesto.

**Decisão 3 — a compensação apaga a `Assessoria`; não a marca como falha.** O slug é reservado pela
constraint `UNIQUE` que já existe em `tb_assessoria.dominio` — não há campo novo. Uma linha mantida
"marcada como falha" **conserva o `dominio`** e prende o slug pela mesma constraint: o coach que
falhasse uma vez perderia o nome da própria assessoria para sempre. Apagar libera o slug sem índice
parcial, sem sufixo no domínio e sem coluna de estado numa tabela madura. O rastro não se perde
porque vive em `tb_signup_provisioning`, que é tabela separada exatamente por isso e sobrevive ao
`DELETE` (`ON DELETE SET NULL`).

**Decisão 4 — o ciclo de vida do cadastro mora em uma tabela só.** `tb_signup_provisioning` guarda
`status` (`PENDING → ASSESSORIA_CREATED → ORG_CREATED → USER_CREATED → COMPLETED`, mais `FAILED` e
`RECONCILIATION_REQUIRED`), o `request_hash` e o `resultado` da primeira execução. `tb_assessoria`
**não** ganha estado — mantém o `ativo` booleano que já tem. Sem isso, "em que pé está este cadastro"
teria duas respostas possíveis em dois lugares.

**Decisão 5 — quando a compensação falha, registra-se em vez de insistir.** Persistir
`RECONCILIATION_REQUIRED` com `correlation_id` e os IDs externos, sem senha e sem token. O
`assessoria_id` entra **quando já existir**; falha anterior ao passo 1 não tem tenant a registrar, e
aí o `correlation_id` é o que amarra o rastro. Retry automático de compensação numa falha que já
demonstrou instabilidade tende a multiplicar o dano — a reconciliação é operação deliberada, com
runbook, não laço.

## Consequências

O cadastro fica **mais lento e mais chato de implementar** do que um `@Transactional` daria a
entender, e cada ponto de falha precisa de teste próprio. Em troca, nenhum caminho de falha produz
conta utilizável sem tenant.

A auditoria de tentativas fracassadas passa a depender inteiramente de `tb_signup_provisioning`. Se
essa tabela for limpa por retenção, o histórico vai junto — decisão consciente, não descuido.

Reaproveitar `AssessoriaServiceImpl.criarAssessoria` fica **vetado** neste fluxo: ele cria a
organização e não compensa se a persistência seguinte falhar. É aceitável no cadastro administrativo,
onde alguém percebe e corrige; não no público, onde o resíduo fica órfão sem plateia.
