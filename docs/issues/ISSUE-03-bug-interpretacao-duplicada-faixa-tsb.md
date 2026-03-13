# ISSUE-03: Bug — `FaixaTsb.FADIGA_ALTA` tem interpretacao identica a `FADIGA_EXCESSIVA`

**Severidade:** MEDIA (Bug de exibicao)
**Arquivo:** `enums/FaixaTsb.java`
**Linhas:** 28-33

---

## Descricao

As faixas `FADIGA_EXCESSIVA` e `FADIGA_ALTA` compartilham a mesma `interpretacao`:

```java
FADIGA_EXCESSIVA(
    ..., "Fadiga excessiva", "FADIGA CRITICA", ...
),
FADIGA_ALTA(
    ..., "Fadiga excessiva", "FADIGA ALTA", ...   // ← mesmo texto!
),
```

O campo `interpretacao` e usado em `PlanoMetaDados.getInterpretacaoTsb()` e possivelmente no PromptBuilder para descrever o estado do atleta. Com textos identicos, o sistema nao consegue comunicar a diferenca entre:

- TSB = -40 (situacao critica, risco de overtraining)
- TSB = -32 (fadiga alta, mas gerenciavel)

## Comportamento Atual

```
TSB = -40 → getInterpretacaoTsb() → "Fadiga excessiva"
TSB = -32 → getInterpretacaoTsb() → "Fadiga excessiva"  // indistinguivel!
```

## Comportamento Esperado

```
TSB = -40 → "Fadiga excessiva"
TSB = -32 → "Fadiga alta"  (ou "Alta fadiga")
```

## Plano de Correcao

### Alteracao no FaixaTsb.java

```java
FADIGA_EXCESSIVA(
    Double.NEGATIVE_INFINITY, MetricasThresholds.TSB_CRITICO,
    NivelAlerta.CRITICO,
    "Fadiga excessiva",      // mantido
    "FADIGA CRITICA",
    "Dia de descanso completo OBRIGATORIO ou apenas atividade regenerativa leve (30min)."
),
FADIGA_ALTA(
    MetricasThresholds.TSB_CRITICO, MetricasThresholds.TSB_SOBRECARGA,
    NivelAlerta.ALTO,
    "Fadiga alta",           // CORRIGIDO: era "Fadiga excessiva"
    "FADIGA ALTA",
    "Reduzir volume em 30-40%. Priorizar treinos regenerativos e descanso."
),
```

## Arquivos Afetados

| Arquivo | Alteracao |
|---|---|
| `enums/FaixaTsb.java` | Alterar interpretacao de FADIGA_ALTA para "Fadiga alta" |

## Verificacao

```bash
./mvnw compile && ./mvnw test
```

- Validar que `FaixaTsb.FADIGA_ALTA.getInterpretacao()` retorna "Fadiga alta"
- Validar que `FaixaTsb.FADIGA_EXCESSIVA.getInterpretacao()` retorna "Fadiga excessiva"
- Verificar se o PromptBuilder usa `getInterpretacaoTsb()` e se o prompt gerado reflete a diferenca
