## ADDED Requirements

### Requirement: Consolidar histórico real do atleta em janelas de 7, 21 e 42 dias
O sistema SHALL calcular um `ProgressaoHistoricoResumo` a partir dos `TreinoRealizado` e `MetricasDiarias` do atleta nas últimas janelas de 7, 21 e 42 dias, incluindo: volume total e médio semanal, TSS médio semanal, aderência (treinos concluídos / planejados), longões concluídos, RPE médio dos treinos duros, CTL/ATL/TSB atuais e `semanasProgressaoContinua`.

#### Scenario: Atleta com histórico completo nos últimos 42 dias
- **WHEN** `ProgressaoTreinoService.calcularHistorico(atletaId)` é chamado para um atleta com treinos realizados nos últimos 42 dias
- **THEN** o serviço retorna um `ProgressaoHistoricoResumo` com `volume21dMedioSemanal > 0`, `aderencia21d` entre 0.0 e 1.0, e `treinosConcluidos21d >= 0`

#### Scenario: Atleta sem treinos nos últimos 42 dias (novo atleta)
- **WHEN** `calcularHistorico(atletaId)` é chamado para atleta sem nenhum `TreinoRealizado` nos últimos 42 dias
- **THEN** o serviço retorna um `ProgressaoHistoricoResumo` com todos os campos de volume e contagem zerados, sem lançar exceção

### Requirement: Produzir DecisaoProgressao com estado e envelope seguro
O sistema SHALL analisar o `ProgressaoHistoricoResumo` e as métricas TSB/rampRate do atleta para produzir uma `DecisaoProgressao` com estado `PROGREDIR`, `PROGREDIR_LEVE`, `MANTER` ou `REDUZIR`, ajuste percentual de volume, ajuste de longão em minutos, flag de permissão de progressão de intensidade e motivo textual.

#### Scenario: Atleta consistente com fadiga baixa deve progredir
- **WHEN** `calcularDecisao(resumo)` é chamado com atleta tendo aderência >= 80%, >= 2 longões concluídos nos últimos 21 dias, RPE médio dos treinos duros <= 7.5 e TSB > -15
- **THEN** a decisão retorna `estado = PROGREDIR`, `ajusteVolumePercentual` entre 0.05 e 0.08, `ajusteLongoMinutos` entre 10 e 15, e `permitirProgressaoIntensidade = true`

#### Scenario: Atleta com fadiga moderada deve progredir levemente
- **WHEN** `calcularDecisao(resumo)` é chamado com aderência >= 70%, mas TSB entre -15 e -22 ou RPE médio entre 7.5 e 8.5
- **THEN** a decisão retorna `estado = PROGREDIR_LEVE`, `ajusteVolumePercentual` entre 0.02 e 0.04, e `permitirProgressaoIntensidade = false`

#### Scenario: Atleta com histórico misto deve manter carga
- **WHEN** `calcularDecisao(resumo)` é chamado com aderência entre 60% e 70% ou longão oscilante (desvio > 20% da média) ou treinos-chave incompletos
- **THEN** a decisão retorna `estado = MANTER`, `ajusteVolumePercentual = 0.0`, `ajusteLongoMinutos = 0`, e `permitirProgressaoIntensidade = false`

#### Scenario: Atleta sobrecarregado ou com aderência muito baixa deve reduzir
- **WHEN** `calcularDecisao(resumo)` é chamado com aderência < 60%, TSB < -22, ou RPE médio dos treinos duros > 8.5 nos últimos 7 dias
- **THEN** a decisão retorna `estado = REDUZIR`, `ajusteVolumePercentual` entre -0.25 e -0.10, `ajusteLongoMinutos` entre -20 e -10, e `permitirProgressaoIntensidade = false`

#### Scenario: Atleta com histórico insuficiente (menos de 3 treinos em 21 dias) — fallback seguro
- **WHEN** `calcularDecisao(resumo)` é chamado com `treinosConcluidos21d < 3`
- **THEN** a decisão retorna `estado = MANTER`, motivo indicando "histórico insuficiente", e sem lançar exceção

### Requirement: Integrar DecisaoProgressao no fluxo de geração de plano
O sistema SHALL calcular a `DecisaoProgressao` antes de chamar a IA e passar o resultado como contexto para o `PeriodizacaoPromptFormatter`, que SHALL incluir no prompt: o estado de progressão, o ajuste máximo de volume permitido, a instrução sobre o longão e o motivo.

#### Scenario: Prompt inclui bloco de progressão quando DecisaoProgressao está disponível
- **WHEN** `PlanoServiceImpl` gera um plano para atleta com `DecisaoProgressao` calculada
- **THEN** o prompt enviado ao LLM contém uma seção explícita de progressão com estado (ex: "MANTER"), ajuste de volume (ex: "0%"), instrução sobre longão e motivo

#### Scenario: Prompt indica redução quando estado é REDUZIR
- **WHEN** a `DecisaoProgressao` retorna `estado = REDUZIR` com `ajusteVolumePercentual = -0.15`
- **THEN** o prompt instrui o LLM a reduzir o volume em aproximadamente 15% e não progredir intensidade

#### Scenario: Falha no cálculo de progressão não bloqueia geração do plano
- **WHEN** `ProgressaoTreinoService.calcularDecisao` lança uma exceção inesperada
- **THEN** `PlanoServiceImpl` loga o erro e continua a geração sem `DecisaoProgressao` no prompt (fallback gracioso)

### Requirement: Ampliar janela de histórico de treinos no fluxo de geração para 42 dias
O sistema SHALL buscar os treinos realizados dos últimos 42 dias ao preparar os dados para geração de plano, em vez de limitar a 7 treinos fixos.

#### Scenario: Dados do plano incluem treinos dos últimos 42 dias
- **WHEN** `PlanoServiceImpl.prepararDadosPlano(atletaId)` é chamado para atleta com treinos nos últimos 42 dias
- **THEN** `DadosPlanoDto.ultimosTreinos()` contém treinos de até 42 dias atrás, ordenados por data decrescente

#### Scenario: Atleta com poucos treinos recentes não falha
- **WHEN** `prepararDadosPlano(atletaId)` é chamado para atleta com apenas 2 treinos nos últimos 42 dias
- **THEN** `DadosPlanoDto.ultimosTreinos()` retorna lista com 2 elementos sem erro
