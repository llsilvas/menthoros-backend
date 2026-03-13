# ISSUE-06: Inconsistencia — `diasConsecutivosTreino` pode estar defasado durante analise de alertas

**Severidade:** MEDIA (Inconsistencia de fluxo)
**Arquivo:** `services/impl/TsbServiceImpl.java`
**Linhas:** 208-224

---

## Descricao

No fluxo de `atualizarTsbDia()`, o metodo `atualizarMetaDados()` atualiza os campos de CTL/ATL/TSB/RampRate e depois executa a analise de alertas:

```java
private void atualizarMetaDados(UUID atletaId, MetricasDiarias metricas) {
    // ...
    metaDados.setCtlAtual(metricas.getCtl());       // ✅ atualizado
    metaDados.setAtlAtual(metricas.getAtl());       // ✅ atualizado
    metaDados.setTsbAtual(metricas.getTsb());       // ✅ atualizado
    metaDados.setRampRateAtual(metricas.getRampRate()); // ✅ atualizado

    // diasConsecutivosTreino       → ❌ NAO atualizado neste fluxo
    // semanasProgressaoContinua    → ❌ NAO atualizado neste fluxo

    metaDados.aplicarAnalise(metricasAlertaService.analisarMetricas(metaDados));
    // ↑ analise usa diasConsecutivosTreino ANTIGO
}
```

Os campos `diasConsecutivosTreino` e `semanasProgressaoContinua` sao atualizados em outro fluxo (provavelmente `MetricasAgregadasService` via `PlanoServiceImpl`).

## Cenario de Falha

```
Estado no banco: diasConsecutivosTreino = 5

1. Atleta registra treino do dia 6 consecutivo
2. atualizarTsbDia() e chamado
3. atualizarMetaDados() roda com diasConsecutivosTreino = 5 (valor antigo)
4. MetricasAlertaService verifica: 5 >= DIAS_CONSECUTIVOS_CRITICO(6)? NAO
5. Nenhum alerta CRITICO emitido
6. Mais tarde, outro fluxo atualiza diasConsecutivosTreino = 6
7. Alerta so sera emitido na proxima execucao de atualizarTsbDia()
```

O alerta critico de dias consecutivos chega **1 dia atrasado**.

## Impacto

- Atleta nao recebe alerta critico no dia correto
- O PromptBuilder pode gerar plano sem considerar necessidade de descanso
- Para `semanasProgressaoContinua`, o atraso pode ser de ate 1 semana

## Plano de Correcao

### Opcao A (Recomendada) — Atualizar contadores antes da analise

Calcular `diasConsecutivosTreino` inline durante o `atualizarMetaDados`:

```java
private void atualizarMetaDados(UUID atletaId, MetricasDiarias metricas) {
    PlanoMetaDados metaDados = planoMetaDadosRepository
            .findByAtletaId(atletaId)
            .orElseThrow(() -> new IllegalArgumentException(
                    "MetaDados nao encontrado para atleta: " + atletaId));

    metaDados.setCtlAtual(metricas.getCtl());
    metaDados.setAtlAtual(metricas.getAtl());
    metaDados.setTsbAtual(metricas.getTsb());
    metaDados.setRampRateAtual(metricas.getRampRate());
    metaDados.setDataUltimaAtualizacao(LocalDate.now());

    // CORRECAO: Atualizar dias consecutivos ANTES da analise
    atualizarDiasConsecutivos(metaDados, atletaId, metricas.getData());

    metaDados.aplicarAnalise(metricasAlertaService.analisarMetricas(metaDados));
    planoMetaDadosRepository.save(metaDados);
}

private void atualizarDiasConsecutivos(PlanoMetaDados metaDados, UUID atletaId, LocalDate data) {
    // Contar dias consecutivos de treino olhando para tras
    int diasConsecutivos = 0;
    LocalDate dia = data;

    while (true) {
        List<TreinoRealizado> treinos = treinoRealizadoRepository
                .findByAtletaIdAndDataTreino(atletaId, dia);
        if (treinos.isEmpty()) break;
        diasConsecutivos++;
        dia = dia.minusDays(1);
    }

    metaDados.setDiasConsecutivosTreino(diasConsecutivos);
}
```

### Opcao B — Garantir ordem de execucao nos chamadores

Garantir que `MetricasAgregadasService` atualiza os contadores **antes** de `atualizarTsbDia()` em todos os fluxos. Mais fragil pois depende de orquestracao externa.

### Opcao C — MetricasAlertaService consulta dados frescos

O service calcula `diasConsecutivosTreino` por conta propria em vez de usar o valor de PlanoMetaDados:

```java
public ResultadoAnalise analisarMetricas(PlanoMetaDados metaDados) {
    // Calcular dias consecutivos em tempo real
    int diasConsecutivos = calcularDiasConsecutivos(metaDados.getAtleta().getId());
    // ... usar diasConsecutivos em vez de metaDados.getDiasConsecutivosTreino()
}
```

Requer injetar repositorios no MetricasAlertaService (aumenta acoplamento).

## Recomendacao

**Opcao A** e a mais limpa: mantem a logica no TsbServiceImpl e garante dados frescos sem adicionar dependencias.

## Arquivos Afetados

| Arquivo | Alteracao |
|---|---|
| `services/impl/TsbServiceImpl.java` | Adicionar `atualizarDiasConsecutivos()` antes da analise |

## Verificacao

```bash
./mvnw compile && ./mvnw test
```

- Simular 6 dias consecutivos de treino e verificar que alerta CRITICO e emitido no dia 6 (nao no dia 7)
- Verificar que dias de descanso resetam o contador corretamente
