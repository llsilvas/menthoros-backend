## Why

Hoje o Menthoros decide elegibilidade de intervalado e risco de sobrecarga usando apenas sinais objetivos derivados de carga (TSB, CTL, ramp rate, dias consecutivos). Esses sinais são posteriores — quando disparam, o atleta já está em fadiga acumulada. Sinais subjetivos diários (sono, humor, dores, energia) antecedem a queda de prontidão em 24–48h e são reconhecidos na literatura como preditores de lesão e overtraining mais sensíveis que TSB isoladamente.

Adicionar um check-in diário estruturado transforma prontidão de uma variável inferida em uma variável observada, e permite que o motor determinístico de prescrição module intervalado, volume e intensidade antes do TSB reagir. É pré-requisito natural para `add-coach-attention-queue` (fila operacional) e enriquece o envelope de decisão de `progressao-treinos`.

## What Changes

- **Nova entidade `CheckinProntidao`**: registro diário por atleta com `qualidadeSono`, `humor`, `doresMusculares`, `nivelEnergia`, `estresse`, `observacoes`, `data`, `tenantId`
- **Novo enum `NivelProntidao`**: `PRONTO` / `CAUTELOSO` / `DESCANSAR`
- **Novo serviço `ReadinessService`**: cálculo do score (0–1) a partir dos sinais subjetivos + classificação em NivelProntidao
- **Nova coluna derivada em `MetricasDiarias`**: `readinessScore` e `nivelProntidao` (persistidos para compor análise histórica)
- **Integração com `IntervaladoElegibilidadeService`**: readinessScore vira um dos portões de decisão (bloqueio quando `DESCANSAR`, atenuação quando `CAUTELOSO`)
- **Integração com `PlanoTreinoPromptBuilder`**: readiness do dia entra no contexto enviado ao LLM para gerar o plano semanal
- **Endpoints REST**: `POST /api/checkins`, `GET /api/checkins/{atletaId}?dias=N`, `GET /api/checkins/{atletaId}/atual`
- **Migration Flyway**: tabela `tb_checkin_prontidao` + colunas novas em `tb_metricas_diarias`

## Capabilities

### New Capabilities

- `daily-readiness-checkin`: captura, cálculo e exposição de prontidão subjetiva diária, com integração no motor de elegibilidade de intervalado e no contexto de prescrição.

### Modified Capabilities

<!-- Nenhuma capability existente tem requisitos alterados diretamente — a integração com elegibilidade de intervalado é aditiva aos portões existentes. -->

## Impact

**Entidades e banco:**
- Nova tabela: `tb_checkin_prontidao` (ID, atleta_id, data, sono, humor, dores, energia, estresse, observacoes, readiness_score, nivel_prontidao, tenant_id, created_at, updated_at)
- Colunas adicionadas em `tb_metricas_diarias`: `readiness_score`, `nivel_prontidao`
- Constraint: UNIQUE(atleta_id, data) em `tb_checkin_prontidao`

**APIs:**
- `POST /api/checkins` — registrar checkin do dia
- `GET /api/checkins/{atletaId}/atual` — checkin mais recente (ou nulo)
- `GET /api/checkins/{atletaId}?dias=N` — histórico
- Sem breaking changes em endpoints existentes

**Motor determinístico:**
- `IntervaladoElegibilidadeService` ganha um sexto portão: readiness. `DESCANSAR` bloqueia intervalado; `CAUTELOSO` reduz volume da sessão em 20–30%.
- `PlanoTreinoPromptBuilder` injeta a sequência dos últimos 7 dias de checkin no contexto (array compacto).

**Dependências com outros changes:**
- Este change é pré-requisito natural de `add-coach-attention-queue` (readiness baixo é um dos sinais de atenção).
- Complementa `progressao-treinos` (envelope técnico passa a considerar subjetivos).
- Independente de Strava (Onda 1) — pode ser executado em paralelo.

**Multi-tenancy:**
- `tb_checkin_prontidao` obrigatoriamente com `tenant_id` e filtro em todas as queries.
- Seguir padrão consolidado pela branch de `fix-multi-tenancy-enforcement`.
