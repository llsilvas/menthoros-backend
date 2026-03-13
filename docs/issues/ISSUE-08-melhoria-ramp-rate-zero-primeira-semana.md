# ISSUE-08: Melhoria — Ramp Rate retorna zero na primeira semana

**Severidade:** BAIXA (Melhoria de cobertura)
**Arquivo:** `services/impl/TsbServiceImpl.java`
**Linhas:** 193-203

---

## Descricao

O calculo de Ramp Rate busca metricas de **exatamente** 7 dias atras:

```java
private double calcularRampRate(UUID atletaId, LocalDate data, double ctlAtual) {
    MetricasDiarias metricasSemanaPassada = metricasDiariasRepository
            .findByAtletaIdAndData(atletaId, data.minusDays(7))
            .orElse(null);

    if (metricasSemanaPassada == null) {
        return 0.0;    // ← retorna zero se nao tem dado de 7 dias atras
    }

    return ctlAtual - metricasSemanaPassada.getCtl();
}
```

### Problema

1. **Atletas novos**: A primeira semana inteira tera ramp rate = 0, justamente quando iniciantes sao mais vulneraveis a progressao excessiva
2. **Gaps de dados**: Se o atleta viajou e nao registrou treinos, o dia exato de 7 dias atras pode nao existir, mesmo tendo dados de 6 ou 8 dias atras

### Cenario

```
Dia 1: CTL = 0   (primeiro treino do atleta)
Dia 2: CTL = 2.3
Dia 3: CTL = 5.1
Dia 4: CTL = 9.2
Dia 5: CTL = 14.8
Dia 6: CTL = 18.5
Dia 7: CTL = 21.0  ← ramp rate = 21 - busca(dia 0) = 0 (nao existe dia 0!)

Ramp rate real: +21 pts em 7 dias — EXTREMAMENTE alto para iniciante
Ramp rate reportado: 0.0 — nenhum alerta emitido
```

## Plano de Correcao

### Opcao A (Recomendada) — Buscar registro mais proximo disponivel

```java
private double calcularRampRate(UUID atletaId, LocalDate data, double ctlAtual) {
    // Tentar exatamente 7 dias atras (caso ideal)
    MetricasDiarias metricasSemanaPassada = metricasDiariasRepository
            .findByAtletaIdAndData(atletaId, data.minusDays(7))
            .orElse(null);

    if (metricasSemanaPassada != null) {
        return ctlAtual - metricasSemanaPassada.getCtl();
    }

    // Fallback: buscar o registro mais recente dentro de uma janela de 5-9 dias
    // e interpolar para 7 dias
    Optional<MetricasDiarias> maisProximo = metricasDiariasRepository
            .findTopByAtletaIdAndDataBetweenOrderByDataDesc(
                    atletaId,
                    data.minusDays(9),
                    data.minusDays(5));

    if (maisProximo.isPresent()) {
        MetricasDiarias ref = maisProximo.get();
        long diasEntre = java.time.temporal.ChronoUnit.DAYS.between(ref.getData(), data);
        double deltaCTL = ctlAtual - ref.getCtl();
        // Interpolar para taxa de 7 dias
        return (deltaCTL / diasEntre) * 7.0;
    }

    // Nenhuma referencia disponivel — pode ser a primeira semana
    // Usar o primeiro registro disponivel para estimar tendencia
    Optional<MetricasDiarias> primeiroRegistro = metricasDiariasRepository
            .findTopByAtletaIdAndDataBeforeOrderByDataDesc(atletaId, data);

    if (primeiroRegistro.isPresent()) {
        MetricasDiarias primeiro = primeiroRegistro.get();
        long diasDesdeInicio = java.time.temporal.ChronoUnit.DAYS.between(primeiro.getData(), data);
        if (diasDesdeInicio > 0 && diasDesdeInicio <= 14) {
            double deltaCTL = ctlAtual - primeiro.getCtl();
            return (deltaCTL / diasDesdeInicio) * 7.0;
        }
    }

    return 0.0;
}
```

### Opcao B (Simples) — Alertar se CTL crescer rapido desde o inicio

Menos preciso, mas mais simples:

```java
if (metricasSemanaPassada == null) {
    // Para atletas novos, verificar se CTL cresceu demais desde o primeiro dia
    if (ctlAtual > MetricasThresholds.RAMP_RATE_CRITICO) {
        // CTL acima de 10 sem ter 7 dias de historico = crescimento rapido
        log.warn("Atleta {} sem historico de 7 dias mas CTL ja em {}",
                atletaId, ctlAtual);
        return ctlAtual; // Retorna CTL como proxy de ramp
    }
    return 0.0;
}
```

## Nota sobre Repositorio

A Opcao A requer um metodo de query adicional no `MetricasDiariasRepository`:

```java
Optional<MetricasDiarias> findTopByAtletaIdAndDataBetweenOrderByDataDesc(
        UUID atletaId, LocalDate dataInicio, LocalDate dataFim);
```

## Arquivos Afetados

| Arquivo | Alteracao |
|---|---|
| `services/impl/TsbServiceImpl.java` | Melhorar `calcularRampRate()` com fallback |
| `repository/MetricasDiariasRepository.java` | Adicionar query (Opcao A) |

## Verificacao

```bash
./mvnw compile && ./mvnw test
```

- Simular atleta novo: 5 dias de treino forte → ramp rate > 0
- Simular gap de dados: treino dia 1, gap dia 2-6, treino dia 7 → ramp rate calculado
- Validar que calculo exato (7 dias) continua funcionando igual
