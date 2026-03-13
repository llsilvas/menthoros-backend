# ISSUE-01: Bug — `calcularStatus()` rebaixa "FADIGA CRITICA" para "FADIGA ALTA"

**Severidade:** ALTA (Bug de calculo)
**Arquivo:** `services/impl/MetricasAlertaService.java`
**Linhas:** 94-106

---

## Descricao

O metodo `calcularStatus()` usa o boolean `sobrecarga` (TSB < -30) para retornar o status. Porem esse boolean engloba **duas faixas distintas**:

- `FADIGA_ALTA` (TSB entre -35 e -30)
- `FADIGA_EXCESSIVA` (TSB < -35)

Como o `if (sobrecarga)` na linha 100 vem **antes** da classificacao por `FaixaTsb`, qualquer TSB < -30 retorna "FADIGA ALTA", inclusive valores criticos como TSB = -40.

## Comportamento Atual (Incorreto)

```
TSB = -40 → sobrecarga = true → return "FADIGA ALTA"   // ERRADO: deveria ser "FADIGA CRITICA"
TSB = -32 → sobrecarga = true → return "FADIGA ALTA"   // Correto
TSB = -15 → sobrecarga = false → FaixaTsb.classificar() → "ACUMULANDO FADIGA"  // Correto
```

O fluxo que deveria funcionar (`FaixaTsb.classificar()` na linha 105) **nunca e atingido** para TSB < -30.

## Comportamento Esperado

```
TSB = -40 → "FADIGA CRITICA"
TSB = -32 → "FADIGA ALTA"
TSB = -15 → "ACUMULANDO FADIGA"
```

## Impacto

- Atleta em risco real de overtraining recebe alerta menos severo
- O PromptBuilder recebe status incorreto, gerando recomendacoes mais brandas
- A faixa FADIGA_EXCESSIVA do enum FaixaTsb nunca e refletida no status composto

## Plano de Correcao

### Opcao A (Recomendada) — Verificacao granular antes do fallback

```java
// Em calcularStatus(), ANTES do check de sobrecarga generico:
private String calcularStatus(Double tsbAtual, Double ctlAtual,
                              boolean rampAlto, boolean sobrecarga,
                              Integer diasConsecutivosTreino) {
    if (tsbAtual == null && ctlAtual == null) {
        return "COLETANDO DADOS";
    }

    // Prioridade 1: Alertas criticos combinados
    if (sobrecarga && rampAlto) {
        // Diferenciar nivel de fadiga no status combinado
        FaixaTsb faixa = FaixaTsb.classificar(tsbAtual);
        if (faixa != null && faixa.isFadigaCritica()) {
            return "FADIGA CRITICA + PROGRESSAO RAPIDA";
        }
        return "FADIGA ALTA + PROGRESSAO RAPIDA";
    }

    if (rampAlto) {
        return "PROGRESSAO MUITO RAPIDA";
    }

    if (diasConsecutivosTreino != null
            && diasConsecutivosTreino >= MetricasThresholds.DIAS_CONSECUTIVOS_CRITICO) {
        FaixaTsb faixa = FaixaTsb.classificar(tsbAtual);
        if (faixa != null && faixa.isFadigaCritica()) {
            return faixa.getStatus();
        }
        if (sobrecarga) {
            return "FADIGA ALTA";
        }
        return "MUITOS DIAS CONSECUTIVOS";
    }

    // CORRECAO: Remover o check generico de sobrecarga
    // e deixar FaixaTsb classificar corretamente
    FaixaTsb faixa = FaixaTsb.classificar(tsbAtual);
    return faixa != null ? faixa.getStatus() : "NORMAL";
}
```

### Opcao B — Adicionar check especifico para TSB critico

```java
// Manter sobrecarga mas adicionar check de critico antes:
if (tsbAtual != null && tsbAtual < MetricasThresholds.TSB_CRITICO) {
    return "FADIGA CRITICA";
}
if (sobrecarga) {
    return "FADIGA ALTA";
}
```

## Arquivos Afetados

| Arquivo | Alteracao |
|---|---|
| `services/impl/MetricasAlertaService.java` | Corrigir logica de `calcularStatus()` |

## Verificacao

```bash
./mvnw compile && ./mvnw test
```

- Validar que TSB = -40 retorna "FADIGA CRITICA"
- Validar que TSB = -32 retorna "FADIGA ALTA"
- Validar que combinacoes compostas (sobrecarga + rampAlto) diferenciam nivel
