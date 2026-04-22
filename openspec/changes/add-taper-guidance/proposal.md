## Why

O período final antes de uma prova-alvo (tapering) é determinante para o desempenho: reduzir volume sem perder qualidade, manter intensidade sob controle, dormir e recuperar. Hoje o Menthoros gera planos semanais iguais até o dia da prova — não há tratamento diferenciado para "duas semanas antes" ou "três dias antes", o que frustra atletas sérios e afeta resultado percebido no dia mais importante da periodização.

Incluir uma camada explícita de tapering (prescrição de taper e peaking) entrega valor imediato ao atleta em um momento de altíssima visibilidade (dia da prova) e permite que o motor aplique literatura clássica (Mujika, Bosquet) de forma determinística antes do LLM compor o plano.

## What Changes

- **Nova entidade `PeriodoTaper`**: janela calculada por atleta e prova-alvo, com `inicio`, `fim`, `duracaoSemanas`, `reducaoVolumePct` (40–60%), `manutencaoIntensidade` (boolean), `estrategia` (LINEAR, EXPONENCIAL, STEP)
- **Novo serviço `TaperService`**: calcula início/fim do taper a partir da data da prova, distância, CTL atual e nível de experiência
- **Integração com `PlanoSemanalService`**: marca semanas dentro da janela de taper com `faseSemanal=TAPER` e aplica reduções sobre volume-base
- **Integração com `IntervaladoElegibilidadeService`**: bloqueia intervalados de alto volume nos últimos 7 dias; permite "tune-up" curto (intervalos de ativação) nos dias 4–6
- **Integração com `PlanoTreinoPromptBuilder`**: envia ao LLM `estaEmTaper`, `diasAteProva`, `estrategiaTaper`, `reducaoVolumePct`
- **Migration Flyway**: tabela `tb_periodo_taper` com índices

## Capabilities

### New Capabilities

- `taper-guidance`: determinação da janela de taper e prescrição diferenciada de volume/intensidade nas últimas semanas antes de prova-alvo.

### Modified Capabilities

<!-- Nenhuma capability existente tem requisitos alterados — taper é uma camada determinística adicional aplicada antes do LLM. -->

## Impact

**Entidades e banco:**
- Nova tabela: `tb_periodo_taper` (ID, atleta_id, prova_id, inicio, fim, duracao_semanas, reducao_volume_pct, manutencao_intensidade, estrategia, tenant_id, created_at)
- Novo enum `EstrategiaTaper` (LINEAR, EXPONENCIAL, STEP)
- Novo enum `FaseSemanal` com valor adicional `TAPER` (se ainda não existir)

**APIs:**
- `GET /api/provas/{provaId}/taper?atletaId=X` — retorna janela calculada
- `POST /api/provas/{provaId}/taper/recalcular?atletaId=X` — força recálculo (uso do treinador)
- Sem breaking changes

**Regras determinísticas:**
- Duração padrão: 21 dias (3 semanas) para prova ≥ 21km; 14 dias para 10km; 7 dias para 5km
- Ajuste por nível: `INICIANTE` → taper mais curto (menos carga cumulativa); `AVANCADO` → taper mais longo (preservar peaking)
- Redução de volume: semana 1 do taper = 60% do volume-base, semana 2 = 75%, semana 3 = 90% (crescimento inverso do último para o primeiro)
- Intensidade: mantida até o dia 3; dias 1–2 antes da prova somente corridas curtas em Z1/Z2

**Integração com LLM:**
- Contexto passa a conter seção `taper` com `ativo`, `diasAteProva`, `estrategia`, `reducaoVolumePct`, `manutencaoIntensidade` para ajustar tom das instruções geradas

**Dependências:**
- Precisa que `Prova` tenha `dataProva` (já existe) e `provaAlvo=true`
- Complementa `add-macrociclo-structure` mas não é bloqueada por ele — se não houver macrociclo definido, `TaperService` calcula janela inline a partir da data da prova

## Referências científicas

- **Mujika, I. & Padilla, S. (2003)** — "Scientific bases for precompetition tapering strategies"
- **Bosquet, L. et al. (2007)** — "Effects of tapering on performance: a meta-analysis"
- **Pfitzinger, P. & Douglas, S. (2009)** — "Advanced Marathoning" (protocolos de taper por distância)
