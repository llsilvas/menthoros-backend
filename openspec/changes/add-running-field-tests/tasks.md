## 1. Modelo

- [ ] 1.1 Definir capability `running-field-tests`
- [ ] 1.2 Definir enum de protocolo com `TRES_KM` e `CINCO_MIN`
- [ ] 1.3 Definir semântica de treino especial de avaliação
- [ ] 1.4 Definir vínculo entre treino de teste e treino substituído

## 2. Agendamento

- [ ] 2.1 Definir fluxo para agendar teste a partir da semana do atleta
- [ ] 2.2 Exigir indicação do treino planejado que será substituído
- [ ] 2.3 Priorizar substituição de `INTERVALADO` ou `TEMPO_RUN`
- [ ] 2.4 Exigir confirmação explícita ao substituir `LONGO`

## 3. Regras de encaixe

- [ ] 3.1 Definir critérios mínimos de recuperação antes e depois do teste
- [ ] 3.2 Bloquear ou alertar adjacência com estímulos de alta intensidade
- [ ] 3.3 Definir frequência recomendada entre testes consecutivos

## 4. Resultado e atualização fisiológica

- [ ] 4.1 Definir contrato mínimo do resultado por protocolo
- [ ] 4.2 Definir critérios mínimos de qualidade do teste
- [ ] 4.3 Definir saída de atualização sugerida de parâmetros do atleta
- [ ] 4.4 Definir quando a atualização será automática versus revisada

## 5. Integração

- [ ] 5.1 Integrar o teste ao fluxo de `TreinoPlanejado` e `TreinoRealizado`
- [ ] 5.2 Integrar resultado do teste ao contexto de zonas e prescrição
- [ ] 5.3 Expor no fluxo do treinador que `3 km` é o protocolo recomendado

## 6. Testes

- [ ] 6.1 Criar testes unitários para regras de agendamento
- [ ] 6.2 Criar testes unitários para substituição de treino da semana
- [ ] 6.3 Criar testes para processamento do resultado de `3 km`
- [ ] 6.4 Criar testes para processamento do resultado de `5 minutos`
