## Why

O sistema atual calcula o TSB do dia **após** incorporar o TSS do próprio dia, misturando dois conceitos distintos: prontidão pré-treino (estado do atleta antes da sessão) e estado pós-carga (fadiga acumulada após a sessão). Isso leva a decisões de prescrição e ajuste de pace baseadas em dados incorretos — o sistema usa fadiga pós-treino para decidir sobre o próprio treino.

## What Changes

- **BREAKING** Separação semântica explícita do TSB em dois estados: `tsbInicioDia` (prontidão, antes da carga) e `tsbFimDia` (estado pós-carga)
- Adição de campos `ctlInicioDia`, `atlInicioDia`, `ctlFimDia`, `atlFimDia` em `MetricasDiarias`
- Adição de `tsbProntidaoAtual` (e opcionalmente `tsbPosCargaAtual`) em `PlanoMetaDados`
- Refatoração de `TsbServiceImpl` para calcular e persistir ambos os estados corretamente
- Correção de todos os consumidores fisiológicos para usar TSB pré-treino (`tsbProntidaoAtual`)
- Atualização dos formatadores de prompt e DTOs para expor a semântica corretamente
- Extensão do recálculo histórico para partir do primeiro treino disponível (em vez de 3 meses)
- Migração de banco compatível: adição de novas colunas sem remoção das antigas

## Capabilities

### New Capabilities

- `tsb-prontidao-pos-carga`: Separação explícita entre TSB de prontidão (início do dia) e TSB pós-carga (fim do dia), com cálculo, persistência e consumo correto em todo o sistema

### Modified Capabilities

- `prova-crud`: Nenhuma alteração de requisitos — não impactado

## Impact

- **Entidades**: `MetricasDiarias` (novos campos de CTL/ATL/TSB início e fim do dia)
- **Serviços**: `TsbServiceImpl` (lógica de cálculo), `PlanoMetadadosService` (atualização de metadados)
- **Consumidores**: `IntervaladoElegibilidadeService`, `PaceZoneCalculator`, `PlanoMetaDados` (métodos de interpretação), `MetricasPromptFormatter`
- **DTOs e APIs**: Campos novos em respostas que expõem TSB; `tsbAtual` mantido temporariamente como alias para `tsbProntidaoAtual`
- **Banco de Dados**: Nova migration Flyway com colunas adicionais em `metricas_diarias`; backfill via recálculo histórico
- **Testes**: Novos cenários obrigatórios cobrindo dias sem treino, blocos de carga, taper e importação histórica longa
