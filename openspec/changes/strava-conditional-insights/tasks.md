## 1. Modelo de Dados — Entidades e Migrations

- [ ] 1.1 Criar migration `V29__Create_alerta_atividade_table.sql` com tabela `tb_alerta_atividade`: `id`, `treino_realizado_id` (FK), `tipo_alerta` (enum), `valor_real`, `threshold_esperado`, `contexto_json`, `criado_em`, `tenant_id`, índices por `(tenant_id, atleta_id, criado_em)`
- [ ] 1.2 Criar migration `V30__Create_insights_atividade_table.sql` com tabela `tb_insights_atividade`: `id`, `alerta_atividade_id` (FK), `narrativa_llm`, `recomendacoes_json`, `tokens_usados`, `latencia_ms`, `modelo_llm`, `criado_em`, `tenant_id`
- [ ] 1.3 Criar entidade `AlertaAtividade.java` com campos: `id`, `treinoRealizado` (FK), `tipoAlerta` (enum `TipoAlerta`), `valorReal` (BigDecimal), `thresholdEsperado` (BigDecimal), `unidade` (String, ex: "%"), `contexto` (Map/JSON), `tenantId`, `criadoEm`
- [ ] 1.4 Criar entidade `InsightsAtividade.java` com campos: `id`, `alertaAtividade` (FK), `narrativaLlm` (TEXT), `recomendacoes` (JSON), `tokensUsados` (Integer), `latenciaMs` (Integer), `modeloLlm` (String), `tenantId`, `criadoEm`
- [ ] 1.5 Criar enum `TipoAlerta.java` com valores: `DESVIO_TSS`, `DESVIO_ZONA_FC`, `DESVIO_CADENCIA`, `DESVIO_VELOCIDADE`, `DADOS_INCOMPLETOS`

## 2. Repositories

- [ ] 2.1 Criar `AlertaAtividadeRepository.java` com: `findByTreinoRealizadoId(UUID)`, `findByAtletaIdAndTenantIdOrderByData(UUID, UUID, Pageable)`, `findByTipoAlertaAndTenantId(TipoAlerta, UUID, Pageable)`
- [ ] 2.2 Criar `InsightsAtividadeRepository.java` com: `findByAlertaAtividadeId(UUID)`, `findByAtletaIdOrderByData(UUID, Pageable)`

## 3. Configuração de Thresholds

- [ ] 3.1 Adicionar bloco `app.strava-insights.thresholds` ao `application.yml` com: `tss-deviation-percent: 15`, `fc-out-of-zone-minutes: 5`, `cadencia-deviation-percent: 10`, `velocity-deviation-percent: 8`
- [ ] 3.2 Criar `StravaInsightsProperties.java` em `config/` com `@ConfigurationProperties(prefix = "app.strava-insights")`
- [ ] 3.3 Adicionar variáveis correspondentes ao `.env.example`

## 4. Serviço de Detecção de Alertas

- [ ] 4.1 Criar `StravaInsightsService.java` com método `detectarAlertas(TreinoRealizado treinoRealizado, TreinoPlanejado planejado, Atleta atleta) -> List<AlertaAtividade>`
- [ ] 4.2 Implementar lógica de comparação TSS: calcular `desvio_tss_percent = (tss_realizado - tss_planejado) / tss_planejado * 100`, gerar alerta `DESVIO_TSS` se `abs(desvio) > threshold`
- [ ] 4.3 Implementar lógica de comparação FC: contar minutos com FC fora da zona esperada (abaixo de `fc_limiar_inferior` ou acima de `fc_limiar_superior`), gerar alerta `DESVIO_ZONA_FC` se minutos > threshold
- [ ] 4.4 Implementar lógica de comparação cadência: calcular desvio de cadência em relação ao histórico do atleta (últimas 10 atividades similares), gerar alerta `DESVIO_CADENCIA` se desvio > threshold
- [ ] 4.5 Implementar lógica de comparação velocidade: similar a cadência, comparar com histórico
- [ ] 4.6 Implementar validação de dados: gerar alerta `DADOS_INCOMPLETOS` se `fcMedia == null` ou `cadenciaMedia == null` em atividade que esperava esses dados
- [ ] 4.7 Persistir alertas gerados via `alertaAtividadeRepository.saveAll(List<AlertaAtividade>)` dentro da mesma transação do `strava-activity-sync`

## 5. Serviço de Análise LLM Condicional

- [ ] 5.1 Criar `StravaInsightsLlmService.java` com método `analisarAletasDoAtleta(UUID atletaId, UUID tenantId) -> InsightsAtividade`
- [ ] 5.2 Implementar método privado `montarContextoAtleta(UUID atletaId, UUID tenantId) -> Map<String, Object>` que retorna: perfil do atleta, plano semanal, TSB/CTL/ATL, últimas 3 atividades, histórico de alertas
- [ ] 5.3 Implementar cache em Caffeine do contexto com TTL 30 minutos: `@Cacheable(value = "strava-insights-context", key = "#atletaId")`
- [ ] 5.4 Implementar método `@Async` para invocar LLM: `analisarAlertasAssincrono(List<AlertaAtividade> alertas, Map<String, Object> contexto)` → chamada ao `ChatClient` com prompt estruturado
- [ ] 5.5 Implementar prompt que instrui o LLM a: a) interpretar cada alerta no contexto do histórico do atleta, b) sugerir 1-2 ações concretas (ajustar próximo treino, aumentar recuperação, revisar execução), c) avisar se há padrão recorrente
- [ ] 5.6 Persistir resultado da análise em `InsightsAtividade` com `tokensUsados` e `latenciaMs`

## 6. Integração com Strava Activity Sync

- [ ] 6.1 No callback de `strava-activity-sync` (após persistir `TreinoRealizado`), invocar `stravaInsightsService.detectarAlertas()` e persistir alertas
- [ ] 6.2 Se alertas foram gerados (não vazio), invocar `stravaInsightsLlmService.analisarAlertasAssincrono()` via `@Async`
- [ ] 6.3 Garantir que o sync retorna rapidamente (< 500ms) mesmo se LLM é invocado assincronamente

## 7. DTOs e Controller

- [ ] 7.1 Criar `AlertaOutputDto.java` com: `id`, `tipoAlerta`, `valorReal`, `thresholdEsperado`, `unidade`, `contexto`, `criadoEm`
- [ ] 7.2 Criar `InsightsOutputDto.java` com: `id`, `narrativaLlm`, `recomendacoes`, `tokensUsados`, `latenciaMs`, `modeloLlm`
- [ ] 7.3 Criar `StravaInsightsController.java` com endpoints:
  - `GET /api/strava/insights/{atletaId}` → últimas 7 dias de alertas + insights (filtrado por tenant)
  - `GET /api/strava/insights/{atletaId}/{treinoRealizadoId}` → alertas + insights de atividade específica
  - `GET /api/strava/alertas/summary/{atletaId}` → contagem por tipo de alerta (últimos 30 dias)
- [ ] 7.4 Adicionar anotações OpenAPI nos endpoints

## 8. Testes Unitários

- [ ] 8.1 Criar `StravaInsightsServiceTest.java` cobrindo: detecção de desvio TSS, FC, cadência, velocidade com limites variados
- [ ] 8.2 Criar teste para `DADOS_INCOMPLETOS` — atividade com `fcMedia == null`
- [ ] 8.3 Criar `StravaInsightsLlmServiceTest.java` cobrindo: montagem de contexto, caching, invocação LLM com múltiplos alertas
- [ ] 8.4 Criar teste de cache hit/miss de contexto
- [ ] 8.5 Criar teste de redução de tokens — verificar que sem alertas não invoca LLM, com alertas invoca 1x

## 9. Segurança e Multi-tenancy

- [ ] 9.1 Verificar que endpoints de insights filtram por `TenantContext` — atleta deve pertencer ao tenant autenticado
- [ ] 9.2 Garantir que alertas de um atleta não são visíveis a outro tenant

## 10. Configuração e Documentação

- [ ] 10.1 Documentar thresholds no `DOCKER_QUICKSTART.md` e explicar como ajustá-los por assessoria (futura)
- [ ] 10.2 Adicionar `application-test.yml` com thresholds apropriados para testes

## 11. Critérios de Aceite

- [ ] 11.1 Atividade com desvio TSS ≤ 15% não gera alerta `DESVIO_TSS`
- [ ] 11.2 Atividade com FC dentro da zona esperada não gera alerta mesmo com desvio TSS
- [ ] 11.3 Alerta é persistido com tipo, valores reais/esperados e contexto
- [ ] 11.4 LLM é invocado apenas se um ou mais alertas foram gerados
- [ ] 11.5 Contexto do atleta é cacheado e reutilizado em mesma janela de 30 min
- [ ] 11.6 Tokens consumidos pelo LLM são contabilizados e podem ser auditados

## 12. Review Gate (OpenSpec)

- [ ] 12.1 Executar `openspec status --change "strava-conditional-insights" --json` e confirmar artifacts `done`
- [ ] 12.2 Verificar que nenhum alerta é gerado para atividades rotineiras (syn
tético em testes)
- [ ] 12.3 Registrar economia de tokens observada em ambiente de test
