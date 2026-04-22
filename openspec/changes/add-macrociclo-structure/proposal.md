## Why

Hoje o Menthoros gera planos semanais de forma contínua a partir do estado atual do atleta, mas não estrutura a progressão ao longo de um ciclo de 12 a 24 semanas com fases clássicas (base, específica, pico, taper, transição). O resultado é que a prescrição tende a ser "homogênea no tempo": cada semana parece derivar só da anterior, e o LLM não tem visibilidade de onde o atleta está dentro do macrociclo.

Introduzir uma estrutura explícita de macrociclo com mesociclos e fases determinísticas dá ao motor e ao LLM uma ancoragem temporal e pedagógica — permite dizer "estou na 3ª semana do bloco específico" e prescrever de forma coerente com periodização moderna, tornando o produto mais próximo de um treinador de alto nível e menos de um gerador genérico de planos.

## What Changes

- **Nova entidade `Macrociclo`**: contêiner temporal ancorado em uma ou mais `Prova`s alvo, com `inicio`, `fim`, `provaPrincipal`, `provaSecundaria` (opcional), `estruturaBlocos`
- **Nova entidade `Mesociclo`**: blocos dentro do macrociclo (tipicamente 3–4 semanas), com `fase` (BASE, ESPECIFICO, PICO, TAPER, TRANSICAO), `inicio`, `fim`, `objetivoCarga`, `destaquesPedagogicos`
- **Novo serviço `MacrocicloService`**: monta macrociclo a partir da prova-alvo e das datas; distribui mesociclos com proporções determinísticas (ex: 40% base, 30% específico, 15% pico, 10% taper, 5% transição)
- **Integração com `PlanoSemanalService`**: ao gerar semana, consulta mesociclo vigente para herdar `fase` e `objetivoCarga`
- **Integração com `PlanoTreinoPromptBuilder`**: contexto passa a incluir `mesocicloAtual` (fase, semana N de M, objetivo), o que melhora coerência narrativa do plano gerado
- **Integração com `progressao-treinos`**: a estratégia de progressão já especificada passa a respeitar os limites do mesociclo (ex: dentro de fase BASE, progressão é mais agressiva de volume; em PICO, mais intensidade)
- **Migration Flyway**: tabelas `tb_macrociclo` e `tb_mesociclo` com índices

## Capabilities

### New Capabilities

- `macrociclo-structure`: representação explícita de macrociclo e mesociclos com fases determinísticas, usada como ancoragem temporal para prescrição e contexto do LLM.

### Modified Capabilities

<!-- Complementa `progressao-treinos` mas sem modificar seus requisitos; a dependência é declarada em design.md. -->

## Impact

**Entidades e banco:**
- Novas tabelas:
  - `tb_macrociclo` (ID, atleta_id, prova_principal_id, prova_secundaria_id, inicio, fim, objetivo_texto, tenant_id, created_at)
  - `tb_mesociclo` (ID, macrociclo_id, fase, inicio, fim, ordem, objetivo_carga, destaques, tenant_id)
- Índices `(atleta_id, inicio)` em macrociclo, `(macrociclo_id, ordem)` em mesociclo
- Novo enum `FaseMesociclo` com valores `BASE`, `ESPECIFICO`, `PICO`, `TAPER`, `TRANSICAO`

**APIs:**
- `POST /api/macrociclos?atletaId=X` — gera macrociclo a partir de prova-alvo
- `GET /api/macrociclos/{id}` — retorna estrutura completa com mesociclos
- `GET /api/atletas/{atletaId}/macrociclo/atual` — retorna macrociclo vigente
- `PUT /api/mesociclos/{id}` — permite ao treinador editar fase/destaques
- Sem breaking changes

**Regras determinísticas para composição:**
- Duração total determinada pela distância: meia-maratona = mínimo 12 semanas, maratona = mínimo 16 semanas, 10 km = mínimo 8 semanas
- Proporção padrão (configurável): BASE 40%, ESPECIFICO 30%, PICO 15%, TAPER 10%, TRANSICAO 5%
- `TAPER` do mesociclo SHALL coincidir com janela de `PeriodoTaper` calculada por `TaperService` (se change `add-taper-guidance` estiver ativa)
- Fases são cronologicamente ordenadas: BASE → ESPECIFICO → PICO → TAPER → TRANSICAO

**Integração com LLM:**
- Contexto inclui `mesocicloAtual: { fase, semanaNdeM, objetivoCarga, destaques }` e `macrocicloProgresso: { semanaAtual, totalSemanas }`

**Dependências:**
- Depende (leve) de `progressao-treinos` estar consciente de fase para evitar dupla modulação de volume
- Pode opcionalmente integrar com `add-taper-guidance` para evitar duplicação do cálculo de taper
- Não depende de `strava-integration` — funciona mesmo sem histórico de Strava

## Referências técnicas

- **Bompa, T. & Buzzichelli, C. (2019)** — "Periodization: Theory and Methodology of Training"
- **Issurin, V. (2010)** — "New horizons for the methodology and physiology of training periodization" (block periodization)
- **Pfitzinger, P. & Douglas, S. (2009)** — "Advanced Marathoning" (estrutura de 12/18 semanas)
