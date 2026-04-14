## 1. Banco de Dados — Migrations

- [ ] 1.1 Criar migration SQL para adicionar campos `teste_tempo_total_segundos`, `teste_distancia_km`, `teste_pace_media_seg_por_km`, `teste_temperatura_c`, `teste_tipo_superficie`, `teste_observacoes` (todos nullable) em `tb_treino_realizado`
- [ ] 1.2 Criar migration SQL para adicionar `data_ultimo_teste_limiar` (DATE, nullable) e `periodicidade_teste_meses` (INT, default 3) em `tb_plano_meta_dados`

## 2. Enum e Domínio

- [ ] 2.1 Adicionar valor `TESTE_LIMIAR` ao enum `TipoTreino` com fator de impacto `1.35`, zona alvo "Zona 4-5 (Limiar/VO2max)" e cor `#E53935`

## 3. Entidades JPA

- [ ] 3.1 Adicionar campos de resultado do teste (`testeTempoTotalSegundos`, `testeDistanciaKm`, `testePaceMediaSegPorKm`, `testeTemperaturaC`, `testeTipoSuperficie`, `testeObservacoes`) na entidade `TreinoRealizado`
- [ ] 3.2 Adicionar campos `dataUltimoTesteLimiar` e `periodicidadeTesteMeses` (com default 3) na entidade `PlanoMetaDados`

## 4. DTOs

- [ ] 4.1 Criar `TesteLimiarResultadoInputDto` com os campos de resultado do teste (tempo, distância, temperatura, superfície, observações)
- [ ] 4.2 Criar `TesteLimiarHistoricoOutputDto` com os campos de saída do histórico (data, tempo, pace, paceLimiarCalculado, FC, RPE, condições)
- [ ] 4.3 Atualizar `TreinoRealizadoInputDto` para incluir os novos campos do teste (opcionais)
- [ ] 4.4 Atualizar `PlanoMetaDadosInputDto` para incluir `periodicidadeTesteMeses`
- [ ] 4.5 Atualizar o DTO/payload de geração do plano semanal para incluir o campo booleano opcional `incluirTesteLimiar` (default `false`)

## 5. Lógica de Prescrição do Teste

- [ ] 5.1 Criar método `criarEtapasTeste3K()` no service ou factory responsável por gerar os `EtapaTreino` padronizados (aquecimento, teste, desaquecimento) para um `TreinoPlanejado` do tipo `TESTE_LIMIAR`
- [ ] 5.2 Integrar a geração automática de etapas no fluxo de criação/persistência de `TreinoPlanejado`, ativado quando `tipoTreino == TESTE_LIMIAR`
- [ ] 5.3 No `PlanoTreinoService`, verificar o flag `incluirTesteLimiar` do payload e, quando `true`, incluir a instrução de posicionamento do teste na semana (substituindo o treino de qualidade) antes de chamar a IA

## 6. Cálculo do Pace de Limiar

- [ ] 6.1 Criar classe utilitária `TesteLimiarCalculator` com o método `calcularPaceLimiar(testeTempoSeg, distanciaKm)` que retorna o `paceLimiarSegPorKm` e os paces de cada zona (Z1-Z5)
- [ ] 6.2 Adicionar lógica no `TreinoRealizadoService` (ou handler de pós-save) para, ao salvar um `TreinoRealizado` do tipo `TESTE_LIMIAR` com `testeTempoTotalSegundos` não-nulo, calcular o pace e atualizar `PlanoMetaDados.paceLimiarSegPorKm` e `dataUltimoTesteLimiar`

## 7. Histórico de Testes — API

- [ ] 7.1 Criar `TesteLimiarRepository` com query `findByAtletaIdAndTipoTreinoOrderByDataTreinoDesc` para buscar os resultados de teste por atleta
- [ ] 7.2 Criar método `listarHistoricoTestes(atletaId)` no `AtletaService` (ou novo `TesteLimiarService`) que retorna a lista mapeada para `TesteLimiarHistoricoOutputDto`
- [ ] 7.3 Adicionar endpoint `GET /atletas/{atletaId}/testes-limiar` no `AtletaController` com controle de acesso multi-tenant

## 8. Alertas de Proximidade e Prazo do Teste

- [ ] 8.1 Criar método `calcularStatusTesteLimiar(PlanoMetaDados metaDados, LocalDate hoje)` que retorna um enum `StatusTesteLimiar` com os valores: `EM_DIA`, `PROXIMO` (≤14 dias), `VENCIDO` (≥0 dias de atraso), incluindo `diasRestantes` e `dataProximoTeste`
- [ ] 8.2 Criar enum `NivelAlertaTesteLimiar` ou reutilizar `NivelAlerta` existente com os tipos `TESTE_LIMIAR_PROXIMO` e `TESTE_LIMIAR_VENCIDO`
- [ ] 8.3 Integrar `calcularStatusTesteLimiar` na consulta do plano semanal e na consulta do atleta, adicionando o alerta correspondente (`TESTE_LIMIAR_PROXIMO` ou `TESTE_LIMIAR_VENCIDO`) quando aplicável — garantindo que apenas um dos dois seja emitido por vez

## 9. Testes Unitários e de Integração

- [ ] 9.1 Testes unitários para `TesteLimiarCalculator` — validar fórmulas de pace de limiar e zonas com casos extremos (pace lento, pace rápido, distância diferente de 3km)
- [ ] 9.2 Testes unitários para `calcularStatusTesteLimiar` — validar todos os cenários: null (vencido), em dia, exatamente 14 dias (proximo), 13 dias (proximo), 15 dias (em dia), exatamente no prazo (vencido), ultrapassado (vencido com diasRestantes negativo)
- [ ] 9.3 Teste de integração para criação de `TreinoPlanejado` do tipo `TESTE_LIMIAR` com verificação das etapas geradas
- [ ] 9.4 Teste de integração para o endpoint `GET /atletas/{atletaId}/testes-limiar` — listagem, ordenação, multi-tenancy e atleta sem testes
