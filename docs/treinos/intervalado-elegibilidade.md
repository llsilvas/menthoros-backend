# Motor de Elegibilidade para Treino Intervalado

## Visão Geral

O `IntervaladoElegibilidadeService` implementa um motor determinístico de 5 portões que avalia
a prontidão fisiológica do atleta **antes** de qualquer chamada ao LLM. O resultado é injetado
como instrução mandatória no prompt pelo `PlanoTreinoPromptBuilder`, impedindo que o modelo
prescreva INTERVALADO quando os dados indicam fadiga, lesão ou base aeróbica insuficiente.

## Fluxo de Dados

```
PlanoTreinoPromptBuilder.buildOptimizedPrompt()
  │
  ├── TreinoHistoricoProvider.prepararContexto(atleta)  ← carregamento único de dados
  │
  ├── IntervaladoElegibilidadeService.avaliar(...)       ← 5 portões determinísticos
  │     └── RecomendacaoIntervalado (sealed)
  │           ├── Elegivel(categoria, motivo, instrucao)
  │           ├── Degradado(categoriaSegura, motivo, instrucao)
  │           └── Substituido(tipoFallback, motivo, instrucao)
  │
  ├── formatarDecisaoIntervalado(recomendacao)           ← seção ASCII no prompt
  │
  └── templateLoader.loadAndFormat(...)                  ← chamada ao Claude
```

## Os 5 Portões de Decisão

### Portão 1 — Contraindicações Absolutas

Bloqueiam completamente qualquer intensidade. Verificados primeiro, sem exceção.

| Condição | Resultado |
|---|---|
| `atleta.temLesao == true` | `Substituido(REGENERATIVO)` |
| `metaDados.tsbAtual < -30` | `Substituido(REGENERATIVO)` |
| `metaDados.alertaDiasConsecutivos == true` | `Substituido(CONTINUO)` |

### Portão 2 — Prontidão Fisiológica por Nível

Limiares de TSB calibrados por experiência do atleta:

| Nível | TSB Mínimo | Base científica |
|---|---|---|
| INICIANTE | -10.0 | Menor capacidade de tolerar fadiga acumulada |
| INTERMEDIARIO | -15.0 | Adaptação progressiva |
| AVANCADO | -20.0 | Maior resiliência ao estresse |
| ELITE | -25.0 | Alta tolerância fisiológica |

Se `tsbAtual < limiar[nivel]` → `Degradado(D)`

Verificação de RPE: se a **média dos últimos 7 dias** for ≥ 7.5 → `Degradado(C)`

### Portão 3 — Recuperação Desde o Último Intensivo

Janela mínima de horas entre treinos INTERVALADO ou TIRO:

| Nível | Horas Mínimas |
|---|---|
| INICIANTE | 72h |
| INTERMEDIARIO | 60h |
| AVANCADO | 48h |
| ELITE | 48h |

Cálculo: `ChronoUnit.HOURS.between(dataTreino.atStartOfDay(), dataReferencia.atStartOfDay())`
Usa `LocalDate.atStartOfDay()` → múltiplos de 24h.

Se `horasDesde < minHoras[nivel]` → `Degradado(D)`

### Portão 4 — Base Aeróbica Mínima (CTL)

CTL mínimo para suportar carga de intervalado com segurança:

| Nível | CTL Mínimo |
|---|---|
| INICIANTE | 15.0 |
| INTERMEDIARIO | 25.0 |
| AVANCADO | 40.0 |
| ELITE | 55.0 |

Se `ctlAtual < ctlMin[nivel]` → `Degradado(D)`

### Portão 5 — Seleção de Categoria (apenas para atletas elegíveis)

Mapeamento por `FasePeriodizacao`:

| Fase | Categorias Autorizadas | Lógica |
|---|---|---|
| `BASE` | A ou B | Alterna para construir motor aeróbico |
| `BUILD` | B ou C | Alterna para adicionar threshold |
| `ESPECIFICO` | C ou E | Alterna para pace de prova |
| `TAPER` | D fixo | Manutenção suave |
| `SEMANA_PROVA` | D fixo | Apenas estímulo leve |
| `POS_PROVA` | D fixo | Reintrodução gradual |
| `DESENVOLVIMENTO_GERAL` | A→B→C→D→E→A | Rotação completa por histórico |

A **detecção do histórico** é feita por pattern matching em `observacao` e `descricao`
dos últimos treinos de alta intensidade (palavras-chave: `400M`/`200M`→A, `3MIN`/`4MIN`→B,
`THRESHOLD`/`LIMIAR`→C, `FARTLEK`→E, default→D).

## Categorias A–E

| Cat | Nome | Descrição | Instrução padrão |
|---|---|---|---|
| **A** | VO2max curto | Tiros 200-600m em Z5 (95-100% FCmax), rec 1:3 | 6-10 tiros de 200-600m |
| **B** | VO2max longo | Repetições 3-5 min em Z5, rec 1:1 | 4-6 repetições de 3-5 min |
| **C** | Threshold | Blocos 4-6 min em Z4 (85-90% FCmax) | 3-5 blocos de 4-6 min |
| **D** | Fartlek suave | Contínuo Z3 ou fartlek leve | 20-30 min em Z2-Z3 |
| **E** | Fartlek de prova | Variações no pace de competição | 10-15 min no pace de prova |

## Tipo Selado: RecomendacaoIntervalado

```java
public sealed interface RecomendacaoIntervalado
        permits Elegivel, Degradado, Substituido {

    record Elegivel(CategoriaIntervalado categoria,
                    String motivo,
                    String instrucaoParaLlm) {}

    record Degradado(CategoriaIntervalado categoriaSegura,
                     String motivo,
                     String instrucaoParaLlm) {}

    record Substituido(TipoTreino tipoFallback,
                       String motivo,
                       String instrucaoParaLlm) {}
}
```

O sealed interface garante exaustividade no switch (Java 21) — nenhum caso pode ser
esquecido sem erro de compilação.

## Integração no Prompt

A seção gerada por `formatarDecisaoIntervalado()` é inserida entre `restricoesLesoes`
e `historicoCompleto`, com prioridade logo abaixo das restrições de saúde:

```
## DECISAO INTERVALADO - INSTRUCAO OBRIGATORIA

[AUTORIZADO/DEGRADADO/PROIBIDO] status...
Categoria: X — nome
Fundamento: motivo fisiológico
Instrucao: diretriz específica para o LLM

ATENCAO: Esta decisao e deterministica...
```

## Arquivos Relevantes

| Arquivo | Papel |
|---|---|
| `src/main/java/com/menthoros/enums/CategoriaIntervalado.java` | Enum A–E com descrições e instruções |
| `src/main/java/com/menthoros/services/helper/RecomendacaoIntervalado.java` | Sealed interface com 3 variantes |
| `src/main/java/com/menthoros/services/helper/IntervaladoElegibilidadeService.java` | Motor dos 5 portões |
| `src/main/java/com/menthoros/services/prompt/PlanoTreinoPromptBuilder.java` | Integração no pipeline de prompt |
| `src/test/java/com/menthoros/services/helper/IntervaladoElegibilidadeServiceTest.java` | 12 testes unitários |

## Como Executar os Testes

```bash
# Apenas os testes do motor de elegibilidade
./mvnw test -Dtest=IntervaladoElegibilidadeServiceTest

# Todos os testes do projeto
./mvnw test

# Build completo sem testes
./mvnw clean package -DskipTests
```

## Validação End-to-End

Após iniciar a aplicação, chamar `POST /api/plano/{atletaId}/gerar` e verificar:

1. **Log do motor:** linha `IntervaladoElegibilidade: elegivel para Categoria X | TSB=Y CTL=Z Fase=W`
   (nível `INFO`) ou `SUBSTITUIDO/DEGRADADO` (nível `WARN`)

2. **Prompt enviado ao Claude:** ativar log de debug do `IaServiceImpl` e procurar pela
   seção `## DECISAO INTERVALADO - INSTRUCAO OBRIGATORIA`

3. **Plano gerado:** verificar se o tipo de treino retornado respeita a decisão (não deve
   conter `INTERVALADO` quando marcado `[PROIBIDO]`)
