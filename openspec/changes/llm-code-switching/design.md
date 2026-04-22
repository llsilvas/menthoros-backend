## Context

O sistema usa dois caminhos de geração de prompt:
1. **Legado** (`buildRequest` + `plano-treino-prompt.txt`) — usado em `gerarPlanoSemanal`.
2. **Otimizado** (`buildOptimizedPrompt` + `plano-treino-otimizado-claude.txt`) — usado em `geraPlanoSemanalAvancado`, o caminho principal em produção.

Ambos os caminhos compõem prompts 100% em português. Os formatters Java (`AlertasPromptFormatter`, `MetricasPromptFormatter`, etc.) também geram seções em português que são injetadas diretamente no corpo do prompt. O `system-prompt.txt` é separado e também 100% em português.

A mudança afeta três camadas:
- **Templates de texto** (`.txt` em `resources/prompts/`) — trocados de PT para EN nos segmentos estruturais.
- **Formatters Java** — cabeçalhos de seção, rótulos e marcadores gerados programaticamente traduzidos para EN.
- **Dados de domínio** (nome, objetivo, observações, justificativas) — permanecem em PT.

## Goals / Non-Goals

**Goals:**
- Instrução do agente (system prompt) em inglês.
- Dados e métricas técnicas de treino em inglês (seções `##`, rótulos, marcadores de status como `[PROIBIDO]`, `[AUTORIZADO]`).
- Input do atleta (nome, objetivo, observações, feedback de treino realizado) em português.
- Chaves JSON em inglês (já são em inglês via DTOs Java); valores descritivos (`justificativaIa`, `descricaoEtapa`) em português.
- Preservar termos esportivos em inglês quando usados dentro de texto em português (e.g., "TSS", "tempo run", "Z2").
- Nenhuma mudança no comportamento funcional ou na lógica de validação pós-LLM.

**Non-Goals:**
- Traduzir dados de entrada do atleta para inglês.
- Alterar DTOs, entidades, repositórios ou a lógica de validação em `IaServiceImpl`.
- Introduzir internacionalização (i18n) ou qualquer mecanismo de troca de idioma em runtime.
- Modificar o sistema de cache ou a integração com Spring AI.

## Decisions

### D1 — Separação por camada, não por arquivo

Cada template `.txt` terá **duas zonas** explícitas:
- **EN zone** (instructions, structure, metrics labels) — primeira parte do template.
- **PT zone** (athlete data placeholder via `%s`) — injetado via `String.format` como antes.

Alternativa considerada: um template separado para cada idioma. Rejeitada por duplicar arquivos e aumentar surface de manutenção sem ganho funcional.

### D2 — Formatters Java traduzem cabeçalhos, não dados

Os métodos dos formatters que retornam `String` serão atualizados para emitir cabeçalhos `##`/`**` em inglês. Os **valores** obtidos das entidades (nome, observação, objetivo) não são tocados.

Alternativa: criar uma constante/enum de rótulos. Desnecessário para este escopo — não há lógica condicional sobre os rótulos.

### D3 — Marcadores de decisão em inglês

A seção `DECISAO INTERVALADO` em `formatarDecisaoIntervalado()` usa marcadores `[PROIBIDO]`, `[DEGRADADO]`, `[AUTORIZADO]`. Estes serão traduzidos para `[FORBIDDEN]`, `[DEGRADED]`, `[AUTHORIZED]` pois são usados como sinais de controle para o LLM, não como saída para o usuário final.

### D4 — Output JSON: chaves EN, valores PT

As chaves já são em inglês (nomes de campo dos records Java). Valores que o LLM preenche livremente (`justificativaIa`, `descricaoEtapa`, `objetivoSemanal`) ficam em PT — o prompt instrui o LLM explicitamente: *"Fill free-text fields in Brazilian Portuguese."*

### D5 — `plano-treino-prompt.txt` (legado)

O caminho legado está desativado em produção (`gerarPlanoSemanal` usa options comentadas e está sendo depreciado). Traduzir as instruções estruturais mas manter como está funcionalmente — sem introduzir riscos ao caminho ativo.

## Risks / Trade-offs

- **[Risk] Testes de formatters assertam strings em PT** → Os testes unitários em `services/prompt/` que verificam saída textual dos formatters precisarão ser atualizados. Mitigação: escopo mapeado; cada formatter tem seu próprio test file.
- **[Risk] LLM pode misturar idiomas nos valores PT se a instrução não for clara** → Mitigação: instrução explícita no system prompt: *"Free-text fields (`justificativaIa`, `descricaoEtapa`, `objetivoSemanal`) must be in Brazilian Portuguese."*
- **[Risk] Termos esportivos em inglês dentro de texto PT podem ser hifenizados ou alterados pelo LLM** → Mitigação: listar os termos na instrução como "preserve as-is in Portuguese text".
- **[Trade-off] Prompt misto pode parecer menos natural para manutenção** → Compensado pela documentação da estratégia no system prompt e no próprio template.

## Migration Plan

1. Atualizar `system-prompt.txt` → EN.
2. Atualizar `plano-treino-otimizado-claude.txt` → estrutura EN, placeholders PT mantidos.
3. Traduzir cabeçalhos/labels dos formatters Java (um formatter por vez, com teste atualizado junto).
4. Traduzir `formatarDecisaoIntervalado()` em `PlanoTreinoPromptBuilder`.
5. Atualizar `plano-treino-prompt.txt` (legado) por último.
6. Rollback: os arquivos `.txt` estão sob controle de versão — revert imediato se assertividade cair.

## Open Questions

- Nenhuma questão em aberto — a estratégia está completamente especificada pela proposta.
