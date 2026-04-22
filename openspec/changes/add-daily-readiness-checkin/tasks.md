## 1. Modelo de dados

- [ ] 1.1 Criar enum `NivelProntidao` com valores `PRONTO`, `CAUTELOSO`, `DESCANSAR` e interpretações associadas
- [ ] 1.2 Criar entidade `CheckinProntidao` em `entity/` com campos subjetivos (sono 1-10, humor 1-10, dores 0-10, energia 1-10, estresse 0-10), `readinessScore` (BigDecimal 0-1), `nivelProntidao`, `observacoes` e `tenantId`
- [ ] 1.3 Criar migration `Vxx__Create_checkin_prontidao_table.sql` com tabela `tb_checkin_prontidao` e índices `(atleta_id, data)` e `(tenant_id)`
- [ ] 1.4 Criar migration `Vyy__Add_readiness_to_metricas_diarias.sql` adicionando `readiness_score` e `nivel_prontidao` em `tb_metricas_diarias`
- [ ] 1.5 Adicionar constraint UNIQUE `(atleta_id, data)` em `tb_checkin_prontidao`

## 2. DTOs e Mapper

- [ ] 2.1 Criar `CheckinProntidaoInputDto` com validações (@Min/@Max nos campos numéricos, @Size no texto)
- [ ] 2.2 Criar `CheckinProntidaoOutputDto` com todos os campos + readiness calculado
- [ ] 2.3 Criar `CheckinProntidaoMapper` MapStruct

## 3. Repository

- [ ] 3.1 Criar `CheckinProntidaoRepository` com `findByAtletaIdAndData`, `findTopByAtletaIdOrderByDataDesc`, `findByAtletaIdAndDataBetween`

## 4. Cálculo de readiness

- [ ] 4.1 Criar `ReadinessService` com método `calcularScore(CheckinProntidao)` retornando BigDecimal 0–1
- [ ] 4.2 Definir função de ponderação inicial: sono 35%, energia 25%, humor 20%, dores 15%, estresse 5% (valores ajustáveis via `@ConfigurationProperties`)
- [ ] 4.3 Implementar `classificarNivel(BigDecimal score)`: `>= 0.75` → PRONTO, `0.50–0.74` → CAUTELOSO, `< 0.50` → DESCANSAR
- [ ] 4.4 Persistir `readinessScore` e `nivelProntidao` no próprio checkin e também na `MetricasDiarias` do dia

## 5. Integração com motor de elegibilidade

- [ ] 5.1 Adicionar portão `readinessPermite()` em `IntervaladoElegibilidadeService` como sexto portão da decisão
- [ ] 5.2 Regra: `DESCANSAR` → bloqueio total de intervalado; `CAUTELOSO` → permite mas sinaliza atenuação de volume (20–30%)
- [ ] 5.3 Expor motivo do bloqueio/atenuação no output do serviço para logging e prompt
- [ ] 5.4 Fallback: se não há checkin do dia, motor opera com o comportamento atual (sem readiness) e registra WARN

## 6. Integração com prompt builder

- [ ] 6.1 Adicionar seção `readiness` ao contexto montado por `PlanoTreinoPromptBuilder`
- [ ] 6.2 Incluir sequência compacta dos últimos 7 dias (ex: `[PRONTO, PRONTO, CAUTELOSO, DESCANSAR, CAUTELOSO, PRONTO, PRONTO]`)
- [ ] 6.3 Incluir score do dia atual se houver

## 7. Endpoints REST

- [ ] 7.1 Criar `CheckinController` em `controller/`
- [ ] 7.2 Endpoint `POST /api/checkins` (cria ou atualiza checkin do dia; idempotente por (atleta, data))
- [ ] 7.3 Endpoint `GET /api/checkins/{atletaId}/atual`
- [ ] 7.4 Endpoint `GET /api/checkins/{atletaId}?dias=N` (default 7, máx 90)
- [ ] 7.5 Anotações OpenAPI e tratamento de erros via `GlobalExceptionHandler`

## 8. Multi-tenancy

- [ ] 8.1 Garantir que todas as queries filtram por `tenant_id` do `TenantContext`
- [ ] 8.2 Alinhamento com padrão da branch `fix-multi-tenancy-enforcement` (cache segmentado por tenant quando aplicável)

## 9. Testes

- [ ] 9.1 Testes unitários do `ReadinessService`: score com diferentes combinações, classificação de níveis, fallback
- [ ] 9.2 Testes de integração do portão de elegibilidade: bloqueio por DESCANSAR, atenuação por CAUTELOSO, operação normal sem checkin
- [ ] 9.3 Testes do controller com MockMvc: criação idempotente, paginação de histórico, erro 400 em valores fora de faixa
