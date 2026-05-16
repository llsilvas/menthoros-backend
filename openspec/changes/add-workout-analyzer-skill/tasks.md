## 1. Infraestrutura Multi-Modelo

- [x] 1.1 Adicionar `spring-ai-anthropic-spring-boot-starter:${spring-ai.version}` em `pom.xml` (necessário para beans Haiku e Sonnet; `spring-ai-openai-spring-boot-starter` já existe)
- [x] 1.2 Criar enum `TaskComplexity` em `br.com.menthoros.backend.routing` (valores: SIMPLE, STANDARD, COMPLEX, EXPERT)
- [x] 1.3 Criar `MultiModelConfig.java` em `config/external/` com os 4 beans `ChatClient` nomeados por `@Qualifier`
- [x] 1.4 Criar `ModelRouter.java` em `routing/` que resolve `ChatClient` por `TaskComplexity`
- [x] 1.5 Escrever teste unitário `ModelRouterTest` validando roteamento para cada `TaskComplexity`
- [x] 1.6 Executar `./mvnw clean test` e garantir 0 falhas

## 2. Skill Resource e Configuração

- [x] 2.1 Criar pasta `src/main/resources/skills/analise/workout-analyzer/`
- [x] 2.2 Copiar `SKILL.md` de `menthoros-product/bmad/_bmad-output/implementation-artifacts/skills/workout-analyzer/SKILL-workout-analyzer.md` para `src/main/resources/skills/analise/workout-analyzer/SKILL.md`
- [x] 2.3 Copiar `calculate_execution_delta.py` de `menthoros-product/bmad/_bmad-output/implementation-artifacts/skills/workout-analyzer/scripts/` para `src/main/resources/skills/analise/workout-analyzer/scripts/`
- [x] 2.4 Verificar que `PromptTemplateLoader` consegue carregar `classpath:skills/analise/workout-analyzer/SKILL.md`

## 3. Camada de Tradução

- [x] 3.1 Criar `WorkoutAnalysisTranslator.java` em `services/` que aceita `AnaliseWorkoutRawDto` e retorna versão com campos textuais traduzidos para PT via `haikuChatClient`
- [x] 3.2 Garantir que `primary_cause`, `tags` e `execution_score` não são traduzidos
- [x] 3.3 Escrever teste de integração `WorkoutAnalysisTranslatorTest` (mock do `haikuChatClient`)

## 4. Persistência — Entidade e Migração

- [x] 4.1 Criar enum `AnaliseStatus` (PENDING, COMPLETED, FAILED) em `enums/`
- [x] 4.2 Criar enum `PrimaryAnalysisCause` (ACCUMULATED_FATIGUE, ENVIRONMENTAL_FACTORS, PACING_ERROR, CNS_FATIGUE, NORMAL, UNDERTRAINING) em `enums/`
- [x] 4.3 Criar entidade `AnaliseWorkout.java` em `entity/` com todos os campos definidos na spec `post-workout-analysis`
- [x] 4.4 Criar `AiWorkoutAnalysisRepository` em `repository/` estendendo `JpaRepository<AnaliseWorkout, UUID>`
- [x] 4.5 Criar Flyway migration `V{N}__add_analise_workout.sql` com DDL de `tb_analise_workout`

## 5. Evento e Listener de Análise

- [x] 5.1 Criar `TreinoRegistradoEvent` em `events/` com campos `treinoRealizadoId` e `tenantId`
- [x] 5.2 Publicar `TreinoRegistradoEvent` em `TreinoService.save()` via `ApplicationEventPublisher` após commit
- [x] 5.3 Criar `WorkoutAnalysisListener.java` em `services/impl/` com `@TransactionalEventListener(phase = AFTER_COMMIT)` e `@Async`
- [x] 5.4 Implementar lógica no listener: carregar treino + plano + métricas, chamar `sonnetChatClient` com prompt da skill, traduzir, persistir `AnaliseWorkout`
- [x] 5.5 Implementar gate: ignorar evento se `TreinoRealizado.rpe == null`
- [x] 5.6 Implementar idempotência: pular análise se já existe `AnaliseWorkout` com `status = COMPLETED` para o treino
- [x] 5.7 Escrever teste de integração `WorkoutAnalysisListenerTest` com `@SpringBootTest`

## 6. DTO e Endpoint de Consulta

- [x] 6.1 Criar record `AnaliseWorkoutOutputDto` em `dto/output/` com todos os campos da análise traduzida
- [x] 6.2 Criar `AnaliseWorkoutController.java` em `controller/` com `GET /api/v1/analise/treino/{treinoRealizadoId}`
- [x] 6.3 Aplicar `@PreAuthorize("isAuthenticated()")` e `@RequireTenant` no controller
- [x] 6.4 Implementar isolamento multi-tenant: query por `treinoRealizadoId` AND `tenantId`
- [x] 6.5 Retornar `204 No Content` se análise não encontrada; `404 Not Found` se treino não pertence ao tenant
- [x] 6.6 Adicionar `@Tag`, `@Operation` e `@ApiResponses` completos conforme CLAUDE.md

## 7. Validação Final

- [x] 7.1 Executar `./mvnw clean test` — todos os testes devem passar (259+ existentes + novos)
- [x] 7.2 Verificar que nenhum bean existente foi quebrado (especialmente `IaService`)
- [x] 7.3 Executar greps de red flags do CLAUDE.md:
  ```bash
  grep -r "@Autowired.*Repository" src/main/java/br/com/menthoros/backend/controller/
  grep -r "public class.*OutputDto" src/main/java/br/com/menthoros/backend/dto/
  ```
- [x] 7.4 Atualizar este `tasks.md` com checkmarks para todas as tasks concluídas
