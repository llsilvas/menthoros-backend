# Plano de Refatoracao - Pacote Services

**Data:** 10/02/2026
**Autor:** Leandro + Claude
**Status:** Aguardando Aprovacao
**Escopo:** `com.menthoros.services` (interfaces, impl, helper, prompt)

---

## 1. Diagnostico da Arquitetura Atual

### 1.1 Mapa de Dependencias (Service -> Service)

```
PlanoServiceImpl
├── IaService
├── AtletaRepository
├── TreinoRealizadoRepository
├── PlanoSemanalRepository
├── PlanoMetadadosRepository
├── PlanoMetadadosService
├── TsbService
├── EmbeddingService
├── RedistribuicaoTreinoHelper
├── RegraGeracaoTreino
├── AtletaMapper, TreinoMapper, PlanoSemanalMapper
└── 639 linhas

IaServiceImpl (@Primary)
├── ChatClient (Spring AI)
├── PlanoTreinoPromptBuilder
├── AtletaRepository          ← acessa repositorio diretamente
└── 808 linhas

TreinoServiceImpl
├── TsbServiceImpl            ← DIP: depende da impl concreta, nao da interface
├── AtletaRepository
├── TreinoRealizadoRepository
├── PlanoSemanalRepository
├── TreinoPlanejadoRepository
├── PlanoMetadadosRepository
├── TreinoMapper, PlanoSemanalMapper
└── 287 linhas

TsbServiceImpl
├── TreinoRealizadoRepository
├── PlanoMetadadosRepository
├── MetricasDiariasRepository
├── AtletaRepository
└── 911 linhas

PlanoTreinoPromptBuilder
├── ProvaRepository            ← builder nao deveria acessar repos
├── TreinoRealizadoRepository  ← builder nao deveria acessar repos
├── PromptTemplateLoader
├── TsbService
├── PlanoMetadadosService
└── ~2475 linhas (MAIOR arquivo do projeto)

AtletaServiceImpl ............. 114 linhas (ok)
PlanoMetadadosServiceImpl ..... 84 linhas (ok)
EmbeddingServiceImpl .......... 36 linhas (ok)
```

### 1.2 Arquivos por Tamanho (LOC)

| Arquivo | Linhas | Status |
|---------|--------|--------|
| PlanoTreinoPromptBuilder.java | 2475 | Critico - decompor |
| TsbServiceImpl.java | 911 | Alto - decompor |
| IaServiceImpl.java | 808 | Alto - extrair responsabilidades |
| PlanoServiceImpl.java | 639 | Medio - ok apos limpeza |
| SpringAiEnhancedIaServiceImpl_old.java | 536 | Deletar |
| SpringAiFixedIaServiceImpl.java | 381 | Avaliar necessidade |
| TreinoServiceImpl.java | 287 | Medio - corrigir DIP + dead code |
| RedistribuicaoTreinoHelper.java | 246 | Ok |
| PromptTemplateLoader.java | 186 | Baixo - fixes pontuais |
| AtletaServiceImpl.java | 114 | Ok |
| PlanoMetadadosServiceImpl.java | 84 | Ok |
| EmbeddingServiceImpl.java | 36 | Ok |

### 1.3 Cobertura de Testes

Apenas **1 arquivo de teste** para todo o pacote services:
- `PlanoServiceImplTest.java`

Classes sem **nenhum teste**:
- TsbServiceImpl (calculos criticos de TSS/CTL/ATL/TSB)
- IaServiceImpl (validacao e normalizacao)
- TreinoServiceImpl
- PlanoTreinoPromptBuilder
- RedistribuicaoTreinoHelper

---

## 2. Bugs Identificados (P0)

### BUG-01: IndexOutOfBoundsException em `TsbServiceImpl.contarDiasConsecutivos`
**Arquivo:** [TsbServiceImpl.java:881-882](src/main/java/com/menthoros/services/impl/TsbServiceImpl.java#L881-L882)

```java
// Loop comeca em i=0 mas acessa dateList.get(i - 1) → indice -1
for (int i = 0; i < dateList.size(); i++) {
    if(dateList.get(i).equals(dateList.get(i - 1).plusDays(1))){
```

**Fix:** Iniciar loop em `i = 1`.

### BUG-02: No-op String Replace em `AtletaServiceImpl`
**Arquivo:** [AtletaServiceImpl.java:89](src/main/java/com/menthoros/services/impl/AtletaServiceImpl.java#L89)

```java
// Substitui "[" por "[" e "]" por "]" → nao faz absolutamente nada
String vetorFormatado = vetor.toString().replace("[", "[").replace("]", "]");
```

**Fix:** Remover as chamadas `.replace()` inuteis ou corrigir se a intencao era outra (ex: remover colchetes).

### BUG-03: Validacao vs Mensagem inconsistentes em `IaServiceImpl`
**Arquivo:** [IaServiceImpl.java:569-574](src/main/java/com/menthoros/services/impl/IaServiceImpl.java#L569-L574)

```java
if (etapas.size() < 6) {                              // valida < 6
    log.error("... (minimo 8)", etapas.size());        // mensagem diz 8
    throw new LLMException("... (minimo 8 para...)");  // mensagem diz 8
}
```

**Fix:** Alinhar o valor `6` com a mensagem `8` (ou vice-versa). Extrair para constante.

### BUG-04: Divisao por zero potencial em `PlanoTreinoPromptBuilder`
**Arquivo:** PlanoTreinoPromptBuilder.java (~linha 906)

```java
double volumeMedioPorTreino = metaDados.getVolumeSemanalMedio().doubleValue() /
        metaDados.getTreinosPorSemanaMedio(); // pode ser 0.0
```

**Fix:** Adicionar guard clause `if (treinosPorSemanaMedio == 0) return 0`.

---

## 3. Plano de Implementacao

### Fase 1: Limpeza e Correcoes Imediatas (1-2 dias)

> **Objetivo:** Eliminar dead code, corrigir bugs, fixes pontuais. Zero risco de regressao.

#### 1.1 Deletar dead code
- [ ] Deletar `SpringAiEnhancedIaServiceImpl_old.java` (536 linhas)
- [ ] Deletar metodos mortos em `TreinoServiceImpl`:
  - `gerarEmbedding()` (linha 271) - retorna null, nunca usado
  - `calcularTSB()` (linha 275) - retorna null, nunca usado
  - `calcularVolumeUltimaSemana()` (linha 279) - retorna null, nunca usado

#### 1.2 Corrigir bugs P0
- [ ] Fix BUG-01: `contarDiasConsecutivos` loop de `i=0` → `i=1`
- [ ] Fix BUG-02: Remover `.replace()` no-op em `AtletaServiceImpl`
- [ ] Fix BUG-03: Alinhar validacao com mensagem em `IaServiceImpl`
- [ ] Fix BUG-04: Guard clause divisao por zero em `PlanoTreinoPromptBuilder`

#### 1.3 Fixes de convencao pontuais
- [ ] `TreinoServiceImpl:41` → mudar `TsbServiceImpl tsbService` para `TsbService tsbService` (fix DIP)
- [ ] `PromptTemplateLoader:35` → trocar `HashMap` por `ConcurrentHashMap` (thread safety)
- [ ] `PromptTemplateLoader:27` → remover `private static final Logger logger` duplicado (ja tem `@Slf4j`)
- [ ] `IaServiceImpl:232` → adicionar `@Override` em `geraPlanoSemanalAvancado`
- [ ] `EmbeddingService:6` → remover `public` redundante na interface

#### 1.4 Padronizar `@Transactional`
- [ ] `PlanoServiceImpl` e `TreinoServiceImpl`: trocar `jakarta.transaction.Transactional` por `org.springframework.transaction.annotation.Transactional`
- [ ] `PlanoService` interface: mover `@Transactional` da interface para a implementacao

---

### Fase 2: Segregacao de Interfaces e Excecoes (2-3 dias)

> **Objetivo:** Limpar contratos, resolver violacoes de ISP e LSP.

#### 2.1 Interface `TreinoService` - resolver metodos nao implementados
Opcao A (recomendada): Remover `updateTreino`, `deleteTreino`, `getTreinoById` da interface ate serem implementados.
Opcao B: Lancar `UnsupportedOperationException` com mensagem clara.

```java
// Antes (interface grande com metodos mortos)
public interface TreinoService {
    TreinoRealizado addTreino(...);
    TreinoRealizado updateTreino(...);   // retorna null
    void deleteTreino(...);              // vazio
    TreinoRealizadoOutputDto getTreinoById(...); // retorna null
    void gravarTreino(...);
    TreinoRealizadoOutputDto lancarTreino(...);
}

// Depois (apenas metodos implementados)
public interface TreinoService {
    TreinoRealizado addTreino(...);
    void gravarTreino(...);
    TreinoRealizadoOutputDto lancarTreino(...);
}
```

#### 2.2 Interface `IaService` - segregar
```java
// Antes (interface com metodos que retornam null em 2 de 3 impls)
public interface IaService {
    PlanoSemanalLlmDto gerarPlanoSemanal(...);
    PlanoSemanalLlmDto geraPlanoSemanalAvancado(...);  // null em 2 impls
    Map<...> gerarPlanosEmLote(...);                   // Map.of() em todas
}

// Depois (segregadas + verbo padronizado)
public interface IaService {
    PlanoSemanalLlmDto gerarPlanoSemanal(...);
    PlanoSemanalLlmDto gerarPlanoSemanalAvancado(...);  // fix: gera → gerar
}
// gerarPlanosEmLote removido (nao implementado por ninguem)
```

#### 2.3 Padronizar hierarquia de excecoes

| Situacao | Excecao atual (inconsistente) | Excecao padrao |
|----------|-------------------------------|----------------|
| Entidade nao encontrada | ResourceNotFound, DomainNotFound, IllegalArgument, LLMException | `DomainNotFoundException` |
| Regra de negocio violada | DomainRuleViolation, IllegalArgument | `DomainRuleViolationException` |
| Falha da IA/LLM | LLMException | `LLMException` |
| Template nao encontrado | RuntimeException | `TemplateLoadException` (novo) |

Acoes:
- [ ] Criar `TemplateLoadException extends RuntimeException`
- [ ] `TsbServiceImpl.buscarAtleta()`: trocar `IllegalArgumentException` → `DomainNotFoundException`
- [ ] `PromptTemplateLoader`: trocar `RuntimeException` → `TemplateLoadException`
- [ ] `IaServiceImpl.validarPlanoGerado()`: trocar `new LLMException("Atleta nao encontrado")` → `DomainNotFoundException`
- [ ] Eliminar uso de `ResourceNotFoundException` vs `DomainNotFoundException` (manter apenas um)

#### 2.4 Centralizar lookup de Atleta

Criar metodo utilitario em `AtletaService` para eliminar duplicacao do pattern `findById().orElseThrow()` que aparece em 8+ locais:

```java
// Em AtletaService (interface)
Atleta buscarAtletaOuFalhar(UUID atletaId);

// Em AtletaServiceImpl
@Override
public Atleta buscarAtletaOuFalhar(UUID atletaId) {
    return atletaRepository.findById(atletaId)
        .orElseThrow(() -> new DomainNotFoundException("Atleta nao encontrado: " + atletaId));
}
```

---

### Fase 3: Decomposicao de `IaServiceImpl` (2-3 dias)

> **Objetivo:** Extrair 3 responsabilidades distintas de `IaServiceImpl` (808 linhas).

#### 3.1 Extrair `LlmSchemaBuilder`
Mover toda a construcao de JSON Schema para classe dedicada.

```
services/
├── ia/
│   ├── LlmSchemaBuilder.java           ← NOVO (~120 linhas)
│   │   buildSchemaTightInlineOrDefs()
│   │   enforceAllRequired()
│   │   putMin(), putMax(), putEnum()
│   │
│   ├── PlanoValidator.java              ← NOVO (~250 linhas)
│   │   validarPlanoGerado()
│   │   validarTreinoIntervalado()
│   │   validarTreinoLongo()
│   │   validarRepeticoes()
│   │
│   ├── TreinoNormalizer.java            ← NOVO (~260 linhas)
│   │   normalizarTreinoIntervalado()
│   │   clampDistancia()
│   │   distribuirDeltaDistancia()
│   │   adicionarTiroERecuperacao()
│   │   recalcularDuracaoTreino()
│   │   filtrarPorTipo(), somarDistancias()
│   │
│   └── IaServiceImpl.java              ← SIMPLIFICADO (~180 linhas)
│       gerarPlanoSemanal()
│       geraPlanoSemanalAvancado()
```

#### 3.2 `IaServiceImpl` simplificado apos extracao

```java
@Service
@Primary
@RequiredArgsConstructor
public class IaServiceImpl implements IaService {

    private final ChatClient chatClient;
    private final PlanoTreinoPromptBuilder promptBuilder;
    private final LlmSchemaBuilder schemaBuilder;
    private final PlanoValidator planoValidator;
    private final TreinoNormalizer treinoNormalizer;

    @Override
    public PlanoSemanalLlmDto gerarPlanoSemanal(...) {
        String prompt = promptBuilder.buildRequest(...);
        PlanoSemanalLlmDto plano = chamarLlm(prompt);
        planoValidator.validar(plano, atletaId);
        return plano;
    }

    @Override
    public PlanoSemanalLlmDto gerarPlanoSemanalAvancado(...) {
        String prompt = promptBuilder.buildOptimizedPrompt(...);
        PlanoSemanalLlmDto plano = chamarLlmComSchema(prompt);
        planoValidator.validar(plano, atletaId);
        treinoNormalizer.normalizar(plano, atleta.getNivelExperiencia());
        return plano;
    }

    private PlanoSemanalLlmDto chamarLlm(String prompt) { ... }
    private PlanoSemanalLlmDto chamarLlmComSchema(String prompt) { ... }
}
```

#### 3.3 Avaliar `SpringAiFixedIaServiceImpl`
Apos deletar `_old`, avaliar se `SpringAiFixedIaServiceImpl` ainda e necessario:
- Se sim: extrair codigo compartilhado com `IaServiceImpl` para classe base ou utilitario
- Se nao: deletar tambem

---

### Fase 4: Decomposicao de `TsbServiceImpl` (2-3 dias)

> **Objetivo:** Separar calculo de TSS (Strategy) da gestao de metricas.

#### 4.1 Extrair `TssCalculator` com Strategy Pattern

```java
// Interface da strategy
public interface TssCalculationStrategy {
    boolean supports(TreinoRealizado treino);
    int calcular(TreinoRealizado treino);
    int priority(); // para resolver ordem de preferencia
}

// Implementacoes
@Component
public class TssFrequenciaCardiacaStrategy implements TssCalculationStrategy {
    @Override public boolean supports(TreinoRealizado t) {
        return t.getFcMedia() != null && t.getFcMedia() > 0;
    }
    @Override public int priority() { return 1; } // maior precisao
    @Override public int calcular(TreinoRealizado treino) { ... }
}

@Component
public class TssPaceStrategy implements TssCalculationStrategy { ... }

@Component
public class TssRpeStrategy implements TssCalculationStrategy { ... }

// Orquestrador
@Component
@RequiredArgsConstructor
public class TssCalculator {
    private final List<TssCalculationStrategy> strategies;

    public int calcular(TreinoRealizado treino) {
        return strategies.stream()
            .sorted(Comparator.comparingInt(TssCalculationStrategy::priority))
            .filter(s -> s.supports(treino))
            .findFirst()
            .map(s -> s.calcular(treino))
            .orElse(0);
    }
}
```

#### 4.2 Estrutura resultante

```
services/
├── tsb/
│   ├── TssCalculator.java                    ← NOVO (orquestrador)
│   ├── strategy/
│   │   ├── TssCalculationStrategy.java       ← NOVO (interface)
│   │   ├── TssFrequenciaCardiacaStrategy.java ← NOVO
│   │   ├── TssPaceStrategy.java              ← NOVO
│   │   └── TssRpeStrategy.java               ← NOVO
│   └── TsbServiceImpl.java                   ← SIMPLIFICADO (~500 linhas)
```

---

### Fase 5: Decomposicao de `PlanoTreinoPromptBuilder` (3-5 dias)

> **Objetivo:** Reduzir de ~2475 linhas para 4-5 classes focadas. Esta e a maior refatoracao.

#### 5.1 Componentes a extrair

```
services/prompt/
├── PlanoTreinoPromptBuilder.java   ← SIMPLIFICADO (~400 linhas, orquestrador)
│   buildRequest()
│   buildOptimizedPrompt()          → chama os analisadores e monta sections
│
├── analise/
│   ├── MetricasAnalyzer.java       ← NOVO (~300 linhas)
│   │   calcularTssAlvo()
│   │   calcularProgressaoSegura()
│   │   interpretarRampRate()
│   │   interpretarTsb()
│   │
│   ├── RecuperacaoAdvisor.java     ← NOVO (~250 linhas)
│   │   detalharRecuperacao()
│   │   gerarTreinosRegenerativos()
│   │   analisarSobreCarga()
│   │   gerarRecomendacoesSonoNutricao()
│   │
│   ├── PeriodizacaoCalculator.java ← NOVO (~200 linhas)
│   │   determinarFasePreparacao()
│   │   getFocoPorFase()
│   │   calcularMaxDiasConsecutivos()
│   │
│   └── TreinoVariabilidadeAnalyzer.java ← NOVO (~200 linhas)
│       identificarMatrizVariabilidade()
│       analisarTiposTreinoEGaps()
│       gerarAlertasVariabilidade()
│       extrairCategoriasPorSemana()
│
├── PromptTemplateLoader.java       ← mantido (com fixes)
└── PromptSectionBuilder.java       ← NOVO (monta sections do prompt)
```

#### 5.2 Remover dependencias de repositorio do builder

Dados devem ser passados como parametros pelo `PlanoServiceImpl`:

```java
// ANTES (builder busca dados do banco)
@Component
public class PlanoTreinoPromptBuilder {
    private final ProvaRepository provaRepository;           // remover
    private final TreinoRealizadoRepository treinoRepo;     // remover
    // ...
}

// DEPOIS (dados injetados via parametro)
@Component
public class PlanoTreinoPromptBuilder {
    private final MetricasAnalyzer metricasAnalyzer;
    private final RecuperacaoAdvisor recuperacaoAdvisor;
    private final PeriodizacaoCalculator periodizacaoCalculator;
    private final PromptTemplateLoader templateLoader;

    public String buildOptimizedPrompt(
        Atleta atleta,
        PlanoMetaDados metaDados,
        Prova prova,
        List<TreinoRealizado> historico,  // dado passado, nao buscado
        LocalDate inicioSemana
    ) { ... }
}
```

---

### Fase 6: Desacoplamento com Spring Events (1-2 dias)

> **Objetivo:** Desacoplar pos-processamento de treinos em `TreinoServiceImpl`.

#### 6.1 Situacao atual (acoplamento sequencial)

```java
// TreinoServiceImpl.addTreino() - 5 chamadas sequenciais acopladas
finalizarTreinoPlanejadoSeAplicavel(planejado);
atualizarPlanoSemanalSeAplicavel(semanal);
atualizarTsb(atleta, treinoRealizadoInputDto.dataTreino());
atualizarMetadadosSeAplicavel(semanal);
atualizarVolumeDiario(atleta, treinoRealizadoInputDto);
```

#### 6.2 Proposta com Spring Events

```java
// Evento
public record TreinoRegistradoEvent(
    TreinoRealizado treino,
    TreinoPlanejado planejado,
    PlanoSemanal planoSemanal
) {}

// Publisher (TreinoServiceImpl simplificado)
TreinoRealizado salvo = treinoRealizadoRepository.save(realizado);
eventPublisher.publishEvent(new TreinoRegistradoEvent(salvo, planejado, semanal));
return salvo;

// Listeners (independentes e testáveis)
@EventListener
public void atualizarTsb(TreinoRegistradoEvent event) { ... }

@EventListener
public void atualizarPlanoSemanal(TreinoRegistradoEvent event) { ... }

@EventListener
public void atualizarMetadados(TreinoRegistradoEvent event) { ... }
```

---

### Fase 7: Eliminacao de Duplicacoes (1-2 dias)

> **Objetivo:** Unificar codigo duplicado entre classes.

#### 7.1 Duplicacao `filtrarDiasValidos` / `filtrarDiasDisponiveis`
3 metodos em 2 classes (`RedistribuicaoTreinoHelper` + `RegraGeracaoTreino`) fazendo a mesma coisa.

**Acao:** Manter apenas em `RegraGeracaoTreino` e reutilizar no helper.

#### 7.2 Duplicacao `calcularDataTreino` / `calcularDataDia`
Mesmo calculo `TemporalAdjusters.nextOrSame()` em `PlanoServiceImpl` e `RedistribuicaoTreinoHelper`.

**Acao:** Mover para `Utils.calcularDataDia(LocalDate semanaInicio, DiaSemana dia)`.

#### 7.3 Duplicacao entre `SpringAiFixedIaServiceImpl` e `IaServiceImpl`
Se `SpringAiFixedIaServiceImpl` ainda for necessario apos Fase 1, extrair metodos comuns (`validateInput`, `sanitizeAtleta`, `parseAndValidateResponse`, etc.) para uma classe base abstrata ou utilitario.

---

### Fase 8: Testes (ongoing, paralelo as demais fases)

> **Objetivo:** Cobrir as classes criticas com testes unitarios.

#### 8.1 Prioridade de testes

| Prioridade | Classe | Justificativa |
|------------|--------|---------------|
| P0 | TssCalculator / Strategies | Calculos financeiros do produto |
| P0 | PlanoValidator | Garantia de qualidade do plano gerado |
| P1 | TsbServiceImpl | CTL/ATL/TSB sao metricas core |
| P1 | TreinoNormalizer | Modifica dados gerados pela IA |
| P2 | RedistribuicaoTreinoHelper | Logica de redistribuicao |
| P2 | MetricasAnalyzer | Calculos de metricas semanais |
| P3 | PromptTemplateLoader | Thread safety, cache |

#### 8.2 Testes a criar por fase

- **Fase 1:** `TsbServiceImplTest` (cobrindo `contarDiasConsecutivos` fix)
- **Fase 3:** `PlanoValidatorTest`, `TreinoNormalizerTest`, `LlmSchemaBuilderTest`
- **Fase 4:** `TssFrequenciaCardiacaStrategyTest`, `TssPaceStrategyTest`, `TssRpeStrategyTest`
- **Fase 5:** `MetricasAnalyzerTest`, `PeriodizacaoCalculatorTest`

---

## 4. Estrutura Final Proposta

```
services/
├── AtletaService.java
├── EmbeddingService.java
├── IaService.java                    (simplificada: 2 metodos)
├── PlanoMetadadosService.java
├── PlanoService.java                 (@Transactional na impl, nao aqui)
├── TreinoService.java               (sem metodos mortos)
├── TsbService.java
│
├── helper/
│   ├── RedistribuicaoTreinoHelper.java
│   └── RegraGeracaoTreino.java
│
├── ia/                               ← NOVO pacote
│   ├── IaServiceImpl.java            (~180 linhas, orquestrador)
│   ├── LlmSchemaBuilder.java         (~120 linhas)
│   ├── PlanoValidator.java           (~250 linhas)
│   └── TreinoNormalizer.java         (~260 linhas)
│
├── tsb/                              ← NOVO pacote
│   ├── TsbServiceImpl.java           (~500 linhas)
│   ├── TssCalculator.java
│   └── strategy/
│       ├── TssCalculationStrategy.java
│       ├── TssFrequenciaCardiacaStrategy.java
│       ├── TssPaceStrategy.java
│       └── TssRpeStrategy.java
│
├── impl/
│   ├── AtletaServiceImpl.java        (mantido)
│   ├── EmbeddingServiceImpl.java     (mantido)
│   ├── PlanoMetadadosServiceImpl.java (mantido)
│   ├── PlanoServiceImpl.java         (simplificado)
│   └── TreinoServiceImpl.java        (com eventos)
│
├── prompt/
│   ├── PlanoTreinoPromptBuilder.java  (~400 linhas, orquestrador)
│   ├── PromptSectionBuilder.java      ← NOVO
│   ├── PromptTemplateLoader.java      (com fixes)
│   └── analise/                       ← NOVO pacote
│       ├── MetricasAnalyzer.java
│       ├── RecuperacaoAdvisor.java
│       ├── PeriodizacaoCalculator.java
│       └── TreinoVariabilidadeAnalyzer.java
│
└── event/                             ← NOVO pacote
    ├── TreinoRegistradoEvent.java
    └── TreinoEventListener.java
```

---

## 5. Resumo de Metricas (Antes vs Depois)

| Metrica | Antes | Depois |
|---------|-------|--------|
| Maior arquivo | 2475 linhas | ~500 linhas |
| Arquivos > 500 linhas | 4 | 0-1 |
| Dead code files | 1 (536 linhas) | 0 |
| Metodos retornando null | 5+ | 0 |
| Violacoes DIP | 1 | 0 |
| Violacoes LSP | 3 metodos | 0 |
| Bugs conhecidos | 4 | 0 |
| Tipos de excecao para "nao encontrado" | 5 | 1 |
| @Transactional inconsistente | 4 arquivos | 0 |
| Arquivos de teste | 1 | 8+ |
| Classes seguindo SRP | ~40% | ~90% |

---

## 6. Cronograma Sugerido

| Fase | Descricao | Esforco | Risco | Pre-requisito |
|------|-----------|---------|-------|---------------|
| **1** | Limpeza e bugs | 1-2 dias | Baixo | Nenhum |
| **2** | Interfaces e excecoes | 2-3 dias | Baixo | Fase 1 |
| **3** | Decomposicao IaServiceImpl | 2-3 dias | Medio | Fase 2 |
| **4** | Decomposicao TsbServiceImpl | 2-3 dias | Medio | Fase 1 |
| **5** | Decomposicao PromptBuilder | 3-5 dias | Alto | Fases 3 e 4 |
| **6** | Spring Events | 1-2 dias | Baixo | Fase 2 |
| **7** | Eliminacao duplicacoes | 1-2 dias | Baixo | Fases 1-6 |
| **8** | Testes | Paralelo | - | Cada fase |

**Total estimado:** 12-20 dias de desenvolvimento

---

## 7. Regras de Implementacao

1. **Uma fase por PR** - cada fase deve ser um pull request independente
2. **Testes antes de refatorar** - escrever teste para o comportamento atual antes de mover codigo
3. **Compilacao verde em cada commit** - nenhum commit deve quebrar o build
4. **Sem mudanca de comportamento** - refatoracao pura, sem alterar logica de negocio
5. **Feature flags nao necessarios** - as mudancas sao internas (service layer), sem impacto na API REST
