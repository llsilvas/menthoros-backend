## ADDED Requirements

### Requirement: Expor testes de campo de corrida como capability explícita
O sistema SHALL tratar testes de campo de corrida como uma capability explícita de planejamento e avaliação.

#### Scenario: Protocolos suportados na primeira versão
- **WHEN** o treinador iniciar o fluxo de agendamento de teste de corrida
- **THEN** o sistema SHALL suportar os protocolos `TRES_KM` e `CINCO_MIN`

#### Scenario: Protocolo recomendado no contexto da corrida
- **WHEN** o sistema apresentar opções de teste de campo para corrida
- **THEN** o protocolo `TRES_KM` SHALL ser exibido como recomendado por padrão

#### Scenario: Semântica de treino especial
- **WHEN** um teste de campo for criado
- **THEN** o sistema SHALL tratá-lo como um treino especial de avaliação, distinto de prova e distinto de treino comum

### Requirement: Agendar o teste no lugar de um treino da semana
O sistema SHALL permitir que o treinador agende um teste substituindo um treino planejado da semana do atleta.

#### Scenario: Substituição de treino de qualidade
- **WHEN** o treinador agendar um teste em uma semana com `INTERVALADO` ou `TEMPO_RUN`
- **THEN** o sistema SHALL permitir substituir um desses treinos pelo teste

#### Scenario: Vínculo explícito de substituição
- **WHEN** o teste for salvo
- **THEN** o registro SHALL conter referência explícita ao treino planejado substituído

#### Scenario: Treino substituído marcado corretamente
- **WHEN** um teste substituir um treino planejado
- **THEN** o treino original SHALL ficar marcado como substituído ou cancelado por avaliação

#### Scenario: Substituição de longo
- **WHEN** o treinador tentar substituir um `LONGO`
- **THEN** o sistema SHALL exigir confirmação explícita ou emitir alerta forte antes de concluir a operação

### Requirement: Respeitar regras mínimas de encaixe semanal
O sistema SHALL proteger o encaixe do teste dentro da semana para evitar conflito de carga.

#### Scenario: Teste adjacente a outro treino intenso
- **WHEN** o teste for agendado em dia adjacente a outro treino de alta intensidade
- **THEN** o sistema SHALL bloquear o agendamento ou expor alerta operacional explícito

#### Scenario: Teste próximo ao longão
- **WHEN** o teste for colocado sem janela adequada de recuperação em relação ao treino longo
- **THEN** o sistema SHALL sinalizar risco de encaixe inadequado

#### Scenario: Semana com recuperação adequada
- **WHEN** houver pelo menos uma janela operacional segura antes e depois do teste
- **THEN** o sistema SHALL permitir o agendamento sem alerta crítico

### Requirement: Processar resultado do teste por protocolo
O sistema SHALL registrar e interpretar o resultado do teste conforme o protocolo executado.

#### Scenario: Resultado de teste de 3 km
- **WHEN** o atleta concluir um teste `TRES_KM`
- **THEN** o sistema SHALL registrar o resultado estruturado e gerar sugestões de atualização de ritmos e parâmetros aplicáveis

#### Scenario: Resultado de teste de 5 minutos
- **WHEN** o atleta concluir um teste `CINCO_MIN`
- **THEN** o sistema SHALL registrar o resultado estruturado e gerar sugestões compatíveis com esse protocolo

#### Scenario: Teste com qualidade insuficiente
- **WHEN** o resultado não atender critérios mínimos de qualidade
- **THEN** o sistema SHALL impedir atualização automática agressiva dos parâmetros do atleta

### Requirement: Reaproveitar o teste para melhorar a prescrição futura
O sistema SHALL tornar o resultado do teste utilizável no fluxo de revisão fisiológica e de prescrição.

#### Scenario: Atualização assistida de parâmetros
- **WHEN** o teste produzir resultado válido
- **THEN** o sistema SHALL expor parâmetros sugeridos para atualização do atleta

#### Scenario: Rastreabilidade da atualização
- **WHEN** parâmetros do atleta forem alterados com base em um teste
- **THEN** o sistema SHALL registrar que a atualização foi derivada de um teste de campo e qual protocolo foi usado

#### Scenario: Zonas afetadas por novo teste
- **WHEN** o resultado do teste for aceito como válido
- **THEN** o fluxo de prescrição SHALL poder usar esse resultado como evidência mais recente para revisão de zonas e ritmos
