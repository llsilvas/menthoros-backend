## Context

O sistema já possui toda a infraestrutura de zonas de treino (`ZonaTreinoService`, `ZonaFC`, `ZonaPace`, `ZonaCompleta`) e as zonas já são enviadas ao prompt via `formatarDadosFisiologicos()`. O problema é duplo:

1. **Cálculo inconsistente**: Z1-Z3 e Z5 usam percentual de `fcMaxima`, enquanto Z4 usa `fcLimiar`. Isso gera inconsistência fisiológica — a zona de Limiar (Z4) é calculada corretamente em relação ao limiar real, mas as demais zonas são âncoras em FCmax, que é frequentemente estimada (220 - idade) e portanto imprecisa.

2. **Schema vs prompt desalinhados**: O schema JSON força o campo `fcAlvoEtapa` no formato `"140-160% FCmax"` (pattern regex), mas o prompt já informa os valores absolutos em bpm (`"Z2: 5:45-6:20 min/km | 140-160 bpm"`). Isso cria uma incoerência que o modelo de linguagem precisa resolver por conta própria, aumentando chance de erro.

3. **Ausência de validação de limites**: O `validarENormalizarPlanoGerado` valida ritmoAlvo contra o teto de pace histórico, mas não valida se `fcAlvoEtapa` está dentro dos limites fisiológicos da zona correspondente ao tipo da etapa.

**Referência fisiológica — modelo LTHR (Friel):**

| Zona | % FC Limiar | Descrição |
|------|-------------|-----------|
| Z1   | 75–85%      | Recuperação ativa |
| Z2   | 85–89%      | Aeróbico base |
| Z3   | 89–94%      | Tempo moderado |
| Z4   | 94–100%     | Limiar anaeróbico |
| Z5   | 100–106%    | VO2max / supralimiar |

Nota: Z5 pode ultrapassar fcLimiar pois representa esforço acima do limiar. A FC real no VO2max é medida, não estimada. O valor de 106% é um limite de segurança prático; valores acima são descartados para fins de prescrição.

## Goals / Non-Goals

**Goals:**
- Recalcular Z1-Z5 usando `fcLimiar` como única âncora fisiológica
- Garantir fallback determinístico quando `fcLimiar` é null (usar `getFcLimiarCalculada()` já existente)
- Corrigir o pattern do schema JSON de `fcAlvoEtapa` para aceitar formato `"NNN-NNN bpm"`
- Adicionar validação pós-geração: checar se a FC prescrita em cada etapa está dentro dos limites da zona esperada
- Manter compatibilidade total da API pública — nenhum endpoint ou DTO externo muda

**Non-Goals:**
- Implementar Z6 para FC (cardiac lag já documentado — mantém-se Z5 como equivalente de FC para Z6)
- Alterar o cálculo de zonas de pace (`calcularZonasPace`) — pace já usa `paceLimiar` corretamente
- Criar novo endpoint ou DTO para expor zonas
- Modificar a entidade `Atleta` ou executar migration de banco

## Decisions

### D1: Modelo LTHR para todas as zonas de FC

**Decisão:** Substituir os percentuais atuais de FCmax por percentuais de `fcLimiar` em todas as 5 zonas.

**Alternativas consideradas:**
- *Modelo misto (manter atual)*: Z4 usa fcLimiar, demais usam FCmax. Rejeitado — inconsistente fisiologicamente e dificulta a instrução ao LLM.
- *Modelo Karvonen (FC reserva)*: usa `(fcLimiar - fcRepouso) * % + fcRepouso`. Mais preciso individualmente, mas adiciona complexidade e depende do `fcRepouso` (campo nullable). Rejeitado — aumenta surface de falha sem ganho proporcional para o caso de uso.
- *Modelo FCmax puro*: todas as zonas como % de FCmax. Rejeitado — FCmax é estimada na maioria dos atletas, tornando as zonas menos individualizadas que usar fcLimiar testada.

**Percentuais adotados (LTHR):**
```
Z1: 75–85%  fcLimiar
Z2: 85–89%  fcLimiar
Z3: 89–94%  fcLimiar
Z4: 94–100% fcLimiar
Z5: 100–106% fcLimiar  (supralimiar — cap prático)
```

### D2: Formato `fcAlvoEtapa` no schema JSON

**Decisão:** Mudar o pattern de `"^[0-9]{1,3}-[0-9]{1,3}% FCmax$"` para `"^[0-9]{2,3}-[0-9]{2,3} bpm$"`.

**Rationale:** O prompt já envia valores absolutos (`140-160 bpm`). O padrão atual força o LLM a usar um formato inconsistente com o que recebeu, gerando divergência. Com bpm absoluto, a validação pós-geração pode comparar diretamente com os limites computados das zonas.

### D3: Estratégia de validação pós-geração de FC

**Decisão:** Para cada `EtapaTreinoLlmDto`, extrair o par `(fcMin, fcMax)` do campo `fcAlvoEtapa`, computar as zonas do atleta e verificar se o range prescrito tem sobreposição razoável (≥50%) com a zona alvo esperada pelo tipo da etapa.

**Mapeamento tipo de etapa → zona esperada:**
```
AQUECIMENTO   → Z1 (75–85%)
PRINCIPAL     → Z2–Z4 (depende do tipo de treino)
INTERVALADO   → Z4–Z5
RECUPERACAO   → Z1
DESAQUECIMENTO → Z1–Z2
```

**Comportamento em falha de validação:** Log de `WARN` + correção automática do range para o centro da zona esperada. Não lança exceção — manter o plano válido é prioridade sobre rejeitar.

**Alternativa considerada:** Rejeitar o plano inteiro se FC inválida. Rejeitado — degradaria a experiência sem ganho de segurança, pois o atleta ainda recebe um plano usável.

### D4: Localização da validação de FC

**Decisão:** Adicionar a lógica de validação de FC dentro de `IaServiceImpl.validarENormalizarPlanoGerado`, usando `ZonaTreinoService` injetado.

**Rationale:** `IaServiceImpl` já injeta `AtletaRepository` e usa padrão de normalização por stream. Centraliza toda a lógica pós-LLM em um lugar, consistente com a abordagem atual para `ritmoAlvo`.

## Risks / Trade-offs

- **[Risk] Zonas Z5 ultrapassam fcLimiar** → Mitigation: cap em 106% é conservador; ao prescrever intervalados, o atleta raramente atinge FC de VO2max sustentada. Validação avisa mas não bloqueia.

- **[Risk] fcLimiar null para atletas sem teste** → Mitigation: `getFcLimiarCalculada()` já existe no `Atleta` com fallback para 85% da FCmax calculada. Nenhum path de null pointer novo.

- **[Risk] Mudança no cálculo das zonas altera comportamento de prescrições existentes** → Mitigation: a diferença prática entre FCmax-based e LTHR-based é pequena para atletas com FCmax bem calibrada (~5-10 bpm por zona). O prompt já explicitará os valores absolutos, dando ao LLM âncora clara.

- **[Trade-off] Correção silenciosa de FC** → Ao corrigir automaticamente o range em vez de rejeitar, o log de WARN permite rastreabilidade, mas o treinador não vê a correção na UI. Aceitável para MVP; alertas explícitos podem ser adicionados posteriormente.

## Migration Plan

1. Alterar `ZonaTreinoService.calcularZonasFC()` — sem impacto em banco ou API.
2. Alterar `IaServiceImpl.buildSchemaTightInlineOrDefs()` — muda pattern do JSON Schema enviado ao OpenAI; compatível com versão atual do modelo.
3. Injetar `ZonaTreinoService` em `IaServiceImpl` e adicionar validação de FC.
4. Testes unitários para os novos percentuais e para a validação.

**Rollback:** Reverter os percentuais em `ZonaTreinoService` e o pattern em `IaServiceImpl`. Nenhum estado persistido muda.

## Open Questions

- Confirmar se percentuais LTHR padrão (Friel) são os adotados pela assessoria, ou se há customização por atleta desejada no futuro.
- Definir se a correção automática de FC deve gerar um campo de auditoria visível ao treinador (fora do escopo desta mudança).
