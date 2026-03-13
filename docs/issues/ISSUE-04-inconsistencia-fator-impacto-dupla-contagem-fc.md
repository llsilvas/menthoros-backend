# ISSUE-04: Inconsistencia — Fator de impacto por tipo de treino causa dupla contagem com FC

**Severidade:** MEDIA (Inconsistencia de calculo)
**Arquivo:** `services/helper/TssCalculatorService.java`
**Linhas:** 40-58, 74-88

---

## Descricao

O `aplicarFatorImpactoTreino()` e aplicado **igualmente** ao TSS base, independente do metodo de calculo (FC, Pace ou RPE). O problema e que cada metodo ja captura a intensidade de forma diferente:

| Metodo | O que a metrica ja captura | Problema com fator adicional |
|---|---|---|
| **FC** | A FC media ja reflete a intensidade total (intervalos, subidas, etc.) | Dupla contagem: FC alta + fator alto = inflacao |
| **Pace** | O pace medio inclui trechos de recuperacao, subestimando esforco real | Fator compensa bem a subestimativa |
| **RPE** | Percepcao subjetiva ja inclui tipo de treino | Fator adiciona correcao razoavel |

## Exemplo Concreto — 1h de Intervalado

### Metodo FC (dupla contagem)
```
FC media = 170 bpm (inclui os intervalos fortes)
Atleta: fcMax=195, fcRepouso=50, fcLimiar=175
HR Reserve = 145, Working HR = 120
hrReservePercent = 120/145 = 0.828
thresholdPercent = (175-50)/145 = 0.862
IF = 0.828/0.862 = 0.961

TSS base = 1.0 x 0.961 x 100 x 0.961 = 92.4
TSS ajustado = 92.4 x 1.4 (INTERVALADO) = 129.3   ← inflado ~30%
TSS esperado = ~90-110 para 1h de intervalado
```

### Metodo Pace (compensacao correta)
```
Pace medio = 5:30/km (inclui trote de recuperacao)
Atleta: paceLimiar = 4:30/km
IF = 4.5/5.5 = 0.818

TSS base = 1.0 x 0.818 x 100 x 0.818 = 66.9
TSS ajustado = 66.9 x 1.4 (INTERVALADO) = 93.7    ← valor razoavel
```

O fator 1.4 compensa bem o pace medio "diluido", mas inflaciona o TSS por FC.

## Impacto

- Atletas com monitor cardiaco recebem TSS ~25-40% mais alto que o real para treinos de alta intensidade
- CTL cresce mais rapido que o real para esses atletas
- TSB fica mais negativo, podendo gerar alertas falsos de sobrecarga
- Discrepancia entre TSS calculado por FC vs Pace para o mesmo treino

## Plano de Correcao

### Opcao A (Recomendada) — Fator atenuado para calculo por FC

Aplicar o fator de impacto com peso reduzido quando o TSS e calculado por FC, ja que a FC captura boa parte do stress:

```java
public int calcularTss(TreinoRealizado treino) {
    int tssBase;
    MetodoCalculoTss metodo;

    if (treino.getFcMedia() != null && treino.getFcMedia() > 0) {
        tssBase = calcularTssFrequenciaCardiaca(treino);
        metodo = MetodoCalculoTss.FC;
    } else if (treino.getPaceMedia() != null) {
        tssBase = calcularTssPace(treino);
        metodo = MetodoCalculoTss.PACE;
    } else {
        tssBase = calcularTssRpe(treino);
        metodo = MetodoCalculoTss.RPE;
    }

    return aplicarFatorImpactoTreino(tssBase, treino, metodo);
}

private int aplicarFatorImpactoTreino(int tssBase, TreinoRealizado treino, MetodoCalculoTss metodo) {
    if (treino.getTipoTreino() == null) {
        return tssBase;
    }

    double fator = treino.getTipoTreino().getFatorImpacto();

    // Para FC, atenuar o fator pois a FC ja captura boa parte da intensidade
    // Aplica apenas o componente "extra" (neuromuscular, metabolico)
    if (metodo == MetodoCalculoTss.FC) {
        // Converter fator de multiplicativo para aditivo atenuado
        // Ex: fator 1.4 → componente extra = 0.4 → atenuado 50% = 0.2 → fator final = 1.2
        double componenteExtra = fator - 1.0;
        fator = 1.0 + (componenteExtra * 0.5);
    }

    int tssAjustado = (int) Math.round(tssBase * fator);
    return tssAjustado;
}
```

### Opcao B — Nao aplicar fator quando calculo por FC

Mais simples, mas perde a correcao neuromuscular:

```java
if (metodo == MetodoCalculoTss.FC) {
    return tssBase; // FC ja e precisa o suficiente
}
return (int) Math.round(tssBase * fator);
```

### Opcao C — Fator especifico por metodo no enum TipoTreino

Adicionar campos separados no enum:

```java
INTERVALADO("INTERVALADO", ..., 1.4, 1.15, 1.4)
//                                FC   PACE   RPE
//                             fatorFc fatorPace fatorRpe
```

Mais preciso, mas mais complexo de manter.

## Recomendacao

**Opcao A** oferece o melhor equilibrio: mantem a correcao para fatores neuromusculares/metabolicos que a FC nao captura, mas evita a inflacao excessiva.

## Arquivos Afetados

| Arquivo | Alteracao |
|---|---|
| `services/helper/TssCalculatorService.java` | Ajustar `aplicarFatorImpactoTreino()` para considerar metodo |

## Verificacao

```bash
./mvnw compile && ./mvnw test
```

- Comparar TSS antes/depois para treinos de intervalado com dados de FC
- Validar que TSS por pace permanece inalterado
- Verificar coerencia: TSS(FC) e TSS(Pace) para mesmo treino devem ser proximos (+/- 15%)
