# ISSUE-07: Melhoria — Elevacao de descida ignorada no calculo de TSS

**Severidade:** BAIXA (Melhoria de precisao)
**Arquivo:** `services/helper/TssCalculatorService.java`
**Linhas:** 200-237

---

## Descricao

O `calcularFatorElevacao()` considera apenas `elevacaoGanhoMetros` (subida). O modelo `TreinoBase` possui o campo `elevacaoPerdaMetros` com documentacao explicita sobre seu impacto:

```java
/**
 * Elevation lost (descent) in meters
 * Important for calculating muscle fatigue on long descents
 * (eccentric contraction causes higher DOMS)
 */
protected Integer elevacaoPerdaMetros;
```

### Fundamento Fisiologico

Na corrida de rua, descidas longas causam:

1. **Contracao excentrica** do quadriceps — principal causa de DOMS (Dor Muscular de Inicio Tardio)
2. **Microlesoes musculares** — pico de CK (creatina quinase) 24-72h apos corridas em descida
3. **Fadiga neuromuscular** — reducao de forca nos dias seguintes
4. **Impacto articular** — forca de impacto 2-3x maior em descida vs plano (Gottschall & Kram, 2005)

Provas com perfil de descida liquida (ex: Maratona de Boston, -140m net downhill) sao reconhecidamente mais "destruidoras" muscularmente do que provas planas, apesar do pace mais rapido.

### Cenario Ignorado

```
Prova: 21km com 50m D+ e 350m D- (net downhill de 300m)
Atual: fator = 1.0 + (50/21 * 0.005) = 1.012 (quase plano)
Real: O impacto muscular e MAIOR que uma prova plana
```

## Plano de Correcao

### Fator de Descida Proposto

A contribuicao da descida para a fadiga muscular e diferente da subida (que e mais cardiovascular):

```java
private double calcularFatorElevacao(TreinoRealizado treino) {
    Integer elevacaoGanho = treino.getElevacaoGanhoMetros();
    Integer elevacaoPerda = treino.getElevacaoPerdaMetros();
    BigDecimal distanciaKm = treino.getDistanciaKm();

    if (distanciaKm == null || distanciaKm.doubleValue() <= 0) {
        return 1.0;
    }

    double distancia = distanciaKm.doubleValue();
    double fator = 1.0;

    // Fator de SUBIDA (custo cardiovascular + muscular concentrico)
    if (elevacaoGanho != null && elevacaoGanho > 0) {
        double gradienteSubida = elevacaoGanho / distancia; // m/km
        fator += calcularComponenteSubida(gradienteSubida);
    }

    // Fator de DESCIDA (custo muscular excentrico + impacto articular)
    if (elevacaoPerda != null && elevacaoPerda > 0) {
        double gradienteDescida = elevacaoPerda / distancia; // m/km
        fator += calcularComponenteDescida(gradienteDescida);
    }

    return Math.min(fator, 2.0);
}

/**
 * Componente de subida (formula existente)
 */
private double calcularComponenteSubida(double gradienteMedio) {
    if (gradienteMedio < 20) {
        return gradienteMedio * 0.005;
    } else if (gradienteMedio < 50) {
        return (20 * 0.005) + ((gradienteMedio - 20) * 0.01);
    } else {
        return (20 * 0.005) + (30 * 0.01) + ((gradienteMedio - 50) * 0.015);
    }
}

/**
 * Componente de descida (fator excentrico)
 *
 * Impacto menor que subida em custo energetico,
 * mas significativo em fadiga muscular.
 * Fator = ~60% do fator de subida equivalente.
 *
 * Ref: Vernillo et al. (2017) - Sports Medicine
 * "Biomechanics and Physiology of Uphill and Downhill Running"
 */
private double calcularComponenteDescida(double gradienteMedio) {
    double fatorSubidaEquivalente;
    if (gradienteMedio < 20) {
        fatorSubidaEquivalente = gradienteMedio * 0.005;
    } else if (gradienteMedio < 50) {
        fatorSubidaEquivalente = (20 * 0.005) + ((gradienteMedio - 20) * 0.01);
    } else {
        fatorSubidaEquivalente = (20 * 0.005) + (30 * 0.01) + ((gradienteMedio - 50) * 0.015);
    }
    // Descida = ~60% do impacto da subida (mais muscular, menos cardiovascular)
    return fatorSubidaEquivalente * 0.6;
}
```

### Exemplo com Correcao

```
Prova: 21km com 50m D+ e 350m D-

Subida:  gradiente = 50/21 = 2.4 m/km → componente = 2.4 * 0.005 = 0.012
Descida: gradiente = 350/21 = 16.7 m/km → componente = 16.7 * 0.005 * 0.6 = 0.050

Fator total = 1.0 + 0.012 + 0.050 = 1.062

Antes:  fator = 1.012 (quase plano)
Depois: fator = 1.062 (+5% de impacto muscular da descida)
```

## Arquivos Afetados

| Arquivo | Alteracao |
|---|---|
| `services/helper/TssCalculatorService.java` | Refatorar `calcularFatorElevacao()` para incluir descida |

## Verificacao

```bash
./mvnw compile && ./mvnw test
```

- Validar que treinos sem elevacao continuam com fator 1.0
- Validar que treinos com D+ retornam valores proximos ao atual
- Validar que treinos com D- significativo (net downhill) recebem fator > 1.0
- Validar limite maximo de 2.0
