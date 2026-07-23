# Menthoros Backend

Domínio de treinamento esportivo: assessorias (tenants) treinam atletas via planos semanais gerados por IA, com um motor determinístico de periodização rodando em paralelo (shadow mode) e um fluxo de onboarding que calibra a confiança nos dados de cada atleta.

## Language

**Plano Semanal** (`PlanoSemanal`):
Plano de treino de uma semana para um atleta — o que é gerado, revisado e (quando aprovado) visível ao atleta.
_Avoid_: Plano de treino (ambíguo com `PlanoTreino`, o plano de longo prazo)

**Status de Revisão** (`PlanoReviewStatus`):
Gate de visibilidade do Plano Semanal ao atleta: `AGUARDANDO_REVISAO` (padrão, invisível ao atleta) → `APROVADO` (visível) ou `REJEITADO`. Nenhum plano nasce aprovado por padrão — a aprovação é sempre um evento explícito, seja do coach ou do sistema.
_Avoid_: WeekSuggestion (nome usado cedo na spec de `athlete-onboarding-baseline`, mas o objeto real é o `PlanoSemanal`/`PlanoReviewStatus`)

**Origem da Aprovação** (`origemAprovacao`, campo novo em `PlanoSemanal`):
Registra *quem* transicionou o plano para `APROVADO`: `COACH` (revisão manual via `PlanoReviewServiceImpl.aprovar`) ou `AUTO_CONFIANCA_ALTA` (sistema, via `aprovarTransicao` chamado direto de `PlanoServiceImpl` quando o atleta é Cenário A de confiança — athlete-onboarding-baseline). Sem esse campo, as duas origens são indistinguíveis uma vez persistidas — decisão tomada em 2026-07-21 para não perder esse dado desde o primeiro plano auto-aprovado em produção.

**Baseline do Atleta** (`AthleteBaselineState`, renomeado de `AthleteBaselineSnapshot` em 2026-07-21):
O estado *atual* (não histórico) de CTL/ATL/TSB estimado/medido de um atleta — 1 linha por atleta, sobrescrita a cada re-baseline semanal durante `CALIBRATION`. "Snapshot" foi rejeitado como nome por sugerir um recorte imutável no tempo, que não é o caso.
_Avoid_: AthleteBaselineSnapshot (nome antigo, sugeria histórico que não existia)

**Histórico de Baseline** (`AthleteBaselineHistory`, tabela nova decidida em 2026-07-21):
Trilha append-only de cada recálculo de baseline/score — 1 linha por evento de re-baseline, nunca sobrescrita. Existe separada da Baseline do Atleta (que é só o estado atual) para não perder a evolução do score durante a calibração — dado necessário para calibrar as próprias heurísticas hardcoded desta change (duração da calibração, threshold do Cenário C) com dado real de produção.

**Cenário de Confiança** (`ConfidenceTier`):
Classificação A/B/C de quão confiável é o dado de onboarding de um atleta — A (score ≥75, alta confiança), B (45-74), C (&lt;45, baixa confiança/cold start). Determina o `PlanningPolicy.reviewMode` (`EXCEPTION_ONLY`/`MANDATORY_NON_BLOCKING`/`MANDATORY_BLOCKING`) que por sua vez decide se o plano é auto-aprovado.
_Avoid_: "Cenario A/B/C" como texto solto (o enum `ConfidenceTier` é o termo canônico no código)

**Perfil de Onboarding** (`PerfilOnboardingAtleta`):
O estado do *processo* de onboarding de um atleta — `status` (`RASCUNHO`/`COMPLETO`) mais os campos coletados. Durante `RASCUNHO`, é a única fonte de verdade dos 11 campos obrigatórios (mesmo os 7 que também existem em `Atleta`) — nada é escrito em `Atleta` até a conclusão (ver ADR-0002). Na conclusão, se `Atleta` foi editada por fora (coach) depois do início do rascunho, a migração é bloqueada em vez de sobrescrever silenciosamente. Distinto da Baseline do Atleta: perfil é dado declarado pelo atleta/coach; baseline é calculado a partir de histórico de treino.
_Avoid_: confundir com `Atleta` (perfil de onboarding é o processo de coleta; `Atleta` é o dado definitivo, só atualizado na conclusão)

**Semana de Calibração** (decisão 2026-07-21):
Não é um intervalo de calendário independente — "uma semana de calibração passou" é definido como "um ciclo de `PlanoServiceImpl.gerarPlanoTreino` rodou para esse atleta", não um cron próprio. `CalibrationService.avaliarSemana` é chamado de dentro de `persistirPlanoCompleto`, no mesmo ponto onde o shadow do `PlannerEngine` já roda — sem scheduler novo.

**Canal de Integração** (`CanalIntegracao`, campo novo do onboarding, decisão 2026-07-21):
Declaração do atleta de qual plataforma ele vai usar para enviar/receber treinos: `INTERVALS_ICU` (com Garmin como dispositivo prioritário na orientação de conexão) ou `MANUAL` (sem integração, upload de .fit). Ortogonal ao Dispositivo (abaixo) — um atleta com Garmin pode usar `INTERVALS_ICU` ou `MANUAL`; a marca não determina o canal (o push de treino planejado já é agnóstico de marca via `WorkoutChannel`/`IntervalsIcuAdapter`). Strava não é oferecido como opção para atletas novos (ver ADR-0003) — em descontinuação, mas ainda ativo para quem já está conectado.
_Avoid_: "relógio do atleta" (ambíguo entre Canal e Dispositivo — sempre nomear qual dos dois)

**Dispositivo do Atleta** (`dispositivoMarca` + `dispositivoModelo`, campos novos do onboarding, decisão 2026-07-21):
Marca (`dispositivoMarca`: `GARMIN`/`COROS`/`POLAR`/`SUUNTO`/`APPLE`/`OUTRO`, obrigatório) e modelo (`dispositivoModelo`, texto livre, opcional) do relógio/dispositivo do atleta. Dois propósitos distintos: (1) a marca alimenta o Confidence Scorer como *prior* via `FontePriority` (a mesma tabela de prioridade já usada por `ActivityDedupService`), antes de qualquer atividade real existir; (2) o modelo é armazenado para uma feature futura de capacidade por dispositivo (ex.: nem todo modelo suporta potência de corrida/running dynamics) — sem lógica de capacidade construída ainda, só a captura do dado. Modelo é texto livre (não lista fixa) porque dispositivos novos lançam constantemente; virar catálogo estruturado fica para quando a feature de capacidade existir.

**Técnico Responsável** (conceito ainda NÃO modelado — gap conhecido, ver ADR-0001):
Um vínculo individual "este técnico cuida deste atleta", distinto de "este atleta pertence a esta Assessoria". Hoje não existe: uma `Assessoria` tem vários `TECNICO`/`ADMIN`, e qualquer um deles acessa qualquer atleta do tenant — não há isolamento por técnico. Vira relevante quando uma `Assessoria` tem múltiplos técnicos e precisa de isolamento de dado sensível entre eles.
