## Why

O Menthoros já possui base sólida de prescrição e análise fisiológica, mas ainda precisa dar o salto de produto para se diferenciar claramente no mercado de assessorias e treinadores de corrida. O produto já consegue gerar planos com bastante contexto, porém ainda falta fechar o ciclo operacional do treinador:

- entender rapidamente quais atletas precisam de atenção
- interpretar o que aconteceu após o treino
- revisar a semana com consistência
- justificar tecnicamente recomendações e ajustes

Essas capacidades são as que mais aproximam o Menthoros do posicionamento de “copiloto do treinador”, que é onde o produto pode competir melhor frente a plataformas amplas como TrainingPeaks e similares.

Este change deve ser lido como um **épico guarda-chuva de produto**. As capabilities abaixo podem e devem ser implementadas em changes menores e independentes.

## What Changes

- **Nova capability de análise pós-treino**: interpretar treinos realizados e sugerir impacto para a sequência do ciclo
- **Nova capability de fila de atenção do treinador**: priorizar atletas e situações que exigem ação
- **Nova capability de revisão semanal automatizada**: consolidar aderência, fadiga, evolução e foco sugerido
- **Nova capability de explicabilidade das recomendações**: expor evidências, regras e motivos por trás de ajustes e prescrições
- **Extensão da análise por etapa**: permitir comparação planejado vs realizado com maior granularidade
- **Nova capability de confiança das zonas**: indicar se as zonas fisiológicas estão confiáveis, estimadas ou vencidas

## Capabilities

### New Capabilities

- `post-workout-debrief`
- `coach-attention-queue`
- `weekly-athlete-review`
- `recommendation-explainability`
- `zone-confidence-management`

### Modified Capabilities

- A geração e ajuste de planos passa a poder consumir os resultados dessas capabilities

## Impact

**Produto:**
- reforça posicionamento como assistente do treinador
- aumenta frequência de uso operacional
- cria diferenciais visíveis para assessorias

**Fluxos de negócio:**
- pós-treino ganha interpretação estruturada
- treinador ganha fila diária de ação
- revisão semanal passa a ser automatizável

**Dados e backend:**
- novas entidades/DTOs de revisão, explicabilidade e sinais operacionais
- uso mais intenso de `EtapaRealizada` e de resultados das skills de domínio
