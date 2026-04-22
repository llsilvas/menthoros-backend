## Context

O sistema hoje possui três fragmentos de lógica de progressão espalhados:

1. **`atualizarProgressao`** em `PlanoServiceImpl`: incrementa/reseta `semanasProgressaoContinua` comparando volume novo contra média histórica — sem olhar execução real.
2. **`calcularProgressaoSegura`** em `PeriodizacaoPromptFormatter`: limita progressão de CTL com base em TSB e rampRate — bom guardrail fisiológico, mas cego ao comportamento real do atleta.
3. **`recalcularSemanasProgressao`** em `TsbServiceImpl`: calcula streak de aumento de volume usando `MetricasDiarias` reais — mais honesto, mas mede apenas tendência de volume, sem qualidade ou aderência.

O histórico disponível para o fluxo de geração de plano está limitado a 7 treinos (`LIMITE_TREINOS_HISTORICO = 7`), insuficiente para janelas de 21 e 42 dias. O repositório `TreinoRealizadoRepository` já possui `findByAtletaAndDataTreinoGreaterThanEqualOrderByDataTreinoDesc`, pronto para uso.

A IA hoje recebe limites de TSS via `PeriodizacaoPromptFormatter`, mas não recebe um estado explícito de progressão nem sabe se o atleta está respondendo bem ao treino.

## Goals / Non-Goals

**Goals:**
- Criar `ProgressaoTreinoService` que consolida janelas de 7, 21 e 42 dias e produz `DecisaoProgressao`.
- Definir `EstadoProgressao` (`PROGREDIR`, `PROGREDIR_LEVE`, `MANTER`, `REDUZIR`) com regras claras de aderência, longão, RPE e TSB.
- Integrar a decisão no fluxo de geração: calcular antes da IA e passar para o prompt via `PeriodizacaoPromptFormatter`.
- Manter `calcularProgressaoSegura` como teto fisiológico (não substituir, incorporar).
- Ampliar histórico buscado no fluxo de geração para 42 dias.

**Non-Goals:**
- Expor endpoint REST de progressão (é decisão interna do fluxo de geração).
- Substituir o TSB/ATL/CTL como métricas base — continuam como limitadores.
- Calcular RPE onde não existe dado (se `rpeMedio` for nulo, tratar como ausente, não bloquear).
- Alterar schema do banco ou entidades JPA existentes.

## Decisions

### D1: Novo serviço `ProgressaoTreinoService` isolado (não embutir em `PlanoServiceImpl`)

`PlanoServiceImpl` já é longo. A lógica de análise de janelas (7/21/42d) é testável isoladamente e tem responsabilidade clara.

**Alternativa descartada:** adicionar os métodos em `TsbServiceImpl`. Progressão de treino não é domínio de TSB — misturar aumentaria o acoplamento.

### D2: `DecisaoProgressao` como Java record

O DTO é imutável e transportado entre serviços. Record é a escolha idiomática em Java 21.

```java
public record DecisaoProgressao(
    EstadoProgressao estado,
    double ajusteVolumePercentual,   // ex: +0.06 para +6%
    int ajusteLongoMinutos,          // ex: +10, 0, -15
    boolean permitirProgressaoIntensidade,
    String motivo
) {}
```

### D3: `ProgressaoHistoricoResumo` como record intermediário (opcional de persistir)

Calculado a cada geração de plano; não precisa ser armazenado. Serve de insumo para `DecisaoProgressao` e pode ser logado para debug.

### D4: Regras de decisão baseadas em thresholds fixos com fallback gracioso

Sem dados suficientes (menos de 3 treinos nos últimos 21 dias), o sistema retorna `MANTER` por segurança. Não bloqueia nem lança exceção.

**Alternativa descartada:** delegar a decisão para a IA. A IA deve receber o envelope já calculado — não ser responsável por criá-lo.

### D5: `PeriodizacaoPromptFormatter` recebe `DecisaoProgressao` como parâmetro adicional

O formatter já monta a seção de periodização do prompt. Adicionar um bloco explícito de progressão (estado, limites, motivo) é extensão natural.

**Alternativa descartada:** criar um novo formatter exclusivo. O contexto de periodização e progressão são interdependentes — separar criaria duplicação de campos TSB/CTL no prompt.

### D6: `DadosPlanoDto` ampliado com janela de 42 dias (lista ou resumo por período)

`PlanoServiceImpl.prepararDadosPlano` passará a buscar treinos realizados dos últimos 42 dias via repositório existente. O campo `ultimosTreinos` no DTO passa a refletir esse intervalo maior, mantendo a mesma estrutura de lista.

A constante `LIMITE_TREINOS_HISTORICO` é substituída pela data de corte `LocalDate.now().minusDays(42)`.

## Risks / Trade-offs

**[Atleta novo com histórico insuficiente]** → `ProgressaoTreinoService` retorna `MANTER` com motivo explícito ("histórico insuficiente"). O prompt da IA indica isso.

**[RPE ausente em muitos treinos]** → Quando `rpeMedio` for nulo/zero, o cálculo de RPE médio dos treinos duros é ignorado e a decisão se apoia em aderência + longões + TSB. Não bloqueia.

**[Busca de 42 dias aumenta volume de dados]** → O repositório já tem índice em `atleta_id + data_treino`. A query é leve para volumes típicos de um atleta (<500 treinos em 42 dias). Sem impacto de performance esperado.

**[Regressão em planos existentes]** → A integração é aditiva: o prompt passa a ter mais contexto, mas os formatters de base (TSS, zona, disponibilidade) não mudam. Em caso de problema, pode-se remover o bloco de `DecisaoProgressao` do prompt sem alterar lógica de negócio.

## Migration Plan

1. Implementar `ProgressaoTreinoService` + DTOs + enum com testes unitários isolados.
2. Integrar chamada em `PlanoServiceImpl.prepararDadosPlano` (sem alterar assinatura do DTO ainda).
3. Ampliar histórico: substituir `LIMITE_TREINOS_HISTORICO` por janela de 42 dias.
4. Atualizar `PeriodizacaoPromptFormatter` para aceitar e incluir `DecisaoProgressao` no prompt.
5. Testes de integração verificando que planos gerados refletem o estado de progressão correto.

Rollback: cada etapa é independente. Se a integração com o formatter gerar instabilidade no prompt, remover o bloco de progressão do texto gerado sem desfazer a lógica de serviço.

## Open Questions

- **RPE no `TreinoRealizado`**: o campo `rpeMedio` (ou equivalente) existe na entidade? Verificar antes de implementar o cálculo de RPE dos treinos duros.
- **Treinos-chave**: como identificar se um treino era "chave" (intervalado, tempo, longão) para medir taxa de conclusão de treinos-chave? Usar `TipoTreino` ou campo de planejamento?
- **Longão**: critério de identificação — maior treino da semana? treino acima de X km/min? Definir threshold no enum `MetricasThresholds`.
