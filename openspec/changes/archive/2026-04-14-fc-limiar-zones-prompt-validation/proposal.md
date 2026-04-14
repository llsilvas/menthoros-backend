## Why

O sistema já possui `ZonaTreinoService` e `formatarDadosFisiologicos`, mas as zonas de FC são calculadas com base em percentuais da **FCmax** (exceto Z4, que usa fcLimiar). A literatura de fisiologia do exercício — e o modelo de Friel/Joe Friel amplamente adotado no triathlon e corrida — preconiza o uso da **FC Limiar** (LTHR) como referência principal de todas as zonas, pois ela reflete a capacidade aeróbica real do atleta com mais precisão que a FCmax estimada. Além disso, o campo `fcAlvoEtapa` no schema JSON exige o padrão `"% FCmax"`, criando contradição com os valores absolutos de bpm já enviados no prompt. A validação pós-geração não verifica se a FC prescrita em cada etapa está dentro dos limites fisiológicos da zona declarada.

## What Changes

- **Recalcular todas as 5 zonas de FC usando `fcLimiar` como base** no `ZonaTreinoService`, substituindo o modelo atual (percentual de FCmax para Z1-Z3/Z5, percentual de fcLimiar só para Z4) por um modelo consistente baseado em LTHR.
- **Atualizar `formatarDadosFisiologicos`** no `PlanoTreinoPromptBuilder` para incluir a FC limiar usada como base e os limites absolutos em bpm de cada zona no prompt (já existente, mas passa a usar o novo cálculo).
- **Corrigir o padrão JSON Schema** de `fcAlvoEtapa` de `"% FCmax"` para um range de bpm absoluto (`"NNN-NNN bpm"`), alinhado com o que a IA já recebe no prompt.
- **Adicionar validação pós-geração de FC por zona** em `IaServiceImpl.validarENormalizarPlanoGerado`: verificar que a FC prescrita em cada etapa está dentro dos limites da zona esperada para aquele tipo de etapa (usando as zonas calculadas do atleta).

## Capabilities

### New Capabilities

- `fc-limiar-zones`: Cálculo consistente das zonas de treino baseado em FC Limiar (LTHR), injeção correta no prompt e validação da resposta da IA contra os limites fisiológicos do atleta.

### Modified Capabilities

<!-- Nenhuma capability existente tem seus requisitos alterados -->

## Impact

- **`ZonaTreinoService`**: lógica de `calcularZonasFC()` substituída para usar fcLimiar como base em todas as zonas.
- **`PlanoTreinoPromptBuilder`**: `formatarDadosFisiologicos()` usa o novo cálculo; nenhuma mudança de assinatura.
- **`IaServiceImpl`**: schema JSON `fcAlvoEtapa` pattern atualizado; nova validação de FC-por-zona em `validarENormalizarPlanoGerado`.
- **Sem quebra de API**: nenhuma mudança em controllers, DTOs de entrada/saída ou endpoints.
- **Sem migration de banco**: nenhum campo novo em entidades.
