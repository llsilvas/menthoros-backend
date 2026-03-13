# ISSUE-10: Melhoria — TSB thresholds nao consideram nivel do atleta

**Severidade:** BAIXA (Melhoria de precisao)
**Arquivo:** `enums/FaixaTsb.java`, `enums/MetricasThresholds.java`

---

## Descricao

As faixas de TSB usam thresholds absolutos identicos para todos os atletas:

```java
TSB_CRITICO = -35.0
TSB_SOBRECARGA = -30.0
TSB_FADIGA_MODERADA = -20.0
// ... etc
```

Porem o sistema ja personaliza as constantes de tempo CTL/ATL por nivel de experiencia:

```java
// INICIANTE:  CTL=30 dias, ATL=5 dias
// AVANCADO:   CTL=42 dias, ATL=7 dias
// ELITE:      CTL=50 dias, ATL=8 dias
```

Essa personalizacao muda a **dinamica** do TSB (como ele sobe e desce), mas os thresholds de **interpretacao** permanecem fixos. Isso cria inconsistencia:

### Cenario de Inconsistencia

| Atleta | CTL | ATL | TSB | Classificacao atual | Realidade fisiologica |
|---|:---:|:---:|:---:|---|---|
| Elite (CTL=100) | 100 | 125 | -25 | FADIGA_MODERADA | Normal em bloco de carga |
| Iniciante (CTL=20) | 20 | 45 | -25 | FADIGA_MODERADA | Risco real de overtraining |

O mesmo TSB = -25 significa coisas muito diferentes para cada nivel.

### Fundamento

- Atletas elite tem maior tolerancia a fadiga (Meeusen et al., 2013 - EJSS)
- Adaptacoes neuromusculares e metabolicas permitem treinar em TSB mais negativo
- Iniciantes tem menor "reserva funcional" e risco maior de NOF/OTS (Non-Functional Overreaching / Overtraining Syndrome)

## Plano de Correcao

### Opcao A (Recomendada) — Fator de ajuste por nivel

Manter os thresholds base mas aplicar um fator de escala conforme o nivel:

```java
// Em MetricasThresholds.java:
/**
 * Retorna fator de escala para thresholds de TSB por nivel de experiencia.
 *
 * Iniciantes: thresholds mais conservadores (1.3x → TSB_CRITICO = -27 em vez de -35)
 * Elite: thresholds mais tolerantes (0.75x → TSB_CRITICO = -47 em vez de -35)
 */
public static double getFatorThresholdTsb(NivelExperiencia nivel) {
    return switch (nivel) {
        case INICIANTE -> 1.3;      // Thresholds ~30% mais restritivos
        case INTERMEDIARIO -> 1.1;  // Levemente mais restritivo
        case AVANCADO -> 1.0;       // Padrao (valores atuais)
        case ELITE -> 0.75;         // ~25% mais tolerante
    };
}
```

### Thresholds Resultantes

| Threshold (base) | Iniciante (1.3x) | Intermediario (1.1x) | Avancado (1.0x) | Elite (0.75x) |
|---|:---:|:---:|:---:|:---:|
| TSB_CRITICO (-35) | **-27** | -32 | -35 | **-47** |
| TSB_SOBRECARGA (-30) | **-23** | -27 | -30 | **-40** |
| TSB_FADIGA_MODERADA (-20) | **-15** | -18 | -20 | **-27** |
| TSB_ACUMULANDO_FADIGA (-10) | **-8** | -9 | -10 | **-13** |

Com este ajuste:
- Iniciante a TSB = -25 → abaixo de -23 (TSB_SOBRECARGA ajustado) → **FADIGA_ALTA** (correto!)
- Elite a TSB = -25 → acima de -27 (TSB_FADIGA_MODERADA ajustado) → **ACUMULANDO_FADIGA** (razoavel para bloco de carga)

### Alteracao no FaixaTsb

O `classificar()` precisa receber o nivel do atleta:

```java
/**
 * Classifica TSB considerando nivel de experiencia do atleta.
 */
public static FaixaTsb classificar(Double tsb, NivelExperiencia nivel) {
    if (tsb == null) return null;

    double fator = MetricasThresholds.getFatorThresholdTsb(nivel);

    // Ajustar TSB para os thresholds padrao (equivale a escalar os thresholds)
    // Se iniciante (fator 1.3): TSB -25 → -25/1.3 = -19.2 → compara com thresholds padrao
    // Alternativa: escalar cada threshold (mais claro mas mais codigo)
    double tsbAjustado = tsb / fator;

    return Arrays.stream(values())
            .filter(f -> tsbAjustado > f.min && tsbAjustado <= f.max)
            .findFirst()
            .orElse(FORMA_IDEAL);
}

// Manter overload sem nivel para retrocompatibilidade
public static FaixaTsb classificar(Double tsb) {
    return classificar(tsb, NivelExperiencia.AVANCADO); // padrao = valores atuais
}
```

### Impacto nos Chamadores

Todos os chamadores de `FaixaTsb.classificar()` precisam passar o `NivelExperiencia`:

1. `PlanoMetaDados.getInterpretacaoTsb()` → precisa acessar `atleta.getNivelExperiencia()`
2. `PlanoMetaDados.estaEmFormaIdeal()` → idem
3. `PlanoMetaDados.estaMuitoFatigado()` → idem
4. `MetricasAlertaService.analisarMetricas()` → precisa receber ou consultar nivel
5. `MetricasAlertaService.calcularStatus()` → idem

## Complexidade e Riscos

Esta e a issue mais complexa e com maior superficie de impacto:

- Muda a semantica do `FaixaTsb.classificar()` (breaking change interna)
- Requer propagar `NivelExperiencia` por varios chamadores
- Atletas existentes podem mudar de faixa repentinamente
- O PromptBuilder pode gerar recomendacoes diferentes para o mesmo TSB

### Mitigacao

- Implementar apos as issues 01-06 (menores e mais urgentes)
- Adicionar logs de comparacao: `log.info("TSB={} Nivel={} Faixa={} (sem ajuste seria {})")`
- Considerar migracao gradual: manter ambos os metodos e comparar em producao

## Arquivos Afetados

| Arquivo | Alteracao |
|---|---|
| `enums/MetricasThresholds.java` | Adicionar `getFatorThresholdTsb()` |
| `enums/FaixaTsb.java` | Overload de `classificar()` com `NivelExperiencia` |
| `entity/PlanoMetaDados.java` | Passar nivel nos metodos `@Transient` |
| `services/impl/MetricasAlertaService.java` | Usar `classificar(tsb, nivel)` |

## Verificacao

```bash
./mvnw compile && ./mvnw test
```

- Validar que `classificar(tsb)` sem nivel retorna valores identicos aos atuais (retrocompativel)
- Validar que iniciante a TSB=-25 recebe classificacao mais severa que avancado
- Validar que elite a TSB=-25 recebe classificacao menos severa que avancado
- Verificar integracao com PromptBuilder: recomendacoes coerentes com nivel
