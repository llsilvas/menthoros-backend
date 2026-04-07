## 1. Modelo

- [ ] 1.1 Definir snapshot semanal estruturado do atleta
- [ ] 1.2 Definir campos de aderência, carga, fadiga, evolução e foco recomendado
- [ ] 1.3 Definir janela temporal explícita da revisão (`semanaInicio`/`semanaFim`)

## 2. Consolidação

- [ ] 2.1 Consolidar dados da semana por atleta
- [ ] 2.2 Incorporar sinais de execução e risco relevantes
- [ ] 2.3 Definir comportamento para semanas com dados insuficientes

## 3. Integração

- [ ] 3.1 Persistir ou disponibilizar revisão semanal
- [ ] 3.2 Integrar revisão à geração do próximo plano
- [ ] 3.3 Definir estratégia de recalculo sob demanda ou fechamento automático semanal

## 4. Testes

- [ ] 4.1 Criar testes unitários para consolidação semanal
- [ ] 4.2 Criar testes unitários para integração com o próximo plano
- [ ] 4.3 Criar testes para semanas com baixa informação
