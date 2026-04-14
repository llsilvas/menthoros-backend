## 1. Banco de Dados — Migrations

- [ ] 1.1 Criar migration: adicionar `protocolo_teste_padrao` (VARCHAR, default `TRES_KM`) em `tb_assessoria`
- [ ] 1.2 Criar migration: adicionar campos `teste_protocolo`, `teste_tempo_total_segundos`, `teste_distancia_km`, `teste_pace_media_seg_por_km`, `teste_temperatura_c`, `teste_tipo_superficie`, `teste_observacoes` (todos nullable) em `tb_treino_realizado`
- [ ] 1.3 Criar migration: adicionar `data_ultimo_teste_campo` (DATE, nullable) e `periodicidade_teste_meses` (INT, default 3) em `tb_plano_meta_dados`

## 2. Enum e Domínio

- [ ] 2.1 Criar enum `ProtocoloTeste` com valores `TRES_KM` e `COOPER_12MIN`, incluindo metadados: label, descrição, tipo de referência (distância fixa vs tempo fixo)
- [ ] 2.2 Adicionar valor `TESTE_CAMPO` ao enum `TipoTreino` com `fatorImpacto=1.35`, `zonaFcAlvo="Zona 4-5 (Limiar/VO2max)"` e cor `#E53935`

## 3. Entidades JPA

- [ ] 3.1 Adicionar campo `protocoloTestePadrao: ProtocoloTeste` (com default `TRES_KM`) na entidade `Assessoria`
- [ ] 3.2 Adicionar campos de resultado do teste (`testeProtocolo`, `testeTempoTotalSegundos`, `testeDistanciaKm`, `testePaceMediaSegPorKm`, `testeTemperaturaC`, `testeTipoSuperficie`, `testeObservacoes`) na entidade `TreinoRealizado`
- [ ] 3.3 Adicionar campos `dataUltimoTesteCampo` e `periodicidadeTesteMeses` (default 3) na entidade `PlanoMetaDados`

## 4. Interface e Implementações de Protocolo

- [ ] 4.1 Criar interface `FieldTestProtocol` em `services/fieldtest/` com métodos: `getProtocolo()`, `buildEtapasTreino(Atleta)`, `calcularParametros(ResultadoTesteDto)`
- [ ] 4.2 Criar record `ParametrosFisiologicosCalculados` com campos: `paceLimiarSegPorKm`, `paceZ1` a `paceZ5`
- [ ] 4.3 Implementar `TresKmProtocol`: etapas fixas (aquecimento 20min Z1-Z2, teste 3km máximo, desaquecimento 10-15min Z1) e fórmula Daniels (`paceLimiar = pace3K × 1.05`, zonas Z1–Z5 por fatores tabelados)
- [ ] 4.4 Implementar `CooperProtocol`: etapas fixas (aquecimento, 12min esforço máximo, desaquecimento) e fórmula Cooper (distância → VO2max → vVO2max → paceLimiar via 88% vVO2max)
- [ ] 4.5 Criar `FieldTestProtocolRegistry` (Spring `@Component`) que resolve `FieldTestProtocol` a partir de `ProtocoloTeste`, injetando as implementações disponíveis

## 5. DTOs

- [ ] 5.1 Criar `TesteResultadoInputDto` com campos de resultado (tempo, distância, temperatura, superfície, observações, protocolo)
- [ ] 5.2 Criar `TesteHistoricoOutputDto` com campos de saída (data, protocolo, tempo, pace, paceLimiarCalculado, FC, RPE, condições)
- [ ] 5.3 Atualizar `TreinoRealizadoInputDto` para incluir os novos campos de resultado de teste (todos opcionais)
- [ ] 5.4 Atualizar `PlanoMetaDadosInputDto` para incluir `periodicidadeTesteMeses`
- [ ] 5.5 Atualizar payload de geração do plano semanal para incluir o campo booleano `incluirTesteCampo` (default `false`)

## 6. Agendamento e Regras de Encaixe

- [ ] 6.1 Criar `FieldTestScheduler` em `services/fieldtest/` com método `agendarTeste(atleta, substituiTreinoPlanejadoId)` que: (a) resolve o protocolo via `FieldTestProtocolRegistry`, (b) constrói o `TreinoPlanejado` com etapas fixas, (c) marca o treino substituído como `CANCELADO_POR_AVALIACAO`
- [ ] 6.2 Implementar priorização de substituição: preferir `INTERVALADO` ou `TEMPO_RUN`; exigir confirmação explícita se `substituiTreinoPlanejadoId` apontar para `LONGO`
- [ ] 6.3 Implementar validações de encaixe seguro com retorno de alertas (não bloqueios): janela de 24h antes/depois, adjacência com alta intensidade, intervalo mínimo de 4 semanas entre testes, conflito com longão no mesmo bloco de 48h
- [ ] 6.4 No `PlanoTreinoService`, verificar flag `incluirTesteCampo` do payload e, quando `true`, incluir instrução de posicionamento do teste na semana antes de chamar a IA (preferir terça ou quarta-feira, nunca após treino pesado)

## 7. Cálculo e Persistência do Pace de Limiar

- [ ] 7.1 No `TreinoRealizadoService` (ou handler de pós-save), ao salvar um `TreinoRealizado` do tipo `TESTE_CAMPO` com resultado preenchido: resolver o protocolo via `FieldTestProtocolRegistry`, chamar `calcularParametros()`, e atualizar `PlanoMetaDados` com `paceLimiarSegPorKm`, `dataUltimoTesteCampo` e zonas calculadas
- [ ] 7.2 Garantir que a atualização do `paceLimiar` persiste também em `Atleta.paceLimiar` para uso imediato na geração do próximo plano

## 8. Histórico de Testes — API

- [ ] 8.1 Criar query `findByAtletaIdAndTipoTreinoOrderByDataTreinoDesc` em `TreinoRealizadoRepository` (ou reutilizar repository existente com filtro)
- [ ] 8.2 Criar método `listarHistoricoTestes(atletaId)` no `AtletaService` (ou novo `FieldTestService`) retornando lista de `TesteHistoricoOutputDto`
- [ ] 8.3 Adicionar endpoint `GET /atletas/{atletaId}/testes-campo` no `AtletaController` com controle de acesso multi-tenant

## 9. Alertas de Proximidade e Prazo

- [ ] 9.1 Criar método `calcularStatusTesteCampo(PlanoMetaDados, LocalDate)` retornando: `EM_DIA`, `PROXIMO` (≤14 dias), `VENCIDO` (prazo atingido/ultrapassado), com `diasRestantes` e `dataProximoTeste`
- [ ] 9.2 Integrar `calcularStatusTesteCampo` na consulta do plano semanal e do atleta, emitindo `TESTE_CAMPO_PROXIMO` ou `TESTE_CAMPO_VENCIDO` quando aplicável (apenas um por vez)

## 10. Testes Unitários e de Integração

- [ ] 10.1 Testes unitários para `TresKmProtocol.calcularParametros()`: pace lento, pace rápido, distância diferente de 3km, validar todas as zonas Z1–Z5
- [ ] 10.2 Testes unitários para `CooperProtocol.calcularParametros()`: distância alta, distância baixa, pacing irregular com qualityFlag
- [ ] 10.3 Testes unitários para `calcularStatusTesteCampo()`: null (vencido), em dia, exatamente 14 dias (próximo), 13 dias (próximo), 15 dias (em dia), exatamente no prazo (vencido), ultrapassado
- [ ] 10.4 Testes unitários para validações de encaixe do `FieldTestScheduler`: janela 24h, adjacência alta intensidade, intervalo entre testes, conflito com longão
- [ ] 10.5 Teste de integração: criação de `TreinoPlanejado` do tipo `TESTE_CAMPO` com verificação das etapas geradas por protocolo (3K e Cooper)
- [ ] 10.6 Teste de integração: endpoint `GET /atletas/{atletaId}/testes-campo` — listagem, ordenação, multi-tenancy, atleta sem testes
