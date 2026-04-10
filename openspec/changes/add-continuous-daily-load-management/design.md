## Context

O Menthoros já possui uma base importante para carga e fadiga:

- `TssCalculatorService` calcula `TSS`
- `TsbServiceImpl` atualiza `CTL`, `ATL`, `TSB` e `Ramp Rate`
- `MetricasDiarias` persiste o estado diário
- `PlanoMetaDados` resume o estado atual do atleta

Também já existe um movimento correto em `fix-tsb-semantics` para separar prontidão (`tsbInicioDia`) de pós-carga (`tsbFimDia`).

O próximo passo é tornar essa linha do tempo realmente contínua. Em termos de produto, isso significa que o dia sem treino não pode ser invisível, e o treino lançado retroativamente não pode contaminar a consistência da semana até que alguém faça um recálculo manual.

## Goals / Non-Goals

**Goals:**
- garantir uma série diária contínua de métricas por atleta
- tratar descanso como dado fisiológico explícito
- recalcular automaticamente a janela afetada por novo treino, edição ou sync
- produzir leitura operacional de prontidão usando múltiplos sinais
- melhorar a qualidade dos insumos para prescrição, revisão semanal e fila do treinador

**Non-Goals:**
- substituir as métricas fisiológicas base por um score único e opaco
- redesenhar todos os thresholds fisiológicos nesta mudança
- implementar todos os ajustes downstream nas telas nesta mesma entrega

## Decisions

### D1: Série diária contínua como regra de domínio

**Decisão:** O sistema deve manter `MetricasDiarias` como série contínua por atleta, inclusive para dias sem treino.

**Rationale:** Sem continuidade, `CTL`, `ATL`, `TSB` e sinais derivados perdem consistência temporal. Descanso também altera o estado fisiológico e precisa existir na série.

---

### D2: Dia sem treino é evento válido, não ausência de dado

**Decisão:** Dias sem treino devem ser persistidos com `TSS = 0`, `volumeKm = 0`, `treinosRealizados = 0` e `foiDiaDescanso = true`, permitindo o decaimento correto de `ATL` e `CTL`.

**Rationale:** O descanso melhora prontidão e altera a distribuição da semana. Tratar esse dia como inexistente reduz a capacidade do sistema de interpretar a sequência de carga.

---

### D3: Recalcular da data afetada até o presente

**Decisão:** Sempre que um treino for criado, atualizado, removido, importado ou sincronizado em uma data passada, o sistema deve recalcular `MetricasDiarias` daquela data até `hoje`.

**Rationale:** `CTL`, `ATL`, `TSB` e `Ramp Rate` são recursivos. Alterar uma carga histórica muda toda a série posterior.

---

### D4: Readiness score como camada operacional, não substituição das métricas

**Decisão:** O produto deve gerar um `readinessScore` operacional derivado de múltiplos sinais, mas sem substituir exposição de `TSB`, `ATL`, `CTL` e `Ramp Rate`.

**Rationale:** O treinador precisa de síntese rápida para decidir, mas também precisa conseguir auditar os sinais principais por trás da recomendação.

---

### D5: Readiness deve combinar sinais complementares

**Decisão:** O readiness score deve considerar pelo menos:

- `tsbInicioDia` como sinal principal de prontidão
- relação entre `ATL` e `CTL`
- `Ramp Rate`
- dias consecutivos de treino
- dias desde último descanso real

**Rationale:** Nenhuma métrica isolada explica bem o estado do atleta dentro da semana.

---

### D6: Dependência explícita com `fix-tsb-semantics`

**Decisão:** Esta capability deve assumir `tsbInicioDia` como referência canônica para prontidão. Onde essa separação ainda não existir, a implementação deve ser planejada em conjunto ou em sequência imediata com `fix-tsb-semantics`.

**Rationale:** O readiness score e as decisões intra-semana ficam muito mais corretos quando baseados em TSB pré-carga.

## Technical Notes

### Contrato mínimo sugerido

```text
DailyLoadStatus
- atletaId
- data
- hasWorkout
- isRestDay
- tss
- ctlInicioDia
- atlInicioDia
- tsbInicioDia
- ctlFimDia
- atlFimDia
- tsbFimDia
- rampRate
- daysSinceLastRest
- consecutiveTrainingDays
- readinessScore
- readinessStatus
- primaryReason
```

### Gatilhos mínimos de recálculo

- lançamento manual de treino
- marcação de treino planejado como realizado
- edição de treino realizado
- importação de atividade externa
- atualização de atividade externa já existente
- deleção de treino realizado

### Janela mínima de recálculo

- de `dataAfetada` até `hoje`
- preenchendo dias sem treino quando não existirem registros
- preservando continuidade entre `fim do dia anterior` e `início do dia seguinte`

### Readiness score sugerido

O score pode ser uma escala operacional simples, por exemplo `0..100`, com interpretação:

- `80-100`: alta prontidão
- `60-79`: prontidão controlada
- `40-59`: atenção moderada
- `0-39`: baixa prontidão

Fontes sugeridas:

- peso principal: `tsbInicioDia`
- penalidade: `ATL/CTL` alto
- penalidade: `Ramp Rate` agressivo
- penalidade: muitos dias consecutivos
- bônus controlado: descanso recente

### Uso sugerido no produto

- geração de plano: modular intensidade do dia
- pós-treino: explicar impacto da sessão na sequência da semana
- revisão semanal: mostrar padrões de carga e recuperação
- fila de atenção: priorizar atletas com baixa prontidão combinada

## Risks / Trade-offs

**[Risco] Mais processamento por atleta** -> Recalcular janelas contínuas custa mais do que recalcular só o dia. Mitigação: recálculo incremental por janela afetada e execução assíncrona quando necessário.

**[Risco] Score simplificar demais a fisiologia** -> Mitigação: manter métricas brutas expostas e explicar o motivo principal do score.

**[Risco] Importações retroativas em lote** -> Podem disparar muito recálculo. Mitigação: agregação por atleta e batch de janela única.

## Migration Plan

1. Definir capability de gestão contínua de carga diária
2. Garantir persistência de dias sem treino em `MetricasDiarias`
3. Implementar recálculo automático da data afetada até `hoje`
4. Calcular e persistir sinais complementares de prontidão operacional
5. Expor readiness score e status para consumidores do produto

## Open Questions

- O recálculo será síncrono para lançamento manual e assíncrono para sync em lote?
- O readiness score deve ser exposto ao atleta desde a primeira versão ou ficar restrito ao treinador?
- A primeira versão deve recalcular apenas até `hoje` ou até a última data materializada em `MetricasDiarias`?
