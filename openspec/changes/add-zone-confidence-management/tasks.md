## 1. Modelo

- [ ] 1.1 Definir status de confiança das zonas
- [ ] 1.2 Definir critérios mínimos para `confiável`, `estimada` e `desatualizada`
- [ ] 1.3 Definir contrato mínimo com motivo principal e indicação de fallback

## 2. Lógica

- [ ] 2.1 Detectar zonas estimadas por fallback
- [ ] 2.2 Detectar zonas vencidas ou inconsistentes com histórico recente
- [ ] 2.3 Gerar recomendação de reteste quando necessário
- [ ] 2.4 Definir janela inicial de recência e critério inicial de incoerência

## 3. Integração

- [ ] 3.1 Expor status de confiança no contexto de prescrição
- [ ] 3.2 Integrar status ao fluxo de revisão do treinador

## 4. Testes

- [ ] 4.1 Criar testes unitários para classificação de confiança
- [ ] 4.2 Criar testes unitários para recomendação de reteste
- [ ] 4.3 Criar testes para marcação explícita de fallback
