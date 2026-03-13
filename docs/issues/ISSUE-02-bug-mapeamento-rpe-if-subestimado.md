# ISSUE-02: Bug — Mapeamento RPE para IF subestima treinos intensos em ~30-40%

**Severidade:** ALTA (Bug de calculo)
**Arquivo:** `services/helper/TssCalculatorService.java`
**Linhas:** 243-261

---

## Descricao

A formula de conversao RPE → IF (Intensity Factor) produz valores sistematicamente baixos para esforcos de alta intensidade, resultando em TSS subestimado quando o calculo e feito por RPE (fallback).

### Formula Atual

```java
double intensityFactor = (rpe / 10.0) * 0.9 + 0.1;
```

### Valores Produzidos vs. Esperados

| RPE (1-10) | Descricao | IF calculado | IF esperado (fisiologia) | Erro |
|:---:|---|:---:|:---:|:---:|
| 3 | Leve | 0.37 | ~0.55-0.60 | -35% |
| 5 | Moderado | 0.55 | ~0.70-0.75 | -25% |
| 7 | Forte | 0.73 | ~0.90-0.95 | -20% |
| **8** | **Limiar** | **0.82** | **~1.00** | **-18%** |
| 9 | Muito forte | 0.91 | ~1.10-1.15 | -18% |
| 10 | Maximo | 1.00 | ~1.20-1.25 | -18% |

### Fundamento Fisiologico

Na escala CR-10 de Borg, RPE 8 corresponde ao esforco de **limiar anaerobico**, que por definicao e IF = 1.0 (TSS = 100 por hora). A formula atual produz apenas 67 TSS/hora para esforco de limiar.

### Impacto em TSS/hora

```
RPE 8, 1 hora:
  Atual:   0.82^2 x 100 = 67 TSS   (subestimado)
  Correto: 1.00^2 x 100 = 100 TSS  (referencia de limiar)
```

Alem disso, o comentario na linha 255 diz _"RPE 6 = IF 0.6"_ mas a formula produz IF = **0.64** para RPE 6.

## Impacto

- TSS subestimado para atletas sem monitor cardiaco ou GPS (registro manual)
- CTL cresce mais devagar que o real
- TSB fica artificialmente positivo, mascarando fadiga acumulada
- Atletas que dependem de RPE podem nao receber alertas de sobrecarga

## Plano de Correcao

### Formula Proposta — Mapeamento nao-linear baseado em fisiologia

```java
/**
 * Converte RPE (1-10) para IF usando mapeamento fisiologico.
 *
 * Referencia:
 *   RPE 3-4 = zona aerobica facil (IF 0.55-0.65)
 *   RPE 5-6 = zona aerobica moderada (IF 0.70-0.80)
 *   RPE 7   = zona de tempo/sublimiar (IF 0.88-0.93)
 *   RPE 8   = limiar anaerobico (IF ~1.00 por definicao)
 *   RPE 9   = VO2max (IF 1.10-1.15)
 *   RPE 10  = maximo/sprint (IF 1.20-1.30)
 */
private double converterRpeParaIf(double rpe) {
    if (rpe <= 1) return 0.45;
    if (rpe <= 3) return 0.45 + (rpe - 1) * 0.075;   // 1→0.45, 3→0.60
    if (rpe <= 6) return 0.60 + (rpe - 3) * 0.067;    // 3→0.60, 6→0.80
    if (rpe <= 8) return 0.80 + (rpe - 6) * 0.10;     // 6→0.80, 8→1.00
    return 1.00 + (rpe - 8) * 0.125;                   // 8→1.00, 10→1.25
}
```

### Tabela de Comparacao

| RPE | IF Atual | IF Proposto | TSS/h Atual | TSS/h Proposto |
|:---:|:---:|:---:|:---:|:---:|
| 3 | 0.37 | 0.60 | 14 | 36 |
| 5 | 0.55 | 0.73 | 30 | 54 |
| 7 | 0.73 | 0.93 | 53 | 86 |
| 8 | 0.82 | 1.00 | 67 | **100** |
| 10 | 1.00 | 1.25 | 100 | 156 |

### Alteracao no calcularTssRpe

```java
private int calcularTssRpe(TreinoRealizado treino) {
    if (treino.getPercepcaoEsforco() == null) {
        log.warn("Treino {} sem dados para calcular TSS", treino.getId());
        return 0;
    }

    double duracaoHoras = treino.getDuracaoMin() != null
        ? treino.getDuracaoMin().toMinutes() / 60.0
        : 0.0;
    double rpe = treino.getPercepcaoEsforco();

    double intensityFactor = converterRpeParaIf(rpe);

    // Limitar IF entre 0.5 e 1.5 (consistente com outros metodos)
    intensityFactor = Math.max(0.5, Math.min(1.5, intensityFactor));

    double tss = duracaoHoras * intensityFactor * 100 * intensityFactor;

    return (int) Math.round(tss);
}
```

## Arquivos Afetados

| Arquivo | Alteracao |
|---|---|
| `services/helper/TssCalculatorService.java` | Substituir formula RPE→IF + corrigir comentario |

## Verificacao

```bash
./mvnw compile && ./mvnw test
```

- Validar que RPE 8 + 1h = ~100 TSS (limiar)
- Validar que RPE 5 + 1h = ~50-55 TSS (moderado)
- Comparar TSS por RPE vs TSS por FC para mesmos treinos no banco (se disponivel)
- **ATENCAO**: Se houver historico, considerar `recalcularHistoricoCompleto()` para atletas que dependem de RPE
