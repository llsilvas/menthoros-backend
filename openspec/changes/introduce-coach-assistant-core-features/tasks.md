## Epic Decomposition

- [ ] Decompor `post-workout-debrief` em change próprio
- [ ] Decompor `coach-attention-queue` em change próprio
- [ ] Decompor `weekly-athlete-review` em change próprio
- [ ] Decompor `recommendation-explainability` em change próprio
- [ ] Decompor `zone-confidence-management` em change próprio

## 1. Capability: post-workout-debrief

- [ ] 1.1 Definir DTO/resultado estruturado de análise pós-treino
- [ ] 1.2 Implementar serviço para comparar planejado vs realizado
- [ ] 1.3 Incorporar análise por `EtapaRealizada` quando disponível
- [ ] 1.4 Persistir score, resumo, riscos e recomendação de sequência
- [ ] 1.5 Expor resultado no fluxo de treino realizado

## 2. Capability: coach-attention-queue

- [ ] 2.1 Definir modelo de item de atenção do treinador
- [ ] 2.2 Consolidar sinais de fadiga, aderência, ausência de estímulos e execução ruim
- [ ] 2.3 Implementar priorização por severidade e urgência
- [ ] 2.4 Expor endpoint/serviço de consulta da fila

## 3. Capability: weekly-athlete-review

- [ ] 3.1 Definir snapshot semanal estruturado do atleta
- [ ] 3.2 Consolidar aderência, carga, fadiga, evolução e foco recomendado
- [ ] 3.3 Persistir ou disponibilizar revisão semanal por atleta
- [ ] 3.4 Integrar revisão ao fluxo de geração do próximo plano

## 4. Capability: recommendation-explainability

- [ ] 4.1 Definir estrutura de explicabilidade para recomendações
- [ ] 4.2 Expor evidências, skill/regra acionada e motivo do ajuste
- [ ] 4.3 Integrar explicabilidade à geração de plano e à análise pós-treino

## 5. Capability: zone-confidence-management

- [ ] 5.1 Definir status de confiança das zonas: confiável, estimada, desatualizada
- [ ] 5.2 Detectar zonas vencidas ou inconsistentes com histórico recente
- [ ] 5.3 Sugerir reavaliação/teste quando necessário
- [ ] 5.4 Expor status de confiança no contexto de prescrição

## 6. Testes e Integração

- [ ] 6.1 Criar testes unitários para análise pós-treino
- [ ] 6.2 Criar testes unitários para priorização da fila de atenção
- [ ] 6.3 Criar testes unitários para revisão semanal
- [ ] 6.4 Criar testes para explicabilidade e status de confiança das zonas
