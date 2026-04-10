## Context

O domínio atual já possui peças importantes para suportar testes de campo: `TreinoPlanejado`, `TreinoRealizado`, `TipoTreino`, geração semanal, redistribuição e campos fisiológicos no atleta. O que falta é explicitar que um teste de corrida não é apenas "mais um treino", mas um evento de avaliação com protocolo, finalidade e efeito esperado sobre a prescrição futura.

Na prática do treinador, o teste precisa cumprir quatro funções:

- ocupar um slot real da semana
- substituir preferencialmente um treino de qualidade
- gerar resultado interpretável por protocolo
- alimentar revisão de zonas e ritmos do atleta

## Goals / Non-Goals

**Goals:**
- tratar teste de campo como capability explícita do produto
- suportar `3 km` e `5 minutos`
- recomendar `3 km` como protocolo padrão no contexto de corrida
- permitir agendar o teste no lugar de um treino específico da semana
- preservar coerência de carga e recuperação ao encaixar o teste
- definir como o resultado influencia atualização fisiológica e confiança das zonas

**Non-Goals:**
- implementar protocolos laboratoriais ou testes avançados fora do contexto operacional de corrida
- definir nesta mudança todos os cálculos fisiológicos finais e fórmulas proprietárias
- substituir o fluxo completo de geração semanal por uma engine exclusiva de testes

## Decisions

### D1: O teste será modelado como treino especial de avaliação

**Decisão:** O sistema deve modelar o teste de campo como um treino planejado especial, com semântica explícita de avaliação.

**Rationale:** Isso permite usar o fluxo já existente de agenda semanal, execução e análise, sem confundir o teste com `PROVA` ou com um treino comum de intensidade.

---

### D2: `3 km` será o protocolo padrão recomendado

**Decisão:** Entre os protocolos suportados, o sistema deve tratar `3 km` como o protocolo padrão recomendado para corrida, mantendo `5 minutos` como alternativa suportada.

**Rationale:** Para o contexto de corrida, `3 km` tende a ser mais familiar para treinadores e atletas, facilita comparação longitudinal e produz resultado mais estável para ajuste de ritmos do que um esforço all-out fixado apenas por tempo.

---

### D3: O teste deve substituir um treino planejado da semana

**Decisão:** O agendamento do teste deve sempre apontar qual treino planejado está sendo substituído, preferencialmente um treino de qualidade (`INTERVALADO` ou `TEMPO_RUN`).

**Rationale:** O teste gera carga relevante e não deve ser somado arbitrariamente à semana. Ele precisa ocupar um slot real para preservar consistência da periodização.

---

### D4: O sistema deve restringir encaixes inseguros

**Decisão:** O sistema deve impedir ou alertar sobre agendamentos que coloquem o teste próximo demais de outro estímulo de alta intensidade ou de um longão.

**Rationale:** O teste tem custo fisiológico semelhante a uma sessão forte. Sem essa proteção, o treinador pode degradar a qualidade do teste e aumentar risco de fadiga desnecessária.

---

### D5: O resultado do teste deve alimentar atualização de parâmetros

**Decisão:** Após a conclusão do teste, o sistema deve produzir um resultado estruturado por protocolo e permitir atualização assistida ou automática dos parâmetros fisiológicos aplicáveis do atleta.

**Rationale:** O valor do teste não está só na execução, mas no reaproveitamento operacional do resultado para melhorar a prescrição seguinte.

## Technical Notes

### Contrato mínimo sugerido para o treino de teste

```text
RunningFieldTestPlan
- treinoPlanejadoId
- atletaId
- protocoloTeste: TRES_KM | CINCO_MIN
- recomendado: true | false
- substituiTreinoPlanejadoId
- motivoSubstituicao
- atualizaParametrosAoConcluir
- statusAvaliacao
```

### Contrato mínimo sugerido para o resultado

```text
RunningFieldTestResult
- treinoRealizadoId
- protocoloTeste
- distanciaTotalKm
- duracaoTotal
- paceMedio
- fcMedia
- fcMax
- perceivedEffort
- qualityFlag
- suggestedParameterUpdates
```

### Regras operacionais mínimas

- `3 km` deve aparecer como protocolo recomendado por padrão
- `5 minutos` deve continuar disponível como alternativa
- o teste deve substituir exatamente um treino planejado
- o treino substituído deve ficar marcado como substituído/cancelado por avaliação
- o sistema deve priorizar substituição de `INTERVALADO` ou `TEMPO_RUN`
- o sistema deve evitar substituição de `LONGO`, salvo confirmação explícita do treinador
- o sistema deve sinalizar necessidade de recuperação antes e depois do teste

### Estratégia sugerida de encaixe semanal

- preferir janela com 24 a 48 horas anteriores leves
- preferir dia seguinte regenerativo ou descanso
- evitar adjacência com outro treino de alta intensidade
- evitar acumular teste e longão no mesmo bloco de 48 horas

### Uso do resultado

- `3 km`: protocolo principal para ajuste de ritmos de treino e revisão de referência aeróbia/velocidade
- `5 minutos`: protocolo alternativo útil quando o treinador deseja esforço máximo curto e alta praticidade
- o sistema deve registrar se a atualização dos parâmetros foi aplicada automaticamente, sugerida para revisão ou recusada pelo treinador

## Risks / Trade-offs

**[Risco] Uso excessivo do teste** -> Pode transformar semanas normais em semanas de avaliação. Mitigação: recomendações de frequência e alertas de proximidade entre testes.

**[Risco] Atualização automática agressiva** -> Pode mudar zonas com base em execução ruim ou pacing inadequado. Mitigação: exigir sinal mínimo de qualidade do teste e permitir revisão manual.

**[Risco] Confusão entre teste e prova/simulado** -> Mitigação: semântica explícita de avaliação, protocolo e vínculo de substituição.

## Migration Plan

1. Definir capability de testes de campo de corrida
2. Introduzir protocolo recomendado `3 km` e protocolo alternativo `5 minutos`
3. Definir fluxo de agendamento com substituição de treino planejado
4. Definir contrato de resultado do teste
5. Integrar saída do teste ao fluxo de atualização fisiológica e confiança de zonas

## Open Questions

- A atualização dos parâmetros do atleta será automática por padrão ou sempre revisada pelo treinador?
- O sistema deve permitir agendar teste sem treino substituído apenas em semanas ainda não geradas?
- A primeira versão deve bloquear substituição de `LONGO` ou apenas exigir confirmação explícita?
