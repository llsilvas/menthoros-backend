# ISSUE-05: Inconsistencia — Ramp Rate usa thresholds absolutos em vez de relativos

**Severidade:** MEDIA (Inconsistencia de dominio)
**Arquivos:** `enums/MetricasThresholds.java`, `services/impl/MetricasAlertaService.java`, `services/impl/TsbServiceImpl.java`

---

## Descricao

O Ramp Rate (variacao semanal do CTL) usa thresholds absolutos para classificar risco:

```java
RAMP_RATE_CRITICO = 10.0;  // pts/semana
RAMP_RATE_ALTO = 8.0;      // pts/semana
```

Na ciencia do treinamento (Tim Gabbett, 2016 - BJSM), o padrao ACWR (Acute:Chronic Workload Ratio) recomenda usar **razao** em vez de diferenca absoluta, porque o mesmo incremento absoluto tem impactos drasticamente diferentes conforme o nivel de fitness:

| Cenario | CTL atual | Ramp +8 | % aumento | Risco real | Alerta emitido |
|---|:---:|:---:|:---:|---|---|
| Iniciante | 15 | → 23 | **+53%** | ALTISSIMO | ALTO (correto por acaso) |
| Iniciante | 20 | → 26 | +30% | ALTO | Nenhum (6 pts < 8) |
| Intermediario | 50 | → 58 | +16% | Moderado | ALTO |
| Avancado | 80 | → 88 | +10% | Baixo | ALTO (falso positivo) |
| Elite | 120 | → 130 | +8.3% | Normal | CRITICO (falso positivo) |

## Impacto

1. **Iniciantes**: Ramp-ups perigosos de 30-50% nao sao detectados se estao abaixo de 8 pts absolutos
2. **Elite/Avancados**: Progressoes normais de mesociclo geram alertas falsos
3. **PromptBuilder**: Recomendacoes de reducao de volume para atletas que nao precisam

## Plano de Correcao

### Opcao A (Recomendada) — Ramp Rate relativo (percentual do CTL)

Converter o ramp rate para percentual e usar thresholds relativos:

```java
// Em MetricasThresholds.java:
/** Ramp rate relativo acima deste valor = progressao critica */
public static final double RAMP_RATE_RELATIVO_CRITICO = 15.0;  // 15% do CTL/semana

/** Ramp rate relativo acima deste valor = progressao rapida */
public static final double RAMP_RATE_RELATIVO_ALTO = 10.0;     // 10% do CTL/semana

/** CTL minimo para usar ramp rate relativo (evitar divisao por CTL muito baixo) */
public static final double CTL_MINIMO_RAMP_RELATIVO = 10.0;
```

```java
// Em TsbServiceImpl.calcularRampRate():
private double calcularRampRate(UUID atletaId, LocalDate data, double ctlAtual) {
    MetricasDiarias metricasSemanaPassada = metricasDiariasRepository
            .findByAtletaIdAndData(atletaId, data.minusDays(7))
            .orElse(null);

    if (metricasSemanaPassada == null || metricasSemanaPassada.getCtl() == null) {
        return 0.0;
    }

    double ctlAnterior = metricasSemanaPassada.getCtl();
    double rampAbsoluto = ctlAtual - ctlAnterior;

    // Se CTL anterior e muito baixo, usar ramp absoluto
    // (evita percentuais absurdos tipo 200% quando CTL = 2)
    if (ctlAnterior < MetricasThresholds.CTL_MINIMO_RAMP_RELATIVO) {
        return rampAbsoluto;
    }

    // Retornar ramp rate relativo (% do CTL anterior)
    return (rampAbsoluto / ctlAnterior) * 100.0;
}
```

```java
// Em MetricasAlertaService.analisarMetricas():
boolean rampAlto = rampRateAtual != null
        && rampRateAtual > MetricasThresholds.RAMP_RATE_RELATIVO_CRITICO;
```

### Opcao B — Thresholds adaptativos por nivel de experiencia

Manter ramp rate absoluto mas com thresholds diferentes:

```java
// Em MetricasThresholds ou como metodo:
public static double getRampRateCritico(NivelExperiencia nivel) {
    return switch (nivel) {
        case INICIANTE -> 5.0;       // Iniciantes: alerta com menos pts
        case INTERMEDIARIO -> 7.0;
        case AVANCADO -> 10.0;       // Padrao atual
        case ELITE -> 14.0;          // Elite suporta mais
    };
}
```

Mais simples de implementar, mas menos preciso que o relativo.

### Opcao C (Hibrida) — Ambos os criterios

Emitir alerta se **qualquer** criterio for atingido:

```java
boolean rampAlto = (rampAbsoluto > RAMP_RATE_CRITICO)
        || (rampRelativo > RAMP_RATE_RELATIVO_CRITICO);
```

## Recomendacao

**Opcao A** e a mais alinhada com a literatura cientifica (ACWR de Gabbett). Requer alterar a semantica do campo `rampRate` em `MetricasDiarias` e `PlanoMetaDados` — considerar adicionar campo `rampRateRelativo` para manter retrocompatibilidade.

## Arquivos Afetados

| Arquivo | Alteracao |
|---|---|
| `enums/MetricasThresholds.java` | Adicionar thresholds relativos |
| `services/impl/TsbServiceImpl.java` | Alterar `calcularRampRate()` |
| `services/impl/MetricasAlertaService.java` | Usar thresholds relativos |
| `entity/MetricasDiarias.java` | Opcional: adicionar `rampRateRelativo` |
| `entity/PlanoMetaDados.java` | Opcional: adicionar `rampRateRelativo` |

## Verificacao

```bash
./mvnw compile && ./mvnw test
```

- Simular cenarios: iniciante CTL=15 com ramp +6 → deve alertar
- Simular cenarios: elite CTL=120 com ramp +10 → nao deve alertar
- Verificar migracao de dados (se rampRate muda de absoluto para relativo)
