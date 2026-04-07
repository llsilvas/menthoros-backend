## 1. Modelo e Contratos

- [ ] 1.1 Definir DTO/resultado estruturado de debrief pós-treino
- [ ] 1.2 Definir score de execução, riscos e recomendação operacional
- [ ] 1.3 Definir enum/status de execução (`ABAIXO_DO_ESPERADO`, `DENTRO_DO_ESPERADO`, `ACIMA_DO_ESPERADO`, `INCONCLUSIVO`)

## 2. Lógica de Negócio

- [ ] 2.1 Implementar comparação entre `TreinoPlanejado` e `TreinoRealizado`
- [ ] 2.2 Priorizar análise por `EtapaRealizada` quando disponível
- [ ] 2.3 Implementar fallback para treinos sem etapas
- [ ] 2.4 Implementar fallback para treinos sem planejado associado
- [ ] 2.5 Definir critério mínimo para classificar resultado como `INCONCLUSIVO`

## 3. Persistência e Exposição

- [ ] 3.1 Persistir resumo, score e recomendação do debrief
- [ ] 3.2 Expor resultado no fluxo de treino realizado
- [ ] 3.3 Tornar o debrief consumível pela revisão semanal e geração do próximo plano
- [ ] 3.4 Decidir entre colunas em `TreinoRealizado` ou estrutura dedicada para payload detalhado

## 4. Testes

- [ ] 4.1 Criar testes unitários para comparação planejado vs realizado
- [ ] 4.2 Criar testes unitários para análise com etapas
- [ ] 4.3 Criar testes unitários para fallback sem etapas
- [ ] 4.4 Criar testes unitários para fallback sem planejado
- [ ] 4.5 Criar testes unitários para status `INCONCLUSIVO`
