## Context

O Menthoros já adota uma estratégia parcialmente híbrida entre regras determinísticas e IA. O `PlanoTreinoPromptBuilder` compila um contexto rico para o modelo, enquanto serviços como `IntervaladoElegibilidadeService` e `MetricasAlertaService` já tomam decisões fisiológicas importantes sem depender do LLM. A aplicação roda em Spring Boot 3.5.4, Java 21, Spring AI, JPA, Flyway e multi-tenancy por `tenant_id`.

O projeto também já possui modelagem para granularidade de treino via `EtapaRealizada`, mas essa granularidade ainda está subutilizada em cálculos e interpretações centrais. Há ainda um roadmap de integração Strava que deve trazer laps/splits, o que torna urgente uma camada que consiga consumir esses dados de forma estruturada.

## Goals / Non-Goals

**Goals:**
- formalizar uma camada de skills determinísticas de domínio
- consolidar resultados das skills em um snapshot estruturado reutilizável
- reduzir dependência do LLM para decisões críticas de prescrição
- validar planos gerados por IA antes de persistir
- habilitar análise de treino mais precisa com base em `EtapaRealizada`
- persistir resultados de skills para auditoria e explainability

**Non-Goals:**
- substituir completamente o LLM na geração textual de planos
- implementar toda a integração Strava nesta mudança
- criar frontend ou dashboard específico para skill results
- migrar todo o código existente de uma vez para a nova camada

## Decisions

### D1: Arquitetura híbrida com domain skills como núcleo

**Decisão:** Adotar skills determinísticas de domínio como fonte principal de decisão esportiva, deixando o LLM como camada de composição e explicação.

**Rationale:** Prescrição de treino, autorização de intensidade e interpretação de fadiga são áreas sensíveis demais para depender apenas do raciocínio do modelo. O projeto já possui lógica determinística útil; a mudança organiza e centraliza esse conhecimento.

**Alternativa descartada:** deixar o LLM no centro da decisão com tools auxiliares. Isso aumentaria risco de incoerência e reduziria auditabilidade.

---

### D2: Novo pacote `skills/` com contratos formais

**Decisão:** Criar uma camada dedicada em `src/main/java/com/menthoros/skills/` contendo:

- `DomainSkill`
- `SkillContext`
- `SkillResult`
- `SkillRegistry`
- `SkillOrchestratorService`
- DTOs de resumo/snapshot

**Rationale:** O conhecimento hoje está fragmentado entre `services/helper`, `services/prompt` e documentação. Um contrato explícito facilita reaproveitamento, testes e versionamento.

---

### D3: Snapshot estruturado antes do prompt

**Decisão:** Gerar um `AthleteAnalysisSnapshot` antes da chamada ao LLM e injetar sua versão serializada no `PlanoTreinoPromptBuilder`.

**Rationale:** Hoje o contexto chega ao modelo majoritariamente como texto livre. Um snapshot estruturado reduz ambiguidade, melhora consistência e viabiliza auditoria do que realmente embasou a prescrição.

**Alternativa descartada:** continuar expandindo apenas o prompt textual. Isso escala mal e dificulta rastrear origem das decisões.

---

### D4: Guard rail determinístico pós-LLM

**Decisão:** Introduzir a skill `training-prescription-guard` como última etapa antes da persistência de um plano.

Ela deve validar, no mínimo:

- volume semanal versus média recente
- TSS do plano versus meta calculada
- máximo de dias consecutivos
- restrições de lesão
- coerência com fase da periodização
- repetição indevida de estímulos
- adequação ao nível do atleta

**Rationale:** Mesmo com bom prompt, o LLM pode produzir plano estruturalmente válido e fisiologicamente inadequado. O guard rail fecha essa lacuna.

---

### D5: Persistência de resultados de skill

**Decisão:** Criar uma entidade/tabela de execução de skills para armazenar resultado estruturado, evidências e recomendações.

Campos esperados:

- `skillKey`
- `skillVersion`
- `severity`
- `confidence`
- `payloadJson`
- `evidenceJson`
- `recommendationsJson`
- associação opcional com atleta, treino realizado e plano semanal

**Rationale:** Auditoria, comparabilidade entre versões, explainability e preparação para analytics.

---

### D6: Refatoração incremental por delegação

**Decisão:** Não remover imediatamente classes atuais como `IntervaladoElegibilidadeService` e `MetricasAlertaService`. Primeiro elas passam a delegar para as novas skills; depois podem ser convertidas em facades/adapters.

**Rationale:** Minimiza risco e evita grande refatoração transversal em uma única entrega.

---

### D7: `EtapaRealizada` como fonte preferencial para análise

**Decisão:** Skills de análise de treino devem priorizar dados de `EtapaRealizada` quando disponíveis, com fallback para métricas agregadas em `TreinoRealizado`.

**Rationale:** Sessões intervaladas e longões com variação interna são mal representados por médias simples. A análise por etapa melhora precisão e prepara o sistema para Strava laps.

---

### D8: Agent skills/tools apenas como camada de consulta

**Decisão:** Permitir exposição de skills ao ecossistema Spring AI como tools, mas apenas para:

- revisão narrativa
- explicações
- chat assistido
- sumarização do snapshot

**Rationale:** Isso preserva o melhor dos dois mundos: decisão segura e comunicação flexível.

## Risks / Trade-offs

**[Risco] Complexidade arquitetural maior** → Mais componentes, contratos e tipos. Mitigação: introdução em fases e forte cobertura de testes.

**[Risco] Regras rígidas demais** → Thresholds mal calibrados podem impedir prescrição adequada. Mitigação: versionamento, datasets de validação e calibração progressiva.

**[Risco] Valor limitado sem dados granulares** → Algumas skills ficarão restritas enquanto `EtapaRealizada` e Strava não forem plenamente aproveitados. Mitigação: priorizar fallback claro e atacar primeiro skills que já geram valor com dados atuais.

**[Trade-off] Mais trabalho inicial para reduzir dependência do LLM** → Há custo de modelagem e persistência, mas o ganho é estabilidade e explainability.

## Migration Plan

1. Criar pacote `skills/` com contratos base e orchestrator
2. Implementar `AthleteAnalysisSnapshot` e adaptar `PlanoTreinoPromptBuilder`
3. Formalizar skills equivalentes às decisões já existentes:
   - recovery/carga
   - elegibilidade de intervalado
4. Introduzir persistência de `SkillExecution`
5. Implementar skill de guarda de prescrição e conectá-la ao fluxo de geração de plano
6. Implementar skills de análise de treino com prioridade para uso de `EtapaRealizada`
7. Expor skills selecionadas como tools do Spring AI quando a camada determinística já estiver estável

## Open Questions

- Qual será o formato final serializado do `AthleteAnalysisSnapshot` no prompt: JSON bruto, markdown estruturado ou híbrido?
- A persistência de `SkillExecution` deve guardar payload integral ou apenas resumo + evidências?
- O recalculo pós-sync Strava será síncrono ou assíncrono quando as skills de treino estiverem ativas?
- O `training-prescription-guard` deve bloquear persistência integralmente ou permitir persistência com status `INVALIDADO` para inspeção?
