# Relatório de reparo: dias de descanso no TSB (homelab, 2026-10-21)

Change: `fix-tsb-rest-days-materialization`. Base: homelab (`menthoros-db`, 6 atletas, 2 com métricas).
Roteiro e consultas: `tsb-rest-days-repair.sql`. Atletas anonimizados (A, B); os ids ficam fora do repositório.

## O que aconteceu

O "antes" não foi capturado: quando o script foi rodado, o job `TsbDecaimentoDiarioScheduler` já tinha
executado nesta base às 07:30 de 2026-10-21 e fechado os buracos. O estado anterior se reconstrói pelas
linhas criadas por ele (`created_at` = 07:30:00/01, todas com `updated_at` igual):

| Atleta | Linhas criadas pelo job | Primeiro dia ausente | Último dia criado |
|---|---|---|---|
| A | 13 | 2026-10-02 | 2026-10-21 |
| B | 8 | 2026-09-30 | 2026-10-21 |

Nenhuma linha preexistente foi reescrita: o seed das linhas de treino que vinham depois dos buracos já
estava coerente.

## Validação depois do reparo

| Verificação | Atleta A | Atleta B |
|---|---|---|
| Linhas / período | 481 / 2025-06-28 a 2026-10-21 | 443 / 2025-08-05 a 2026-10-21 |
| Consulta 1 (buraco ou última < hoje) | vazia | vazia |
| Pares de dias consecutivos com buraco | 0 de 480 | 0 de 442 |
| Seed (CTL e ATL de início do dia) diferente do fim do dia anterior em mais de 0,05 | 0 | 0 |
| Dias de descanso (TSS 0) com CTL fora de exp(-1/tau) do dia anterior (tolerância 0,05) | 0 de 377 | 0 de 297 |
| Maior desvio do decaimento | 0,0050 | 0,0049 |

O tau usado na consulta 2 é o do nível de experiência de cada atleta (ver o script), não necessariamente 42.

## Não verificado aqui

- Gauge `tsb.atletas_defasados` em `/actuator/prometheus` (sem acesso ao endpoint); a consulta 1 vazia é
  o equivalente em banco.
- Execução do job na próxima hora cheia depois do deploy da versão final (com o lock do atleta e o gauge -1).
