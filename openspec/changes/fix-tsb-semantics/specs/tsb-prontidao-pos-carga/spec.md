## ADDED Requirements

### Requirement: Calcular e persistir TSB de prontidão (início do dia)
O sistema SHALL calcular o `tsbInicioDia` como a diferença entre CTL e ATL do dia anterior (`ctl_inicio_d - atl_inicio_d`), antes de incorporar o TSS do dia corrente. Este valor representa o estado fisiológico do atleta no início do dia, antes de qualquer carga de treino.

#### Scenario: Dia com treino — TSB início não muda com o treino
- **WHEN** um atleta executa um treino em um dia D
- **THEN** `tsbInicioDia` do dia D deve ser igual a `ctl_fim_{D-1} - atl_fim_{D-1}`
- **THEN** `tsbInicioDia` do dia D NÃO deve incluir o TSS do treino do dia D

#### Scenario: Dia sem treino — TSB início do dia seguinte sobe
- **WHEN** um atleta não executa nenhum treino em um dia D
- **THEN** `atl_fim_D` cai mais que `ctl_fim_D` (pois τ_atl < τ_ctl)
- **THEN** `tsbInicioDia` do dia D+1 é maior que `tsbInicioDia` do dia D

#### Scenario: Primeiro dia sem histórico anterior
- **WHEN** não existe `MetricasDiarias` para o dia anterior
- **THEN** `ctl_inicio = 0`, `atl_inicio = 0`, `tsbInicioDia = 0`
- **THEN** `PlanoMetaDados` SHALL conter flag indicando período de aquecimento

### Requirement: Calcular e persistir TSB pós-carga (fim do dia)
O sistema SHALL calcular `ctlFimDia`, `atlFimDia` e `tsbFimDia` usando o TSS do dia corrente, aplicando a fórmula EMA sobre os valores de início do dia.

#### Scenario: Cálculo correto de CTL e ATL fim do dia
- **WHEN** um atleta executa um treino com TSS = T em um dia D
- **THEN** `ctl_fim_D = T * (1 - exp(-1/τ_ctl)) + ctl_inicio_D * exp(-1/τ_ctl)`
- **THEN** `atl_fim_D = T * (1 - exp(-1/τ_atl)) + atl_inicio_D * exp(-1/τ_atl)`
- **THEN** `tsb_fim_D = ctl_fim_D - atl_fim_D`

#### Scenario: Dia sem treino — CTL e ATL decaem
- **WHEN** TSS do dia D = 0
- **THEN** `ctl_fim_D < ctl_inicio_D` (decaimento EMA sem carga)
- **THEN** `atl_fim_D < atl_inicio_D`

### Requirement: PlanoMetaDados deve expor TSB de prontidão separadamente
O sistema SHALL manter o campo `tsbProntidaoAtual` em `PlanoMetaDados`, populado com o `tsbInicioDia` da métrica diária mais recente do atleta. O campo `tsbAtual` SHALL ser mantido como alias para `tsbProntidaoAtual` durante o período de transição.

#### Scenario: Atualização de PlanoMetaDados após persistência de métricas
- **WHEN** `TsbServiceImpl` persiste métricas de um dia
- **THEN** `PlanoMetaDados.tsbProntidaoAtual` é atualizado com o `tsbInicioDia` do dia calculado
- **THEN** `PlanoMetaDados.tsbAtual` retorna o mesmo valor que `tsbProntidaoAtual`

#### Scenario: tsbPosCargaAtual disponível para analytics
- **WHEN** a API retorna `PlanoMetaDados` de um atleta
- **THEN** `tsbPosCargaAtual` (quando exposto) reflete `tsbFimDia` da métrica mais recente

### Requirement: Consumidores fisiológicos devem usar TSB de prontidão
O sistema SHALL garantir que toda decisão sobre o treino do dia — elegibilidade para intervalado, ajuste de pace, e interpretações de estado fisiológico — use `tsbProntidaoAtual` (TSB antes da carga do dia).

#### Scenario: Elegibilidade para treino intervalado
- **WHEN** `IntervaladoElegibilidadeService` avalia se o atleta pode realizar intervalado
- **THEN** o gate fisiológico usa `metaDados.getTsbProntidaoAtual()`
- **THEN** um atleta com TSB pré-treino abaixo do threshold é considerado fatigado mesmo que o TSS do dia ainda não tenha sido registrado

#### Scenario: Ajuste de pace por fadiga
- **WHEN** `PaceZoneCalculator` calcula ajuste de pace para uma sessão
- **THEN** o fator de ajuste é calculado com base em `tsbProntidaoAtual`
- **THEN** um atleta descansado (TSB pré-treino alto) recebe ajuste positivo de pace

#### Scenario: Interpretação de estado fisiológico em PlanoMetaDados
- **WHEN** `PlanoMetaDados.estaEmFormaIdeal()` ou `estaMuitoFatigado()` é chamado
- **THEN** a avaliação usa `tsbProntidaoAtual`, não o valor pós-carga

### Requirement: Formatação de prompts deve diferenciar TSB prontidão de TSB pós-carga
O sistema SHALL formatar métricas de TSB nos prompts de IA de forma que fique explícito qual valor representa prontidão e qual representa pós-carga.

#### Scenario: Prompt com TSB explícito
- **WHEN** `MetricasPromptFormatter` gera texto de métricas para um atleta
- **THEN** o texto inclui "TSB (Prontidão hoje): X" para `tsbProntidaoAtual`
- **THEN** o texto pode incluir "TSB (Pós-carga): Y" para `tsbFimDia` quando relevante

### Requirement: Recálculo histórico deve partir do primeiro treino do atleta
O sistema SHALL, ao recalcular métricas históricas, iniciar a partir da data do primeiro treino disponível do atleta, em vez de limitar-se a um janela fixa de 3 meses.

#### Scenario: Recálculo com histórico longo
- **WHEN** `TsbServiceImpl.recalcularHistorico()` é executado para um atleta
- **THEN** `determinarDataInicio()` retorna a data do primeiro treino do atleta
- **THEN** o recálculo produz séries de CTL/ATL/TSB estáveis desde o início

#### Scenario: Atleta sem treinos
- **WHEN** `recalcularHistorico()` é chamado para um atleta sem treinos registrados
- **THEN** o sistema NÃO executa o recálculo
- **THEN** nenhuma exceção é lançada

#### Scenario: Consistência pós-recálculo
- **WHEN** o recálculo histórico completo é executado
- **THEN** o `tsbInicioDia` de cada dia D é igual ao `tsb_fim_{D-1}` do dia anterior
- **THEN** não há gaps ou inconsistências na série de métricas

### Requirement: Migração de banco compatível
O sistema SHALL adicionar as novas colunas de métricas (`ctlInicioDia`, `atlInicioDia`, `tsbInicioDia`, `ctlFimDia`, `atlFimDia`, `tsbFimDia`) sem remover as colunas existentes, garantindo rollback seguro.

#### Scenario: Migration Flyway aplicada
- **WHEN** a migration é aplicada a um banco com dados existentes
- **THEN** as novas colunas são adicionadas como nullable
- **THEN** os dados existentes em colunas antigas são preservados intactos

#### Scenario: Rollback da migration
- **WHEN** a migration precisa ser revertida
- **THEN** apenas as novas colunas são removidas
- **THEN** o sistema funciona normalmente com os campos legados

### Requirement: Validação estrutural de `TipoTreino` declarado
O sistema SHALL comparar o `TipoTreino` declarado no `TreinoRealizado` com a estrutura observada (IF médio, variação intra-treino, duração) e sugerir reclassificação sem bloquear a ingestão quando houver divergência.

#### Scenario: REGENERATIVO coerente
- **WHEN** o treino for declarado `REGENERATIVO` e a estrutura observada apresentar IF médio ≤ 0.70 e nenhuma etapa com IF > 0.80
- **THEN** o validator SHALL considerar o tipo coerente e não emitir sugestão

#### Scenario: REGENERATIVO com picos de intensidade
- **WHEN** o treino for declarado `REGENERATIVO` mas tiver etapas com IF > 0.80
- **THEN** o validator SHALL retornar `SugestaoReclassificacao` com `tipoSugerido` coerente com a estrutura (p.ex. `CONTINUO` ou `TEMPO_RUN`), `confianca` entre 0 e 1, e `motivo` textual

#### Scenario: CONTINUO coerente
- **WHEN** o treino for declarado `CONTINUO` e tiver IF médio em `[0.70, 0.90]` com variação intra-treino < 20%
- **THEN** o validator SHALL considerar o tipo coerente

#### Scenario: TEMPO_RUN coerente
- **WHEN** o treino for declarado `TEMPO_RUN` e tiver IF médio em `[0.85, 0.95]` com segmento central dominante acima de 0.85
- **THEN** o validator SHALL considerar o tipo coerente

#### Scenario: Sugestão não bloqueia ingestão
- **WHEN** o validator emitir `SugestaoReclassificacao`
- **THEN** o sistema SHALL persistir o treino com o tipo originalmente declarado e expor `sugestaoReclassificacao` em `TreinoRealizadoOutputDto` (nullable) para decisão do treinador
