## ADDED Requirements

### Requirement: Espaçamento entre sessões-chave
O sistema SHALL garantir no mínimo 1 dia de treino fácil (ou dia off) entre quaisquer duas sessões classificadas como sessão-chave na mesma semana.

#### Scenario: LONGO após INTERVALADO
- **WHEN** a semana tiver um INTERVALADO e um LONGO
- **THEN** SHALL haver ao menos 1 dia entre eles com sessão de recuperação, corrida fácil ou dia off

#### Scenario: Duas sessões de qualidade consecutivas
- **WHEN** a distribuição proposta colocar duas sessões-chave em dias consecutivos
- **THEN** o sistema SHALL reordenar os dias até que a regra de espaçamento mínimo seja respeitada

#### Scenario: Semana com três sessões-chave
- **WHEN** a semana tiver LONGO, INTERVALADO e TEMPO_RUN
- **THEN** o sistema SHALL intercalá-las de forma que nenhuma fique adjacente a outra sessão-chave

---

### Requirement: Alinhamento com disponibilidade do atleta
O sistema SHALL priorizar sessões longas/intensas nos dias com maior `disponibilidadeMinutos` cadastrada no perfil do atleta.

#### Scenario: Longo no dia de maior disponibilidade
- **WHEN** o atleta tiver disponibilidade maior no sábado ou domingo
- **THEN** o LONGO SHALL ser alocado preferencialmente em um desses dias, respeitando espaçamento

#### Scenario: Sem disponibilidade cadastrada
- **WHEN** o atleta não tiver `disponibilidadeSemanal` cadastrada
- **THEN** o sistema SHALL aplicar defaults (`seg-sex=60min`, `sab=120min`, `dom=180min`) e registrar log WARN

#### Scenario: Duração de sessão excede disponibilidade
- **WHEN** a `duracaoPrevistaMin` de uma sessão-chave exceder `disponibilidadeMinutos` de qualquer dia disponível
- **THEN** o sistema SHALL escolher o dia com maior disponibilidade e registrar no `rebalanceamentoLog` o aviso de excedente

---

### Requirement: Padrão hard/easy
O sistema SHALL evitar dois dias consecutivos com `nivelEsforco` ≥ ALTO.

#### Scenario: Dias de esforço alto consecutivos
- **WHEN** a distribuição proposta tiver dois dias consecutivos com `nivelEsforco=ALTO` ou `MUITO_ALTO`
- **THEN** o sistema SHALL reordenar para intercalar ao menos um dia com `nivelEsforco` ≤ MEDIO

#### Scenario: Recuperação após sessão-chave
- **WHEN** o dia anterior for sessão-chave
- **THEN** o dia seguinte SHALL ser recuperação ativa, corrida fácil ou off

---

### Requirement: Rebalanceamento sob demanda
O sistema SHALL permitir rebalancear uma `PlanoSemanal` existente sem regenerar todo o plano.

#### Scenario: Rebalanceamento mantém estrutura
- **WHEN** o endpoint de rebalanceamento for chamado
- **THEN** o sistema SHALL manter a lista de sessões (quantidade e tipos) e alterar apenas a distribuição dos dias

#### Scenario: Log de movimentos
- **WHEN** o rebalanceamento alterar algum dia
- **THEN** o sistema SHALL retornar `RebalanceamentoResultadoDto` contendo `movimentos` com `sessaoId`, `diaAntes`, `diaDepois`, `motivo`

#### Scenario: Persistência do log
- **WHEN** o rebalanceamento ocorrer em tempo de geração do plano
- **THEN** o sistema SHALL persistir `rebalanceamentoLog` na `PlanoSemanal` para auditoria futura
