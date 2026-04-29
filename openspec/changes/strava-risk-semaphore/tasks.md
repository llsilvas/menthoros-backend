## 1. Modelo de Dados — Entidades e Migrations

- [ ] 1.1 Criar migration `V31__Create_risco_atleta_table.sql` com tabela `tb_risco_atleta`: `id`, `atleta_id` (FK), `data` (date, parte de UNIQUE), `score_risco` (Integer 0-100), `status_semaforo` (enum: RED, YELLOW, GREEN), `dimensoes_json` (JSON com scores de cada dimensão), `motivo_principal` (qual dimensão contribuiu mais), `recomendacao` (TEXT), `tenant_id`, `criado_em`, `atualizado_em`, índices por `(tenant_id, atleta_id, data)`
- [ ] 1.2 Criar entidade `RiscoAtleta.java` com: `id`, `atleta` (FK), `data` (LocalDate), `scoreRisco` (Integer), `statusSemaforo` (enum `StatusSemaforo`), `dimensoes` (Map/JSON), `motivoPrincipal` (String), `recomendacao` (String), `tenantId`, `criadoEm`, `atualizadoEm`
- [ ] 1.3 Criar enum `StatusSemaforo.java` com valores: `RED`, `YELLOW`, `GREEN`
- [ ] 1.4 Criar classe `DimensoesRisco.java` com campos: `tsbScore`, `alertasScore`, `aderenciaScore`, `padraoScore`, `dadosScore` (cada Integer 0-100)

## 2. Repository

- [ ] 2.1 Criar `RiscoAtletaRepository.java` com: `findByAtletaIdAndData(UUID, LocalDate)`, `findByTenantIdAndData(UUID, LocalDate, Pageable)`, `findByTenantIdAndDataOrderByScoreRiscoDesc(UUID, LocalDate, Pageable)`, `findByTenantIdAndStatusSemaforoAndData(UUID, StatusSemaforo, LocalDate, Pageable)`, `updateOrInsertRisco(RiscoAtleta)`

## 3. Serviço de Cálculo de Risco — Dimensões

- [ ] 3.1 Criar `StravaRiskSemaphoreService.java` com método `calcularRiscoAtleta(UUID atletaId, UUID tenantId) -> RiscoAtleta`
- [ ] 3.2 Implementar cálculo da dimensão **TSB**: 
  - TSB > -10: 0 pontos
  - TSB -10 a -20: 50 pontos
  - TSB < -20: 100 pontos
- [ ] 3.3 Implementar cálculo da dimensão **Alertas** (consultar últimos 7 dias):
  - Sem alertas: 0 pontos
  - 1-2 alertas: 40 pontos
  - 3+ alertas ou 1+ alerta crítico (DESVIO_TSS > 50%): 100 pontos
- [ ] 3.4 Implementar cálculo da dimensão **Aderência** (últimas 4 semanas do plano):
  - Aderência > 80%: 0 pontos
  - 50-80%: 50 pontos
  - < 50%: 100 pontos
- [ ] 3.5 Implementar cálculo da dimensão **Padrão Histórico** (comparar distribuição de tipos nas últimas 4 semanas):
  - Mudança < 15%: 0 pontos
  - Mudança 15-30%: 40 pontos
  - Mudança > 30%: 100 pontos
- [ ] 3.6 Implementar cálculo da dimensão **Dados Incompletos** (últimas 10 atividades):
  - Todos os campos (FC, cadência, pace): 0 pontos
  - 1-2 campos ausentes em 50%+: 30 pontos
  - Maioria ausente ou impossível calcular TSS: 100 pontos
- [ ] 3.7 Implementar fórmula de agregação: `score = 0.30*tsbScore + 0.25*alertasScore + 0.20*aderenciaScore + 0.15*padraoScore + 0.10*dadosScore`
- [ ] 3.8 Implementar mapeamento de score para `StatusSemaforo`: score < 25 → GREEN, 25-60 → YELLOW, > 60 → RED
- [ ] 3.9 Implementar identificação de `motivoPrincipal` — qual dimensão contribuiu mais para o score final

## 4. Geração de Recomendações

- [ ] 4.1 Implementar `gerarRecomendacao(RiscoAtleta risco, Atleta atleta) -> String` que retorna frase estruturada
- [ ] 4.2 Implementar regra: Se `tsbScore > 75`, recomendação contém "Aumentar recuperação"
- [ ] 4.3 Implementar regra: Se `alertasScore > 75`, recomendação contém "Revisar padrão de execução"
- [ ] 4.4 Implementar regra: Se `aderenciaScore > 75`, recomendação contém "Verificar entendimento do plano"
- [ ] 4.5 Implementar regra: Se `padraoScore > 75`, recomendação contém "Mudança de padrão detectada"

## 5. Integração com Strava Activity Sync

- [ ] 5.1 Após persistir `TreinoRealizado` e gerar alertas (no serviço do change anterior), invocar `stravaRiskSemaphoreService.calcularRiscoAtleta(atletaId, tenantId)`
- [ ] 5.2 Persistir `RiscoAtleta` com update-or-insert na data do dia (mesma data → update, nova data → insert)

## 6. Job Noturno de Recalculation

- [ ] 6.1 Criar `RiscoAtletaScheduledTask.java` com método `@Scheduled(cron = "0 2 * * *")` (2am diariamente)
- [ ] 6.2 Implementar lógica que itera sobre todos os atletas ativos e recalcula score para data de hoje
- [ ] 6.3 Persistir resultados via `riscoBatch` para performance (não linha por linha)
- [ ] 6.4 Logar início/fim e número de atletas atualizados

## 7. DTOs e Controller

- [ ] 7.1 Criar `RiscoSemaforoOutputDto.java` com: `atletaId`, `atletaNome`, `scoreRisco`, `statusSemaforo`, `dimensoes` (resumo), `motivoPrincipal`, `recomendacao`, `data`
- [ ] 7.2 Criar `DimensoesOutputDto.java` com: `tsbScore`, `alertasScore`, `aderenciaScore`, `padraoScore`, `dadosScore`
- [ ] 7.3 Criar `StravaRiskSemaphoreController.java` com endpoints:
  - `GET /api/strava/risk-semaphore?status=RED,YELLOW&order=score_desc&page=0&limit=50` → lista de atletas ordenada por risco
  - `GET /api/strava/risk-semaphore/{atletaId}` → risco detalhado de atleta específico
  - `GET /api/strava/risk-semaphore/summary` → contagem por status (X atletas vermelhos, Y amarelos, Z verdes)
- [ ] 7.4 Adicionar anotações OpenAPI nos endpoints
- [ ] 7.5 Garantir filtro por `TenantContext` — atletas devem pertencer ao tenant autenticado

## 8. Testes Unitários

- [ ] 8.1 Criar `StravaRiskSemaphoreServiceTest.java` cobrindo cálculo de cada dimensão com valores de fronteira
- [ ] 8.2 Criar teste para TSB (> -10, -10 a -20, < -20)
- [ ] 8.3 Criar teste para Alertas (0, 1-2, 3+)
- [ ] 8.4 Criar teste para Aderência (> 80%, 50-80%, < 50%)
- [ ] 8.5 Criar teste para Padrão Histórico (mudança de distribuição)
- [ ] 8.6 Criar teste para Dados Incompletos (todos presentes, parcialmente ausentes, maioria ausente)
- [ ] 8.7 Criar teste para agregação de fórmula ponderada
- [ ] 8.8 Criar teste para mapeamento score → semáforo
- [ ] 8.9 Criar teste para geração de recomendações por tipo de risco
- [ ] 8.10 Criar teste para job noturno — recalcula todos os atletas

## 9. Validação Empírica

- [ ] 9.1 Com 3+ treinadores, validar que score RED correlaciona com situações de risco real
- [ ] 9.2 Validar que atleta em taper (TSB negativo proposital) não é marcado RED apenas por TSB isolado (exigir múltiplos sinais)
- [ ] 9.3 Coletar feedback: "Qual dimensão foi mais útil para identificar risco?"

## 10. Documentação

- [ ] 10.1 Documentar fórmula de risco no `DOCKER_QUICKSTART.md`
- [ ] 10.2 Explicar limites de semáforo e quando cada status deve gerar ação
- [ ] 10.3 Adicionar exemplo de payload de resposta do endpoint

## 11. Segurança e Multi-tenancy

- [ ] 11.1 Verificar que endpoint de semáforo filtra por `TenantContext`
- [ ] 11.2 Garantir que atleta de um tenant não é visível a outro
- [ ] 11.3 Verificar que recomendações não expõem dados de outros atletas

## 12. Critérios de Aceite

- [ ] 12.1 Score de risco é recalculado após cada sincronização de atividade
- [ ] 12.2 Atleta com TSB < -20 é sempre RED
- [ ] 12.3 Atleta com múltiplos alertas na semana é mínimo YELLOW
- [ ] 12.4 Endpoint retorna atletas ordenados por score descendente
- [ ] 12.5 Cada atleta no endpoint inclui score, status, motivo e recomendação
- [ ] 12.6 Job noturno recalcula scores para data de hoje
- [ ] 12.7 Snapshot do dia anterior sobrescreve se nova atividade sincronizada

## 13. Review Gate (OpenSpec)

- [ ] 13.1 Executar `openspec status --change "strava-risk-semaphore" --json` e confirmar artifacts `done`
- [ ] 13.2 Validação empírica com treinadores: semáforo identifica risco real?
- [ ] 13.3 Registrar feedback de usabilidade antes de merge
