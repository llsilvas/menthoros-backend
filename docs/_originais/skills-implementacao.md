# Skills — Guia de Implementação Prático

## O que são Skills no contexto do Menthoros

Skills são **ferramentas (tools) que o LLM pode invocar durante o raciocínio** para buscar
dados sob demanda, em vez de receber tudo pré-carregado no prompt. O Claude decide quando
e o que buscar — cada busca é auditável e logada.

A abordagem atual do `buildOptimizedPrompt()` empurra ~3.400–3.800 tokens antes de cada
chamada independentemente do que a semana precisa. Com Skills, o agente busca o que precisa:

```
ANTES (push total):
  buildOptimizedPrompt() → ~3.600 tokens → 1 chamada ao LLM

DEPOIS (pull sob demanda):
  system prompt enxuto (~950 tokens) + Claude chama tools conforme raciocina
  → semana regenerativa: 2 tools → ~1.550 tokens totais (-57%)
  → semana de pico: 5 tools → ~3.100 tokens totais (-18%, porém mais preciso)
```

---

## Pergunta Frequente: com `.md`, ainda preciso do `@Tool` na classe?

**Não.** São dois caminhos mutuamente exclusivos no Spring AI:

| Abordagem | Descrição para o LLM | `@Tool` necessário? |
|---|---|---|
| `@Tool(description="...")` + `.tools(bean)` | Anotação no código (compile-time) | **Sim** |
| `FunctionCallbackWrapper.builder()` + `.withDescription(md)` | Arquivo `.md` em runtime | **Não** |

Com `FunctionCallbackWrapper`, os `*Tools.java` ficam **completamente livres de Spring AI**.
São classes Java puras — sem imports de `org.springframework.ai`, testáveis como qualquer
`@Service`. Todo o wiring com o LLM fica isolado no `ToolsConfig.java`:

```java
// MetricasTools.java — Java puro, sem @Tool
@Component
@RequiredArgsConstructor
public class MetricasTools {
    private final PlanoMetaDadosRepository repo;

    // Método Java comum — a descrição para Claude está no .md, não aqui
    public MetricasAtuaisResult calcularMetricasAtuais(String atletaId) {
        // ...
    }
}

// ToolsConfig.java — único lugar com Spring AI
FunctionCallbackWrapper.builder(metricasTools::calcularMetricasAtuais)
    .withName("calcularMetricasAtuais")
    .withDescription(templateLoader.loadTemplate("skills/skill-metricas-atuais.md"))
    .build()
```

---

## Estrutura de Arquivos

```
src/main/resources/
├── prompts/
│   ├── system-prompt.txt                    (existente — regras gerais do sistema)
│   ├── plano-treino-otimizado-claude.txt    (existente — template do plano)
│   └── skills/                              ← NOVO subdiretório
│       ├── skill-metricas-atuais.md         ← descrição carregada em runtime
│       ├── skill-historico-treinos.md
│       ├── skill-macrociclo-ativo.md
│       ├── skill-readiness-dia.md
│       ├── skill-predicao-prova.md
│       ├── skill-debrief-pos-treino.md
│       ├── skill-previsao-climatica.md
│       └── skill-aderencia-plano.md
│
└── tools-manifest.yml                       ← feature flags por contexto

src/main/java/com/menthoros/services/
├── prompt/              (existente — formatters)
└── tools/               ← NOVO pacote
    ├── MetricasTools.java
    ├── TreinoHistoricoTools.java
    ├── MacrocicloPlanejamentoTools.java
    ├── ProvaTools.java
    └── ToolsConfig.java  (@Configuration — wiring com ChatClient)
```

**Separação de responsabilidades:**

```
skill-{nome}.md        ← O QUE a tool faz (editável por produto/coach sem tocar Java)
{Nome}Tools.java       ← COMO executa (lógica Java pura, sem Spring AI)
tools-manifest.yml     ← QUANDO está disponível (feature flag, contexto)
ToolsConfig.java       ← ONDE é registrada (Spring AI wiring, único ponto de acoplamento)
```

---

## Exemplo Completo — Cenário Real: Semana Regenerativa

**Atleta:** INTERMEDIÁRIO | TSB = -25 | `alertaDiasConsecutivos = true`
**Resultado do Gate 1 (IntervaladoElegibilidadeService):** `Substituido(CONTINUO)` — intervalado proibido.

### Passo 1 — Criar `skill-metricas-atuais.md`

```markdown
## calcularMetricasAtuais

**Propósito:** Retorna as métricas de carga e fadiga atuais do atleta:
TSB (forma), CTL (fitness), ATL (fadiga), ramp rate e interpretações.

**Quando chamar:** Quando precisar calibrar volume e intensidade da semana.
Chame ANTES de definir TSS-alvo. Sempre útil como primeiro passo.

**Parâmetro:** `atletaId` (String UUID)

**Retorna:**
- `tsbAtual` — Training Stress Balance. Negativo = fatigado, positivo = descansado.
  Faixa normal: -10 a +10. Abaixo de -20: reduzir volume obrigatoriamente.
- `ctlAtual` — Fitness atual (Chronic Training Load). Base para calcular TSS-alvo semanal.
- `atlAtual` — Fadiga aguda. Alta = treinos recentes pesados.
- `rampRateAtual` — Variação de CTL/semana. Acima de +8: risco de overtraining.
- `interpretacaoTsb` — "FORMA_OTIMA" | "LEVEMENTE_FATIGADO" | "MUITO_FATIGADO" | "SUPER_COMPENSACAO"
- `alertaDiasConsecutivos` — boolean. Se true: obrigatório incluir ao menos 1 dia de descanso.
- `diasConsecutivosTreino` — quantos dias seguidos o atleta treinou.
```

### Passo 2 — Criar `skill-historico-treinos.md`

```markdown
## buscarHistoricoTreinos

**Propósito:** Busca os treinos realizados pelo atleta nos últimos N dias.

**Quando chamar:** Para entender padrão recente — tipos de treino, gaps de descanso,
RPE reportado. Para semanas regenerativas: diasJanela=7 é suficiente.
Para análise de variabilidade ou tendência: diasJanela=28.
Não chame se a semana for puramente REGENERATIVO sem contexto histórico necessário.

**Parâmetros:**
- `atletaId` (String UUID)
- `diasJanela` (int): 7, 14 ou 28

**Retorna:** lista com por treino:
- `dataTreino`, `tipoTreino`, `duracaoMin`, `distanciaKm`
- `tssCalculado`, `percepcaoEsforco` (RPE 1–10)
- `descricao` (observação livre do atleta)
```

### Passo 3 — Criar `MetricasTools.java`

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricasTools {

    private final PlanoMetaDadosRepository planoMetaDadosRepository;

    // Record leve — não expõe entidade JPA ao LLM
    public record MetricasAtuaisResult(
            Double tsbAtual,
            Double ctlAtual,
            Double atlAtual,
            Double rampRateAtual,
            String interpretacaoTsb,
            Boolean alertaDiasConsecutivos,
            Integer diasConsecutivosTreino
    ) {}

    // Método Java puro — sem @Tool, sem Spring AI
    public MetricasAtuaisResult calcularMetricasAtuais(String atletaId) {
        log.info("[TOOL] calcularMetricasAtuais — atletaId={}", atletaId);

        PlanoMetaDados meta = planoMetaDadosRepository
                .findFirstByAtletaIdOrderByDataAtualizacaoDesc(UUID.fromString(atletaId))
                .orElseThrow(() -> new IllegalArgumentException("Atleta sem metadados: " + atletaId));

        return new MetricasAtuaisResult(
                meta.getTsbAtual(),
                meta.getCtlAtual(),
                meta.getAtlAtual(),
                meta.getRampRateAtual(),
                meta.getInterpretacaoTsb(),
                meta.getAlertaDiasConsecutivos(),
                meta.getDiasConsecutivosTreino()
        );
    }
}
```

### Passo 4 — Criar `TreinoHistoricoTools.java`

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class TreinoHistoricoTools {

    private final TreinoRealizadoRepository treinoRepo;

    public record TreinoResumo(
            LocalDate dataTreino,
            String tipoTreino,
            Integer duracaoMin,
            Double distanciaKm,
            Integer tssCalculado,
            Integer percepcaoEsforco,
            String descricao
    ) {}

    // Parâmetro composto — Spring AI serializa automaticamente via JSON
    public record BuscarHistoricoInput(String atletaId, int diasJanela) {}

    // Método Java puro — sem @Tool
    public List<TreinoResumo> buscarHistoricoTreinos(BuscarHistoricoInput input) {
        log.info("[TOOL] buscarHistoricoTreinos — atletaId={} diasJanela={}",
                input.atletaId(), input.diasJanela());

        LocalDate inicio = LocalDate.now().minusDays(input.diasJanela());
        return treinoRepo
                .findByAtletaIdAndDataTreinoAfterOrderByDataTreinoDesc(
                        UUID.fromString(input.atletaId()), inicio)
                .stream()
                .map(t -> new TreinoResumo(
                        t.getDataTreino(),
                        t.getTipoTreino().name(),
                        t.getDuracaoMin(),
                        t.getDistanciaKm() != null ? t.getDistanciaKm().doubleValue() : 0.0,
                        t.getTssCalculado(),
                        t.getPercepcaoEsforco(),
                        t.getObservacao()))
                .toList();
    }
}
```

### Passo 5 — Criar `ToolsConfig.java`

```java
@Configuration
@RequiredArgsConstructor
public class ToolsConfig {

    // Todo o acoplamento com Spring AI fica aqui — único ponto de mudança
    private final PromptTemplateLoader templateLoader;
    private final MetricasTools metricasTools;
    private final TreinoHistoricoTools treinoHistoricoTools;

    @Bean("toolsPlanoSemanal")
    public List<FunctionCallback> toolsPlanoSemanal() {
        return List.of(
            // Descrição vem do .md — sem texto hardcoded aqui
            FunctionCallbackWrapper.builder(metricasTools::calcularMetricasAtuais)
                .withName("calcularMetricasAtuais")
                .withDescription(templateLoader.loadTemplate("skills/skill-metricas-atuais.md"))
                .withInputType(String.class)  // atletaId direto
                .build(),

            FunctionCallbackWrapper.builder(treinoHistoricoTools::buscarHistoricoTreinos)
                .withName("buscarHistoricoTreinos")
                .withDescription(templateLoader.loadTemplate("skills/skill-historico-treinos.md"))
                .withInputType(TreinoHistoricoTools.BuscarHistoricoInput.class)
                .build()
        );
    }
}
```

### Passo 6 — Modificar `IaServiceImpl.geraPlanoSemanalAvancado()`

```java
// ANTES (linhas 236–245 atuais):
String prompt = promptBuilder.buildOptimizedPrompt(atleta, metaDados, prova, inicioSemana);

PlanoSemanalLlmDto plano = chatClient.prompt()
        .user(prompt)
        .options(defaultJsonSchemaOptions())
        .call()
        .entity(PlanoSemanalLlmDto.class);

// DEPOIS — system prompt só com restrições hard + tools disponíveis:
String systemPromptComRestricoes = promptBuilder.buildSystemPromptComRestricoes(
        atleta, metaDados, prova, inicioSemana);
// buildSystemPromptComRestricoes mantém: perfil do atleta, decisão intervalado,
// alertas obrigatórios, restrições de lesão, contexto macrociclo (se ativo).
// Remove: histórico 28d, métricas, variabilidade, volume 3 semanas (viram tools).

PlanoSemanalLlmDto plano = chatClient.prompt()
        .system(systemPromptComRestricoes)
        .user("Gere o plano semanal para o atleta "
              + atleta.getId() + " referente à semana de " + inicioSemana)
        .tools(toolsPlanoSemanal)  // Spring AI: multi-turn interno automático
        .options(defaultJsonSchemaOptions())
        .call()
        .entity(PlanoSemanalLlmDto.class);
```

---

## Trace de Raciocínio do Agente (semana regenerativa)

O que acontece internamente após `.tools(toolsPlanoSemanal).call()`:

```
→ Claude recebe:
  - Perfil: INTERMEDIARIO, FCmax=180, dias disponíveis: seg/qua/sex/sab
  - [PROIBIDO] treino intervalado — Gate 1 ativo: dias consecutivos + TSB extremo
  - Macrociclo: semana 8/18, fase BASE, TSS-alvo: 180 (recuperação)

Turno 1 — Claude raciocina internamente:
  "Preciso confirmar exatamente o estado de fadiga antes de prescrever volumes."
  → tool_use: calcularMetricasAtuais("uuid-atleta")
  ← resultado: { tsbAtual: -25, ctlAtual: 48, interpretacaoTsb: "MUITO_FATIGADO",
                  alertaDiasConsecutivos: true, diasConsecutivosTreino: 6 }

Turno 2 — Claude raciocina:
  "6 dias seguidos, TSB=-25. Semana claramente regenerativa.
   Preciso ver o que foi feito para entender o que recuperar."
  → tool_use: buscarHistoricoTreinos({ atletaId: "uuid", diasJanela: 7 })
  ← resultado: [
      { INTERVALADO 50min TSS=85 RPE=9 },
      { CONTINUO 65min TSS=70 RPE=7 },
      { LONGO 90min TSS=110 RPE=8 },
      { CONTINUO 45min TSS=55 RPE=7 },
      { TIRO 40min TSS=75 RPE=9 }
    ]

Turno 3 — Claude raciocina:
  "RPE alto nos 5 últimos dias, TSS acumulado da semana ~395.
   Semana anterior muito pesada. TSS-alvo desta semana: 120 máximo.
   Distribuição: 2 × REGENERATIVO + 1 × CONTINUO leve. Sem intervalados.
   Dados suficientes — sem necessidade de mais tool calls."
  → [STOP] → gera PlanoSemanalLlmDto

Resultado:
  - Segunda: REGENERATIVO 25min Z1 — TSS=20
  - Quarta:  CONTINUO 40min Z2 — TSS=45
  - Sexta:   REGENERATIVO 20min Z1 — TSS=15
  - TSS total: 80 (abaixo do alvo 120 por margem de segurança com TSB=-25)
```

**Tokens consumidos:**
```
System prompt (restrições hard):  ~950 tokens
Tool call 1 (request + response): ~280 tokens
Tool call 2 (request + response): ~420 tokens
Output (PlanoSemanalLlmDto):      ~450 tokens
─────────────────────────────────────────────
TOTAL:                           ~2.100 tokens

ANTES (buildOptimizedPrompt completo): ~3.650 tokens
REDUÇÃO: 43% neste cenário
```

---

## `tools-manifest.yml` — Feature Flags por Contexto

```yaml
# src/main/resources/tools-manifest.yml
tools:
  metricas-atuais:
    enabled: true
    skill-file: prompts/skills/skill-metricas-atuais.md
    contexts: [PLANO_SEMANAL, AI_DEBRIEF, ADAPTATION_TRACKING]

  historico-treinos:
    enabled: true
    skill-file: prompts/skills/skill-historico-treinos.md
    contexts: [PLANO_SEMANAL, AI_DEBRIEF, ADAPTATION_TRACKING]
    max-dias-janela: 28

  macrociclo-ativo:
    enabled: true
    skill-file: prompts/skills/skill-macrociclo-ativo.md
    contexts: [PLANO_SEMANAL]

  readiness-dia:
    enabled: true
    skill-file: prompts/skills/skill-readiness-dia.md
    contexts: [PLANO_SEMANAL, AI_DEBRIEF]

  predicao-prova:
    enabled: true
    skill-file: prompts/skills/skill-predicao-prova.md
    contexts: [PLANO_SEMANAL]

  previsao-climatica:
    enabled: false   # Feature 8 — habilitar após implementar ClimaTreinoService
    skill-file: prompts/skills/skill-previsao-climatica.md
    contexts: [PLANO_SEMANAL]

  aderencia-plano:
    enabled: false   # Feature 5 — habilitar após implementar AderenciaPlanoService
    skill-file: prompts/skills/skill-aderencia-plano.md
    contexts: [PLANO_SEMANAL, AI_DEBRIEF]
```

`enabled: false` desabilita a tool sem recompilação. Com Spring Cloud Config,
nem reinício é necessário — toggle em runtime.

---

## O que NUNCA deve virar tool

| Componente | Por que fica no system prompt |
|---|---|
| `IntervaladoElegibilidadeService` | Decisão de segurança fisiológica — Claude não pode "optar" por não verificar |
| `SemanaPlano` do Macrociclo | Restrição hard de TSS/fase — deve ser visível antes de qualquer raciocínio |
| `alertasObrigatorios` (lesão, fadiga extrema) | Se Claude não vir, pode gerar prescrição perigosa |
| `restricoesLesoes` | Idem — não pode depender do Claude decidir chamar |

**Regra:** se omitir pode gerar prescrição perigosa → vai no system prompt.
Se omitir apenas reduz personalização → vira tool.

---

## Ganhos de Produto

### Para o Coach (B2B)
Cada tool call aparece no log com parâmetros e resposta. O coach passa a ter rastreabilidade
completa da decisão da IA — auditável treino a treino:

```
[TOOL] calcularMetricasAtuais → TSB=-25 CTL=48 (MUITO_FATIGADO)
[TOOL] buscarHistoricoTreinos diasJanela=7 → 5 treinos (TSS acumulado: 395)
[IA] Plano gerado: 2 × REGENERATIVO + 1 × CONTINUO — TSS=80
```

Em vez de hoje, onde o log mostra apenas:
```
[INFO] Plano gerado com sucesso para atleta abc — 4.2s
```

### Para o Atleta (B2C)
O campo `justificativaIa` do `TreinoPlanejadoLlmDto` passa a referenciar dados reais:

> "Semana regenerativa: TSB=-25 após 6 dias consecutivos (RPE médio 8.4 nos últimos 5 treinos).
> Volume reduzido a 40% do habitual para permitir supercompensação antes da próxima carga."

vs. hoje (texto genérico, sem rastreamento de qual dado originou a decisão):

> "Semana de recuperação para reduzir fadiga acumulada."

### Técnico / Custo
| Métrica | Antes | Com Skills |
|---|---|---|
| Tokens — semana regenerativa | ~3.650 | ~2.100 (-43%) |
| Tokens — semana de pico BUILD | ~3.800 | ~3.200 (-16%, mais preciso) |
| Adicionar nova feature ao contexto | Modificar `PlanoTreinoPromptBuilder` | Novo `.md` + método Java + entry no manifest |
| Feature flag sem redeploy | Não | `enabled: false` no YAML |
| Depuração de erro de prescrição | Impossível | Log por tool call com payload |

---

## Convenções a Seguir

| Aspecto | Convenção |
|---|---|
| `*Tools.java` | `@Component @RequiredArgsConstructor @Slf4j`, sem `@Tool`, sem imports Spring AI |
| Método de tool | Retorna record leve (não entidade JPA), loga `[TOOL] nomeTool — param=valor` |
| Parâmetro único | `String` direto (ex: `atletaId`) |
| Parâmetros múltiplos | Record inner `Input` (ex: `BuscarHistoricoInput`) |
| Descrição | Exclusivamente no `.md` — sem texto na classe Java |
| `ToolsConfig.java` | Único arquivo com imports Spring AI do pacote `tools/` |
| Skill file | Seções: `## nomeTool`, `**Propósito:**`, `**Quando chamar:**`, `**Parâmetros:**`, `**Retorna:**` |
