## Why

Boa parte da qualidade da prescrição depende da confiança nas zonas fisiológicas do atleta. Se as zonas estiverem estimadas, vencidas ou incoerentes com o histórico recente, o sistema corre o risco de parecer preciso sem realmente estar baseado em dado confiável.

## What Changes

- nova capability `zone-confidence-management`
- status explícito para zonas: confiável, estimada, desatualizada
- detecção de inconsistência ou vencimento
- recomendação de reavaliação/teste quando necessário

## Capabilities

### New Capabilities

- `zone-confidence-management`

## Impact

**Produto:**
- melhora segurança e credibilidade da prescrição
- reduz falsa sensação de precisão

**Backend:**
- novo modelo de status de confiança
- integração com contexto de prescrição
