## Why

Hoje o Menthoros já gera e redistribui treinos semanais, mas ainda não trata o teste de campo como um elemento operacional do ciclo de treino. Na prática do treinador de corrida, testes como `3 km` e `5 minutos` servem para recalibrar ritmos, revisar zonas e dar segurança para a prescrição das semanas seguintes.

Sem essa capability, o treinador precisa improvisar o teste como treino comum ou prova, o que prejudica a clareza do planejamento, a substituição correta de um treino de qualidade da semana e o reaproveitamento do resultado para atualizar parâmetros do atleta.

## What Changes

- nova capability `running-field-tests`
- suporte a testes de campo de corrida agendáveis dentro da semana do atleta
- protocolo `3 km` como padrão recomendado e protocolo `5 minutos` como alternativa suportada
- substituição explícita de um treino planejado da semana por um treino de teste
- regras de encaixe do teste respeitando recuperação e distribuição de carga
- uso do resultado do teste para recomendar ou aplicar atualização dos parâmetros fisiológicos do atleta

## Capabilities

### New Capabilities

- `running-field-tests`

## Impact

**Produto:**
- dá ao treinador uma forma clara e profissional de inserir testes de corrida no ciclo semanal
- melhora a qualidade da prescrição pós-teste
- reduz ambiguidade entre treino, simulado e prova

**Backend:**
- novo modelo para treino especial de avaliação
- novo fluxo de agendamento com substituição de treino planejado
- regra de processamento do resultado por protocolo

**Treinabilidade:**
- padroniza o `3 km` como protocolo principal no contexto da corrida
- mantém suporte ao `5 minutos` para cenários específicos
