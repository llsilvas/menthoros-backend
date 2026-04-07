## Context

O sistema calcula métricas de treinamento (CTL, ATL, TSB) diariamente em `TsbServiceImpl`. O problema central é que o TSB persistido em `MetricasDiarias` — e propagado para `PlanoMetaDados` — é calculado **após** incorporar o TSS do próprio dia. Isso significa que todos os consumidores que tomam decisões sobre o treino do dia (elegibilidade para intervalado, ajuste de pace, formatação de prompts) estão usando um valor que já inclui a carga do treino que ainda vai prescrever ou que acabou de prescrever.

Arquivos-chave afetados:
- `TsbServiceImpl.java` (linhas 112, 309, 371) — cálculo e recálculo histórico
- `MetricasDiarias.java` — modelo de dados
- `PlanoMetaDados.java` (linha 49, 150) — estado agregado por atleta
- `IntervaladoElegibilidadeService.java` (linha 94) — gate fisiológico
- `PaceZoneCalculator.java` (linha 40) — ajuste de pace
- `MetricasPromptFormatter.java` (linhas 46, 55) — prompts de IA

## Goals / Non-Goals

**Goals:**
- Separar explicitamente TSB de prontidão (início do dia, antes da carga) de TSB pós-carga (fim do dia)
- Garantir que toda decisão de prescrição use TSB pré-treino
- Manter compatibilidade retroativa durante a transição (`tsbAtual` como alias)
- Estender o recálculo histórico ao primeiro treino disponível do atleta
- Deixar explícito nos dados e prompts qual TSB é prontidão e qual é pós-carga

**Non-Goals:**
- Mudar os algoritmos de CTL/ATL (EMA) — apenas a semântica do TSB
- Remover campos legados neste ciclo (será feito em release futura)
- Alterar a integração com Garmin/Strava
- Modificar lógica de sincronização de dados externos

## Decisions

### 1. Manter dois estados explícitos por dia (recomendado)

**Decisão:** Adicionar os campos `ctlInicioDia`, `atlInicioDia`, `tsbInicioDia`, `ctlFimDia`, `atlFimDia`, `tsbFimDia` em `MetricasDiarias`.

**Alternativa considerada:** Manter apenas `tsb` renomeado semanticamente + adicionar apenas `tsbFimDia`.

**Rationale:** A versão com campos explícitos elimina ambiguidade futura. Ter início e fim de CTL/ATL permite auditoria completa dos cálculos e facilita debug. O custo de colunas extras no banco é desprezível ante o ganho de clareza.

### 2. Definição matemática canônica

Para um dia D com TSS = `tss_d`:

```
ctl_inicio_d = ctl_fim_{d-1}
atl_inicio_d = atl_fim_{d-1}
tsb_inicio_d = ctl_inicio_d - atl_inicio_d   ← PRONTIDÃO

ctl_fim_d = tss_d * (1 - e^(-1/τ_ctl)) + ctl_inicio_d * e^(-1/τ_ctl)
atl_fim_d = tss_d * (1 - e^(-1/τ_atl)) + atl_inicio_d * e^(-1/τ_atl)
tsb_fim_d = ctl_fim_d - atl_fim_d            ← PÓS-CARGA
```

Para dia sem treino: `tss_d = 0`, portanto CTL e ATL decaem (ATL cai mais rápido) e o TSB do dia seguinte sobe.

### 3. Baseline para primeiro dia sem histórico

**Decisão:** `ctl_inicio = atl_inicio = 0` com flag de "warm-up period" (primeiros 42 dias ou `τ_ctl` dias).

**Rationale:** Baseline zero é conservador e seguro. Estimar baseline inicial a partir dos primeiros treinos (melhoria futura) pode introduzir viés. A flag de warm-up permite que os consumidores suavizem interpretações agressivas durante o período de aquecimento.

### 4. PlanoMetaDados: campo tsbProntidaoAtual

**Decisão:** Adicionar `tsbProntidaoAtual` (valor canônico para decisões) e manter `tsbAtual` como alias temporário apontando para `tsbProntidaoAtual`.

**Rationale:** Garante compatibilidade sem quebrar consumidores enquanto a migração acontece.

### 5. Recálculo histórico desde o primeiro treino

**Decisão:** `determinarDataInicio()` deve usar a data do primeiro treino do atleta (em vez de fixos 3 meses). Se não houver treinos, não recalcular.

**Rationale:** Um histórico truncado leva a CTL/ATL subestimados, distorcendo TSB. A correção completa só é possível com toda a série histórica.

### 6. Migração em releases graduais (3 releases)

- **Release 1:** Adicionar colunas, recalcular histórico, manter `tsbAtual` compatível
- **Release 2:** Migrar consumidores para `tsbProntidaoAtual`, atualizar DTOs e prompts
- **Release 3:** Remover semântica ambígua antiga

## Risks / Trade-offs

- **Dashboards e prompts mudam de valor sem aviso visual** → Mitigation: documentar a mudança em release notes; manter `tsbAtual` como alias durante transição
- **Thresholds fisiológicos podem precisar de ajuste** → Mitigation: realizar comparação entre valores antigos e novos via script de análise antes do release 2
- **Recálculo histórico mais longo aumenta custo de processamento** → Mitigation: processar em background, com rate limiting; usar `max(120 dias, 3 × τ_ctl)` como janela mínima quando dados são muito antigos
- **Atletas com histórico curto podem ter TSB instável nos primeiros dias** → Mitigation: flag `emPeriodoAquecimento` em `PlanoMetaDados` para suavizar interpretações
- **Risco de regressão em elegibilidade e pace** → Mitigation: testes de cenário obrigatórios antes do release 2 (seção 13 da especificação)

## Migration Plan

### Release 1 — Modelo de dados e recálculo
1. Criar migration Flyway (`V26__Add_tsb_inicio_fim_dia_to_metricas_diarias.sql`) com novas colunas nullable
2. Atualizar `MetricasDiarias.java` com novos campos
3. Refatorar `TsbServiceImpl.atualizarTsbDia()` para calcular e persistir ambos os estados
4. Estender `determinarDataInicio()` para usar o primeiro treino
5. Executar recálculo histórico para todos os atletas (job batch)
6. `tsbAtual` em `PlanoMetaDados` continua apontando para valor legado (compatibilidade)

### Release 2 — Consumidores e PlanoMetaDados
1. Adicionar `tsbProntidaoAtual` a `PlanoMetaDados`, popular a partir de `tsbInicioDia`
2. Atualizar `tsbAtual` para alias de `tsbProntidaoAtual`
3. Migrar `IntervaladoElegibilidadeService`, `PaceZoneCalculator`, `PlanoMetaDados` (métodos de interpretação) e `MetricasPromptFormatter`
4. Atualizar DTOs de saída com campos explícitos
5. Validar com cenários de teste obrigatórios

### Rollback
- Release 1: colunas são nullable → sistema funciona sem dados novos; reverter migration e código
- Release 2: reverter consumidores para campos antigos (alias ainda existe)

## Open Questions

- Threshold de "warm-up period": usar 42 dias fixo ou `τ_ctl` (42 dias por padrão)?
- Expor `tsbPosCargaAtual` nos DTOs de API agora ou apenas em release futura?
- Backfill do histórico: executar via job ao deploy ou via endpoint admin on-demand?
