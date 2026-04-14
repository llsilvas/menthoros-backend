## Why

O sistema atualmente não possui suporte a **testes de avaliação fisiológica periódica** na prescrição de planos de treino. O Teste de Limiar (Teste de 3K) é a principal ferramenta utilizada por treinadores brasileiros para aferir a evolução do atleta e recalibrar os paces de treino a cada trimestre. Sem isso, os planos gerados pela IA podem ficar desatualizados conforme o atleta evolui, prescrevendo ritmos inadequados.

## What Changes

- **Novo tipo de treino `TESTE_LIMIAR`** adicionado ao enum `TipoTreino`, com fator de impacto calibrado para o esforço máximo de 3km.
- **Prescrição estruturada do Teste de 3K**: geração automática de um `TreinoPlanejado` com etapas predefinidas (aquecimento, strides, teste, desaquecimento) ao incluir um teste no plano semanal.
- **Armazenamento dos resultados do teste**: novos campos no `TreinoRealizado` para capturar tempo total, pace médio do esforço, paces derivados (limiar, VO2max, zonas) e condições de realização.
- **Cálculo e persistência do pace de limiar**: a partir do resultado do teste, o sistema calcula o `paceLimiar` do atleta e atualiza seus metadados de treinamento.
- **Histórico e evolução**: endpoint para consultar o histórico de testes do atleta, permitindo visualizar a progressão do pace de limiar ao longo do tempo.
- **Inclusão opcional pelo treinador**: ao gerar o plano semanal, o treinador pode escolher se inclui ou não o Teste de 3K na semana; o sistema nunca insere o teste automaticamente sem confirmação explícita.
- **Alertas de proximidade**: o sistema emite alertas em dois momentos — quando o teste está se aproximando (janela configurável, ex: 2 semanas antes de completar o intervalo) e quando o prazo já foi atingido ou ultrapassado.

## Capabilities

### New Capabilities

- `teste-limiar`: Prescrição, execução e análise do Teste de Limiar 3K — incluindo a estrutura do treino, captura dos dados do resultado, cálculo do pace de limiar e histórico de evolução do atleta.

### Modified Capabilities

<!-- Nenhuma capability existente tem seus requisitos alterados -->

## Impact

- **Entidades**: `TipoTreino` (novo valor), `TreinoRealizado` (novos campos de resultado de teste), `PlanoMetaDados` (periodicidade do teste).
- **Services**: `PlanoTreinoService` (lógica de inserção do teste no plano), `AtletaService` (atualização do pace de limiar após teste).
- **Controllers**: `TreinoRealizadoController` (endpoint de registro do resultado), novo endpoint de histórico de testes.
- **Banco de dados**: migration para novos campos em `tb_treino_realizado` e `tb_plano_meta_dados`.
- **LLM/IA**: o contexto enviado à IA deve incluir o pace de limiar atual do atleta para prescrição correta das zonas de treino.
