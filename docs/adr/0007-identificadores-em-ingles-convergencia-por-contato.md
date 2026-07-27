# Identificadores em inglês — convergência por contato, não por mutirão

A base nasceu bilíngue sem que ninguém decidisse isso. Hoje há **369 campos distintos em 31 entidades**, com maioria em português (`aceiteLgpd`, `alturaCm`, `alertaSobrecarga`, `atlAtual`, `adicionadoPeloCoach`), mas com ilhas de inglês que apareceram sempre que o termo técnico não tinha tradução natural (`recommendationType`, `analyzedAt`, `asaasCustomerId`). A `RevisaoSemanal` levou a inconsistência ao limite: `recommendationType` e `adherenceStatus` em inglês convivendo com `dadosSuficientes` e `percentualRealizacao`, **na mesma entidade**, criada na mesma change.

Decidimos que **identificador novo — campo, enum, DTO, coluna, tipo — nasce em inglês**, e que campo legado em português é normalizado **apenas quando a change já está modificando aquela entidade por outro motivo**, com migration de rename e PR coordenado nos repos afetados. Idioma de resposta, comentários, JavaDoc e mensagens de commit permanece PT-BR — a decisão é sobre identificadores, não sobre comunicação. O glossário (`CONTEXT.md`) segue em PT-BR, com o identificador do código entre parênteses.

As alternativas eram reais e foram pesadas:

**Só daqui pra frente**, sem nunca tocar no legado, foi rejeitada por deixar entidades como a `RevisaoSemanal` permanentemente incoerentes mesmo quando estão abertas na mesa — o custo marginal de renomear dois campos numa entidade que já vai receber migration é próximo de zero, e não aproveitar isso condena a base a nunca convergir.

**Renomeação global** (os 369 campos, migrations de rename em todas as tabelas, contrato do front refeito) foi rejeitada por ser uma change de porte XL que congelaria os dois repositórios, com risco de migration sobre dado vivo em toda tabela, para um ganho que é de legibilidade — nome de campo não muda comportamento. O `CLAUDE.md` da raiz já proíbe refactor fora do escopo da task; uma renomeação global é essa proibição elevada à máxima potência.

A cláusula "já está modificando por outro motivo" é o coração da decisão. Sem ela, a regra ou bloqueia a normalização oportuna (e a base nunca converge), ou vira licença para refactor em qualquer arquivo que alguém abriu (e toda change vira um mutirão de renomeação). Ela também é o que autoriza o primeiro caso concreto: `add-weekly-review-llm-focus` já adiciona colunas na `RevisaoSemanal`, então normaliza `dadosSuficientes`→`sufficientData` e `percentualRealizacao`→`completionRate` no mesmo movimento.

Consequências:

- A base fica **bilíngue por tempo indefinido, de propósito**. Entidades tocadas com frequência convergem rápido; entidades estáveis ficam em português talvez para sempre. Isso é resultado esperado, não dívida a ser cobrada depois — quem encontrar um campo PT numa entidade que a task não toca deve deixá-lo em paz.
- Renomeação de campo de contrato **quebra o front** e exige PR coordenado nos dois repos. Não há alias de compatibilidade por padrão: descartamos tanto o `@JsonProperty` duplo (sujaria justamente a entidade sendo limpa) quanto a tolerância dupla no adapter do front (dívida que alguém precisa lembrar de apagar). O custo aceito é uma janela curta, entre os dois merges, com o campo chegando `undefined` — tolerável enquanto o produto está pré-lançamento, e a razão pela qual essa decisão deve ser revisitada se a renomeação de contrato voltar a acontecer depois de haver clientes em produção.
- Migration de rename sobre dado vivo entra na lista de operações que exigem confirmação explícita do founder (`CLAUDE.md` da raiz), como qualquer migration destrutiva.
