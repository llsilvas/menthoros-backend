## 1. Modelo de dados

- [ ] 1.1 Validar que entidade `Prova` tem campo `tempoObjetivo` (Duration); adicionar caso não tenha (+migration)
- [ ] 1.2 Criar enum `MetodoPredicao` com valores `RIEGEL`, `VDOT`, `HIBRIDO`, `ESTIMATIVA`
- [ ] 1.3 Criar entidade `PredicaoProva` com `atleta`, `prova`, `tempoEstimadoSeg`, `metodoUsado`, `fonteReferenciaId`, `confiabilidade` (0–1), `calculadoEm`, `tenantId`
- [ ] 1.4 Criar migration `Vxx__Create_predicao_prova_table.sql` com índice `(atleta_id, prova_id, calculado_em DESC)`

## 2. DTOs e Mapper

- [ ] 2.1 Criar `PredicaoProvaOutputDto` expondo campos calculados + gap para objetivo
- [ ] 2.2 Criar `PredicaoProvaMapper` (MapStruct)

## 3. Repository

- [ ] 3.1 Criar `PredicaoProvaRepository` com `findTopByAtletaIdAndProvaIdOrderByCalculadoEmDesc` e `findByAtletaIdAndProvaId` (histórico)

## 4. Motor de predição

- [ ] 4.1 Criar `PredicaoProvaService`
- [ ] 4.2 Implementar fórmula de Riegel: `T2 = T1 * (D2/D1)^1.06`
- [ ] 4.3 Implementar conversão VDOT a partir de pace limiar + tabelas de Daniels (referência constante)
- [ ] 4.4 Implementar método HIBRIDO: média ponderada entre Riegel (de prova recente) e VDOT (de teste de campo), pesos proporcionais à frescura
- [ ] 4.5 Implementar seleção automática da melhor referência: prova mesma distância > prova distância próxima > teste de campo > estimativa por CTL
- [ ] 4.6 Implementar cálculo de `confiabilidade` (0–1) como função de frescura (dias desde referência) e tipo de fonte

## 5. Exposição

- [ ] 5.1 Criar `PredicaoProvaController` em `controller/`
- [ ] 5.2 Endpoint `GET /api/provas/{provaId}/predicao?atletaId=X` com cache do cálculo mais recente (TTL 24h)
- [ ] 5.3 Endpoint `GET /api/provas/{provaId}/predicao/historico?atletaId=X`
- [ ] 5.4 Anotações OpenAPI

## 6. Job agendado

- [ ] 6.1 Criar `PredicaoProvaScheduler` com `@Scheduled(cron = "0 0 3 * * MON")` (segunda às 3h)
- [ ] 6.2 Job percorre provas-alvo ativas de todos os atletas ativos e chama `PredicaoProvaService.calcular()` persistindo snapshot

## 7. Integração com prompt builder

- [ ] 7.1 Adicionar seção `predicao` ao contexto de `PlanoTreinoPromptBuilder` para provas com `provaAlvo=true`
- [ ] 7.2 Incluir: `objetivoTempoSeg`, `predicaoTempoSeg`, `gapSeg`, `metodoUsado`, `confiabilidade`

## 8. Multi-tenancy

- [ ] 8.1 Queries filtram por `tenant_id` do `TenantContext`
- [ ] 8.2 Job agendado deve propagar tenant corretamente ou rodar por tenant em ciclo

## 9. Testes

- [ ] 9.1 Testes unitários do `PredicaoProvaService`: Riegel 10k→21k, VDOT de pace limiar, HIBRIDO com pesos, seleção de referência
- [ ] 9.2 Teste de confiabilidade: referência de 1 semana vs 3 meses
- [ ] 9.3 Teste de integração do endpoint: cache de 24h, histórico paginado
