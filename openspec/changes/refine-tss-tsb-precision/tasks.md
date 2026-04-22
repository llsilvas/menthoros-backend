## 1. Fator de elevação bidirecional (ex-ISSUE-07)

- [ ] 1.1 Refatorar `TssCalculatorService.calcularFatorElevacao()` extraindo `calcularComponenteSubida(double gradienteMedio)` a partir da lógica existente
- [ ] 1.2 Implementar `calcularComponenteDescida(double gradienteMedio)` com peso 0.6x sobre o componente equivalente de subida (Vernillo et al., 2017)
- [ ] 1.3 Somar componentes de subida e descida no `calcularFatorElevacao()`, preservando limite máximo de 2.0
- [ ] 1.4 Garantir que treinos sem `elevacaoPerdaMetros` (null ou 0) continuam retornando o fator atual (retrocompatibilidade)
- [ ] 1.5 Adicionar testes em `TssCalculatorServiceElevacaoTest.java`: cenário plano, só subida, só descida, misto com net downhill, limite de 2.0

## 2. Ramp Rate com fallback (ex-ISSUE-08)

- [ ] 2.1 Adicionar em `MetricasDiariasRepository` o método `findTopByAtletaIdAndDataBetweenOrderByDataDesc(UUID atletaId, LocalDate dataInicio, LocalDate dataFim)`
- [ ] 2.2 Adicionar em `MetricasDiariasRepository` o método `findTopByAtletaIdAndDataBeforeOrderByDataDesc(UUID atletaId, LocalDate data)` para recuperar o primeiro registro disponível
- [ ] 2.3 Refatorar `TsbServiceImpl.calcularRampRate()` implementando estratégia em três níveis: (1) exato 7 dias, (2) interpolar janela 5–9 dias, (3) estimar a partir do primeiro registro (janela ≤14 dias)
- [ ] 2.4 Preservar retorno de 0.0 quando nenhuma das estratégias resultar em referência válida
- [ ] 2.5 Adicionar testes em `TsbServiceImplRampRateFallbackTest.java`: 7 dias exatos, gap com registro a 6 dias, atleta novo com 5 dias de histórico, atleta sem histórico

## 3. TSS por etapa (ex-ISSUE-09)

- [ ] 3.1 Avaliar necessidade de `@EntityGraph` ou `JOIN FETCH` em `TreinoRealizadoRepository` para carregar `etapasRealizadas` sem N+1 no fluxo de cálculo
- [ ] 3.2 Criar método privado `calcularTssPorEtapas(TreinoRealizado)` em `TssCalculatorService`
- [ ] 3.3 Criar método privado `calcularIfEtapa(EtapaRealizada, Atleta)` respeitando prioridade FC > Pace > RPE
- [ ] 3.4 Criar método privado `calcularIfPorFc(int fcMedia, Atleta)` reutilizando a fórmula de HR reserve
- [ ] 3.5 Alterar `calcularTss(TreinoRealizado)` para delegar a `calcularTssPorEtapas()` quando `treino.getEtapasRealizadas()` estiver populado e não vazio
- [ ] 3.6 Preservar o caminho atual (cálculo pela média geral) como fallback quando não há etapas
- [ ] 3.7 Reavaliar interação com `aplicarFatorImpactoTreino()` (ver nota de integração com ISSUE-04 já resolvida — garantir que atenuação de 50% do componente extra para FC continua correta)
- [ ] 3.8 Adicionar testes em `TssCalculatorServiceEtapasTest.java`: treino intervalado com 5 etapas (verificar desigualdade de Jensen: TSS por etapa ≥ TSS por média), treino contínuo com 1 etapa, treino sem etapas (fallback)

## 4. Thresholds de TSB por nível de experiência (ex-ISSUE-10)

- [ ] 4.1 Adicionar `MetricasThresholds.getFatorThresholdTsb(NivelExperiencia nivel)` retornando 1.3 / 1.1 / 1.0 / 0.75
- [ ] 4.2 Criar overload `FaixaTsb.classificar(Double tsb, NivelExperiencia nivel)` que divide `tsb` pelo fator antes de comparar com thresholds base
- [ ] 4.3 Preservar `FaixaTsb.classificar(Double tsb)` como overload de retrocompatibilidade delegando a `classificar(tsb, NivelExperiencia.AVANCADO)`
- [ ] 4.4 Atualizar `PlanoMetaDados.getInterpretacaoTsb()`, `estaEmFormaIdeal()` e `estaMuitoFatigado()` para passar `atleta.getNivelExperiencia()` ao `classificar`
- [ ] 4.5 Atualizar `MetricasAlertaService.analisarMetricas()` e `calcularStatus()` para propagar o `NivelExperiencia` nos pontos que hoje classificam TSB
- [ ] 4.6 Adicionar log informativo comparando classificação com e sem ajuste durante rollout (ex.: `log.info("TSB={} Nivel={} FaixaAjustada={} FaixaSemAjuste={}")`)
- [ ] 4.7 Adicionar testes em `FaixaTsbPorNivelTest.java`: iniciante TSB=-25 deve cair em faixa mais severa que avançado, elite TSB=-25 deve cair em faixa menos severa, `classificar(tsb)` sem nível deve retornar valor idêntico ao atual

## 5. Validação e observabilidade

- [ ] 5.1 Gerar relatório comparativo TSS antes/depois para uma amostra de treinos históricos (intervalados vs contínuos) em ambiente de stage
- [ ] 5.2 Gerar relatório comparativo `FaixaTsb` antes/depois por nível de experiência em ambiente de stage
- [ ] 5.3 Documentar no CHANGELOG (ou equivalente) a mudança esperada nos valores de TSS/TSB para dados históricos e recomendação de reexecutar `GET /atleta/{id}/recalcular-metricas` por atleta afetado
- [ ] 5.4 Verificar cobertura de testes agregada com `./mvnw verify` e relatório JaCoCo

## 6. Pré-requisitos

- [ ] 6.1 Confirmar que `fix-tsb-semantics` está merged ou tem semântica estabilizada antes de iniciar a seção 3 (TSS por etapa)
- [ ] 6.2 Confirmar que `add-continuous-daily-load-management` está merged antes de iniciar a seção 2 (Ramp Rate fallback depende do contrato final de `MetricasDiarias`)
- [ ] 6.3 Confirmar que `progressao-treinos` está merged antes de iniciar a seção 4 (classificação por nível conversa com o envelope de progressão)

## 7. Piso de pace para IF saturável (ex-BACKLOG P2-A)

- [ ] 7.1 Identificar em `TssCalculatorService.calcularIfPorPace()` o comportamento atual quando o pace do treino é **mais rápido** que o pace limiar do atleta
- [ ] 7.2 Documentar o problema: IF calculado sem piso tende a subestimar TSS em sessões de qualidade (ex: 400m em 3:00/km de um atleta com limiar em 4:00/km) quando o teto de IF não é mantido
- [ ] 7.3 Definir constante `IF_TETO` (ex: 1.20) e `IF_PISO_POR_ZONA` para evitar tanto estouro quanto saturação em zonas altas
- [ ] 7.4 Aplicar piso de pace no cálculo de IF por pace: `if = min(IF_TETO, max(IF_PISO_ZONA, paceLimiar / paceTreino))`
- [ ] 7.5 Validar que o cálculo por pace não contradiz o cálculo por FC quando ambos estão disponíveis (usar prioridade FC > Pace já existente)
- [ ] 7.6 Adicionar testes em `TssCalculatorServicePacePisoTest.java`: pace extremamente rápido (400m 3:00/km com limiar 4:00/km), pace ligeiramente rápido, pace no limiar, pace mais lento que limiar

## 8. Triângulo pace/distância/duração (ex-BACKLOG P2-B)

- [ ] 8.1 Criar `TreinoConsistenciaValidator` com método `validarTriangulo(TreinoRealizado)` retornando `ResultadoValidacao { consistente, inconsistenciaPct, campoSuspeito }`
- [ ] 8.2 Implementar regra: dado `distanciaKm`, `duracaoMin` e `paceMedio`, verificar se `paceMedio * distanciaKm ≈ duracaoMin` com tolerância de 5%
- [ ] 8.3 Se dois campos estiverem presentes e um ausente, derivar o terceiro automaticamente (preenchimento via `TreinoRealizadoService.normalizarCampos()` antes do cálculo de TSS)
- [ ] 8.4 Se os três estiverem presentes e divergirem > 5%, registrar log WARN com `campoSuspeito` e usar o par mais confiável (prioridade: duração + distância > pace) para recalcular o pace derivado
- [ ] 8.5 Integrar `TreinoConsistenciaValidator` ao fluxo de `TreinoRealizadoService.criar()` e `atualizar()` antes do cálculo de TSS
- [ ] 8.6 Adicionar testes em `TreinoConsistenciaValidatorTest.java`: triângulo consistente, pace ausente derivado, distância ausente derivada, divergência > 5%, divergência ≤ 5%
- [ ] 8.7 Garantir que o endpoint de criação de treino não retorne 400 Bad Request por inconsistência — apenas registra log e usa par de maior confiança (comportamento silencioso para não quebrar ingestões existentes)
