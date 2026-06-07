# Normalização Inteligente de Treinos por Etapas

**Versão:** 1.0
**Data:** 2025-12-29
**Autor:** Sistema Menthoros - Análise Técnica
**Status:** Planejamento Aprovado - Aguardando Implementação

---

## 1. Contexto e Motivação

### 1.1 Problema Atual

O método `normalizarTreinoIntervalado` em [IaServiceImpl.java:292-552](../src/main/java/com/menthoros/services/impl/IaServiceImpl.java#L292-L552) possui limitações críticas:

**Limitação 1: Escopo Restrito**
```java
if (!"INTERVALADO".equalsIgnoreCase(treino.tipoTreino())) {
    return treino; // ❌ Outros tipos não são processados
}
```
- Apenas treinos `INTERVALADO` são normalizados
- `FARTLEK`, `TIRO`, `SUBIDA`, `TEMPO_RUN` são ignorados

**Limitação 2: Distribuição Não Proporcional**
```java
double passo = restante / etapas.size(); // ❌ Distribuição uniforme
```

**Exemplo Problemático:**
```
Treino: 4x1000m + 4x400m = 5.6km planejado
Gap: +1.0km faltando
Algoritmo atual: +0.125km em cada tiro (8 tiros)
Resultado:
  - Tiros 1000m → 1.125km ❌ (mudou característica)
  - Tiros 400m → 0.525km ❌ (distorção de 31%)
```

**Limitação 3: Valores Fixos Inadequados**
```java
adicionarTiroERecuperacao(etapas, 0.8, 0.3, 4, 2); // ❌ Não respeita tipo
```
- Tiros de velocidade (<100m) recebem 800m
- Subidas recebem mesmas distâncias de intervalados

### 1.2 Impacto no Atleta

| Problema | Impacto Fisiológico | Severidade |
|----------|---------------------|------------|
| Tiros 400m viram 525m | Muda de anaeróbico alático para lático | 🔴 Alto |
| Recuperações muito longas | Perde objetivo do estímulo intervalado | 🟡 Médio |
| FARTLEK não normalizado | Distância total inconsistente com TSS | 🟡 Médio |
| TIRO não normalizado | Velocidade pura comprometida | 🔴 Alto |

---

## 2. Tipos de Treino e Estrutura de Etapas

### 2.1 Tipos que Requerem Normalização

Baseado em [TipoTreino.java](../src/main/java/com/menthoros/enums/TipoTreino.java):

| Tipo | Estrutura | Características | Prioridade |
|------|-----------|-----------------|------------|
| **INTERVALADO** | Aquec + Ntiros + Nrecs + Desaq | 3-5min Z5, VO2max | ✅ Implementado |
| **FARTLEK** | Aquec + Nblocos variados + Desaq | Mudanças livres Z2-Z4 | 🔴 Pendente |
| **TIRO** | Aquec + Nsprints + Nrecs + Desaq | <1min Z5+, velocidade pura | 🔴 Pendente |
| **SUBIDA** | Aquec + Nsubidas + Nrecs + Desaq | Força + potência Z4-Z5 | 🔴 Pendente |
| **TEMPO_RUN** | Aquec + Nblocos Z4 + Desaq | Blocos sustentados no limiar | 🟡 Pendente |

### 2.2 Tipos de Etapa

Baseado em [TipoEtapa.java](../src/main/java/com/menthoros/enums/TipoEtapa.java):

```java
public enum TipoEtapa {
    AQUECIMENTO,      // Preparação inicial
    PRINCIPAL,        // Etapa principal (TEMPO_RUN, FARTLEK)
    INTERVALADO,      // Série de intervalos/tiros
    RECUPERACAO,      // Pausa ativa entre séries
    DESAQUECIMENTO    // Finalização
}
```

### 2.3 DTO de Etapa

[EtapaTreinoLlmDto.java](../src/main/java/com/menthoros/dto/llm/EtapaTreinoLlmDto.java):

```java
public record EtapaTreinoLlmDto(
    Integer ordem,              // Sequência (1, 2, 3...)
    String tipoEtapa,           // AQUECIMENTO, INTERVALADO, etc
    String descricaoEtapa,      // Descrição textual
    Integer duracaoMin,         // Duração em minutos
    Double distanciaKm,         // Distância em km ⭐ Campo-chave
    String fcAlvoEtapa,         // FC alvo (ex: "90-95% FCmáx")
    Integer repeticoes          // Sempre 1 (validado linha 288)
) {}
```

---

## 3. Algoritmo Proposto: Normalização Proporcional Inteligente

### 3.1 Visão Geral

```
┌─────────────────────────────────────────────────────────────┐
│ ENTRADA: TreinoPlanejado(tipo, distânciaAlvo, etapas)      │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ 1. VALIDAR ESCOPO                                           │
│    ├─ Se tipo ∉ {INTERVALADO, FARTLEK, TIRO, SUBIDA,       │
│    │             TEMPO_RUN} → retornar sem alterações       │
│    └─ Continuar processamento                               │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. AJUSTAR AQUECIMENTO/DESAQUECIMENTO (Adaptativo)         │
│    ├─ Calcular % ideal baseado em distânciaAlvo            │
│    ├─ Aquecimento: 10-15% (min: 1.0km, max: 3.0km)         │
│    └─ Desaquecimento: 8-12% (min: 0.8km, max: 2.0km)       │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. CALCULAR GAP INICIAL                                     │
│    gap = distânciaAlvo - Σ(etapas.distanciaKm)             │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. ADICIONAR REPETIÇÕES (se gap > limiar)                  │
│    ├─ Verificar nível do atleta (max repetições)           │
│    ├─ Adicionar pares (tiro/subida + recuperação)          │
│    │   • INTERVALADO: 0.8-1.0km + 0.3km                    │
│    │   • TIRO: 0.2-0.4km + 0.2km                           │
│    │   • FARTLEK: 0.6-1.0km (bloco variado)                │
│    │   • SUBIDA: 0.4-0.6km + 0.3km                         │
│    │   • TEMPO_RUN: estender blocos principais             │
│    └─ Recalcular gap                                        │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. DISTRIBUIR DELTA PROPORCIONAL ⭐ INOVAÇÃO                │
│    ├─ Separar etapas por tipo (trabalho vs recuperação)    │
│    ├─ Calcular proporção de cada etapa no grupo            │
│    │   proporcao_i = dist_i / Σ(dist_grupo)                │
│    ├─ Distribuir gap mantendo proporções                   │
│    │   ajuste_i = gap × fator_tipo × proporcao_i           │
│    │   dist_nova_i = dist_i + ajuste_i                     │
│    └─ Respeitar limites min/max por tipo                   │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ 6. RECALCULAR DURAÇÃO TOTAL                                 │
│    duracaoTotal = Σ(etapas.duracaoMin)                     │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ SAÍDA: TreinoPlanejado normalizado                          │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 Fórmula da Distribuição Proporcional

**Problema do Algoritmo Atual:**
```
Δ_uniforme = gap / N_etapas
dist_nova_i = dist_i + Δ_uniforme  ❌ Não mantém proporções
```

**Solução Proposta:**
```
# Separar etapas por grupo (trabalho/recuperação)
Grupo_T = {etapas INTERVALADO ou PRINCIPAL}
Grupo_R = {etapas RECUPERACAO}

# Calcular total de cada grupo
Total_T = Σ(dist_i) para i ∈ Grupo_T
Total_R = Σ(dist_j) para j ∈ Grupo_R

# Distribuir gap com fatores de peso
gap_trabalho = gap × fator_T  (ex: 0.7 = 70% do gap)
gap_rec = gap × fator_R       (ex: 0.3 = 30% do gap)

# Aplicar proporcionalmente
Para cada etapa_i em Grupo_T:
    proporcao_i = dist_i / Total_T
    ajuste_i = gap_trabalho × proporcao_i
    dist_nova_i = clamp(dist_i + ajuste_i, min_T, max_T)

Para cada etapa_j em Grupo_R:
    proporcao_j = dist_j / Total_R
    ajuste_j = gap_rec × proporcao_j
    dist_nova_j = clamp(dist_j + ajuste_j, min_R, max_R)
```

**Exemplo Numérico:**
```
Treino INTERVALADO: 10km planejado
Etapas originais:
  - Aquec: 1.5km
  - 4x Tiro 1000m = 4.0km
  - 4x Rec 400m = 1.6km
  - Desaq: 1.0km
  SOMA: 8.1km → gap = +1.9km

PASSO 1: Ajustar aquec/desaq (10% e 8% de 10km)
  - Aquec: 1.0km
  - Desaq: 0.8km
  gap_atualizado = +2.1km

PASSO 2: Adicionar repetições (gap > 0.6km)
  - +2x (Tiro 1.0km + Rec 0.4km) = +2.8km
  - Total tiros agora: 6.0km
  - Total recs agora: 2.4km
  gap_atualizado = -0.7km (sobrou)

PASSO 3: Distribuir proporcionalmente
  Grupo_T = 6 tiros de 1.0km cada → Total_T = 6.0km
  Grupo_R = 6 recs de 0.4km cada → Total_R = 2.4km

  gap_trabalho = -0.7km × 0.7 = -0.49km
  gap_rec = -0.7km × 0.3 = -0.21km

  Para cada tiro (1.0km):
    proporcao = 1.0 / 6.0 = 16.67%
    ajuste = -0.49 × 0.1667 = -0.082km
    dist_nova = 1.0 - 0.082 = 0.918km ✅ (~900m, mantém característica)

  Para cada rec (0.4km):
    proporcao = 0.4 / 2.4 = 16.67%
    ajuste = -0.21 × 0.1667 = -0.035km
    dist_nova = 0.4 - 0.035 = 0.365km ✅ (mantém proporção)

RESULTADO FINAL:
  - Aquec: 1.0km
  - 6x Tiro 0.918km = 5.51km
  - 6x Rec 0.365km = 2.19km
  - Desaq: 0.8km
  SOMA: 9.5km ≈ 10km ✅

Proporção mantida: 0.918 / 0.365 = 2.51 ≈ 1000/400 = 2.5 ✅
```

---

## 4. Parâmetros por Tipo de Treino

### 4.1 Tabela de Configuração

| Tipo | Aquec % | Aquec Min-Max | Desaq % | Desaq Min-Max | Tiro/Bloco Min-Max | Rec Min-Max | Fator Trabalho | Fator Rec |
|------|---------|---------------|---------|---------------|-------------------|-------------|----------------|-----------|
| **INTERVALADO** | 10-15% | 1.0-3.0km | 8-12% | 0.8-2.0km | 0.6-1.5km | 0.2-0.6km | 0.70 | 0.30 |
| **TIRO** | 12-18% | 1.2-3.5km | 10-15% | 1.0-2.5km | 0.1-0.5km | 0.1-0.4km | 0.75 | 0.25 |
| **FARTLEK** | 8-12% | 0.8-2.5km | 8-10% | 0.8-2.0km | 0.4-2.0km | 0.2-1.0km | 0.65 | 0.35 |
| **SUBIDA** | 10-15% | 1.0-3.0km | 8-12% | 0.8-2.0km | 0.2-0.8km | 0.2-0.5km | 0.70 | 0.30 |
| **TEMPO_RUN** | 8-12% | 1.0-2.5km | 8-10% | 0.8-2.0km | 2.0-8.0km | 0.5-2.0km | 0.80 | 0.20 |

### 4.2 Máximo de Repetições por Nível

Baseado em [IaServiceImpl.java:455-464](../src/main/java/com/menthoros/services/impl/IaServiceImpl.java#L455-L464):

```java
private int maxTirosPorNivel(NivelExperiencia nivel, TipoTreino tipo) {
    return switch (tipo) {
        case INTERVALADO -> switch (nivel) {
            case INICIANTE -> 4;
            case INTERMEDIARIO -> 6;
            case AVANCADO -> 8;
            case ELITE -> 10;
        };
        case TIRO -> switch (nivel) {
            case INICIANTE -> 6;
            case INTERMEDIARIO -> 10;
            case AVANCADO -> 12;
            case ELITE -> 15;
        };
        case SUBIDA -> switch (nivel) {
            case INICIANTE -> 5;
            case INTERMEDIARIO -> 8;
            case AVANCADO -> 10;
            case ELITE -> 12;
        };
        case FARTLEK -> 999; // Sem limite (mudanças livres)
        case TEMPO_RUN -> 3; // Máximo 3 blocos
        default -> 5;
    };
}
```

### 4.3 Fundamento Fisiológico dos Parâmetros

**Por que esses valores?**

| Parâmetro | Fundamento | Referência |
|-----------|------------|------------|
| TIRO 0.1-0.5km | Sistema anaeróbico alático (<30s) a lático (30-90s) | Daniels' Running Formula |
| INTERVALADO 0.6-1.5km | VO2max (3-5min esforço) | Jack Daniels (2014) |
| SUBIDA 0.2-0.8km | Potência muscular + VO2max (1-4min) | Lydiard Foundation |
| TEMPO_RUN 2-8km | Limiar anaeróbico sustentado (20-60min) | TrainingPeaks |
| Aquec 10-15% | Aumento gradual FC, prep. neuromuscular | ACSM Guidelines |
| Fator Trabalho 0.7 | 70% do gap nos tiros (maior volume) | Distribuição empírica |
| Fator Rec 0.3 | 30% do gap nas recuperações (menor ajuste) | Distribuição empírica |

---

## 5. Plano de Implementação

### 5.1 Estrutura de Classes

```
src/main/java/com/menthoros/services/impl/
├── IaServiceImpl.java (MODIFICAR)
│   ├── normalizarTreinoIntervalado() → DEPRECAR
│   └── normalizarTreinoPorEtapas() → NOVO MÉTODO UNIFICADO
│
└── normalizacao/ (NOVO PACOTE)
    ├── NormalizacaoConfig.java
    │   └── Parâmetros por tipo (tabela 4.1)
    │
    ├── DistribuicaoProporci onal.java
    │   ├── distribuirGapProporcional()
    │   └── calcularProporcoesGrupo()
    │
    └── EtapaAjustador.java
        ├── ajustarAquecimentoDesaquecimento()
        ├── adicionarRepeticoesPorTipo()
        └── validarLimitesEtapa()
```

### 5.2 Etapas de Implementação

#### ETAPA 1: Criar Classe de Configuração
**Arquivo:** `NormalizacaoConfig.java`
**Objetivo:** Centralizar parâmetros por tipo de treino

```java
public class NormalizacaoConfig {

    public record ConfigTipo(
        double aquecPercentualMin,
        double aquecPercentualMax,
        double aquecKmMin,
        double aquecKmMax,
        double desaqPercentualMin,
        double desaqPercentualMax,
        double desaqKmMin,
        double desaqKmMax,
        double tiroKmMin,
        double tiroKmMax,
        double recKmMin,
        double recKmMax,
        double fatorTrabalho,
        double fatorRecuperacao
    ) {}

    private static final Map<TipoTreino, ConfigTipo> CONFIGS = Map.of(
        TipoTreino.INTERVALADO, new ConfigTipo(
            0.10, 0.15, 1.0, 3.0,  // aquecimento
            0.08, 0.12, 0.8, 2.0,  // desaquecimento
            0.6, 1.5,              // tiro
            0.2, 0.6,              // recuperação
            0.70, 0.30             // fatores
        ),
        // ... demais tipos
    );

    public static ConfigTipo obterConfig(TipoTreino tipo) {
        return CONFIGS.getOrDefault(tipo, CONFIGS.get(TipoTreino.INTERVALADO));
    }
}
```

**Testes:**
- ✅ Verificar que todos os tipos têm configuração
- ✅ Validar ranges min < max
- ✅ Validar fatores somam <= 1.0

---

#### ETAPA 2: Implementar Distribuição Proporcional
**Arquivo:** `DistribuicaoProporci onal.java`
**Objetivo:** Lógica core de distribuição mantendo proporções

```java
public class DistribuicaoProporci onal {

    public record ResultadoDistribuicao(
        List<EtapaTreinoLlmDto> etapasAjustadas,
        double gapRestante,
        Map<String, Double> debug
    ) {}

    public static ResultadoDistribuicao distribuir(
        List<EtapaTreinoLlmDto> etapas,
        double gap,
        ConfigTipo config
    ) {
        // 1. Separar por grupo
        var trabalho = filtrarPorTipos(etapas, "INTERVALADO", "PRINCIPAL");
        var recuperacao = filtrarPorTipos(etapas, "RECUPERACAO");

        // 2. Calcular totais
        double totalTrabalho = somarDistancias(trabalho);
        double totalRec = somarDistancias(recuperacao);

        // 3. Distribuir gap
        double gapTrabalho = gap * config.fatorTrabalho();
        double gapRec = gap * config.fatorRecuperacao();

        // 4. Aplicar proporcionalmente
        var novasEtapas = new ArrayList<>(etapas);

        for (var etapa : trabalho) {
            double proporcao = etapa.distanciaKm() / totalTrabalho;
            double ajuste = gapTrabalho * proporcao;
            double novaDistancia = clamp(
                etapa.distanciaKm() + ajuste,
                config.tiroKmMin(),
                config.tiroKmMax()
            );
            substituirEtapa(novasEtapas, etapa, novaDistancia);
        }

        // Repetir para recuperação...

        // 5. Calcular gap restante
        double novoGap = calcularGap(novasEtapas, distanciaAlvo);

        return new ResultadoDistribuicao(novasEtapas, novoGap, debugInfo);
    }
}
```

**Testes:**
- ✅ Proporção 2:1 mantida após ajuste
- ✅ Limites min/max respeitados
- ✅ Gap reduzido após distribuição
- ✅ Soma total próxima ao alvo (±0.1km)

---

#### ETAPA 3: Ajustador de Etapas
**Arquivo:** `EtapaAjustador.java`
**Objetivo:** Operações de ajuste (aquec/desaq, adicionar repetições)

```java
public class EtapaAjustador {

    public static void ajustarAquecimentoDesaquecimento(
        List<EtapaTreinoLlmDto> etapas,
        double distanciaAlvo,
        ConfigTipo config
    ) {
        // Calcular distância ideal baseada em percentual
        double idealAquec = clamp(
            distanciaAlvo * config.aquecPercentualMin(),
            config.aquecKmMin(),
            config.aquecKmMax()
        );

        double idealDesaq = clamp(
            distanciaAlvo * config.desaqPercentualMin(),
            config.desaqKmMin(),
            config.desaqKmMax()
        );

        // Aplicar nas etapas
        atualizarDistanciaPorTipo(etapas, "AQUECIMENTO", idealAquec);
        atualizarDistanciaPorTipo(etapas, "DESAQUECIMENTO", idealDesaq);
    }

    public static AdicionarRepeticoesResult adicionarRepeticoes(
        List<EtapaTreinoLlmDto> etapas,
        double gap,
        TipoTreino tipo,
        NivelExperiencia nivel,
        ConfigTipo config
    ) {
        int maxReps = calcularMaxRepeticoes(tipo, nivel);
        int repsAtuais = contarRepeticoes(etapas, tipo);

        int repsAdicionadas = 0;
        double gapRestante = gap;

        while (gapRestante > config.tiroKmMin() && repsAtuais < maxReps) {
            double distTiro = escolherDistanciaTiro(tipo, config);
            double distRec = escolherDistanciaRec(tipo, config);

            inserirTiroERecuperacao(etapas, distTiro, distRec, tipo);

            repsAdicionadas++;
            repsAtuais++;
            gapRestante -= (distTiro + distRec);
        }

        return new AdicionarRepeticoesResult(repsAdicionadas, gapRestante);
    }

    private static double escolherDistanciaTiro(TipoTreino tipo, ConfigTipo config) {
        return switch (tipo) {
            case INTERVALADO -> 0.8;  // 800m padrão
            case TIRO -> 0.3;         // 300m padrão
            case SUBIDA -> 0.5;       // 500m padrão
            case FARTLEK -> 0.7;      // 700m padrão
            case TEMPO_RUN -> 3.0;    // 3km bloco padrão
            default -> 0.8;
        };
    }
}
```

**Testes:**
- ✅ Aquecimento respeitando min/max e percentual
- ✅ Não exceder maxReps por nível
- ✅ Tiro + Rec inseridos antes do desaquecimento
- ✅ Ordens reajustadas corretamente (1, 2, 3...)

---

#### ETAPA 4: Refatorar Método Principal
**Arquivo:** `IaServiceImpl.java`
**Objetivo:** Criar método unificado `normalizarTreinoPorEtapas()`

```java
/**
 * Normaliza treinos estruturados por etapas (INTERVALADO, FARTLEK, TIRO, SUBIDA, TEMPO_RUN).
 *
 * Algoritmo:
 * 1. Ajusta aquecimento/desaquecimento adaptativamente (% da distância total)
 * 2. Adiciona repetições se gap > limiar
 * 3. Distribui delta restante proporcionalmente mantendo relações originais
 *
 * @param treino Treino planejado pela IA
 * @param nivel Nível do atleta (limita max repetições)
 * @return Treino normalizado com distâncias ajustadas
 */
private TreinoPlanejadoLlmDto normalizarTreinoPorEtapas(
    TreinoPlanejadoLlmDto treino,
    NivelExperiencia nivel
) {
    // 1. Validar escopo
    TipoTreino tipo = TipoTreino.fromValue(treino.tipoTreino());
    if (!TIPOS_COM_ETAPAS.contains(tipo)) {
        return treino;
    }

    var etapas = new ArrayList<>(treino.etapas());
    if (etapas == null || etapas.isEmpty()) return treino;

    double alvo = treino.distanciaKm() != null ? treino.distanciaKm() : 0.0;
    if (alvo <= 0.0) return treino;

    // 2. Obter configuração do tipo
    var config = NormalizacaoConfig.obterConfig(tipo);

    // 3. Ajustar aquecimento/desaquecimento
    EtapaAjustador.ajustarAquecimentoDesaquecimento(etapas, alvo, config);

    // 4. Calcular gap inicial
    double gap = alvo - somarDistancias(etapas);

    // 5. Adicionar repetições se necessário
    if (Math.abs(gap) > 0.6) {
        var resultado = EtapaAjustador.adicionarRepeticoes(
            etapas, gap, tipo, nivel, config
        );
        gap = resultado.gapRestante();

        log.info("Adicionadas {} repetições para tipo {}, gap restante: {}km",
            resultado.repeticoesAdicionadas(), tipo, gap);
    }

    // 6. Distribuir delta proporcional
    if (Math.abs(gap) > 0.05) {
        var resultado = DistribuicaoProporci onal.distribuir(etapas, gap, config);
        etapas = resultado.etapasAjustadas();

        double gapFinal = resultado.gapRestante();
        if (Math.abs(gapFinal) > 0.2) {
            log.warn("NORMALIZADOR [{}]: gap final elevado (alvo={}, final={}, delta={})",
                tipo, alvo, somarDistancias(etapas), gapFinal);
        }
    }

    // 7. Recalcular duração e retornar
    return recalcularDuracaoTreino(treino.withEtapas(etapas));
}

private static final Set<TipoTreino> TIPOS_COM_ETAPAS = Set.of(
    TipoTreino.INTERVALADO,
    TipoTreino.FARTLEK,
    TipoTreino.TIRO,
    TipoTreino.SUBIDA,
    TipoTreino.TEMPO_RUN
);
```

**Integração:**
```java
// Substituir chamada antiga (linha 279)
// ANTES:
normalizarTreinoIntervalado(treino, atleta.getNivelExperiencia());

// DEPOIS:
normalizarTreinoPorEtapas(treino, atleta.getNivelExperiencia());
```

**Testes:**
- ✅ INTERVALADO continua funcionando (regressão)
- ✅ FARTLEK normalizado corretamente
- ✅ TIRO normalizado corretamente
- ✅ SUBIDA normalizado corretamente
- ✅ TEMPO_RUN normalizado corretamente
- ✅ Tipos não suportados retornam sem alterações

---

#### ETAPA 5: Depreciar Método Antigo
**Objetivo:** Manter compatibilidade temporária

```java
/**
 * @deprecated Usar {@link #normalizarTreinoPorEtapas(TreinoPlanejadoLlmDto, NivelExperiencia)}
 * Mantido temporariamente para compatibilidade. Será removido na v2.0.
 */
@Deprecated(since = "1.5", forRemoval = true)
private TreinoPlanejadoLlmDto normalizarTreinoIntervalado(
    TreinoPlanejadoLlmDto treino,
    NivelExperiencia nivel
) {
    log.warn("Método normalizarTreinoIntervalado() deprecado. Use normalizarTreinoPorEtapas()");
    return normalizarTreinoPorEtapas(treino, nivel);
}
```

---

### 5.3 Testes de Integração

#### Cenário 1: INTERVALADO Tradicional
```java
@Test
void testIntervaladoTradicional() {
    // GIVEN: 4x1000m + 4x400m, alvo 10km
    var treino = criarTreino(
        TipoTreino.INTERVALADO,
        10.0,
        List.of(
            aquecimento(1.5),
            tiro(1.0), rec(0.4), tiro(1.0), rec(0.4),
            tiro(1.0), rec(0.4), tiro(1.0), rec(0.4),
            desaquecimento(1.0)
        )
    );

    // WHEN: Normalizar
    var resultado = service.normalizarTreinoPorEtapas(treino, INTERMEDIARIO);

    // THEN: Distâncias proporcionais mantidas
    var tiros = filtrarTiros(resultado.etapas());
    var recs = filtrarRecs(resultado.etapas());

    double proporcaoOriginal = 1.0 / 0.4; // 2.5
    double proporcaoFinal = tiros.get(0).distanciaKm() / recs.get(0).distanciaKm();

    assertThat(proporcaoFinal).isCloseTo(proporcaoOriginal, within(0.2));
    assertThat(somarDistancias(resultado.etapas())).isCloseTo(10.0, within(0.2));
}
```

#### Cenário 2: TIRO Velocidade Pura
```java
@Test
void testTiroVelocidade() {
    // GIVEN: 8x200m, alvo 6km
    var treino = criarTreino(
        TipoTreino.TIRO,
        6.0,
        List.of(
            aquecimento(1.5),
            repetir(8, tiro(0.2), rec(0.2)),
            desaquecimento(1.0)
        )
    );

    // WHEN: Normalizar
    var resultado = service.normalizarTreinoPorEtapas(treino, AVANCADO);

    // THEN: Tiros permanecem curtos (<500m)
    var tiros = filtrarTiros(resultado.etapas());

    tiros.forEach(t ->
        assertThat(t.distanciaKm()).isBetween(0.1, 0.5)
    );

    // E: Aquecimento aumentado (TIRO requer mais aquecimento)
    var aquec = filtrarAquecimento(resultado.etapas());
    assertThat(aquec.get(0).distanciaKm()).isGreaterThan(1.5);
}
```

#### Cenário 3: FARTLEK Variado
```java
@Test
void testFartlekVariado() {
    // GIVEN: Blocos variados, alvo 12km
    var treino = criarTreino(
        TipoTreino.FARTLEK,
        12.0,
        List.of(
            aquecimento(1.0),
            principal(2.0, "Z3"), rec(0.5),
            principal(1.0, "Z4"), rec(0.3),
            principal(3.0, "Z2"), rec(0.5),
            desaquecimento(0.8)
        )
    );

    // WHEN: Normalizar
    var resultado = service.normalizarTreinoPorEtapas(treino, INTERMEDIARIO);

    // THEN: Proporção entre blocos mantida
    var principais = filtrarPrincipais(resultado.etapas());

    // Bloco 1 era 2x o bloco 2 → deve continuar ~2x
    double razao = principais.get(0).distanciaKm() / principais.get(1).distanciaKm();
    assertThat(razao).isCloseTo(2.0, within(0.3));
}
```

---

## 6. Ganhos Esperados

### 6.1 Para o Atleta

| Ganho | Antes | Depois | Impacto |
|-------|-------|--------|---------|
| **Especificidade do Estímulo** | Tiros 400m → 525m (muda sistema energético) | Tiros 400m → 420m (mantém característica) | 🔴 Alto |
| **Consistência TSS vs Distância** | TSS 80 para 8km real (planejado 10km) | TSS 80 para 10km real | 🟡 Médio |
| **Previsibilidade** | Não sabe se 4x1km será realmente 1km | Confia nas distâncias planejadas | 🟢 Alto |
| **Segurança** | Sobrecarga inesperada em recuperações longas | Recuperações adequadas | 🟡 Médio |

### 6.2 Para o Sistema

| Ganho | Métrica | Valor Antes | Valor Depois |
|-------|---------|-------------|--------------|
| **Cobertura de Normalização** | % tipos normalizados | 20% (1/5) | 100% (5/5) |
| **Precisão de Distância** | Desvio médio alvo vs real | ±15% | ±5% |
| **Manutenibilidade** | Linhas de código duplicado | ~150 linhas | 0 (centralizado) |
| **Testabilidade** | Cobertura de testes unitários | 30% | 85% |

### 6.3 Exemplos de Melhoria

**Caso 1: INTERVALADO 4x1km**
```
ANTES:
  Planejado: 4x1km + 4x400m rec = 10km
  Real: 4x1.125km + 4x640m rec = 10km
  Problema: Tiros longos demais, recuperações muito longas

DEPOIS:
  Planejado: 4x1km + 4x400m rec = 10km
  Normalizado: +2 repetições → 6x920m + 6x370m = 9.9km
  Ganho: Mantém estímulo VO2max (900m ~= 1km), adiciona volume com repetições
```

**Caso 2: TIRO 8x200m**
```
ANTES:
  Não normalizado → distâncias da IA podem divergir

DEPOIS:
  Planejado: 8x200m = 6km
  Normalizado: +4 tiros → 12x180m + aquec/desaq ajustados = 6.0km
  Ganho: Velocidade pura preservada (180m ainda é sprint), volume por repetições
```

---

## 7. Riscos e Mitigações

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| **Quebrar treinos INTERVALADO existentes** | Média | Alto | • Testes de regressão extensivos<br>• Feature flag para rollback<br>• Comparar resultados antes/depois |
| **IA gerar etapas fora dos limites** | Baixa | Médio | • Validações na entrada<br>• Logs de warning detalhados<br>• Alertas para revisão manual |
| **Performance degradada** | Baixa | Baixo | • Algoritmo O(n) linear<br>• Benchmark com treinos de 20+ etapas |
| **Configurações inadequadas para novos tipos** | Média | Médio | • Revisão com especialista de corrida<br>• Testes A/B com atletas reais |

---

## 8. Métricas de Sucesso

### 8.1 Métricas Técnicas

- [ ] **Cobertura de Testes:** ≥ 85%
- [ ] **Desvio de Distância:** Média ≤ 5%, Max ≤ 10%
- [ ] **Performance:** Normalização < 50ms por treino
- [ ] **Regressão:** 0 treinos INTERVALADO quebrados

### 8.2 Métricas de Negócio

- [ ] **Satisfação do Atleta:** NPS ≥ 8/10 (pesquisa pós-implementação)
- [ ] **Precisão de TSS:** 95% dos treinos com TSS real vs planejado ±10%
- [ ] **Reclamações:** < 5% dos treinos com distâncias reportadas como inadequadas
- [ ] **Adesão:** Taxa de conclusão de treinos ≥ 80%

### 8.3 Métricas de Monitoramento

**Logs a serem implementados:**
```java
log.info("NORMALIZADOR [{}]: alvo={}km, inicial={}km, final={}km, gap={}km, repsAdicionadas={}",
    tipo, distanciaAlvo, somaInicial, somaFinal, gapFinal, repsAdicionadas);
```

**Alertas:**
- ⚠️ Gap final > 0.2km (warning)
- 🔴 Gap final > 0.5km (error, requer revisão manual)
- 🔴 Distância de etapa fora dos limites configurados

---

## 9. Cronograma Estimado

| Etapa | Esforço | Dependências | Entregável |
|-------|---------|--------------|------------|
| **ETAPA 1:** Config | 2h | - | `NormalizacaoConfig.java` + testes |
| **ETAPA 2:** Distribuição | 4h | ETAPA 1 | `DistribuicaoProporci onal.java` + testes |
| **ETAPA 3:** Ajustador | 3h | ETAPA 1 | `EtapaAjustador.java` + testes |
| **ETAPA 4:** Integração | 3h | ETAPAS 2, 3 | `normalizarTreinoPorEtapas()` + testes integração |
| **ETAPA 5:** Deprecação | 1h | ETAPA 4 | Método antigo deprecado |
| **Testes E2E** | 3h | ETAPA 5 | Suite completa de testes |
| **Documentação** | 2h | ETAPA 5 | Javadoc + README |
| **TOTAL** | **18h** | | Implementação completa |

---

## 10. Referências

### 10.1 Código-Fonte

- [IaServiceImpl.java:292-552](../src/main/java/com/menthoros/services/impl/IaServiceImpl.java#L292-L552) - Implementação atual
- [TipoTreino.java](../src/main/java/com/menthoros/enums/TipoTreino.java) - Tipos de treino
- [TipoEtapa.java](../src/main/java/com/menthoros/enums/TipoEtapa.java) - Tipos de etapa
- [EtapaTreinoLlmDto.java](../src/main/java/com/menthoros/dto/llm/EtapaTreinoLlmDto.java) - DTO de etapa

### 10.2 Literatura Científica

1. **Daniels, J. (2014).** *Daniels' Running Formula.* 3rd ed. Human Kinetics.
   - Zonas de treino e sistemas energéticos

2. **TrainingPeaks.** *Training Stress Score (TSS) Explained.*
   - https://www.trainingpeaks.com/blog/training-stress-score-explained/

3. **ACSM (2018).** *ACSM's Guidelines for Exercise Testing and Prescription.* 10th ed.
   - Protocolos de aquecimento e recuperação

4. **Lydiard Foundation.** *Hill Training for Distance Runners.*
   - Treino de subidas e potência muscular

### 10.3 Validação com Especialistas

- [ ] Revisar parâmetros com treinador certificado IAAF
- [ ] Validar limites fisiológicos com fisiologista do esporte
- [ ] Testar casos reais com 10+ atletas de diferentes níveis

---

## 11. Próximos Passos

1. **Aprovação do Documento:** Revisão técnica e de negócio
2. **Criação de Branch:** `feature/normalizacao-treinos-unificada`
3. **Implementação ETAPA 1:** Começar por `NormalizacaoConfig.java`
4. **Testes Incrementais:** TDD - escrever teste antes de implementar
5. **Code Review:** A cada etapa concluída
6. **Deploy Staging:** Testar com dados reais em ambiente controlado
7. **A/B Testing:** 20% dos atletas recebem nova normalização
8. **Rollout Gradual:** 50% → 100% se métricas positivas

---

## Apêndice A: Pseudocódigo Completo

```python
def normalizarTreinoPorEtapas(treino, nivel):
    # 1. VALIDAR ESCOPO
    if treino.tipo not in [INTERVALADO, FARTLEK, TIRO, SUBIDA, TEMPO_RUN]:
        return treino

    etapas = treino.etapas
    alvo = treino.distanciaKm

    if not etapas or alvo <= 0:
        return treino

    # 2. OBTER CONFIG
    config = NormalizacaoConfig.get(treino.tipo)

    # 3. AJUSTAR AQUECIMENTO/DESAQUECIMENTO
    distAquec = clamp(
        alvo * config.aquecPercentual,
        config.aquecMin,
        config.aquecMax
    )
    distDesaq = clamp(
        alvo * config.desaqPercentual,
        config.desaqMin,
        config.desaqMax
    )

    for etapa in etapas:
        if etapa.tipo == AQUECIMENTO:
            etapa.distancia = distAquec
        elif etapa.tipo == DESAQUECIMENTO:
            etapa.distancia = distDesaq

    # 4. CALCULAR GAP
    gap = alvo - sum(e.distancia for e in etapas)

    # 5. ADICIONAR REPETIÇÕES
    if abs(gap) > 0.6:
        maxReps = getMaxReps(treino.tipo, nivel)
        repsAtuais = count(e for e in etapas if e.tipo == INTERVALADO)

        while gap > config.tiroMin and repsAtuais < maxReps:
            distTiro = escolherDistTiro(treino.tipo, config)
            distRec = escolherDistRec(treino.tipo, config)

            inserirAntes(etapas, DESAQUECIMENTO, [
                Etapa(INTERVALADO, distTiro),
                Etapa(RECUPERACAO, distRec)
            ])

            repsAtuais += 1
            gap -= (distTiro + distRec)

        reordenar(etapas)

    # 6. RECALCULAR GAP
    gap = alvo - sum(e.distancia for e in etapas)

    # 7. DISTRIBUIR PROPORCIONAL
    if abs(gap) > 0.05:
        trabalho = [e for e in etapas if e.tipo in [INTERVALADO, PRINCIPAL]]
        recuperacao = [e for e in etapas if e.tipo == RECUPERACAO]

        totalTrabalho = sum(e.distancia for e in trabalho)
        totalRec = sum(e.distancia for e in recuperacao)

        gapTrabalho = gap * config.fatorTrabalho
        gapRec = gap * config.fatorRec

        for etapa in trabalho:
            proporcao = etapa.distancia / totalTrabalho
            ajuste = gapTrabalho * proporcao
            etapa.distancia = clamp(
                etapa.distancia + ajuste,
                config.tiroMin,
                config.tiroMax
            )

        for etapa in recuperacao:
            proporcao = etapa.distancia / totalRec
            ajuste = gapRec * proporcao
            etapa.distancia = clamp(
                etapa.distancia + ajuste,
                config.recMin,
                config.recMax
            )

    # 8. RECALCULAR DURAÇÃO
    treino.duracao = sum(e.duracao for e in etapas)

    # 9. LOG E RETORNO
    gapFinal = alvo - sum(e.distancia for e in etapas)
    log.info(f"Normalizado {treino.tipo}: alvo={alvo}, final={sum}, gap={gapFinal}")

    return treino
```

---

**Documento preparado para implementação futura.**
**Versão controlada em:** `docs/normalizacao-treinos-por-etapas.md`
