# ISSUE-09: Melhoria — TSS por etapas (`EtapaRealizada`) nao utilizado

**Severidade:** BAIXA (Melhoria de precisao)
**Arquivo:** `services/helper/TssCalculatorService.java`
**Linhas:** 40-58

---

## Descricao

O modelo ja possui `EtapaRealizada` com metricas detalhadas por etapa do treino:

```java
// EtapaRealizada.java
private Duration duracao;
private Integer fcMedia;
private Integer fcMax;
private Duration paceMedia;
private Integer percepcaoEsforco;
private BigDecimal distanciaKm;
```

Porem o `calcularTss()` usa apenas os dados **agregados** do `TreinoRealizado` (FC media geral, pace media geral). Para treinos intervalados, isso e impreciso.

### Problema com Media Geral

Um treino intervalado tipico:

```
Etapa 1: Aquecimento   - 15min @ FC 135 (zona 2)
Etapa 2: 5x1000m      - 25min @ FC 178 (zona 5)
Etapa 3: Recuperacao   - 5min  @ FC 125 (zona 1)
Etapa 4: 5x1000m      - 25min @ FC 180 (zona 5)
Etapa 5: Desaquecimento- 10min @ FC 130 (zona 1)

FC media geral = (135*15 + 178*25 + 125*5 + 180*25 + 130*10) / 80
              = 160 bpm
```

Calculando TSS pela media:
- IF com FC media 160: ~0.85 → TSS = 80min/60 * 0.85^2 * 100 = **96**

Calculando TSS por etapa e somando:
- Aquecimento: IF 0.55 → TSS = 15/60 * 0.55^2 * 100 = 7.6
- Intervalos 1: IF 1.05 → TSS = 25/60 * 1.05^2 * 100 = 45.9
- Recuperacao: IF 0.48 → TSS = 5/60 * 0.48^2 * 100 = 1.9
- Intervalos 2: IF 1.07 → TSS = 25/60 * 1.07^2 * 100 = 47.7
- Desaquecimento: IF 0.52 → TSS = 10/60 * 0.52^2 * 100 = 4.5
- **Total: 107.6** (vs 96 pela media → diferenca de ~12%)

A funcao IF^2 e convexa, entao pela **desigualdade de Jensen**: `E[IF^2] >= E[IF]^2`. O calculo por media **sempre subestima** TSS para treinos com variacao de intensidade.

## Plano de Correcao

### Opcao A (Recomendada) — Calculo hibrido: por etapas quando disponivel

```java
public int calcularTss(TreinoRealizado treino) {
    // Se tem etapas detalhadas, calcular por etapa (mais preciso)
    if (treino.getEtapasRealizadas() != null && !treino.getEtapasRealizadas().isEmpty()) {
        int tssPorEtapas = calcularTssPorEtapas(treino);
        return aplicarFatorImpactoTreino(tssPorEtapas, treino);
    }

    // Fallback: calculo pela media (comportamento atual)
    int tssBase = calcularTssMediaGeral(treino);
    return aplicarFatorImpactoTreino(tssBase, treino);
}

/**
 * Calcula TSS somando contribuicao de cada etapa.
 * Mais preciso para treinos intervalados (desigualdade de Jensen).
 */
private int calcularTssPorEtapas(TreinoRealizado treino) {
    Atleta atleta = treino.getAtleta();
    double tssTotal = 0.0;

    for (EtapaRealizada etapa : treino.getEtapasRealizadas()) {
        double duracaoHoras = etapa.getDuracao() != null
                ? etapa.getDuracao().toMinutes() / 60.0
                : 0.0;

        if (duracaoHoras <= 0) continue;

        double intensityFactor = calcularIfEtapa(etapa, atleta);
        tssTotal += duracaoHoras * intensityFactor * 100 * intensityFactor;
    }

    return (int) Math.round(tssTotal);
}

/**
 * Calcula IF para uma etapa individual,
 * usando o melhor dado disponivel (FC > Pace > RPE).
 */
private double calcularIfEtapa(EtapaRealizada etapa, Atleta atleta) {
    // Prioridade 1: FC
    if (etapa.getFcMedia() != null && etapa.getFcMedia() > 0
            && atleta.getFcMaxima() != null && atleta.getFcRepouso() != null) {
        return calcularIfPorFc(etapa.getFcMedia(), atleta);
    }

    // Prioridade 2: Pace
    if (etapa.getPaceMedia() != null && atleta.getPaceLimiar() != null) {
        double paceMin = etapa.getPaceMedia().toMillis() / 60000.0;
        if (paceMin > 0) {
            double paceLimiar = atleta.getPaceLimiar().doubleValue();
            return Math.max(0.5, Math.min(1.5, paceLimiar / paceMin));
        }
    }

    // Prioridade 3: RPE
    if (etapa.getPercepcaoEsforco() != null) {
        return converterRpeParaIf(etapa.getPercepcaoEsforco());
    }

    // Sem dados: assumir intensidade moderada
    return 0.7;
}

private double calcularIfPorFc(int fcMedia, Atleta atleta) {
    Integer fcMax = atleta.getFcMaxima();
    Integer fcRepouso = atleta.getFcRepouso();
    Integer fcLimiar = atleta.getFcLimiar() != null
            ? atleta.getFcLimiar()
            : (int) (fcRepouso + (fcMax - fcRepouso) * 0.85);

    double hrReserve = fcMax - fcRepouso;
    double workingHR = fcMedia - fcRepouso;
    double hrReservePercent = workingHR / hrReserve;
    double thresholdPercent = (fcLimiar - fcRepouso) / hrReserve;
    double intensityFactor = hrReservePercent / thresholdPercent;

    return Math.max(0.5, Math.min(1.5, intensityFactor));
}
```

### Nota sobre Fator de Impacto

Se o TSS e calculado por etapas, o fator de impacto por tipo de treino (`aplicarFatorImpactoTreino`) pode precisar de ajuste menor, pois o calculo por etapa ja captura melhor a variacao de intensidade. Considerar integrar com a correcao da ISSUE-04.

### Nota sobre Lazy Loading

`EtapaRealizada` esta mapeada com `FetchType.LAZY`. O `calcularTssDia()` recebe `List<TreinoRealizado>` ja carregados. Verificar se as etapas serao carregadas corretamente ou se precisa de `@EntityGraph` / `JOIN FETCH` na query.

## Arquivos Afetados

| Arquivo | Alteracao |
|---|---|
| `services/helper/TssCalculatorService.java` | Adicionar `calcularTssPorEtapas()` e metodos auxiliares |
| `repository/TreinoRealizadoRepository.java` | Possivel: adicionar query com JOIN FETCH etapas |

## Verificacao

```bash
./mvnw compile && ./mvnw test
```

- Comparar TSS por etapas vs TSS por media para treinos intervalados existentes
- Validar que treinos sem etapas continuam usando calculo por media
- Verificar lazy loading: etapas sao carregadas corretamente no fluxo
- Validar que TSS por etapas >= TSS por media (desigualdade de Jensen)
