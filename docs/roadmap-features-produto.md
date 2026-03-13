# Roadmap de Features — Menthoros

## Visão de Arquitetura Geral

O Menthoros segue uma arquitetura em camadas com Spring Boot 3.5.4 / Java 21. O fluxo
central é: **Controller → Service → [IA / Repositório]**, com um pipeline de prompt
especializado composto por formatters independentes que alimentam o `PlanoTreinoPromptBuilder`.

```
┌──────────────────────────────────────────────────────────────┐
│  Controllers  (REST, validação de entrada)                   │
│  AtletaController · PlanoTreinoController · TreinoController │
└───────────────────────┬──────────────────────────────────────┘
                        │
┌───────────────────────▼──────────────────────────────────────┐
│  Services  (orquestração e regras de negócio)                │
│  PlanoServiceImpl · TreinoServiceImpl · AtletaServiceImpl    │
│  MetricasAlertaService · TsbServiceImpl · IaServiceImpl      │
└───────────────┬───────────────────────────┬──────────────────┘
                │                           │
┌───────────────▼──────────┐  ┌────────────▼─────────────────┐
│  Helpers / Calculadores  │  │  Pipeline de Prompt           │
│  TreinoHistoricoProvider │  │  PlanoTreinoPromptBuilder     │
│  TssCalculatorService    │  │  ├─ AlertasPromptFormatter    │
│  ZonaTreinoService       │  │  ├─ MetricasPromptFormatter   │
│  RedistribuicaoHelper    │  │  ├─ VariabilidadeFormatter    │
│  IntervaladoElegibilidade│  │  ├─ PeriodizacaoFormatter     │
└───────────────┬──────────┘  │  ├─ RecuperacaoFormatter      │
                │              │  └─ DisponibilidadeFormatter  │
┌───────────────▼──────────────▼───────────────────────────────┐
│  Entidades JPA  (PostgreSQL + pgvector)                      │
│  Atleta · PlanoMetaDados · PlanoSemanal · TreinoRealizado    │
│  TreinoPlanejado · Prova · MetricasDiarias · EtapaRealizada  │
└──────────────────────────────────────────────────────────────┘
```

Todas as features abaixo se encaixam nessa arquitetura sem reescrita. Cada uma adiciona
uma nova "fatia vertical": entidade → repositório → serviço → (formatter de prompt?) → controller.

---

## Feature 1 — Check-in Diário de Prontidão (Readiness Score)

### Problema
O sistema atual só avalia fadiga via TSB (dado calculado). Não captura sinais subjetivos
do atleta (sono, humor, dores musculares) que muitas vezes precedem o TSB em 24–48h.

### Arquitetura

```
┌─────────────────────────────────────────────┐
│  POST /atletas/{id}/checkin                 │
│  GET  /atletas/{id}/readiness               │
└──────────────────┬──────────────────────────┘
                   │
         ReadinessService
              │           │
              │    CheckInRepository
              │
         ReadinessScoreCalculator
           (TSB + dados subjetivos)
              │
         PlanoTreinoPromptBuilder
           (nova seção no prompt)
```

### Entidade nova: `CheckInDiario`

```java
@Entity
@Table(name = "tb_checkin_diario")
public class CheckInDiario {
    UUID id;
    LocalDate data;                    // unique por atleta+data
    Integer qualidadeSono;             // 1–10
    Integer nivelHumor;                // 1–10
    Integer doresMuscularesPercebidas; // 1–10 (10 = sem dores)
    Integer motivacaoTreino;           // 1–10
    String observacaoLivre;            // texto opcional, max 500
    Double readinessScore;             // calculado e persistido
    String interpretacao;              // "PRONTO", "CAUTELOSO", "DESCANSAR"

    @ManyToOne Atleta atleta;
}
```

### Algoritmo de Readiness Score

```
readiness = (0.35 × normalizarTsb(tsbAtual))
          + (0.25 × qualidadeSono / 10)
          + (0.20 × doresMuscularesPercebidas / 10)
          + (0.10 × nivelHumor / 10)
          + (0.10 × motivacaoTreino / 10)
```

Onde `normalizarTsb(tsb)` mapeia [-30, +20] → [0, 1] via min-max scaling.

Interpretação:
- `>= 0.75` → PRONTO — carga planejada autorizada
- `0.50–0.74` → CAUTELOSO — reduzir intensidade em 15%
- `< 0.50` → DESCANSAR — substituir por regenerativo

### Integração no Pipeline de Prompt

Criar `ReadinessPromptFormatter` seguindo o padrão dos formatters existentes:

```java
@Component
public class ReadinessPromptFormatter {
    public String formatarReadiness(CheckInDiario checkin, PlanoMetaDados meta) {
        // retorna seção "## PRONTIDÃO DO ATLETA HOJE"
    }
}
```

Injetar em `PlanoTreinoPromptBuilder` e adicionar como ETAPA 0 (antes de todos os outros dados).

### Dependências cruzadas

`ReadinessService.calcularReadiness()` deve ser chamado por `IntervaladoElegibilidadeService`
como **Portão 0** (antes do Gate 1 atual), usando o `readinessScore` do dia para reforçar
ou sobrescrever a decisão de elegibilidade de intervalado.

---

## Feature 2 — Análise Pós-Treino por IA (AI Debrief)

### Problema
O atleta recebe o plano mas não recebe interpretação do que aconteceu depois de executar.
A IA hoje só olha para frente (gerar próxima semana), nunca para trás (o que esse treino significou).

### Arquitetura

```
POST /treinos-realizados/{id}/analisar
          │
  AnalisePosTrainoService
          │          │
          │   IaService.gerarAnalise()
          │      (novo método, prompt próprio)
          │
  TreinoRealizado.analiseIa  (novo campo TEXT)
  TreinoRealizado.tagsTreino (novo campo, ex: "SUPERCOMPENSACAO,FADE")
```

### Prompt especializado para análise pós-treino

Criar `AnalisePosTrainoPromptBuilder` (independente do builder de plano):

```java
public String buildAnalisePrompt(TreinoRealizado realizado, TreinoPlanejado planejado,
                                  PlanoMetaDados metaDados, Atleta atleta) {
    // 4 seções:
    // 1. Diferença planejado vs realizado (TSS, pace, FC, RPE)
    // 2. Contexto de fadiga no dia (TSB, dias consecutivos)
    // 3. Tendência dos últimos 14 dias
    // 4. Pergunta ao modelo: o que esse treino significa para a semana?
}
```

### Output estruturado do LLM (BeanOutputConverter)

```java
public record AnaliseTreinoDto(
    String resumo,            // 1 frase, max 100 chars
    String interpretacao,     // 2–3 frases técnicas
    String recomendacaoProxima, // 1 frase para próximo treino
    List<String> tags,        // ["SUPERCOMPENSACAO", "RITMO_ALTO", "RECUPERACAO_OK"]
    Integer scoreExecucao     // 1–10, avaliação de qualidade do treino
)
```

### Campos adicionais em `TreinoRealizado`

```sql
ALTER TABLE tb_treino_realizado
  ADD COLUMN analise_ia TEXT,
  ADD COLUMN tags_treino VARCHAR(500),
  ADD COLUMN score_execucao INTEGER;
```

### Gatilho de execução

Executar de forma assíncrona (`@Async`) quando `TreinoRealizado` for persistido com
`percepcaoEsforco != null`. Não bloquear o request de registro do treino.

---

## Feature 3 — Predição de Tempo de Prova

### Problema
O atleta não sabe se o treinamento está evoluindo em direção ao objetivo de prova.
A predição existe nos relógios Garmin mas não considera o contexto de treinamento.

### Arquitetura

```
GET /atletas/{id}/predicao-provas
          │
  PredicaoProvaService
          │
  ├── RiegelCalculator.preverTempo(distanciaBase, tempoBase, distanciaAlvo)
  ├── VdotCalculator.estimarVdot(paceRecente, distancia)
  └── PaceStrategyCalculator.calcularSplits(tempoAlvo, distanciaKm, perfil)
```

### Fórmulas

**Riegel (predição cruzada de distâncias):**
```
T2 = T1 × (D2 / D1)^1.06
```
Onde T1/D1 = tempo/distância de referência recente, T2/D2 = predição.

**VDOT (Jack Daniels):**
```
percentVO2max = 0.8 + 0.1894393 × e^(-0.012778 × T)
              + 0.2989558 × e^(-0.1932605 × T)
VO2 = (-4.60 + 0.182258 × v + 0.000104 × v²) / percentVO2max
```
Onde v = velocidade em m/min, T = tempo em minutos.

### Entidade de suporte (histórico de predições)

```java
@Entity
@Table(name = "tb_predicao_prova")
public class PredicaoProva {
    UUID id;
    LocalDate dataCalculo;
    DistanciaProva distancia;    // enum existente
    Duration tempoPrevisto;
    String paceAlvo;
    String metodoCalculo;        // "RIEGEL", "VDOT", "HISTORICO_PROVAS"
    Double confianca;            // 0.0–1.0 baseado em quantidade de dados
    @ManyToOne Atleta atleta;
}
```

### Integração no prompt de plano

`PeriodizacaoPromptFormatter` já formata provas. Estender para incluir gap entre
predição atual e objetivo:

```
Predição atual 10k: 52:30 | Objetivo: 48:00 | Gap: -4:30 (11 semanas restantes)
Ramp necessário: +2.1 TSS/semana — dentro do limite seguro para INTERMEDIARIO
```

---

## Feature 4 — Testes Protocolados com Auto-atualização de Dados Fisiológicos

### Problema
`atleta.fcLimiar` e `atleta.paceLimiar` ficam desatualizados. Os fallbacks do sistema
(já implementados em `PlanoTreinoPromptBuilder.validarEFallbacksDadosFisiologicos()`)
existem porque esses dados envelhecem. Testes periódicos automatizados resolvem isso na raiz.

### Entidade nova: `ProtocoloTeste`

```java
@Entity
@Table(name = "tb_protocolo_teste")
public class ProtocoloTeste {
    UUID id;
    TipoTeste tipoTeste;            // enum: LIMIAR_20MIN, COOPER, CADENCIA_2KM
    LocalDate dataExecucao;
    String instrucoes;              // geradas pela IA para o nível do atleta
    // Resultados
    Integer fcMediaTeste;
    BigDecimal paceMediaTeste;      // min/km
    Integer tempoCoooper;           // segundos (teste Cooper)
    Double distanciaCooper;         // km (teste Cooper)
    // Calculados após o teste
    Integer fcLimiarCalculado;
    BigDecimal paceLimiarCalculado;
    BigDecimal vo2maxEstimado;
    String observacao;
    @ManyToOne Atleta atleta;
}
```

### Novo enum `TipoTeste`

```java
public enum TipoTeste {
    LIMIAR_20MIN("Teste de 20 minutos", "Corra ao máximo por 20 min. FC limiar ≈ 95% FC média."),
    COOPER_12MIN("Teste de Cooper 12 min", "Corra ao máximo por 12 min. Mede VO2max estimado."),
    CADENCIA_2KM("Pace fácil 2 km", "Corra 2 km em Z2. Calibra pace de Z2 real."),
    INCREMENTAL_5X1KM("Progressivo 5×1 km", "5 repetições de 1 km com FC crescente.");
}
```

### Serviço de cálculo pós-teste

```java
@Service
public class ProtocoloTesteService {

    // Após o atleta registrar o resultado do teste:
    public void processarResultado(UUID testeId, ResultadoTesteDto resultado) {
        ProtocoloTeste teste = repo.findById(testeId);
        switch (teste.getTipoTeste()) {
            case LIMIAR_20MIN -> {
                int fcLimiar = (int)(resultado.fcMedia() * 0.95);
                BigDecimal paceLimiar = resultado.paceMedia().multiply(BigDecimal.valueOf(1.05));
                atleta.setFcLimiar(fcLimiar);
                atleta.setPaceLimiar(paceLimiar);
                atleta.setDataUltimoTesteFc(LocalDate.now());
            }
            case COOPER_12MIN -> {
                double vo2max = (resultado.distanciaKm() * 1000 - 504.9) / 44.73;
                atleta.setVo2maxEstimado(BigDecimal.valueOf(vo2max));
            }
        }
        atletaRepo.save(atleta);
    }
}
```

### Alertas automáticos de teste vencido

`MetricasAlertaService` deve incluir verificação de data do último teste:

```java
// Adicionar em analisarMetricas():
if (atleta.getDataUltimoTesteFc() == null ||
    atleta.getDataUltimoTesteFc().isBefore(LocalDate.now().minusDays(90))) {
    alertas.add("Dados fisiológicos desatualizados (>90 dias). Realizar teste de limiar.");
}
```

---

## Feature 5 — Planned vs Actual Dashboard (Aderência ao Plano)

### Problema
Não há visibilidade sobre quão bem o atleta está seguindo o plano ao longo do tempo.
Um coach profissional acompanha isso semanalmente para ajustar a abordagem.

### Arquitetura

```
GET /atletas/{id}/aderencia?semanas=8
          │
  AderenciaPlanoService.calcularAderencia(atletaId, semanas)
          │
  ├── PlanoSemanalRepository.findByAtletaAndPeriodo()
  ├── TreinoRealizadoRepository.findByAtletaAndPeriodo()
  └── AderenciaCalculator (sem IA, lógica pura)
```

### Record de output

```java
public record AderenciaPlanoDto(
    List<SemanaAderencia> semanas,
    Double taxaAderenciaGeral,     // 0–100%
    Map<TipoTreino, Double> taxaPorTipo, // qual tipo mais pulado
    String tipoMaisPulado,
    Double tssRealizadoVsPlanejado, // ratio TSS realizado / planejado
    List<String> insights           // gerados pela IA opcionalmente
)

public record SemanaAderencia(
    LocalDate semanaInicio,
    int treinosPlanejados,
    int treinosRealizados,
    double taxaAderencia,
    int tssplanejado,
    int tssRealizado,
    String statusSemana  // "EXCELENTE", "BOA", "PARCIAL", "AUSENTE"
)
```

### Query nativa necessária

```sql
SELECT
    ps.semana_inicio,
    COUNT(tp.id) AS planejados,
    COUNT(tr.id) AS realizados,
    SUM(tp.tss_planejado) AS tss_planejado,
    SUM(tr.tss_calculado) AS tss_realizado
FROM tb_plano_semanal ps
LEFT JOIN tb_treino_planejado tp ON tp.plano_semanal_id = ps.id
LEFT JOIN tb_treino_realizado tr ON tr.treino_planejado_id = tp.id
WHERE ps.atleta_id = :atletaId
  AND ps.semana_inicio >= :inicio
GROUP BY ps.semana_inicio
ORDER BY ps.semana_inicio DESC;
```

---

## Feature 6 — Planejamento de Macrociclo (Auto-gerado pela Prova Alvo)

### Problema
O sistema gera planos semanais de forma isolada, sem uma estrutura longitudinal de periodização.
Um coach profissional pensa em blocos progressivos: BASE → BUILD → ESPECIFICO → TAPER, com
CTL-alvo definido por semana desde o primeiro dia de preparação. Sem esse mapa macro, o LLM
não sabe se está na semana 2 de 20 ou na semana 18 de 20 — o contexto muda tudo.

A ideia central: **ao cadastrar uma prova alvo, o macrociclo completo é criado automaticamente**.
Cada semana já nasce com fase, CTL-alvo e TSS-alvo definidos. Os planos semanais gerados
pelo LLM consultam esse mapa antes de qualquer outra coisa.

---

### Trigger Automático: Cadastro de Prova Alvo

O macrociclo é disparado como efeito colateral de `ProvaService.cadastrar()` quando
`provaInputDto.provaAlvo() == true`. Não requer chamada manual nem endpoint dedicado.

```
ProvaService.cadastrar(atletaId, provaInputDto)
  │
  ├── Persiste Prova (provaAlvo=true, dataProva, distancia)
  │
  └── [se provaAlvo] MacrocicloPlanejamentoService
          .gerarAutomaticamente(atleta, prova)
                │
                ├── calcularJanelaPreparacao(dataProva, dataAtual)
                ├── validarSemanasMinimas(semanasDisponiveis, distancia)   ← alerta se insuficiente
                ├── distribuirFases(semanasDisponiveis, distancia)          ← algoritmo determinístico
                ├── calcularCargaSemanal(ctlAtual, ctlAlvo, totalSemanas)   ← ramp rate seguro
                └── persistirMacrociclo + SemanaPlano × N
```

**Regra de unicidade:** um atleta só pode ter um macrociclo ativo por vez. Se já existe um,
ele é arquivado (`status = ARQUIVADO`) antes de criar o novo.

---

### Semanas Mínimas por Distância (padrão de treinadores)

Baseado na metodologia Jack Daniels / Pfitzinger. Abaixo do mínimo, o sistema gera o macrociclo
com flag `preparacaoInsuficiente = true` e exibe alerta ao atleta.

| Distância | Mínimo | Recomendado | Ideal |
|---|---|---|---|
| **5K** | 8 semanas | 12 semanas | 16 semanas |
| **10K** | 10 semanas | 14 semanas | 18 semanas |
| **21K** | 14 semanas | 18 semanas | 22 semanas |
| **42K** | 18 semanas | 24 semanas | 28 semanas |

```java
private static final Map<DistanciaProva, Integer> SEMANAS_MINIMAS = Map.of(
    DistanciaProva.KM_5,  8,
    DistanciaProva.KM_10, 10,
    DistanciaProva.KM_21, 14,
    DistanciaProva.KM_42, 18
);
private static final Map<DistanciaProva, Integer> SEMANAS_RECOMENDADAS = Map.of(
    DistanciaProva.KM_5,  12,
    DistanciaProva.KM_10, 14,
    DistanciaProva.KM_21, 18,
    DistanciaProva.KM_42, 24
);
```

---

### Distribuição de Fases (algoritmo determinístico)

Fases fixas ao final, fases variáveis ao início. A lógica é **de trás para frente**
a partir da data da prova, garantindo que TAPER e SEMANA_PROVA nunca sejam cortados.

```
Total de semanas disponíveis = N

Semana N     → SEMANA_PROVA (1 semana — fixa)
Semanas N-2 a N-1 → TAPER (2 semanas — fixas; 3 semanas se 42K ou N > 20)
Semanas restantes → dividir proporcionalmente:
    BASE:      35-40% (maior para 21K/42K; menor para 5K)
    BUILD:     30% fixo
    ESPECIFICO: remainder
```

Tabela de proporções por distância (percentual das semanas variáveis):

| Distância | BASE | BUILD | ESPECIFICO |
|---|---|---|---|
| **5K** | 35% | 30% | 35% |
| **10K** | 40% | 30% | 30% |
| **21K** | 45% | 30% | 25% |
| **42K** | 50% | 30% | 20% |

Dentro de cada fase, aplica padrão **3:1** (três semanas de CARGA + uma de RECUPERACAO),
exceto ESPECIFICO que usa **2:1** (maior freqüência de estímulo específico).

```
Exemplo — 10K, 14 semanas:
  Semana 1–2: BASE CARGA (CTL+3 por semana)
  Semana 3:   BASE CARGA
  Semana 4:   BASE RECUPERACAO (CTL-3)
  Semana 5–7: BUILD CARGA
  Semana 8:   BUILD RECUPERACAO
  Semana 9–10: ESPECIFICO CARGA
  Semana 11:   ESPECIFICO RECUPERACAO
  Semana 12–13: TAPER
  Semana 14:    SEMANA_PROVA
```

---

### CTL-Alvo por Semana (Ramp Rate Seguro)

CTL-alvo de pico por distância e nível (base nas recomendações de treinadores):

| Distância | INICIANTE | INTERMEDIARIO | AVANCADO | ELITE |
|---|---|---|---|---|
| **5K** | 30–40 | 45–55 | 60–75 | 80–100 |
| **10K** | 35–45 | 50–60 | 65–80 | 90–110 |
| **21K** | 45–55 | 60–70 | 75–90 | 100–130 |
| **42K** | 55–65 | 70–85 | 90–110 | 120–150 |

**Cálculo do ramp:**

```java
// ctlAtual = metaDados.getCtlAtual()
// ctlAlvoPico = tabela acima (valor médio da faixa)
// semanasParaAtingirPico = totalSemanas - semanasFixas (taper + prova)

double ctlRampPorSemana = (ctlAlvoPico - ctlAtual) / semanasParaAtingirPico;

// Validar: ramp seguro é +3 CTL/semana. Acima disso, usar +3 e ajustar ctlAlvoPico.
if (ctlRampPorSemana > 3.0) {
    ctlAlvoPico = ctlAtual + (3.0 * semanasParaAtingirPico);
    log.warn("CTL-alvo ajustado para {} por ramp rate ({}→ máx +3/semana)", ctlAlvoPico, distancia);
}
```

Para semanas de RECUPERACAO: `ctlAlvo = ctlSemanaAnterior - 5` (ATL cai, TSB sobe ~15).

---

### Arquitetura

```
ProvaService (trigger automático)
  └── MacrocicloPlanejamentoService
            │
            ├── MacrocicloPlanejamentoRepository
            ├── SemanasMacrocicloRepository
            └── PlanoMetaDadosRepository (lê CTL atual do atleta)

GET /atletas/{id}/macrociclo/ativo        → resumo do macrociclo atual
GET /atletas/{id}/macrociclo/semana-atual → SemanaPlano da semana em curso
```

---

### Entidades novas

```java
@Entity
@Table(name = "tb_macrociclo")
@Builder(toBuilder = true) @Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Macrociclo {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    String nome;                   // "Preparação São Silvestre 2026" (gerado automaticamente)
    LocalDate dataInicio;
    LocalDate dataFim;             // = prova.dataProva
    Integer totalSemanas;
    MacrocicloPlanejamentoStatus status; // ATIVO, ARQUIVADO, CONCLUIDO
    boolean preparacaoInsuficiente; // true se abaixo do mínimo recomendado

    Integer ctlInicialAtleta;      // snapshot do CTL no momento da criação
    Integer ctlAlvoPico;           // CTL máximo antes do taper
    Integer ctlAlvoProva;          // CTL alvo no dia da prova (após taper)

    @ManyToOne(fetch = FetchType.LAZY)
    Prova provaAlvo;

    @ManyToOne(fetch = FetchType.LAZY)
    Atleta atleta;

    @OneToMany(mappedBy = "macrociclo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numeracao ASC")
    List<SemanaPlano> semanasPlano;
}

@Entity
@Table(name = "tb_semana_plano")
@Builder(toBuilder = true) @Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SemanaPlano {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    Integer numeracao;                   // 1, 2, 3 ... N
    LocalDate semanaInicio;
    LocalDate semanaFim;

    @Enumerated(EnumType.STRING)
    TipoSemana tipoSemana;               // CARGA, RECUPERACAO, TAPER, SEMANA_PROVA, TRANSICAO

    @Enumerated(EnumType.STRING)
    FasePeriodizacao fase;               // BASE, BUILD, ESPECIFICO, TAPER, SEMANA_PROVA

    Integer ctlAlvo;                     // CTL esperado ao final desta semana
    Integer tssAlvo;                     // TSS total da semana (CTL × 7 aproximadamente)
    Double volumeAlvoKm;                 // km totais estimados

    String focoSemanal;                  // "Construir motor aeróbico — 80% Z2"
    String instrucaoParaLlm;             // seção injetada no prompt pelo MacrocicloPlanejamentoFormatter

    boolean concluida;                   // true após a semana passar

    @ManyToOne(fetch = FetchType.LAZY)
    Macrociclo macrociclo;
}
```

### Novo enum `TipoSemana`

```java
public enum TipoSemana {
    CARGA("Semana de Carga", "Volume e intensidade normais ou progressivos"),
    RECUPERACAO("Semana de Recuperação", "Redução de 30-40% no volume — TSB deve subir"),
    CHOQUE("Semana Choque", "Sobrecarga controlada, seguida obrigatoriamente de recuperação"),
    TESTE("Semana de Teste", "Inclui protocolo de teste fisiológico para re-calibrar zonas"),
    TAPER("Taper", "Redução progressiva de 40-60% do volume mantendo estímulos curtos"),
    SEMANA_PROVA("Semana de Prova", "Apenas ativação neuromuscular leve e briefing de prova"),
    TRANSICAO("Transição", "Semana pós-prova de recuperação ativa — sem pressão de carga");
}
```

---

### MacrocicloPlanejamentoService — Método principal

```java
@Service @RequiredArgsConstructor @Slf4j
public class MacrocicloPlanejamentoService {

    public Macrociclo gerarAutomaticamente(Atleta atleta, Prova prova) {
        LocalDate hoje = LocalDate.now();
        long totalSemanas = ChronoUnit.WEEKS.between(hoje, prova.getDataProva());

        // 1. Validar janela
        int minimoSemanas = SEMANAS_MINIMAS.get(prova.getDistancia());
        boolean insuficiente = totalSemanas < minimoSemanas;
        if (insuficiente) {
            log.warn("Preparação insuficiente para {}: {} semanas (mínimo: {})",
                prova.getDistancia(), totalSemanas, minimoSemanas);
        }

        // 2. Arquivar macrociclo anterior se existir
        macrocicloRepo.findByAtletaAndStatus(atleta, MacrocicloPlanejamentoStatus.ATIVO)
            .ifPresent(m -> { m.setStatus(ARQUIVADO); macrocicloRepo.save(m); });

        // 3. Calcular CTL-alvo
        Double ctlAtual = Optional.ofNullable(planoMetaDadosRepo.findUltimoByAtleta(atleta))
            .map(PlanoMetaDados::getCtlAtual).orElse(20.0);
        double ctlAlvoPico = calcularCtlAlvoPico(prova.getDistancia(), atleta.getNivelExperiencia());
        int semanasVariaveis = (int)(totalSemanas - SEMANAS_FIXAS_TAPER_E_PROVA);
        double ramp = (ctlAlvoPico - ctlAtual) / semanasVariaveis;
        if (ramp > 3.0) { ctlAlvoPico = ctlAtual + 3.0 * semanasVariaveis; }

        // 4. Distribuir fases e gerar semanas
        List<SemanaPlano> semanas = distribuirSemanas(
            (int) totalSemanas, hoje, prova, ctlAtual, ctlAlvoPico);

        // 5. Persistir
        Macrociclo macro = Macrociclo.builder()
            .nome("Preparação " + prova.getNomeProva() + " " + prova.getDataProva().getYear())
            .dataInicio(hoje)
            .dataFim(prova.getDataProva())
            .totalSemanas((int) totalSemanas)
            .status(MacrocicloPlanejamentoStatus.ATIVO)
            .preparacaoInsuficiente(insuficiente)
            .ctlInicialAtleta(ctlAtual.intValue())
            .ctlAlvoPico((int) ctlAlvoPico)
            .provaAlvo(prova)
            .atleta(atleta)
            .semanasPlano(semanas)
            .build();

        return macrocicloRepo.save(macro);
    }
}
```

---

### Integração com `PlanoServiceImpl` — SemanaPlano como restrição hard

Quando existe um `Macrociclo` ativo, `gerarPlanoTreino()` consulta a `SemanaPlano` da
semana atual **antes** de chamar o LLM. Os dados da semana sobrepõem o cálculo autônomo
de `calcularTssAlvoAjustado()`.

```java
// Em PlanoServiceImpl.gerarPlanoTreino()

Optional<SemanaPlano> semanaAtiva = macrocicloRepo
    .findSemanaAtiva(atleta, inicioSemana);  // busca por semanaInicio = inicioSemana

if (semanaAtiva.isPresent()) {
    SemanaPlano semana = semanaAtiva.get();
    metaDados.setFasePeriodizacaoAtual(semana.getFase()); // substitui FasePeriodizacao calculada
    // tssAlvo e focoSemanal são passados para o PlanoTreinoPromptBuilder
}
```

Criar `MacrocicloPlanejamentoFormatter` (novo formatter seguindo padrão existente):

```java
@Component
public class MacrocicloPlanejamentoFormatter {

    public String formatarContextoMacrociclo(SemanaPlano semana, Macrociclo macro) {
        return """
            ## MACROCICLO ATIVO — INSTRUCAO OBRIGATORIA

            Prova alvo: %s (%s) em %s
            Semana: %d/%d — %s (%s)
            Fase: %s
            Foco desta semana: %s

            Metas desta semana:
            - CTL-alvo ao final: %d
            - TSS-alvo total: %d TSS
            - Volume-alvo: %.0f km

            INSTRUCAO: O plano DEVE respeitar os alvos acima.
            TSS e volume sao restricoes hard — nao ultrapasse em mais de 5%%.
            """.formatted(
                macro.getProvaAlvo().getNomeProva(),
                macro.getProvaAlvo().getDistancia().getLabel(),
                macro.getProvaAlvo().getDataProva(),
                semana.getNumeracao(), macro.getTotalSemanas(),
                semana.getTipoSemana().getNome(),
                semana.getSemanaInicio(),
                semana.getFase(),
                semana.getFocoSemanal(),
                semana.getCtlAlvo(),
                semana.getTssAlvo(),
                semana.getVolumeAlvoKm()
            );
    }
}
```

---

### Padrão clássico de ondulação (3:1 e 2:1)

```
BASE (3:1):
  Semana 1: CARGA     (CTL atual + ramp)
  Semana 2: CARGA     (CTL anterior + ramp)
  Semana 3: CARGA     (CTL anterior + ramp)
  Semana 4: RECUPERACAO (CTL anterior - 5, ATL cai, TSB sobe ~15)

BUILD (3:1 — mesmo padrão com foco em threshold e intervalado):
  Semanas 5–7: CARGA
  Semana  8:   RECUPERACAO

ESPECIFICO (2:1 — maior densidade de estímulo específico):
  Semanas 9–10: CARGA específica (pace de prova, intervalados categoria C/E)
  Semana  11:   RECUPERACAO

TAPER (fixo — nunca encurtado):
  Semanas -3/-2: TAPER (volume -40%, manter estímulos curtos em intensidade)

SEMANA_PROVA (fixo):
  Semana -1: SEMANA_PROVA (ativação leve + briefing de prova)
```

---

## Feature 7 — Rastreamento de Equipamento (Tênis e Itens)

### Problema
Tênis desgastados após 700–800 km são a principal causa de lesões por overuse.
Nenhum app rastreia isso de forma confiável sem integração com e-commerce.

### Entidades novas

```java
@Entity
@Table(name = "tb_equipamento")
public class Equipamento {
    UUID id;
    TipoEquipamento tipo;            // TENIS_TREINO, TENIS_COMPETICAO, MEIAS, etc.
    String marca;
    String modelo;
    LocalDate dataAquisicao;
    Integer kmMaximoRecomendado;     // default por tipo: 700–800 para tênis
    BigDecimal kmAcumulado;          // atualizado a cada TreinoRealizado
    boolean ativo;
    LocalDate dataDescarte;
    @ManyToOne Atleta atleta;
}
```

### Integração com `TreinoRealizado`

```java
// Em TreinoRealizado (campo adicional):
@ManyToOne
@JoinColumn(name = "equipamento_id")
private Equipamento equipamentoUtilizado;
```

### Serviço de atualização automática

`EquipamentoService.atualizarKmAposRegistroTreino()` chamado no callback de
`TreinoServiceImpl.registrarTreinoRealizado()`:

```java
@Transactional
public void atualizarKmAposRegistroTreino(TreinoRealizado treino) {
    if (treino.getEquipamentoUtilizado() == null) return;
    Equipamento eq = treino.getEquipamentoUtilizado();
    eq.setKmAcumulado(eq.getKmAcumulado().add(treino.getDistanciaKm()));
    // Gerar alerta se > 80% do limite
    if (eq.getKmAcumulado().compareTo(
            BigDecimal.valueOf(eq.getKmMaximoRecomendado() * 0.80)) > 0) {
        alertarDesgaste(eq);
    }
    equipamentoRepo.save(eq);
}
```

### Alerta no prompt

`AlertasPromptFormatter.gerarAlertasObrigatorios()` deve incluir alerta de tênis próximo
do limite — nível `INFO` ou `YELLOW` — pois impacta a prescrição de treinos longos.

---

## Feature 8 — Integração Climática com Ajuste de Ritmo

### Problema
Prescrever "pace 5:30/km" em um dia de 34°C é inadequado. O sistema não considera
condições externas na geração do plano.

### Arquitetura

```
ClimaTreinoService
  │
  ├── WeatherApiClient (Feign ou RestTemplate)
  │      → OpenMeteo (gratuito, sem chave)
  │         GET https://api.open-meteo.com/v1/forecast
  │             ?latitude=X&longitude=Y&hourly=temperature_2m,humidity&...
  │
  ├── AjusteClimaCalculator
  │      calcularAjustePace(temperatura, umidade, altitude) → segundosPorKm
  │
  └── ClimaPromptFormatter (novo, injeta no PlanoTreinoPromptBuilder)
```

### Algoritmo de ajuste de pace por calor

Baseado em pesquisas da Runner's World e ACSM:

```
ajusteSeg/km = 0 se temperatura <= 15°C
ajusteSeg/km = (temperatura - 15) × 2.5   // +2.5s/km por grau acima de 15°C
ajusteSeg/km += (umidade - 40) × 0.3       // +0.3s/km por % de umidade acima de 40%
ajusteSeg/km = min(ajusteSeg/km, 60)       // cap em +60s/km (além disso, recomenda trocal tipo)
```

Exemplo: 32°C com 80% umidade → +42.5 + 12 = +54.5 seg/km → "5:30" vira "6:25/km"

### Dados do atleta necessários

Adicionar em `Atleta`:
```java
@Column(name = "cidade_treino")
private String cidadeTreino;

@Column(name = "latitude_treino")
private Double latitudeTreino;

@Column(name = "longitude_treino")
private Double longitudeTreino;
```

### Seção no prompt

```
## CONDIÇÕES CLIMÁTICAS DA SEMANA

Previsão para [cidade]:
- Segunda: 28°C / 75% umidade → ajuste +32s/km em treinos externos
- Quarta: 22°C / 60% umidade → condições normais
- Sábado: 34°C / 85% umidade → RECOMENDADO: treino indoor ou horário 06h–08h

INSTRUÇÃO: Para treinos em dias com temperatura > 28°C, reduzir pace alvo conforme tabela
acima. Considerar substituir LONGO do sábado por indoor (esteira) ou adiantar para 06h.
```

---

## Feature 9 — Semana de Prova Guiada (Taper Ritual)

### Problema
A semana de prova é onde mais atletas amadores erram — ou treinam demais por ansiedade,
ou ficam completamente parados. Um guia diário D-7 até D-0 elimina a incerteza.

### Arquitetura

```
GET /provas/{id}/guia-taper/dia/{diaOffset}   // diaOffset: -7 a 0
          │
  TaperGuiadoService
          │
  ├── TaperDiarioCalculator
  │      calcularOrientacaoDia(diaOffset, atleta, metaDados, prova)
  │
  └── IaService.gerarBriefingProva()  (apenas no D-0)
```

### Record de output por dia

```java
public record TaperDiarioDto(
    Integer diaOffset,        // -7 a 0
    String titulo,            // ex: "D-3: Aceleração Curta"
    String treino,            // descrição do treino ou "Descanso ativo"
    TipoTreino tipoTreino,
    Integer duracaoMin,
    String fcAlvo,
    String orientacaoNutricional,
    String orientacaoSono,
    String mensagemMotivacional,
    Double tsbAtual,
    String statusTsb          // "IDEAL", "MUITO_DESCANSADO", "AINDA_FATIGADO"
)
```

### Lógica de geração por dia

```
D-7: CONTINUO leve 40min Z2 — limpar resíduos metabólicos
D-6: Descanso ativo (caminhada/alongamento)
D-5: FARTLEK curto 30min com 4×30s em ritmo de prova
D-4: REGENERATIVO 20–25min Z1
D-3: CONTINUO 25min + 4×100m em pace de prova
D-2: Descanso completo
D-1: REGENERATIVO 15–20min + 4 acelerações de 20s
D-0: Briefing de prova (IA gera estratégia de splits + checklist)
```

### Checklist de material (D-0)

Gerado pela IA como parte do briefing:
- Tênis de prova (confirmar km acumulado < 300 km)
- Gel/alimentação (calculado por tempo estimado de prova)
- Hidratação (baseado na previsão climática)
- Pace de largada recomendado (primeiro km conservador)

---

## Feature 10 — Adaptation Tracking (Análise de Resposta ao Estímulo)

### Problema
Dois atletas com CTL=50 podem estar em estados completamente diferentes de adaptação.
Um está respondendo ao treino (supercompensando), o outro está acumulando fadiga residual.
Esse dado é invisível hoje.

### Arquitetura

```
AdaptacaoTreinoService
  │
  ├── calcularDriftPace(atleta, janela=28dias)
  │      Mesmo RPE, pace caindo? → Fadiga não resolvida
  │      Mesmo RPE, pace melhorando? → Supercompensação
  │
  ├── calcularIndiceAdaptacao(atleta, janela=14dias)
  │      ratio (TSS realizado / TSS planejado) × (RPE planejado / RPE realizado)
  │
  └── detectarPlatoAdaptativo(atleta)
       Se IndiceAdaptacao < 0.8 por 3 semanas consecutivas → platô
```

### Algoritmo de Drift de Pace

```java
public DriftAnalise calcularDriftPace(Atleta atleta, int diasJanela) {
    List<TreinoRealizado> treinos = repo.findByAtletaAndTipoAndPeriodo(
        atleta, TipoTreino.CONTINUO, LocalDate.now().minusDays(diasJanela), LocalDate.now());

    // Agrupar por semana, calcular pace médio em Z2 (FC 60-75% FCmax)
    Map<LocalDate, Double> pacePorSemana = ...

    // Regressão linear simples para detectar tendência
    double slope = calcularRegressaoLinear(pacePorSemana);
    // slope < 0 → pace melhorando (bom)
    // slope > 0 → pace piorando (drift negativo)
    return new DriftAnalise(slope, classificarDrift(slope));
}
```

### Persistência em `MetricasDiarias`

`MetricasDiarias` já existe como entidade. Adicionar campos:

```sql
ALTER TABLE tb_metricas_diarias
  ADD COLUMN indice_adaptacao DECIMAL(5,3),
  ADD COLUMN drift_pace_seg_km DECIMAL(6,2),
  ADD COLUMN tendencia_adaptacao VARCHAR(20); -- "SUPERCOMPENSANDO","ESTAVEL","PLATO","FADIGA"
```

### Integração no prompt

`MetricasPromptFormatter.formatarMetricas()` deve incluir a tendência de adaptação:

```
## ANÁLISE DE ADAPTAÇÃO (últimas 4 semanas)
- Drift de pace Z2: -8 seg/km → SUPERCOMPENSAÇÃO (melhorando)
- Índice de aderência: 87% TSS realizado/planejado
- Tendência: POSITIVA — atleta respondendo bem ao estímulo atual
→ Recomendação: manter ou aumentar carga em 5–8% esta semana
```

---

## Feature 11 — Arquitetura de Skills (Tool Use para o Agente de Treinamento)

### Problema

A abordagem atual **empurra todos os dados no prompt antes de cada chamada** ao LLM —
histórico de 28 dias, métricas, alertas, zonas, provas — independentemente do que aquela
semana específica realmente precisa. O resultado é um prompt de 3.000–5.000 tokens para
toda geração, onde Claude processa dados irrelevantes (ex.: histórico completo numa semana
regenerativa simples).

A arquitetura de Skills transforma o agente de **receptor passivo** para **agente ativo**:
Claude decide quais dados buscar, quando, e por quê — e cada busca é auditável.

---

### Estrutura de Arquivos no Projeto

O projeto já tem a separação prompts (resources) ↔ formatters (Java). Skills seguem
a mesma divisão com uma camada adicional de **definição de tool**:

```
src/main/resources/
├── prompts/
│   ├── system-prompt.txt                       (existente — regras gerais)
│   ├── plano-treino-otimizado-claude.txt       (existente — template do plano)
│   └── skills/                                 ← NOVO subdiretório
│       ├── skill-historico-treinos.md          ← descrição carregada em runtime
│       ├── skill-metricas-atuais.md
│       ├── skill-macrociclo-ativo.md           ← integra Feature 6
│       ├── skill-readiness-dia.md              ← integra Feature 1
│       ├── skill-predicao-prova.md             ← integra Feature 3
│       ├── skill-debrief-pos-treino.md         ← integra Feature 2
│       ├── skill-previsao-climatica.md         ← integra Feature 8
│       └── skill-aderencia-plano.md            ← integra Feature 5
│
└── tools-manifest.yml                          ← registro de tools por contexto
```

```
src/main/java/com/menthoros/
└── services/
    ├── prompt/          (existente — formatters de seções do prompt)
    └── tools/           ← NOVO pacote
        ├── AtletaTools.java
        ├── TreinoHistoricoTools.java
        ├── MacrocicloPlanejamentoTools.java
        ├── MetricasTools.java
        ├── ProvaTools.java
        └── ToolsConfig.java   (@Configuration — wiring no ChatClient por contexto)
```

---

### Por que `.md` para descrições e `.yml` para o manifest

**Arquivos `.md` em `prompts/skills/`:**

O `PromptTemplateLoader` já carrega qualquer extensão de `classpath:prompts/` com cache
automático. A extensão `.md` é convenção — o conteúdo é texto puro que Claude lê como
documentação da ferramenta. Usar `.md` (e não `.txt`) comunica que:

- O arquivo é **para leitura humana** (editável pela equipe de produto sem tocar Java)
- Contém **markdown estruturado**: descrição, quando usar, exemplos, formato de retorno
- É a **única source of truth** da descrição da tool — não está duplicada no código

Spring AI permite criar `FunctionCallback` programaticamente com descrição injetada em
runtime (sem ser constante de compilação), então o `.md` é carregado no startup via
`PromptTemplateLoader` e passado ao builder da tool.

**Arquivo `tools-manifest.yml`:**

Precisa de estrutura hierárquica (nome → contextos → arquivo). YAML casa com
`application.yml` já existente e suporta listas nativamente:

```yaml
# src/main/resources/tools-manifest.yml
tools:
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
    enabled: false   # habilitar quando Feature 8 estiver implementada
    skill-file: prompts/skills/skill-previsao-climatica.md
    contexts: [PLANO_SEMANAL]
```

Isso permite **habilitar/desabilitar tools por feature flag** sem redeployment — basta
mudar o YAML e reiniciar (com Spring Cloud Config, nem isso).

---

### Responsabilidades por Camada

```
┌─────────────────────────────────────────────────────────────────────┐
│ skill-{nome}.md                                                      │
│   O QUE a tool faz — linguagem natural, exemplos de uso, retorno    │
│   Quem edita: equipe de produto / coach advisor                      │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ carregado em startup por
┌──────────────────────────────▼──────────────────────────────────────┐
│ {Nome}Tools.java             │                                       │
│   COMO a tool executa — lógica Java, repositórios, cálculos         │
│   Quem edita: engenheiro backend                                     │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ registrado conforme
┌──────────────────────────────▼──────────────────────────────────────┐
│ tools-manifest.yml                                                   │
│   QUANDO e ONDE a tool está disponível — contexto, flag, config     │
│   Quem edita: engenheiro + produto (deploy-free toggle)             │
└─────────────────────────────────────────────────────────────────────┘
```

---

### Exemplo de Skill File — `skill-macrociclo-ativo.md`

```markdown
## buscarSemanaAtivaMacrociclo

**Propósito:** Retorna a semana ativa do macrociclo de preparação do atleta.

**Quando chamar:** SEMPRE ao iniciar o raciocínio sobre o plano semanal.
A semana do macrociclo define fase, TSS-alvo, CTL-alvo e foco — são restrições
hard que devem ser respeitadas antes de qualquer outra decisão de carga.

**Parâmetro:** `atletaId` (String UUID)

**Retorna:**
- `numeracao` / `totalSemanas` — posição no macrociclo (ex: semana 8 de 18)
- `fase` — BASE | BUILD | ESPECIFICO | TAPER | SEMANA_PROVA
- `tipoSemana` — CARGA | RECUPERACAO | TAPER
- `ctlAlvo` — CTL esperado ao final desta semana
- `tssAlvo` — TSS total da semana
- `volumeAlvoKm` — km totais alvo
- `focoSemanal` — instrução textual do foco (ex: "Construir motor aeróbico")

**Se retornar null:** Atleta não tem macrociclo ativo. Use lógica autônoma baseada
em FasePeriodizacao calculada e TSB.
```

---

### Implementação Spring AI — `FunctionCallback` com descrição do arquivo

```java
@Configuration
@RequiredArgsConstructor
public class ToolsConfig {

    private final PromptTemplateLoader templateLoader;
    private final TreinoHistoricoTools treinoHistoricoTools;
    private final MacrocicloPlanejamentoTools macrocicloTools;
    private final MetricasTools metricasTools;

    // Bean de tools para contexto PLANO_SEMANAL
    @Bean("toolsPlanoSemanal")
    public List<FunctionCallback> toolsPlanoSemanal() {
        return List.of(
            FunctionCallbackWrapper.builder(treinoHistoricoTools::buscarHistoricoTreinos)
                .withName("buscarHistoricoTreinos")
                .withDescription(templateLoader.loadTemplate("skills/skill-historico-treinos.md"))
                .build(),

            FunctionCallbackWrapper.builder(macrocicloTools::buscarSemanaAtivaMacrociclo)
                .withName("buscarSemanaAtivaMacrociclo")
                .withDescription(templateLoader.loadTemplate("skills/skill-macrociclo-ativo.md"))
                .build(),

            FunctionCallbackWrapper.builder(metricasTools::calcularMetricasAtuais)
                .withName("calcularMetricasAtuais")
                .withDescription(templateLoader.loadTemplate("skills/skill-metricas-atuais.md"))
                .build()
        );
    }
}
```

Integração no `IaServiceImpl`:

```java
// gerarPlanoTreino() — após montar o system prompt com restrições hard:
return chatClient.prompt()
        .system(promptBuilder.buildOptimizedPrompt(atleta, metaDados, provaAlvo, inicioSemana))
        .tools(toolsPlanoSemanal)   // Spring AI: multi-turn interno até Claude parar de chamar tools
        .call()
        .entity(PlanoSemanalLlmDto.class);
```

---

### O que NUNCA deve virar tool — a regra de segurança

| Componente | Motivo para manter no prompt (não tool) |
|---|---|
| `IntervaladoElegibilidadeService` | Decisão de segurança fisiológica — Claude não pode "esquecer" de checar |
| `SemanaPlano` do macrociclo | Restrição hard de TSS/fase — injetada antes, não opcional |
| `alertasObrigatorios` | Bloqueios de lesão/fadiga extrema — devem ser visíveis desde o início |
| `restricoesLesoes` | Idem — contexto de segurança não pode depender de raciocínio do modelo |

**Regra:** se a omissão do dado pode causar uma prescrição perigosa, ele vai no prompt.
Se a omissão apenas tornaria o plano menos personalizado, ele vira tool.

---

### Arquitetura Híbrida Final (sistema com Skills)

```
IaServiceImpl.gerarPlanoTreino()
│
├── [PRÉ-LLM — determinístico, obrigatório]
│   ├── IntervaladoElegibilidadeService.avaliar()  → injetado no system prompt
│   ├── MacrocicloPlanejamentoFormatter.formatarContexto() → injetado se ativo
│   └── AlertasPromptFormatter.gerarAlertasObrigatorios() → injetado sempre
│
├── ChatClient.prompt(systemPromptComRestricoes).tools(toolsPlanoSemanal)
│   │
│   └── [MULTI-TURN INTERNO — Claude decide]
│       ├── call buscarHistoricoTreinos(atletaId, 14)?     → se precisar de contexto
│       ├── call calcularMetricasAtuais(atletaId)?          → se quiser comparar TSB/CTL
│       ├── call buscarReadinessDoDia(atletaId, hoje)?      → se disponível (Feature 1)
│       ├── call calcularPredicaoProva(atletaId, "10K")?   → se for ajustar pace-alvo
│       └── [stop] → gera PlanoSemanalLlmDto
│
└── [PÓS-LLM — validação]
    └── validarEFallbacksDadosFisiologicos() (já existente)
```

---

### Ganhos de Produto

| Dimensão | Antes (push total) | Com Skills |
|---|---|---|
| Tokens por plano | 3.500–5.000 tokens | 800 (system) + calls pontuais |
| Custo estimado por geração | Alto (contexto sempre cheio) | Redução de 40–60% em semanas simples |
| Debugabilidade | "Por que Claude decidiu X?" — opaco | Cada tool call logada: parâmetro + resposta |
| Dados stale | Snapshot no buildPrompt | Buscados no momento do raciocínio |
| Extensibilidade | Novo dado = novo formatter + modificar builder | Novo dado = novo `.md` + novo `@Tool` |
| Features novas | Requerem modificar `PlanoTreinoPromptBuilder` | Criam um novo skill file + `@Tool` independente |
| Toggle de feature | Recompilação | `enabled: false` no YAML |

---

### Dependências Cruzadas com outras Features

```
Skills (11)
  └─ habilita → Feature 1 (Readiness) via skill-readiness-dia.md
  └─ habilita → Feature 3 (Predição) via skill-predicao-prova.md
  └─ habilita → Feature 6 (Macrociclo) via skill-macrociclo-ativo.md
  └─ habilita → Feature 8 (Clima) via skill-previsao-climatica.md
  └─ usa → PromptTemplateLoader (já existente) para carregar descrições
  └─ usa → ChatClient (já existente) via .tools() no request
```

```
┌─────────────────────────────────────────────────────────────┐
│                     IMPACTO ALTO                            │
│                                                             │
│  ESFORÇO BAIXO          │          ESFORÇO ALTO            │
│  ─────────────────────  │  ──────────────────────────────  │
│  [1] Readiness Score    │  [5] Macrociclo                  │
│  [2] AI Debrief         │  [10] Adaptation Tracking        │
│  [9] Semana Prova       │  [3] Predição de Prova           │
│                         │                                  │
├─────────────────────────────────────────────────────────────┤
│                     IMPACTO MÉDIO                           │
│                                                             │
│  ESFORÇO BAIXO          │          ESFORÇO ALTO            │
│  ─────────────────────  │  ──────────────────────────────  │
│  [7] Rastr. Tênis       │  [6] Planned vs Actual           │
│  [8] Integração Clima   │  [4] Testes Protocolados         │
└─────────────────────────────────────────────────────────────┘
```

### Sprint 1 (quick wins de alto impacto)
1. **Check-in de Prontidão** — nova entidade + cálculo simples + seção no prompt
2. **AI Debrief pós-treino** — reutiliza `IaService`, novo prompt, campo em `TreinoRealizado`
3. **Semana de Prova Guiada** — lógica determinística, sem nova entidade

### Sprint 2 (diferenciadores técnicos)
4. **Predição de Prova** — fórmulas matemáticas, sem IA
5. **Rastreamento de Tênis** — nova entidade pequena + integração no registro de treino
6. **Testes Protocolados** — melhora qualidade dos dados base para toda a IA

### Sprint 3 (features de coach)
7. **Integração Climática** — API externa gratuita + formatter
8. **Planned vs Actual** — query agregada + novo endpoint
9. **Adaptation Tracking** — algoritmos matemáticos + enriquece `MetricasDiarias`
10. **Macrociclo** — maior esforço, maior diferenciação de mercado

---

## Dependências Cruzadas entre Features

```
Readiness Score (1)
  └─ alimenta → IntervaladoElegibilidadeService (já implementado)
  └─ alimenta → AI Debrief (2) como contexto do dia

Testes Protocolados (4)
  └─ melhora → todos os cálculos de zona (ZonaTreinoService)
  └─ melhora → predição de prova (3) com dados confiáveis

Macrociclo (6)
  └─ trigger: ProvaService.cadastrar() com provaAlvo=true
  └─ guia → PlanoServiceImpl (SemanaPlano como restrição hard de TSS/fase)
  └─ usa → PlanoMetaDados.ctlAtual para calcular ramp rate inicial
  └─ consome → Predição de Prova (3) para validar se CTL-alvo é realista

Adaptation Tracking (10)
  └─ alimenta → MetricasAlertaService (novos alertas)
  └─ alimenta → prompt via MetricasPromptFormatter
  └─ consome → AI Debrief (2) para gerar tendências acumuladas

Rastreamento de Tênis (7)
  └─ alimenta → AlertasPromptFormatter (alerta de desgaste)
  └─ alimenta → Semana de Prova (9) no checklist de D-0
```

---

## Stack e Convenções a Seguir

Todas as features devem seguir os padrões já estabelecidos no projeto:

| Aspecto | Convenção |
|---|---|
| Entidades | `@Builder(toBuilder=true)`, `@Getter @Setter @NoArgsConstructor @AllArgsConstructor` |
| Serviços | `@Service @RequiredArgsConstructor @Slf4j` ou `@Component @Slf4j` |
| Formatters de prompt | `@Component`, recebem dados pré-carregados, sem repositórios |
| DTOs de output | Java records com anotações OpenAPI |
| Migrações | Flyway em `src/main/resources/db/migration/` |
| Testes | `@ExtendWith(MockitoExtension.class)`, métodos `deve[Acao][Condicao]`, sem Spring context |
| Chamadas IA | `BeanOutputConverter<T>` para output estruturado, schema inline via `buildSchemaTightInlineOrDefs()` |
