## 1. Modelo

- [ ] 1.1 Definir capability `continuous-daily-load-management`
- [ ] 1.2 Definir contrato mínimo de status diário de carga e prontidão
- [ ] 1.3 Definir modelo de readiness score e readiness status

## 2. Série diária

- [ ] 2.1 Garantir criação de `MetricasDiarias` para dias sem treino
- [ ] 2.2 Persistir `isRestDay`, `daysSinceLastRest` e `consecutiveTrainingDays`
- [ ] 2.3 Garantir continuidade entre dias consecutivos da série

## 3. Recalculo

- [ ] 3.1 Definir gatilhos de recálculo para criação, edição e deleção de treinos
- [ ] 3.2 Definir gatilhos de recálculo para importação e atualização de sync externo
- [ ] 3.3 Recalcular janela `dataAfetada..hoje`
- [ ] 3.4 Consolidar recálculo em batch por atleta quando houver múltiplos eventos

## 4. Prontidão operacional

- [ ] 4.1 Usar `tsbInicioDia` como base canônica de prontidão
- [ ] 4.2 Definir peso de `ATL/CTL`, `Ramp Rate`, descanso recente e dias consecutivos
- [ ] 4.3 Produzir `readinessScore`, `readinessStatus` e `primaryReason`

## 5. Integração

- [ ] 5.1 Integrar a capability à geração de plano e ajuste de intensidade
- [ ] 5.2 Integrar à fila de atenção do treinador
- [ ] 5.3 Integrar à revisão semanal do atleta
- [ ] 5.4 Integrar à explicabilidade das recomendações

## 6. Testes

- [ ] 6.1 Criar testes para dias sem treino com decaimento válido
- [ ] 6.2 Criar testes para recálculo após treino retroativo
- [ ] 6.3 Criar testes para múltiplos treinos no mesmo dia
- [ ] 6.4 Criar testes para readiness score em cenários de fadiga, recuperação e progressão agressiva
