# Runbook — `RECONCILIATION_REQUIRED` no auto-cadastro de assessoria

Cobre o único desfecho do auto-cadastro que **exige um humano**. Todos os outros — inclusive falha
com compensação bem-sucedida — se resolvem sozinhos e não geram trabalho.

## O que este estado significa

Um cadastro falhou **e a compensação também falhou**. Existe recurso órfão no Keycloak: uma
organização, um usuário, ou os dois.

O que **não** significa:

- Não é o mesmo que `FAILED`. `FAILED` é o caminho normal de erro: a compensação limpou tudo e nada
  ficou para trás. `FAILED` é rotina e não pede ação.
- Não há conta utilizável pendurada. O usuário órfão nasce com `VERIFY_EMAIL` pendente e nunca
  recebeu o e-mail — ele não conclui login.

O risco real é outro e é silencioso: **o slug e o e-mail podem ficar presos**. Se a organização
sobreviveu no Keycloak, o mesmo `alias` não pode ser recriado, e o coach que tentar de novo com o
mesmo identificador recebe erro sem entender por quê.

## Como o alerta chega

Métrica: `signup_coach_total{desfecho="reconciliacao_necessaria"}`.

Qualquer incremento merece olhar — o volume esperado é **zero**. Sugestão de regra:

```promql
increase(signup_coach_total{desfecho="reconciliacao_necessaria"}[1h]) > 0
```

O log correspondente sai em `ERROR` com `correlationId` e `signupStatus=compensando`.

⚠️ **Não confundir com `desfecho="limite_teto_global"`**, que também sai em `ERROR`. Aquele é o teto
diário de cadastros — abuso em curso ou crescimento real, e a ação é outra (ver `application.yml`,
bloco `app.coach-signup`).

## Diagnóstico

```sql
SELECT id, correlation_id, email, slug, status,
       assessoria_id, keycloak_organization_id, keycloak_user_id,
       error_detail, created_at
  FROM tb_signup_provisioning
 WHERE status = 'RECONCILIATION_REQUIRED'
 ORDER BY created_at;
```

`error_detail` traz a falha original **e** a falha da compensação, separadas por `|`. Nenhuma das
duas contém senha ou token — o resumo é só tipo e mensagem da exceção.

Leia as colunas de id como um mapa do que sobrou:

| Coluna preenchida | Recurso que pode ter ficado órfão |
|---|---|
| `assessoria_id` **não nulo** | a `Assessoria` local **chegou a existir** — pode ou não estar lá |
| `keycloak_organization_id` | organização no Keycloak |
| `keycloak_user_id` | usuário no Keycloak |

⚠️ **Nenhuma coluna de id prova existência — todas provam apenas criação.** Desde a **V76** a
coluna `assessoria_id` não tem FK, então nada a zera quando a assessoria é apagada: o id
permanece de propósito, como perícia. Antes da V76 ela era `ON DELETE SET NULL` e o nulo era
ambíguo ("apagada **ou** nunca criada"); agora a ambiguidade some do outro lado — preenchido
significa "existiu", e só a consulta abaixo diz se ainda existe.

```sql
-- Pelo id (preferível: imune a reuso do slug por outro cadastro posterior)
SELECT id, nome, dominio FROM tb_assessoria WHERE id = '<assessoria_id>';

-- Pelo slug, quando assessoria_id estiver nulo (falha antes de criar a assessoria)
SELECT id, nome, dominio FROM tb_assessoria WHERE dominio = '<slug-da-linha>';
```

📌 **Por que a FK saiu (V76).** Ela derrubava justamente a linha que registra a falha: o Postgres
zerava a coluna no `DELETE` da compensação, mas a entidade em memória seguia com o id antigo, e o
`UPDATE` que grava o desfecho reescrevia a referência pendurada. Efeito: o rastro **congelava no
passo anterior** e nunca chegava a `FAILED` nem a `RECONCILIATION_REQUIRED` — ou seja, a fila deste
runbook ficava cega. Coberto por `CoachSignupCompensacaoIT`.

## Correção

A ordem importa: **remova no Keycloak antes de apagar a assessoria local.** Ao contrário, você perde
o `tenant_id` que identifica a organização.

```bash
B=http://192.168.15.24:8080      # HomeLab; no Railway use o host correspondente
T=$(curl -s -d "client_id=admin-cli" -d "username=admin" -d "password=$KC_ADMIN_PASSWORD" \
     -d "grant_type=password" "$B/realms/master/protocol/openid-connect/token" \
     | python3 -c "import json,sys; print(json.load(sys.stdin)['access_token'])")

# 1. Confirme que o recurso existe antes de remover
curl -s -H "Authorization: Bearer $T" "$B/admin/realms/menthoros/users/<keycloak_user_id>" | head -c 300
curl -s -H "Authorization: Bearer $T" "$B/admin/realms/menthoros/organizations/<keycloak_organization_id>" | head -c 300

# 2. Remova na ordem inversa da criação — usuário, depois organização
curl -s -o /dev/null -w "DELETE user -> %{http_code}\n" -X DELETE \
  "$B/admin/realms/menthoros/users/<keycloak_user_id>" -H "Authorization: Bearer $T"
curl -s -o /dev/null -w "DELETE org  -> %{http_code}\n" -X DELETE \
  "$B/admin/realms/menthoros/organizations/<keycloak_organization_id>" -H "Authorization: Bearer $T"
```

`404` nas remoções é **sucesso**: o recurso já não estava lá.

Depois, no banco — apagar a assessoria é o que devolve o slug ao pool:

```sql
-- Só se a assessoria existir E não tiver usuários/atletas vinculados.
-- Se tiver, PARE: o cadastro avançou mais do que o rastro indica, e apagar perde dado.
SELECT (SELECT count(*) FROM tb_usuario WHERE tenant_id = '<assessoria_id>') AS usuarios,
       (SELECT count(*) FROM tb_atleta  WHERE tenant_id = '<assessoria_id>') AS atletas;

DELETE FROM tb_assessoria WHERE id = '<assessoria_id>';

-- Fecha o rastro. Mantém a linha: ela é o histórico do incidente.
UPDATE tb_signup_provisioning
   SET status = 'FAILED',
       error_detail = error_detail || ' | reconciliado manualmente em ' || NOW(),
       updated_at = NOW()
 WHERE id = '<id-da-linha>';
```

**Não apague a linha de `tb_signup_provisioning`.** Ela é o único registro de que o incidente
aconteceu, e o índice parcial que a varredura usa só enxerga `RECONCILIATION_REQUIRED` — mudar o
status já a tira da fila.

## Desligar o cadastro (kill switch)

`COACH_SIGNUP_ENABLED=false` faz o endpoint responder **404** — não 503, para não anunciar a um
scanner um provisionamento público que ainda não foi lançado. Nada já criado é afetado.

⚠️ **Exige reiniciar o serviço.** A propriedade é `@ConfigurationProperties` sem `@RefreshScope`:
o valor é lido no boot, não por request. Mudar a variável e não reiniciar não desliga nada. No
Railway é um restart do serviço — não um deploy de código, e não precisa reverter commit.

Confirme que caiu antes de considerar o incidente contido — e **use a sonda abaixo, não um corpo
vazio**:

```bash
# Corpo VÁLIDO com o honeypot (`website`) preenchido. Com a flag ligada o serviço responde 201 e
# NÃO cria nada — é a resposta que ele dá a bot. Com a flag desligada, 404 antes de tudo.
curl -s -o /dev/null -w "%{http_code}\n" -X POST "$API/api/public/coach-signups" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: probe-$RANDOM" \
  -d '{"nome":"Probe","email":"probe@menthoros.test","senha":"Senha#Forte#2026",
       "nomeAssessoria":"Probe","slug":"probe-kill-switch","website":"http://bot.example"}'
# 404 = desligado · 201 = LIGADO
```

⚠️ **Não sonde com `-d '{}'`.** A validação do corpo (`@Valid`) roda **antes** do teste da flag,
então um corpo inválido devolve `400` com a flag ligada **ou** desligada — a resposta não
discrimina, e quem sondar assim vai concluir "ligado" nas duas situações. Verificado em 2026-08-11:
a versão anterior desta seção prescrevia exatamente essa sonda inútil.

O honeypot é o que torna a sonda segura de repetir: ele é o único caminho que responde sucesso sem
tocar em Keycloak nem no banco (confirmado — nenhuma linha em `tb_assessoria` ou
`tb_signup_provisioning` com o slug da sonda).

## Lacuna conhecida: desfazer um cadastro que **deu certo**

Este runbook cobre `RECONCILIATION_REQUIRED`, onde o `Usuario` local não chegou a existir. A
checagem antes do `DELETE` da assessoria reflete isso — se houver usuário ou atleta vinculado, ela
manda **PARAR**, porque nesse cenário significa que o cadastro avançou mais do que o rastro indica.

Ao executar a limpeza de 2026-08-11 (resíduos de QA no HomeLab), o guard disparou **corretamente**:
os cadastros tinham concluído, então o coach existia por construção. Não há procedimento escrito
para esse caso, e ele não é hipotético — é o mesmo caminho de uma **exclusão de conta a pedido do
titular (LGPD)**.

O que foi feito na limpeza, na ordem inversa da criação, e que serve de rascunho:

1. Keycloak: `DELETE` do usuário, depois da organização (`404` também é sucesso).
2. Banco: `DELETE FROM tb_usuario WHERE tenant_id = '<id>'`, depois `DELETE FROM tb_assessoria`.
3. Rastro: **anotar**, não reescrever o status. Marcar `FAILED` mentiria — o cadastro concluiu; o
   que houve depois foi remoção deliberada. O schema não tem estado para isso.

Formalizar vira change própria: envolve dado do atleta, retenção e prazo legal, nada que caiba num
apêndice de runbook.

## Por que não há retry automático

Decisão do ADR-0009. A compensação falha porque o Keycloak acabou de falhar; insistir dentro do
request do usuário troca **um recurso órfão registrado** por **um loop sob pressão**, exatamente
quando o sistema já está degradado. O rastro registrado é o que torna a correção possível depois —
e ela é barata, porque o volume esperado é zero.

Se este runbook passar a ser executado com frequência, o problema não é a falta de retry: é a
estabilidade da integração com o Keycloak, e é lá que se deve olhar.
