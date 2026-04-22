## Why

A lógica atual de progressão de treinos baseia-se apenas em um contador de semanas de aumento de volume (`semanasProgressaoContinua`), sem considerar aderência, qualidade de execução, longões realizados, RPE ou resposta real do atleta nos últimos ciclos. Isso impede que a IA receba um envelope técnico confiável e leva a planos que podem ignorar sinais claros de fadiga ou subutilizar um atleta bem adaptado.

## What Changes

- **Novo serviço** `ProgressaoTreinoService`: consolida histórico real do atleta nas janelas de 7, 21 e 42 dias e produz uma decisão objetiva de progressão.
- **Novos DTOs** `ProgressaoHistoricoResumo` e `DecisaoProgressao` (record), com enum `EstadoProgressao` (`PROGREDIR`, `PROGREDIR_LEVE`, `MANTER`, `REDUZIR`).
- **Ampliação do histórico** em `PlanoServiceImpl`: de `LIMITE_TREINOS_HISTORICO = 7` para busca por janela de 42 dias via repositório.
- **Integração no fluxo de geração de plano**: a `DecisaoProgressao` é calculada antes da chamada à IA e passada como contexto no prompt via `PeriodizacaoPromptFormatter`.
- **Reutilização** de `calcularProgressaoSegura` (TSB/rampRate) como limitador de teto dentro do novo serviço.

## Capabilities

### New Capabilities

- `progressao-treinos`: Motor de decisão de progressão baseado em janelas de histórico real (7/21/42 dias), aderência, longões, RPE e métricas TSB/ATL/CTL. Produz um `DecisaoProgressao` que define o envelope seguro para os próximos treinos.

### Modified Capabilities

- `fc-limiar-zones`: sem alteração de requisitos — não afetado.

## Impact

- **`PlanoServiceImpl`**: integra chamada ao `ProgressaoTreinoService` antes da geração; amplia janela de histórico passada ao `DadosPlanoDto`.
- **`PeriodizacaoPromptFormatter`**: recebe `DecisaoProgressao` e inclui estado, limites de volume/longão e motivo no prompt.
- **`TreinoRealizadoRepository`**: reutiliza `findByAtletaAndDataTreinoGreaterThanEqualOrderByDataTreinoDesc` já existente.
- **`DadosPlanoDto`**: adiciona campo `decisaoProgressao` (ou recebe o resumo como parâmetro do formatter).
- **`TsbServiceImpl`**: `recalcularSemanasProgressao` permanece inalterado; `semanasProgressaoContinua` continua como sinal auxiliar.
- Sem breaking changes de API REST.
