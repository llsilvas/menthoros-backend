## 1. Modelo de Dados — MetricasDiarias

- [ ] 1.1 Criar migration Flyway `V26__Add_tsb_inicio_fim_dia_to_metricas_diarias.sql` com colunas `ctl_inicio_dia`, `atl_inicio_dia`, `tsb_inicio_dia`, `ctl_fim_dia`, `atl_fim_dia`, `tsb_fim_dia` (nullable, tipo DOUBLE PRECISION) na tabela `metricas_diarias`
- [ ] 1.2 Adicionar campos `ctlInicioDia`, `atlInicioDia`, `tsbInicioDia`, `ctlFimDia`, `atlFimDia`, `tsbFimDia` à entidade `MetricasDiarias.java`
- [ ] 1.3 Verificar se há DTOs de saída que expõem campos de `MetricasDiarias` e adicionar os novos campos correspondentes

## 2. Modelo de Dados — PlanoMetaDados

- [ ] 2.1 Adicionar campo `tsbProntidaoAtual` (Double) à entidade/classe `PlanoMetaDados.java`
- [ ] 2.2 Adicionar campo `tsbPosCargaAtual` (Double) a `PlanoMetaDados.java` para analytics retrospectivo
- [ ] 2.3 Atualizar `tsbAtual` em `PlanoMetaDados.java` para ser alias de `tsbProntidaoAtual` (manter compatibilidade durante transição)
- [ ] 2.4 Criar migration Flyway `V27__Add_tsb_prontidao_pos_carga_to_plano_metadados.sql` para as novas colunas em `plano_metadados` (se persistido no banco)

## 3. Refatoração do Cálculo TSB — TsbServiceImpl

- [ ] 3.1 Refatorar `atualizarTsbDia()` para calcular `ctlInicioDia`, `atlInicioDia`, `tsbInicioDia` a partir das métricas do dia anterior (antes de aplicar TSS do dia corrente)
- [ ] 3.2 Refatorar `atualizarTsbDia()` para calcular `ctlFimDia`, `atlFimDia`, `tsbFimDia` aplicando o TSS do dia corrente sobre os valores de início
- [ ] 3.3 Persistir ambos os estados (início e fim do dia) em `MetricasDiarias` dentro de `atualizarTsbDia()`
- [ ] 3.4 Atualizar `atualizarMetaDados()` para popular `PlanoMetaDados.tsbProntidaoAtual` com `tsbInicioDia` e `tsbPosCargaAtual` com `tsbFimDia`

## 4. Recálculo Histórico — TsbServiceImpl

- [ ] 4.1 Refatorar `determinarDataInicio()` para buscar a data do primeiro treino do atleta no repositório, em vez de usar janela fixa de 3 meses
- [ ] 4.2 Adicionar guard em `recalcularHistorico()`: se não houver treinos, retornar sem executar e sem lançar exceção
- [ ] 4.3 Implementar flag de "período de aquecimento" (`emPeriodoAquecimento`) em `PlanoMetaDados` quando histórico for menor que `τ_ctl` dias (default: 42 dias)

## 5. Consumidores Fisiológicos

- [ ] 5.1 Atualizar `IntervaladoElegibilidadeService.java` (linha ~94) para usar `metaDados.getTsbProntidaoAtual()` no gate fisiológico
- [ ] 5.2 Atualizar `PaceZoneCalculator.java` (linha ~40) para usar `tsbProntidaoAtual` no cálculo de ajuste de pace
- [ ] 5.3 Atualizar métodos `estaEmFormaIdeal()`, `estaMuitoFatigado()`, `interpretarTsb()` e `getRecomendacaoTsb()` em `PlanoMetaDados.java` (linha ~150) para usar `tsbProntidaoAtual`

## 6. Formatadores de Prompt

- [ ] 6.1 Atualizar `MetricasPromptFormatter.java` (linha ~46) para exibir "TSB (Prontidão hoje): X" usando `tsbProntidaoAtual`
- [ ] 6.2 Adicionar linha opcional "TSB (Pós-carga): Y" usando `tsbPosCargaAtual` no formatador de prompt

## 7. Testes Unitários — TsbServiceImpl

- [ ] 7.1 Adicionar teste: atleta sem histórico — `tsbInicioDia = 0` e flag de aquecimento ativo
- [ ] 7.2 Adicionar teste: atleta com 1 treino isolado — verificar que `tsbInicioDia` do dia do treino não inclui TSS do próprio treino
- [ ] 7.3 Adicionar teste: dia sem treino — `atl_fim` cai mais que `ctl_fim`, TSB do dia seguinte sobe
- [ ] 7.4 Adicionar teste: 7 dias consecutivos leves — verificar progressão estável de CTL e ATL
- [ ] 7.5 Adicionar teste: longão seguido de 2 dias leves — verificar recuperação de ATL e subida de TSB
- [ ] 7.6 Adicionar teste: bloco intervalado + rodagem + intervalado — verificar acúmulo e recuperação
- [ ] 7.7 Adicionar teste: semana de taper — TSB cresce conforme TSS cai

## 8. Testes Unitários — Consumidores

- [ ] 8.1 Atualizar testes de `IntervaladoElegibilidadeService` para verificar que o gate usa `tsbProntidaoAtual`
- [ ] 8.2 Atualizar testes de `PaceZoneCalculator` para verificar que o ajuste de pace usa `tsbProntidaoAtual`
- [ ] 8.3 Atualizar testes de `PlanoMetaDados` para verificar que `estaEmFormaIdeal()` e `estaMuitoFatigado()` usam `tsbProntidaoAtual`

## 9. Testes de Integração e Recálculo

- [ ] 9.1 Adicionar teste de integração: importação histórica longa — verificar consistência entre `tsbInicioDia` de cada dia D e `tsbFimDia` de D-1
- [ ] 9.2 Adicionar teste de comparação: executar recálculo com lógica antiga e nova no mesmo atleta, documentar diferenças esperadas
- [ ] 9.3 Verificar que o recálculo histórico para atleta sem treinos não lança exceção

## 10. Validação e Documentação

- [ ] 10.1 Executar `./mvnw clean verify` e garantir que todos os testes unitários e de integração passam
- [ ] 10.2 Verificar no Swagger UI que os DTOs de saída expõem os novos campos com documentação clara
- [ ] 10.3 Revisar prompts gerados pela IA para confirmar que a semântica de TSB está explícita no texto

## 11. Validação estrutural por `TipoTreino` (ex-BACKLOG P3-A)

- [ ] 11.1 Documentar em `TipoTreino` (ou enum auxiliar) as características estruturais esperadas de `REGENERATIVO`, `CONTINUO`, `TEMPO_RUN`: faixa típica de IF alvo, proporção entre segmentos de alta/baixa intensidade e duração mínima/máxima recomendada
- [ ] 11.2 Criar `TipoTreinoConsistenciaValidator` com método `validarEstrutura(TreinoRealizado)` que compara o `TipoTreino` declarado com a estrutura observada (distribuição de IF ao longo das etapas, duração total, variação de FC média entre etapas)
- [ ] 11.3 Implementar regras iniciais:
  - `REGENERATIVO` → IF médio esperado ≤ 0.70 e nenhuma etapa com IF > 0.80
  - `CONTINUO` → IF médio em `[0.70, 0.90]` e variação intra-treino < 20% (sem picos estruturais)
  - `TEMPO_RUN` → IF médio em `[0.85, 0.95]` com segmento central dominante acima de 0.85
- [ ] 11.4 Se `TipoTreino` declarado divergir da estrutura observada, `TipoTreinoConsistenciaValidator` SHALL retornar `SugestaoReclassificacao { tipoSugerido, confianca, motivo }` sem bloquear ingestão
- [ ] 11.5 Integrar o validator ao fluxo de `TreinoRealizadoService.criar()` e `atualizar()` — registra log INFO quando houver sugestão de reclassificação; não altera o tipo automaticamente
- [ ] 11.6 Expor `sugestaoReclassificacao` em `TreinoRealizadoOutputDto` (opcional/nullable) para que o frontend possa exibir e permitir ao treinador ajustar manualmente
- [ ] 11.7 Adicionar testes em `TipoTreinoConsistenciaValidatorTest.java`: regenerativo coerente, regenerativo com picos (sugere contínuo/tempo), contínuo coerente, tempo_run coerente, tempo_run com base fraca (sugere contínuo)
