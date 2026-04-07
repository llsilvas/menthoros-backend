## 1. Fundacional - Contratos e Orquestração

- [ ] 1.1 Criar pacote `src/main/java/com/menthoros/skills/`
- [ ] 1.2 Criar `DomainSkill.java`, `SkillContext.java` e `SkillResult.java`
- [ ] 1.3 Criar enums/objetos auxiliares como `SkillCategory`, `SkillSeverity` e `SkillConfidence`
- [ ] 1.4 Criar `SkillRegistry.java` para descoberta/registro de skills Spring
- [ ] 1.5 Criar `SkillOrchestratorService.java` para execução ordenada e consolidação de resultados
- [ ] 1.6 Criar `AthleteAnalysisSnapshot.java` e DTOs/resumos associados

## 2. Persistência e Modelo de Dados

- [ ] 2.1 Criar entidade `SkillExecution.java`
- [ ] 2.2 Criar migration Flyway para tabela de execuções/resultados de skill
- [ ] 2.3 Criar `SkillExecutionRepository.java`
- [ ] 2.4 Implementar serialização segura de `payload`, `evidence` e `recommendations`

## 3. Formalização de Skills Existentes

- [ ] 3.1 Criar skill de recuperação/carga a partir da lógica de `MetricasAlertaService`
- [ ] 3.2 Criar skill de elegibilidade intervalada a partir de `IntervaladoElegibilidadeService`
- [ ] 3.3 Adaptar serviços atuais para delegarem às novas skills sem quebrar contratos existentes
- [ ] 3.4 Garantir versionamento inicial das skills formalizadas

## 4. Integração com Geração de Plano

- [ ] 4.1 Adaptar `TreinoHistoricoProvider` para fornecer contexto adequado às skills
- [ ] 4.2 Integrar `SkillOrchestratorService` ao fluxo de geração de plano
- [ ] 4.3 Fazer `PlanoTreinoPromptBuilder` consumir `AthleteAnalysisSnapshot`
- [ ] 4.4 Adaptar `IaServiceImpl` para usar o snapshot como base do prompt

## 5. Capability: training-prescription-guard

- [ ] 5.1 Criar `TrainingPrescriptionGuardSkill.java`
- [ ] 5.2 Validar TSS do plano versus meta semanal
- [ ] 5.3 Validar volume e progressão versus histórico recente
- [ ] 5.4 Validar dias consecutivos, lesão e restrições fisiológicas
- [ ] 5.5 Validar coerência com fase de periodização e rotação de estímulos
- [ ] 5.6 Bloquear ou sinalizar persistência de planos inválidos

## 6. Capability: workout-analysis-skills

- [ ] 6.1 Criar `IntervalWorkoutAnalysisSkill.java`
- [ ] 6.2 Criar `LongRunAnalysisSkill.java`
- [ ] 6.3 Priorizar `EtapaRealizada` como fonte de análise quando disponível
- [ ] 6.4 Implementar fallback para métricas agregadas em `TreinoRealizado`
- [ ] 6.5 Expor resultados estruturados no fluxo pós-treino

## 7. Precisão de Dados e Aproveitamento de Etapas

- [ ] 7.1 Adaptar `TssCalculatorService` para calcular TSS por etapas quando `EtapaRealizada` existir
- [ ] 7.2 Comparar execução planejada versus realizada por etapa quando houver `etapaPlanejada`
- [ ] 7.3 Preparar contratos de skills para futura ingestão de laps/splits do Strava

## 8. Testes

- [ ] 8.1 Criar testes unitários para `SkillOrchestratorService`
- [ ] 8.2 Criar testes unitários para skill de recuperação/carga
- [ ] 8.3 Criar testes unitários para skill de elegibilidade intervalada
- [ ] 8.4 Criar testes unitários para `TrainingPrescriptionGuardSkill`
- [ ] 8.5 Criar testes unitários para `IntervalWorkoutAnalysisSkill` e `LongRunAnalysisSkill`
- [ ] 8.6 Criar testes de regressão com cenários por nível: iniciante, intermediário, avançado e elite

## 9. Agent Layer

- [ ] 9.1 Definir quais skills podem ser expostas ao Spring AI como tools
- [ ] 9.2 Expor apenas skills de consulta/explicação, nunca as de decisão crítica
- [ ] 9.3 Validar que o LLM consulta resultados das skills sem sobrescrever constraints determinísticas
