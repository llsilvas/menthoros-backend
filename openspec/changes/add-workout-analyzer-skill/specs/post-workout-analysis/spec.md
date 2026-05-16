## ADDED Requirements

### Requirement: Análise pós-treino disparada assincronamente por evento

O sistema SHALL publicar um `TreinoRegistradoEvent` contendo o `treinoRealizadoId` após a persistência bem-sucedida de um `TreinoRealizado`.

O `WorkoutAnalysisListener` SHALL consumir o evento com `@TransactionalEventListener(phase = AFTER_COMMIT)` e `@Async`, garantindo:
- O treino está persistido antes de iniciar a análise
- A análise não bloqueia o HTTP response do registro
- Uma falha na análise NÃO causa rollback do treino

A análise SHALL ser ignorada (evento descartado silenciosamente) se `TreinoRealizado.rpe` for `null`.

#### Scenario: Análise disparada após registro com RPE
- **WHEN** um `TreinoRealizado` com `rpe` não-nulo é salvo
- **THEN** `TreinoRegistradoEvent` é publicado e `WorkoutAnalysisListener` inicia análise em thread assíncrona
- **THEN** o HTTP response do endpoint de registro retorna antes da análise concluir

#### Scenario: Análise ignorada sem RPE
- **WHEN** um `TreinoRealizado` com `rpe = null` é salvo
- **THEN** nenhuma análise é iniciada e o evento é descartado sem erro

#### Scenario: Falha na análise não reverte o treino
- **WHEN** `WorkoutAnalysisListener` lança exceção durante a análise
- **THEN** o `TreinoRealizado` permanece persistido no banco de dados
- **THEN** o erro é registrado no log com `treinoRealizadoId` e `tenantId`

---

### Requirement: Análise usa skill workout-analyzer com modelo COMPLEX

O sistema SHALL carregar a skill `SKILL.md` de `classpath:skills/analise/workout-analyzer/SKILL.md` via `PromptTemplateLoader` e enviá-la como system prompt para o `sonnetChatClient` (TaskComplexity.COMPLEX).

O prompt de usuário SHALL incluir os dados de `TreinoPlanejado`, `TreinoRealizado` e `MetricasDiarias` (TSB, CTL) formatados em JSON.

A resposta SHALL ser deserializada em `AnaliseWorkoutRawDto` com os campos:
`summary`, `technical_interpretation`, `primary_cause`, `recommendation`, `tags`, `execution_score`, `rationale`.

#### Scenario: Análise completa com dados suficientes
- **WHEN** `TreinoRealizado` tem `rpe`, `distanciaMetros`, `TreinoPlanejado` associado, e `MetricasDiarias` disponível
- **THEN** o sistema chama `sonnetChatClient` com o prompt da skill
- **THEN** retorna `AnaliseWorkoutRawDto` deserializado com todos os campos preenchidos

#### Scenario: Análise sem MetricasDiarias
- **WHEN** não existe `MetricasDiarias` para a data do treino
- **THEN** o sistema usa `tsb = 0`, `ctl = 0`, `consecutive_load_days = 0` como defaults
- **THEN** a análise prossegue normalmente com esses valores

#### Scenario: LLM retorna JSON inválido
- **WHEN** o modelo retorna resposta que não pode ser deserializada em `AnaliseWorkoutRawDto`
- **THEN** a análise falha com log de erro incluindo a resposta bruta
- **THEN** a entidade `AnaliseWorkout` é salva com `status = FAILED`

---

### Requirement: Tradução do resultado para português

O sistema SHALL traduzir os campos textuais do output da skill (produzido em inglês) para português via `WorkoutAnalysisTranslator`, usando `haikuChatClient` (TaskComplexity.STANDARD).

Campos a traduzir: `summary`, `technical_interpretation`, `recommendation`, `rationale`.
Campos a NÃO traduzir (invariantes): `primary_cause` (enum), `tags` (array de strings técnicas), `execution_score` (integer).

#### Scenario: Tradução de análise em inglês
- **WHEN** `AnaliseWorkoutRawDto` com campos em inglês é recebido pelo translator
- **THEN** os campos textuais são traduzidos para português via `haikuChatClient`
- **THEN** os campos enum e numeric permanecem inalterados

---

### Requirement: Persistência da análise em tb_analise_workout

O sistema SHALL persistir o resultado em `AnaliseWorkout` com os campos:
- `id` (UUID)
- `treinoRealizadoId` (FK para `tb_treino_realizado`)
- `tenantId` (isolamento multi-tenant)
- `executionScore` (int 1–10)
- `primaryCause` (enum: ACCUMULATED_FATIGUE | ENVIRONMENTAL_FACTORS | PACING_ERROR | CNS_FATIGUE | NORMAL | UNDERTRAINING)
- `summary` (varchar 255)
- `technicalInterpretation` (text)
- `recommendation` (text)
- `rationale` (text)
- `tags` (text, JSON array serializado)
- `status` (enum: PENDING | COMPLETED | FAILED)
- `createdAt` (timestamp)

A tabela SHALL ser criada via Flyway migration. O `tenantId` SHALL ser obrigatório (NOT NULL).

#### Scenario: Análise persistida após conclusão bem-sucedida
- **WHEN** a análise completa sem erros
- **THEN** uma linha é inserida em `tb_analise_workout` com `status = COMPLETED`
- **THEN** todos os campos de análise estão preenchidos em português

#### Scenario: Apenas uma análise por treino (idempotência)
- **WHEN** `TreinoRegistradoEvent` é recebido para um `treinoRealizadoId` que já tem análise `COMPLETED`
- **THEN** nenhuma nova análise é iniciada

---

### Requirement: Endpoint para recuperar análise de treino

O sistema SHALL expor `GET /api/v1/analise/treino/{treinoRealizadoId}` com:
- Autenticação JWT obrigatória (`@PreAuthorize("isAuthenticated()")`)
- Multi-tenant: só retorna análise do tenant do token JWT
- Retorna `200 OK` com `AnaliseWorkoutOutputDto` se análise existe
- Retorna `204 No Content` se análise ainda não existe (em processamento)
- Retorna `404 Not Found` se `treinoRealizadoId` não existe no tenant

#### Scenario: Análise disponível
- **WHEN** `GET /api/v1/analise/treino/{id}` é chamado com JWT válido e análise existe
- **THEN** retorna `200 OK` com `AnaliseWorkoutOutputDto`

#### Scenario: Análise ainda processando
- **WHEN** `GET /api/v1/analise/treino/{id}` é chamado e análise não existe ainda
- **THEN** retorna `204 No Content`

#### Scenario: Treino de outro tenant
- **WHEN** `GET /api/v1/analise/treino/{id}` é chamado com JWT de tenant diferente do dono do treino
- **THEN** retorna `404 Not Found` (nunca expõe dados cross-tenant)
