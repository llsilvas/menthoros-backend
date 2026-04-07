## ADDED Requirements

### Requirement: Validar plano semanal proposto pela IA antes de persistir
O sistema SHALL executar uma skill determinística de guarda de prescrição antes de persistir qualquer plano semanal gerado pela IA.

#### Scenario: Plano dentro dos limites
- **WHEN** um plano proposto estiver compatível com os limites fisiológicos e históricos do atleta
- **THEN** o sistema SHALL permitir a persistência do plano

#### Scenario: Plano com violação crítica
- **WHEN** um plano proposto violar restrições mandatórias de fadiga, lesão ou dias consecutivos
- **THEN** o sistema SHALL bloquear a persistência ou marcar o plano como inválido para revisão

### Requirement: Validar coerência de carga e progressão
O sistema SHALL checar volume e carga semanal propostos contra o histórico recente e contra as metas calculadas para a semana.

#### Scenario: Volume excessivo versus histórico
- **WHEN** o volume do plano exceder o limite de progressão aceitável para o atleta
- **THEN** a skill SHALL sinalizar violação de progressão

#### Scenario: TSS incompatível com meta semanal
- **WHEN** o TSS total planejado ficar acima ou abaixo do intervalo aceitável da meta calculada
- **THEN** a skill SHALL registrar a inconsistência

### Requirement: Validar coerência esportiva da distribuição semanal
O sistema SHALL avaliar se a distribuição de estímulos respeita phase da periodização, rotação de estímulos e restrições do atleta.

#### Scenario: Repetição indevida de estímulo
- **WHEN** o plano repetir o mesmo padrão de intervalado em desacordo com as regras de variabilidade
- **THEN** a skill SHALL sinalizar repetição indevida

#### Scenario: Incompatibilidade com lesão ou restrição
- **WHEN** o atleta possuir lesão ativa ou restrição explícita
- **THEN** a skill SHALL impedir a inclusão de estímulos incompatíveis

#### Scenario: Incompatibilidade com nível do atleta
- **WHEN** o plano incluir densidade ou intensidade incompatíveis com o nível do atleta
- **THEN** a skill SHALL sinalizar a incoerência

### Requirement: Constraints determinísticas devem prevalecer sobre o LLM
O sistema SHALL garantir que restrições mandatórias calculadas pelas skills prevaleçam sobre qualquer proposta do modelo.

#### Scenario: Intervalado proibido
- **WHEN** o atleta estiver inelegível para intensidade pela camada determinística
- **THEN** o plano final NÃO SHALL conter treino intervalado ou equivalente intenso

#### Scenario: Máximo de dias consecutivos
- **WHEN** o máximo de dias consecutivos calculado for N
- **THEN** o plano final NÃO SHALL exceder N dias consecutivos de treino
