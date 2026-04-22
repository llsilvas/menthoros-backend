## 1. DTOs e Enum de Progressão

- [ ] 1.1 Criar enum `EstadoProgressao` em `enums/` com valores `PROGREDIR`, `PROGREDIR_LEVE`, `MANTER`, `REDUZIR`
- [ ] 1.2 Criar record `ProgressaoHistoricoResumo` em `dto/` com campos de volume (7d/21d/42d), TSS, aderência, longões, RPE, CTL/ATL/TSB e semanasProgressaoContinua
- [ ] 1.3 Criar record `DecisaoProgressao` em `dto/` com estado, ajusteVolumePercentual, ajusteLongoMinutos, permitirProgressaoIntensidade e motivo

## 2. ProgressaoTreinoService

- [ ] 2.1 Criar interface `ProgressaoTreinoService` com métodos `calcularHistorico(UUID atletaId)` e `calcularDecisao(ProgressaoHistoricoResumo resumo)`
- [ ] 2.2 Criar `ProgressaoTreinoServiceImpl` com injeção de `TreinoRealizadoRepository` e `PlanoMetadadosService`
- [ ] 2.3 Implementar `calcularHistorico`: buscar treinos dos últimos 42 dias via `findByAtletaAndDataTreinoGreaterThanEqualOrderByDataTreinoDesc`, calcular janelas de 7/21/42 dias
- [ ] 2.4 Implementar cálculo de aderência 21d: `treinosConcluidos21d / treinosPlanejados21d` (0.0 quando planejados = 0)
- [ ] 2.5 Implementar identificação de longões: maior treino da semana ou treino com duração >= threshold em `MetricasThresholds`
- [ ] 2.6 Implementar cálculo de RPE médio dos treinos duros (tipo `INTERVALADO` ou `TEMPO`), com fallback gracioso quando campo ausente
- [ ] 2.7 Implementar `calcularDecisao`: aplicar regras de `PROGREDIR`, `PROGREDIR_LEVE`, `MANTER`, `REDUZIR` conforme spec; incorporar `calcularProgressaoSegura` como teto fisiológico
- [ ] 2.8 Garantir fallback `MANTER` quando `treinosConcluidos21d < 3` ou dados insuficientes

## 3. Testes Unitários do Serviço

- [ ] 3.1 Testar `calcularHistorico` com atleta com histórico completo (42 dias de treinos)
- [ ] 3.2 Testar `calcularHistorico` com atleta sem treinos (novo atleta) — sem exceção, campos zerados
- [ ] 3.3 Testar `calcularDecisao` → `PROGREDIR` (aderência >= 80%, 2+ longões, RPE <= 7.5, TSB > -15)
- [ ] 3.4 Testar `calcularDecisao` → `PROGREDIR_LEVE` (aderência >= 70%, TSB entre -15 e -22)
- [ ] 3.5 Testar `calcularDecisao` → `MANTER` (aderência entre 60-70% ou longão oscilante)
- [ ] 3.6 Testar `calcularDecisao` → `REDUZIR` (aderência < 60%, TSB < -22, ou RPE > 8.5)
- [ ] 3.7 Testar fallback `MANTER` com histórico insuficiente (< 3 treinos em 21 dias)
- [ ] 3.8 Testar que semana boa isolada não vence histórico ruim de 42 dias

## 4. Ampliar Histórico no Fluxo de Geração

- [ ] 4.1 Em `PlanoServiceImpl.prepararDadosPlano`, substituir `LIMITE_TREINOS_HISTORICO = 7` por busca de treinos dos últimos 42 dias usando `LocalDate.now().minusDays(42)`
- [ ] 4.2 Verificar que `DadosPlanoDto.ultimosTreinos()` continua com o mesmo tipo de lista (sem breaking change de contrato)

## 5. Integração no Fluxo de Geração de Plano

- [ ] 5.1 Em `PlanoServiceImpl`, chamar `progressaoTreinoService.calcularHistorico` e `calcularDecisao` antes da chamada à IA
- [ ] 5.2 Tratar exceção em `calcularDecisao` com try-catch: logar erro e continuar geração sem `DecisaoProgressao` (fallback gracioso)
- [ ] 5.3 Passar `DecisaoProgressao` para `PeriodizacaoPromptFormatter` (ajustar assinatura do método de formatação relevante ou adicionar novo método)

## 6. Atualizar PeriodizacaoPromptFormatter

- [ ] 6.1 Adicionar método (ou sobrecarga) que recebe `DecisaoProgressao` e a inclui no bloco de periodização do prompt
- [ ] 6.2 Incluir no prompt: estado de progressão, ajuste máximo de volume (% formatado), instrução sobre longão e motivo
- [ ] 6.3 Garantir que quando `DecisaoProgressao` é `null` (fallback), o formatter funciona como antes sem erro

## 7. Testes de Integração

- [ ] 7.1 Testar que geração de plano para atleta consistente (mock de histórico) inclui estado `PROGREDIR` no prompt
- [ ] 7.2 Testar que geração de plano para atleta com TSB muito negativo inclui estado `REDUZIR` no prompt
- [ ] 7.3 Testar que falha no `ProgressaoTreinoService` não impede geração do plano (plano gerado sem bloco de progressão)
