## Why

Atletas amadores e treinadores convivem diariamente com a pergunta "estou a caminho do meu objetivo de tempo para a prova X?". Sem uma predição estruturada, a resposta fica na intuição do treinador ou em apps externos (VDOT calculators, Riegel spreadsheets), e o Menthoros perde oportunidade de diferenciação e de fechar o loop entre capacidade atual, objetivo e plano gerado.

Incluir predição de tempo de prova baseada em evidência (Riegel + VDOT + histórico de provas/testes) entrega valor percebido alto ao atleta, alimenta decisões de prescrição (gap atual → plano mais agressivo ou conservador) e abre espaço para métricas de evolução ao longo da periodização.

## What Changes

- **Nova entidade `PredicaoProva`**: snapshot de predição por atleta e prova-alvo, com `tempoEstimado`, `metodoUsado` (RIEGEL / VDOT / HIBRIDO), `fonteReferencia` (teste ou prova usada como base), `confiabilidade`, `calculadoEm`, `tenantId`
- **Novo serviço `PredicaoProvaService`**: implementa fórmulas de Riegel, VDOT (Daniels) e ensemble híbrido; escolhe automaticamente a melhor referência disponível
- **Novo endpoint de cálculo sob demanda**: `GET /api/provas/{provaId}/predicao?atletaId=X`
- **Job agendado semanal**: recalcula predição para todas as provas-alvo ativas e persiste snapshot
- **Integração com `PlanoTreinoPromptBuilder`**: gap entre predição e objetivo entra no contexto do LLM para calibrar agressividade do plano
- **Migration Flyway**: tabela `tb_predicao_prova` com índices

## Capabilities

### New Capabilities

- `race-time-prediction`: cálculo, persistência e exposição de predição de tempo de prova por atleta, com seleção determinística do método mais confiável dado o histórico disponível.

### Modified Capabilities

<!-- Nenhuma capability existente tem requisitos alterados por este change — a predição é camada adicional de análise. -->

## Impact

**Entidades e banco:**
- Nova tabela: `tb_predicao_prova` (ID, atleta_id, prova_id, tempo_estimado_seg, metodo_usado, fonte_referencia_id, confiabilidade, calculado_em, tenant_id, created_at)
- Índice `(atleta_id, prova_id, calculado_em DESC)` para consulta do snapshot mais recente

**APIs:**
- `GET /api/provas/{provaId}/predicao?atletaId=X` — calcula sob demanda ou retorna cache da última execução
- `GET /api/provas/{provaId}/predicao/historico?atletaId=X` — evolução da predição ao longo do tempo
- Sem breaking changes

**Motor determinístico:**
- `PredicaoProvaService.calcular()` escolhe referência na seguinte ordem: (1) prova recente com mesma distância, (2) prova recente com distância próxima + Riegel, (3) teste de campo com pace limiar → VDOT, (4) estimativa por CTL + pace limiar atual
- Confiabilidade é função da frescura da referência (dias desde) e do tipo (prova > teste > estimativa)

**Integração com LLM:**
- Contexto passa a conter: `objetivoTempoSeg`, `predicaoTempoSeg`, `gapSeg` (positivo = atrás do objetivo, negativo = à frente), `confiabilidade`

**Dependências:**
- Requer entidade `Prova` com campo `tempoObjetivo` (confirmar se já existe; caso não, adicionar como pré-requisito).
- Independente de Strava — pode rodar em paralelo, mas Strava enriquece o histórico de provas/testes e melhora a qualidade da predição.

## Referências científicas

- **Riegel, P.S. (1977)** — "Time predicting" (Runner's World) — fórmula de previsão por expoente 1.06
- **Daniels, J. (2014)** — "Daniels' Running Formula" — VDOT como unidade equivalente de capacidade
- **McMillan, G.** — calculadora de equivalências como referência operacional
