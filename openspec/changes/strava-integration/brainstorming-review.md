# Brainstorming Review - Strava Integration

Data de consolidação: 2026-04-26  
Fonte: `_bmad-output/brainstorming/brainstorming-session-2026-04-26-1143.md`

## Objetivo

Registrar, no contexto OpenSpec, a revisão dos pontos levantados no brainstorming BMAD para `strava-integration`, com rastreabilidade entre:

- ideia levantada,
- estado de implementação atual,
- evidência no código,
- recomendação de ação (novo change / backlog).

## Escopo desta revisão

Esta revisão foca nos itens priorizados como MVP no brainstorming:

- `#1` Renovação de Token Silenciosa
- `#7` Estratégia de TSS por Fonte de Dados
- `#35` Webhook-First com Fila de Prioridade
- `#36` Análise LLM Assíncrona e Condicional
- `#11` Alerta Proativo de Desvio de Carga
- `#16` Semáforo de Atletas por Risco

## Matriz de Rastreabilidade (BMAD -> Implementação)

| ID | Ideia | Status | Evidência | Gap principal | Ação recomendada |
|---|---|---|---|---|---|
| #1 | Renovação de token silenciosa com fallback | **Parcial** | `StravaOAuthService.getValidToken` com margem de 5 min | Sem retry/backoff estruturado e sem política de notificação por persistência de falha | Refinar em `strava-oauth` |
| #7 | Estratégia de TSS por fonte (`FC`/`PACE`) | **Não implementado** | Campo `metodo_calculo_tss` existe em `TreinoRealizado` | Lógica de eleição automática não aplicada no sync Strava | Implementar em `strava-activity-sync` |
| #35 | Webhook-first com fila de prioridade | **Parcial** | `@Async` + executor dedicado (`StravaWebhookAsyncConfig`) | Não há fila com priorização por criticidade/atleta | Evoluir em `strava-webhooks` |
| #36 | Análise LLM assíncrona e condicional | **Não implementado** | Sem pipeline condicional pós-sync/webhook | Custo/latência LLM sem governança por gatilho | Novo change futuro (LLM + observabilidade) |
| #11 | Alerta proativo de desvio de carga | **Não implementado** | Não há emissão automática de alerta coach no fluxo Strava | Loop coach-atleta ainda passivo | Novo change futuro (alertas) |
| #16 | Semáforo de atletas por risco | **Não implementado no fluxo Strava** | Sem atualização de status de risco conectada ao sync | Sem triagem operacional para técnico | Novo change futuro (dashboard/risk-engine) |

## O que já está sólido no baseline

- OAuth Strava funcional (auth, callback, status, disconnect)
- Sync manual de atividades e laps com deduplicação por `externalId`
- Webhook create/update/delete com processamento assíncrono
- Proteção de segurança para endpoints (`/api/strava/webhook` público; demais autenticados)
- Base de testes unitários para serviços Strava

## Decisão de decomposição

O change original `strava-integration` foi decomposto em três changes para execução em branches separadas:

- `strava-oauth`
- `strava-activity-sync`
- `strava-webhooks`

Esta decomposição reduz risco, melhora revisão e permite evolução incremental dos gaps do brainstorming.

## Recomendações para revisão OpenSpec

### 1) `strava-oauth`

- Adicionar task explícita para política de retry/backoff de refresh token.
- Definir critério de "falha persistente" para observabilidade/notificação.

### 2) `strava-activity-sync`

- Adicionar task para preencher `metodoCalculoTss` automaticamente por disponibilidade de dados.
- Definir fallback formal quando FC não estiver disponível.

### 3) `strava-webhooks`

- Adicionar task para enfileiramento com prioridade (ao menos por regra simples de criticidade).
- Definir comportamento de reprocessamento em falha assíncrona.

### 4) Backlog pós-MVP (novos changes)

- `strava-llm-conditional-analysis` (ideia #36)
- `strava-coach-proactive-alerts` (ideia #11)
- `strava-athlete-risk-semaphore` (ideia #16)

## Checklist de revisão (pronto para cerimônia)

- [ ] Validar se cada gap acima está refletido nas tasks dos 3 novos changes
- [ ] Priorizar o que entra no próximo sprint (MVP estrito)
- [ ] Criar changes futuros para itens #36, #11 e #16
- [ ] Definir critérios de aceite mensuráveis por item de brainstorming

