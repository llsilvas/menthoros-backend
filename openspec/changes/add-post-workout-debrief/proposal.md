## Why

O Menthoros já prescreve com bastante contexto, mas ainda não fecha bem o ciclo depois da execução do treino. Hoje o sistema sabe planejar, mas ainda precisa interpretar de forma estruturada o que aconteceu na sessão realizada e como isso impacta a sequência do ciclo.

Sem essa capability, o treinador continua fazendo manualmente a leitura de:

- planejado versus realizado
- execução abaixo, dentro ou acima do esperado
- sinais de excesso ou boa adaptação
- necessidade de ajuste no próximo estímulo

Essa capability é central para o posicionamento do Menthoros como assistente do treinador.

## What Changes

- nova capability `post-workout-debrief`
- comparação estruturada entre `TreinoPlanejado` e `TreinoRealizado`
- uso prioritário de `EtapaRealizada` quando disponível
- persistência de score, resumo, riscos e recomendação de sequência
- disponibilização do debrief para revisão técnica e para a próxima prescrição

## Capabilities

### New Capabilities

- `post-workout-debrief`

## Impact

**Produto:**
- fecha o ciclo planejado → executado → ajustado
- aumenta confiança do treinador na plataforma

**Backend:**
- novos DTOs/entidades de debrief
- maior uso de `EtapaRealizada`
