## Why

Hoje o Menthoros já calcula `TSS`, `CTL`, `ATL` e `TSB`, mas o comportamento ainda está muito acoplado ao lançamento de treinos do dia. Isso enfraquece o valor do produto em três pontos críticos:

- dias de descanso não são tratados como parte explícita da série fisiológica
- treinos retroativos ou sincronizados podem deixar a linha do tempo inconsistente até haver recálculo amplo
- o treinador vê métricas importantes, mas ainda não recebe uma leitura operacional simples de prontidão dentro da semana

Para um produto que quer ser o copiloto do treinador, a linha do tempo diária do atleta precisa ser contínua e confiável, inclusive nos dias sem treino.

## What Changes

- nova capability `continuous-daily-load-management`
- persistência de métricas diárias contínuas, com e sem treino
- dias de descanso explícitos com `TSS = 0` e decaimento fisiológico válido
- recálculo automático da janela afetada quando entra treino lançado, editado, importado ou sincronizado
- consolidação de uma leitura operacional de prontidão diária para orientar prescrição e revisão
- readiness score derivado de múltiplos sinais, sem substituir as métricas fisiológicas base

## Capabilities

### New Capabilities

- `continuous-daily-load-management`

## Impact

**Produto:**
- aumenta assertividade da prescrição diária e semanal
- melhora confiança do treinador na leitura de fadiga e prontidão
- transforma descanso em parte visível da inteligência do sistema

**Backend:**
- nova regra de continuidade temporal para `MetricasDiarias`
- recálculo em janela afetada, não apenas no dia do treino
- novo contrato de prontidão operacional

**Analytics e IA:**
- melhora qualidade dos sinais usados em fila de atenção, revisão semanal, explicabilidade e geração de plano
