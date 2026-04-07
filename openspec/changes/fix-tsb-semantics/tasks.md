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
