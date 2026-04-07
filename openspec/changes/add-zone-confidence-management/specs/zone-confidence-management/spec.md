## ADDED Requirements

### Requirement: Expor confiança das zonas fisiológicas
O sistema SHALL indicar se as zonas usadas na prescrição estão confiáveis, estimadas ou desatualizadas.

#### Scenario: Contrato mínimo do status
- **WHEN** o status de confiança de zonas for calculado
- **THEN** o resultado SHALL conter `confidenceStatus`, `primaryReason` e indicação se houve uso de fallback

#### Scenario: Zonas com dados recentes e consistentes
- **WHEN** os limiares do atleta estiverem atualizados e coerentes com o histórico recente
- **THEN** o status SHALL ser `confiável`

#### Scenario: Zonas estimadas
- **WHEN** a prescrição depender de fallback por ausência de teste válido
- **THEN** o status SHALL ser `estimada`

#### Scenario: Zonas desatualizadas
- **WHEN** os dados fisiológicos estiverem vencidos ou incompatíveis com o histórico observado
- **THEN** o status SHALL ser `desatualizada`

#### Scenario: Dados estimados por fallback
- **WHEN** a prescrição usar FC limiar, pace limiar ou zonas derivados de estimativa
- **THEN** o sistema SHALL marcar explicitamente que a confiança foi afetada por fallback

### Requirement: Recomendar reavaliação quando necessário
O sistema SHALL sugerir reavaliação de zonas quando a confiança estiver comprometida.

#### Scenario: Recomendação de teste
- **WHEN** o status de confiança for `desatualizada`
- **THEN** o sistema SHALL recomendar reteste ou atualização fisiológica antes de aumentar precisão da prescrição

#### Scenario: Prescrição com confiança reduzida
- **WHEN** o status for `estimada` ou `desatualizada`
- **THEN** o contexto de prescrição SHALL expor essa limitação ao fluxo decisório
