# Análise e Sugestões de Melhorias - Prompt de Treinos de Corrida

## Resumo Executivo

O prompt atual é **bem estruturado** e cobre a maioria dos aspectos importantes para geração de treinos individualizados. Porém, há oportunidades significativas de melhoria em **precisão fisiológica**, **personalização avançada** e **estrutura organizacional**.

---

# 🚨 DIAGNÓSTICO: POR QUE A IA NÃO ANALISA TODOS OS DADOS

## Problemas Estruturais Identificados

### 1. **PROMPT MUITO LONGO (730 linhas) - Perda de Atenção**

**Problema:** LLMs têm uma "janela de atenção" - quanto mais longo o prompt, menor a probabilidade de processar todas as informações com igual importância.

**Evidência:**
- Linhas 1-230: Dados do atleta e histórico
- Linhas 231-730: Regras e formato de saída

A IA pode estar "saltando" para as regras e ignorando os dados.

**Solução:**
```
ESTRUTURA RECOMENDADA (ordem de importância):

1. [CRÍTICO] RESUMO EXECUTIVO (10 linhas máximo)
   - O que a IA DEVE fazer esta semana
   - Alertas que NÃO podem ser ignorados

2. [DADOS] Informações do atleta (compactas)

3. [REGRAS] Como gerar (mais curto possível)

4. [FORMATO] JSON esperado
```

---

### 2. **DADOS APRESENTADOS FORA DE ORDEM LÓGICA**

**Problema:** Os dados estão espalhados e repetidos em várias seções:

| Dado | Aparece em |
|------|------------|
| TSB -9.8 | Linha 67, 178, 237 |
| 5 semanas progressão | Linha 74, 96 |
| Dias disponíveis | Linha 10, 88 |
| Volume médio | Linha 101, 138 |

**Impacto:** A IA pode processar uma versão e ignorar outra, ou se confundir com redundância.

**Solução:** Consolidar em UMA única seção de dados, sem repetição.

---

### 3. **ALERTAS DILUÍDOS NO TEXTO**

**Problema:** Alertas críticos estão "enterrados" no meio do prompt:

```
Linha 62: **Recomendação:** Considerar semana regenerativa
Linha 78: 🟡 **PONTOS DE ATENÇÃO:** 5 semanas de progressão
Linha 133: 🔴 Mais de 50% dos treinos com RPE ≥8
Linha 162: 🔴 TEMPO_RUN: NUNCA realizado
Linha 163: 🔴 FARTLEK: ausente há 24 dias
```

**Impacto:** A IA pode não "ver" esses alertas como prioridade máxima.

**Solução:** Criar seção **NO TOPO** com alertas obrigatórios:

```markdown
## ⛔ ALERTAS OBRIGATÓRIOS (LEIA PRIMEIRO)

1. 🔴 FADIGA ALTA: RPE médio 7.1, >50% treinos RPE≥8 → REDUZIR INTENSIDADE
2. 🔴 FARTLEK ausente há 24 dias → INCLUIR ESTA SEMANA
3. 🔴 TEMPO_RUN nunca realizado → CONSIDERAR INCLUSÃO
4. 🟡 5 semanas progressão → SEMANA REGENERATIVA RECOMENDADA
5. 🟡 TSB -9.8 (Fatigado) → PRIORIZAR RECUPERAÇÃO
```

---

### 4. **REGRAS COMPETINDO ENTRE SI (SEM HIERARQUIA CLARA)**

**Problema:** Existem instruções conflitantes:

| Regra A | Regra B | Conflito |
|---------|---------|----------|
| "Incluir FARTLEK" (linha 163) | "Reduzir intensidade" (linha 133) | Fartlek é intenso |
| "TSS Alvo: 100" (linha 234) | "Semana regenerativa -40-50%" (linha 62) | 100 TSS não é regenerativo |
| "Categoria A VO2max" (linha 157) | "RPE alto, reduzir intensidade" (linha 133) | VO2max aumenta RPE |

**Impacto:** A IA escolhe uma regra e ignora as outras.

**Solução:** Criar hierarquia explícita com resolução de conflitos:

```markdown
## HIERARQUIA DE DECISÃO (em caso de conflito)

NÍVEL 1 - SEGURANÇA (sempre vence):
- Se RPE médio > 7.5 → FORÇAR semana leve
- Se TSB < -15 → APENAS Z1-Z2

NÍVEL 2 - RECUPERAÇÃO:
- Se recomendação = regenerativa → REDUZIR volume 40-50%

NÍVEL 3 - VARIABILIDADE:
- Incluir estímulos ausentes (mas em versão LEVE se conflitar com N1/N2)

NÍVEL 4 - OBJETIVO:
- Alinhar com meta do atleta
```

---

### 5. **INSTRUÇÕES "FAÇA ANÁLISE MENTAL" NÃO FUNCIONAM**

**Problema:** Linhas 245-297 pedem que a IA "analise mentalmente" antes de gerar.

```
Linha 254: Antes de gerar o plano, você DEVE analisar mentalmente:
```

**Impacto:** LLMs não "pensam antes de responder" dessa forma. Se você pede JSON puro no final, a IA vai direto para o JSON.

**Solução:** Usar técnica de **Chain of Thought forçado**:

```markdown
## FORMATO DE RESPOSTA

PASSO 1 - ANÁLISE (obrigatório, 5-10 linhas):
```json
{
  "analise": {
    "alertas_processados": ["RPE alto", "Fartlek ausente"],
    "decisao_volume": "Reduzir 40% por fadiga",
    "tipo_semana": "REGENERATIVA",
    "conflitos_resolvidos": "Fartlek incluído mas em versão leve (Z3)"
  }
}
```

PASSO 2 - PLANO:
```json
{ "treinos": [...] }
```
```

---

### 6. **SEÇÃO "METAS PARA ESTA SEMANA" CONTRADIZ OS ALERTAS**

**Problema Crítico:**

```
Linha 62: Considerar semana regenerativa (reduzir volume em 40-50%)
Linha 234: TSS Alvo Semanal: 100 pontos
```

Se a média recente é ~170 TSS (linha 71) e você quer regenerativa (-40%), o alvo deveria ser ~100.
MAS o cálculo mostra CTL 2.2 (muito baixo), o que indica inconsistência nos dados.

**Impacto:** A IA não sabe se deve seguir o "TSS Alvo calculado" ou a "recomendação de regenerativa".

**Solução:** O sistema que gera o prompt deve:
1. Calcular o TSS Alvo CONSIDERANDO a recomendação
2. Ou remover a recomendação se o TSS Alvo já está ajustado
3. Deixar explícito: "TSS Alvo = 100 (já inclui redução de 40% por recomendação regenerativa)"

---

### 7. **DADOS FISIOLÓGICOS INVÁLIDOS/ZERADOS**

**Problema:** Os dados de entrada estão corrompidos:

```
Linha 32: Pace Limiar: nu min/km  ← INVÁLIDO
Linha 37-41: Zonas com 0,00-0,00 min/km ← INUTILIZÁVEIS
Linha 48: TSS total: 0 pontos ← ZERADO
```

**Impacto:** A IA não pode usar zonas de pace, então ignora ou inventa valores.

**Solução:** Adicionar fallback explícito no prompt:

```markdown
## FALLBACK PARA DADOS INCOMPLETOS

⚠️ Zonas de pace estão zeradas. USE APENAS FC para prescrição.
⚠️ Pace Limiar inválido. Estimar por nível INTERMEDIARIO: ~5:30-6:00/km

Ao prescrever, usar formato:
- "Z2 (135-151 bpm)" em vez de "Z2 (X:XX min/km, 135 bpm)"
```

---

### 8. **FORMATO DE DURAÇÃO INCONSISTENTE**

**Problema:**
```
Linha 52: PT49M30S min  ← ISO 8601 misturado com "min"
Linha 53: PT1H2M28S min ← Confuso
```

**Impacto:** A IA pode não parsear corretamente.

**Solução:** Padronizar para minutos:
```
- 2026-01-18: LONGO - 8,0 km, 50 min, TSS 0 | RPE 9/10
```

---

## RESUMO: CAUSAS RAIZ

| Causa | Impacto | Prioridade |
|-------|---------|------------|
| Prompt muito longo | IA "pula" seções | 🔴 CRÍTICA |
| Alertas diluídos | Não são tratados como prioridade | 🔴 CRÍTICA |
| Regras conflitantes | IA escolhe arbitrariamente | 🔴 CRÍTICA |
| Dados repetidos | Confusão sobre qual usar | 🟡 ALTA |
| Dados zerados/inválidos | IA ignora ou inventa | 🟡 ALTA |
| "Análise mental" não funciona | IA vai direto pro output | 🟡 ALTA |
| Formato inconsistente | Parsing incorreto | 🟢 MÉDIA |

---

## PROPOSTA: PROMPT REESTRUTURADO (RESUMO)

```markdown
# GERADOR DE TREINO SEMANAL

## ⛔ ALERTAS OBRIGATÓRIOS (processe PRIMEIRO)
[5-7 alertas máximo, já priorizados]

## 📊 DADOS DO ATLETA (consolidados)
[Tudo em uma única seção, sem repetição]

## 🎯 META DESTA SEMANA
[Uma linha clara: "Semana REGENERATIVA, TSS 100, máx 3 treinos"]

## 📋 REGRAS (compactas)
[Apenas o essencial, hierarquia clara]

## 📤 FORMATO DE SAÍDA
[JSON schema]
```

**Redução estimada:** De 730 linhas para ~250 linhas (65% menor)

---

# ✅ ANÁLISE DO PROMPT MELHORADO (prompt-melhorado.md)

## Comparativo: O que foi implementado

| Sugestão Original | Status | Observação |
|-------------------|--------|------------|
| Alertas no topo | ✅ IMPLEMENTADO | Linhas 23-29 - Seção "⛔ ALERTAS OBRIGATÓRIOS" |
| Hierarquia de decisão | ✅ IMPLEMENTADO | Linhas 31-51 - 4 níveis claros |
| Seção de lesões/restrições | ✅ IMPLEMENTADO | Linhas 52-54 |
| Fallback para dados incompletos | ✅ IMPLEMENTADO | Linhas 77-88 |
| TSS Alvo ajustado | ✅ IMPLEMENTADO | Linha 283: "55 pontos (reduzido 45% por semana regenerativa)" |
| Tipo de semana explícito | ✅ IMPLEMENTADO | Linha 281: "REGENERATIVA (redução de carga)" |

## O que MELHOROU significativamente

### 1. ✅ Alertas Consolidados no Topo
**Antes:** Alertas espalhados nas linhas 62, 78, 133, 162, 163
**Agora:** Consolidados nas linhas 23-29

```markdown
## ⛔ ALERTAS OBRIGATÓRIOS (PROCESSE PRIMEIRO)
1. 🔴 FARTLEK: ausente há 25 dias → REINTRODUZIR ESTA SEMANA
2. 🟡 TEMPO_RUN: NUNCA realizado → CONSIDERAR INCLUSÃO
3. 🟡 6 semanas de progressão → SEMANA REGENERATIVA RECOMENDADA
```

**Impacto:** IA agora vê alertas PRIMEIRO.

### 2. ✅ Hierarquia de Decisão Clara
**Antes:** Regras conflitantes sem prioridade
**Agora:** 4 níveis explícitos (linhas 31-51)

```markdown
NÍVEL 1 - SEGURANÇA (sempre vence)
NÍVEL 2 - RECUPERAÇÃO
NÍVEL 3 - VARIABILIDADE
NÍVEL 4 - OBJETIVO
```

**Impacto:** Conflitos agora têm resolução clara.

### 3. ✅ TSS Alvo com Justificativa
**Antes:** "TSS Alvo: 100" (sem explicar o porquê)
**Agora:** "TSS Alvo: 55 pontos (reduzido 45% por semana regenerativa)"

**Impacto:** IA entende que o valor já considera a redução.

### 4. ✅ Tipo de Semana Explícito
**Antes:** Implícito, a IA tinha que inferir
**Agora:** "**Tipo de Semana:** REGENERATIVA (redução de carga)"

**Impacto:** Não há ambiguidade sobre a natureza da semana.

### 5. ✅ Fallback para Dados Incompletos
**Antes:** Não existia
**Agora:** Seção dedicada (linhas 77-88)

```markdown
⚠️ **Pace Limiar inválido/zerado** → Usar estimativa por nível
- Pace estimado: 5:30-6:00 min/km
- **USAR APENAS FC para prescrição, não pace
```

**Impacto:** IA sabe como proceder quando dados estão zerados.

---

## ⚠️ PROBLEMAS QUE AINDA PERSISTEM

### 1. 🔴 PROMPT AINDA MUITO LONGO (779 linhas)

**Problema:** O prompt melhorado tem 779 linhas vs 730 do original.
- Adicionou seções úteis (+49 linhas)
- Mas NÃO removeu redundâncias

**Impacto:** A "perda de atenção" da IA ainda é um risco.

**Solução necessária:** Remover/condensar seções redundantes:
- Linhas 294-347: "ANÁLISE OBRIGATÓRIA PRÉ-PLANEJAMENTO" - Redundante com hierarquia
- Linhas 348-375: "PRIORIZAÇÃO POR OBJETIVO" - Já coberto na hierarquia
- Muitas regras repetidas entre seções

---

### 2. 🔴 DADOS AINDA REPETIDOS

| Dado | Aparece em (linhas) |
|------|---------------------|
| 6 semanas progressão | 27, 40, 104, 109, 141 |
| Dias disponíveis | 10, 133 |
| TSB -9.8 | 97, 224, 285 |
| Volume médio | 101, 146-147, 182-187 |

**Impacto:** IA ainda pode processar versões diferentes.

**Solução:** Consolidar em UMA seção de dados.

---

### 3. ✅ ALERTA DE RPE ALTO - CORRIGIDO

**Problema Original:** O alerta de RPE alto não estava aparecendo nos alertas obrigatórios.

**Causa Raiz:** Bug no código - o loop que imprimia os alertas estava ANTES de popular a lista.

**Correção Aplicada em `PlanoTreinoPromptBuilder.java`:**
1. Movido o bloco de impressão para DEPOIS de popular a lista de alertas
2. Adicionado alerta 🔴 para >50% dos treinos com RPE ≥8

```java
// Alerta crítico: >50% dos treinos com RPE ≥8
if (percentualRpeAlto > 50) {
    alertas.add("🔴 FADIGA ALTA: X% dos treinos com RPE ≥8 → REDUZIR INTENSIDADE");
}
```

**Status:** ✅ IMPLEMENTADO

---

### 4. 🟡 FORMATO DE DURAÇÃO AINDA INCONSISTENTE

**Problema:** Linhas 123-127 ainda usam formato ISO 8601:
```
- 2026-01-18: LONGO - 8,0 km, PT49M30S min, TSS 0 | RPE 9/10
```

**Solução:** Converter para minutos simples:
```
- 2026-01-18: LONGO - 8,0 km, 50 min, TSS 0 | RPE 9/10
```

---

### 5. 🟡 ZONAS DE PACE AINDA ZERADAS

**Problema:** Linhas 70-74 ainda mostram:
```
- Z1 (Recuperação): 0,00-0,00 min/km | 117-117 bpm
```

**Observação:** O fallback foi adicionado (linhas 77-88), mas os dados originais ainda estão lá. A IA pode se confundir.

**Solução:** Quando pace está zerado, OMITIR o pace da zona:
```
- Z1 (Recuperação): 117 bpm (50-60% FCmax)
- Z2 (Aeróbico): 135 bpm (60-70% FCmax)
```

---

### 6. 🟡 "ANÁLISE MENTAL" AINDA PRESENTE

**Problema:** Linha 303:
```
Antes de gerar o plano, você DEVE analisar mentalmente:
```

Esta técnica não funciona bem com LLMs que retornam JSON puro.

**Solução:** Remover esta seção OU forçar Chain of Thought no JSON:
```json
{
  "analise_previa": {
    "alertas_processados": [...],
    "tipo_semana": "REGENERATIVA",
    "decisao_volume": "Reduzir 45%"
  },
  "treinos": [...]
}
```

---

### 7. 🟢 CONFLITO RESIDUAL: FARTLEK vs SEMANA REGENERATIVA

**Contexto:**
- Alerta: "FARTLEK ausente há 25 dias → REINTRODUZIR"
- Tipo de semana: REGENERATIVA

**Problema:** Fartlek tradicional é intenso (Z3-Z5).

**A hierarquia resolve isso?** Parcialmente. Linha 46 diz:
```
Se conflitar com N1/N2 → usar versão LEVE do estímulo
```

**Sugestão de melhoria:** Ser mais explícito:
```markdown
## ⛔ ALERTAS OBRIGATÓRIOS
1. 🔴 FARTLEK ausente há 25 dias → INCLUIR versão LEVE (Z2-Z3, não Z4-Z5)
```

---

## 📊 RESUMO COMPARATIVO

| Aspecto | Antes | Depois | Melhoria |
|---------|-------|--------|----------|
| Alertas no topo | ❌ | ✅ | +++ |
| Hierarquia de decisão | ❌ | ✅ | +++ |
| Fallback dados | ❌ | ✅ | ++ |
| TSS justificado | ❌ | ✅ | ++ |
| Tipo semana explícito | ❌ | ✅ | ++ |
| Tamanho do prompt | 730 linhas | 779 linhas | - |
| Dados repetidos | Sim | Sim | = |
| Formato duração | ISO | ISO | = |
| RPE no topo | ❌ | ❌ | = |

---

## 🎯 PRÓXIMOS PASSOS RECOMENDADOS

### Prioridade Alta (fazer agora):
1. **Adicionar alerta de RPE alto** na seção de alertas obrigatórios
2. **Condensar o prompt** removendo seções redundantes (meta: <500 linhas)
3. **Converter durações** de ISO 8601 para minutos simples

### Prioridade Média (fazer depois):
4. Consolidar dados em uma única seção (remover repetições)
5. Omitir pace das zonas quando zerado (mostrar só FC)
6. Substituir "análise mental" por campo JSON obrigatório

### Prioridade Baixa (opcional):
7. Adicionar exemplo completo de JSON esperado no final
8. Considerar usar Chain of Thought forçado

---

## 1. PONTOS FORTES DO PROMPT ATUAL

### O que já funciona bem:
- Estrutura clara de enums e campos obrigatórios
- Matriz de variabilidade de estímulos bem detalhada
- Regras de expansão de intervalados (etapa por etapa)
- Checklist de validação final
- Priorização por objetivo bem definida

---

## 2. MELHORIAS CRÍTICAS RECOMENDADAS

### 2.1. Falta de Contexto de Lesões e Restrições

**Problema:** O prompt não menciona histórico de lesões, limitações físicas ou restrições médicas.

**Impacto:** Risco de prescrever treinos inadequados para atletas com lesões recorrentes ou condições específicas.

**Sugestão:**
```
### RESTRIÇÕES E HISTÓRICO DE SAÚDE
- Lesões recentes (últimos 6 meses): [lista]
- Lesões crônicas/recorrentes: [lista]
- Limitações de movimento: [lista]
- Restrições médicas: [lista]
- Terreno a evitar: [asfalto/trilha/esteira]
- Condições climáticas limitantes: [calor extremo/frio/altitude]

**Regras de Segurança por Lesão:**
- Se houver lesão ativa → máximo Z2, sem intervalados
- Se houver histórico de canelite → evitar superfícies duras, limitar volume
- Se houver fascite plantar → aquecimento estendido, evitar sprints
```

---

### 2.2. Ausência de Dados de Corrida (Running Dynamics)

**Problema:** O prompt usa apenas FC e pace, ignorando métricas modernas de relógios GPS.

**Sugestão:** Adicionar seção de métricas de corrida:
```
### MÉTRICAS DE CORRIDA (Running Dynamics)
- Cadência média: [passos/min]
- Tempo de contato com solo: [ms]
- Oscilação vertical: [cm]
- Rácio vertical: [%]
- Potência de corrida: [watts] (se disponível)
- Running Power CP (Critical Power): [watts]

**Uso das métricas:**
- Cadência < 170 ppm → incluir drills de cadência no aquecimento
- Oscilação > 10cm → trabalho de economia de corrida
- Tempo de contato > 250ms → foco em strides e técnica
```

---

### 2.3. Zonas de Treino com Valores Zerados

**Problema Detectado no Exemplo:**
```
- Z1 (Recuperação): 0,00-0,00 min/km | 117-117 bpm
- Z2 (Aeróbico): 0,00-0,00 min/km | 135-135 bpm
```

Os valores de pace estão zerados, tornando as zonas inutilizáveis.

**Sugestão:** Adicionar lógica de fallback:
```
### FALLBACK PARA ZONAS INCOMPLETAS

Se zonas de pace estiverem zeradas ou incompletas:

1. **Usar FC como referência primária** (sempre disponível se FCmax conhecida)
2. **Estimar pace por nível do atleta:**
   - INICIANTE: Z2 = 7:00-8:00 min/km
   - INTERMEDIARIO: Z2 = 5:30-6:30 min/km
   - AVANCADO: Z2 = 4:30-5:30 min/km

3. **Recomendar teste de limiar** na justificativa:
   "Zonas de pace estimadas - recomenda-se teste de limiar para precisão"
```

---

### 2.4. Falta de Ajuste por Condições Externas

**Problema:** O prompt não considera fatores ambientais que afetam performance.

**Sugestão:**
```
### AJUSTES POR CONDIÇÕES EXTERNAS

**Temperatura:**
- > 25°C: reduzir intensidade em 5-10%, aumentar hidratação
- > 30°C: apenas Z1-Z2, evitar intervalados
- < 5°C: aquecimento estendido (+5min), evitar sprints iniciais

**Altitude:**
- 1000-2000m: reduzir volume em 10-15%, FC alvo +5-10 bpm
- > 2000m: primeira semana apenas Z1-Z2 (aclimatação)

**Umidade > 80%:**
- Reduzir intensidade percebida, monitorar FC mais de perto

**Horário de Treino:**
- Manhã cedo: aquecimento estendido
- Meio-dia (calor): evitar intervalados intensos
- Noite: considerar impacto no sono após treinos Z4-Z5
```

---

### 2.5. Ausência de Nutrição/Estratégia de Abastecimento

**Problema:** Para treinos longos (>90min), não há orientação de nutrição durante treino.

**Sugestão:**
```
### ESTRATÉGIA DE NUTRIÇÃO PARA TREINOS LONGOS

**Para treinos > 60 minutos:**
- Indicar necessidade de hidratação a cada 20-30min
- Sugerir consumo de 30-60g CHO/hora após 60min

**Incluir no campo de descrição do treino longo:**
- "Treinar estratégia de abastecimento para prova"
- "Testar gel/isotônico que usará na prova"
- "Hidratação a cada 4km ou 20min"

**Para longos com simulação de prova:**
- Praticar exatamente o protocolo de prova
- Mesmos produtos, mesmo timing
```

---

### 2.6. Falta de Progressão de Longo Prazo

**Problema:** O prompt foca apenas na semana atual, sem visão de mesociclo.

**Sugestão:**
```
### CONTEXTO DE MESOCICLO (4 semanas)

**Semana atual no ciclo:** [1, 2, 3 ou 4]
- Semana 1: Base/Introdução (70% da carga máxima)
- Semana 2: Desenvolvimento (85% da carga)
- Semana 3: Pico (100% da carga)
- Semana 4: Regeneração/Deload (50-60% da carga)

**Ajuste automático:**
- Se semana 4 → forçar volume reduzido (não sobrescrever com progressão)
- Se semana 3 → permitir treino-chave mais desafiador
- Se semana 1 → foco em consistência, não intensidade
```

---

### 2.7. RPE Subjetivo vs. Objetivo

**Problema:** O prompt usa RPE como métrica, mas não cruza com dados objetivos.

**Sugestão:**
```
### ANÁLISE DE COERÊNCIA RPE vs. DADOS OBJETIVOS

**Detectar descompasso:**
- RPE alto (≥8) + FC baixa + pace lento = possível fadiga acumulada/overtraining
- RPE baixo (≤5) + FC alta = possível desidratação ou estresse externo
- RPE consistentemente alto = necessidade de semana regenerativa

**Alertas automáticos:**
- Se RPE médio > 7.5 nas últimas 2 semanas → FORÇAR semana mais leve
- Se RPE subindo mas performance caindo → sinais de overreaching
```

---

### 2.8. Melhoria na Estrutura de Priorização

**Problema:** A lista de prioridades não tem pesos claros para conflitos.

**Sugestão:** Criar hierarquia explícita:
```
### HIERARQUIA DE DECISÃO (em caso de conflito)

1. **SEGURANÇA** (peso 10) - Nunca comprometer
   - Não exceder dias consecutivos máximos
   - Respeitar sinais de lesão/fadiga extrema
   - TSB < -20 = semana obrigatoriamente regenerativa

2. **RECUPERAÇÃO** (peso 8)
   - TSB negativo = priorizar recuperação sobre performance
   - RPE médio > 8 = reduzir volume

3. **OBJETIVO PRINCIPAL** (peso 6)
   - Treino-chave alinhado com meta

4. **VARIABILIDADE** (peso 4)
   - Alternar estímulos

5. **PREFERÊNCIAS** (peso 2)
   - Dias e horários preferidos
```

---

### 2.9. Dados de Sono e HRV

**Problema:** O prompt não considera dados de recuperação além de TSB.

**Sugestão:**
```
### MÉTRICAS DE RECUPERAÇÃO DIÁRIA (se disponível)

**HRV (Variabilidade da Frequência Cardíaca):**
- HRV média (7 dias): [ms]
- HRV hoje: [ms]
- Tendência: [acima/abaixo/na média]

**Qualidade de Sono:**
- Horas dormidas (média 7 dias): [h]
- Score de sono (se disponível): [0-100]

**Regras de ajuste:**
- HRV < 80% da média pessoal → reduzir intensidade do dia
- Sono < 6h → apenas Z1-Z2
- HRV em queda por 3+ dias → sinal de overreaching
```

---

### 2.10. Feedback Loop (Aprendizado do Sistema)

**Problema:** O prompt não menciona como usar feedback de treinos anteriores.

**Sugestão:**
```
### APRENDIZADO COM EXECUÇÕES ANTERIORES

**Padrões a detectar:**
- Treinos consistentemente subexecutados (distância real < planejada)
  → Reduzir volume planejado em 10-15%

- Treinos sempre superados (atleta faz mais que o pedido)
  → Possivelmente subestimando capacidade

- Determinado tipo de treino sempre cancelado
  → Verificar se é preferência ou dificuldade logística

**Feedback específico:**
- Se intervalado sempre com RPE > planejado → reduzir número de tiros ou pace
- Se longo sempre interrompido → verificar nutrição/hidratação
- Se regenerativo com RPE alto → atleta não sabe fazer Z1 (educá-lo)
```

---

## 3. MELHORIAS DE ESTRUTURA E ORGANIZAÇÃO

### 3.1. Reorganizar Seções por Fluxo Lógico

**Ordem atual:** Mistura perfil, histórico, métricas, regras, formato.

**Ordem sugerida:**
```
1. CONTEXTO DO ATLETA
   - Perfil básico
   - Restrições e saúde
   - Preferências

2. ESTADO ATUAL
   - Métricas fisiológicas e zonas
   - Métricas de fadiga (CTL, ATL, TSB)
   - Métricas de recuperação (HRV, sono)

3. HISTÓRICO RECENTE
   - Últimos treinos
   - Padrões detectados
   - Alertas ativos

4. CONTEXTO DE PLANEJAMENTO
   - Fase de periodização
   - Semana no mesociclo
   - Prova alvo (se houver)

5. METAS CALCULADAS
   - TSS alvo
   - Volume alvo
   - Limites de segurança

6. REGRAS DE GERAÇÃO
   - Prioridades
   - Matriz de variabilidade
   - Estrutura de etapas

7. FORMATO DE SAÍDA
   - JSON schema
   - Validações
```

---

### 3.2. Adicionar Exemplos Concretos

**Problema:** Falta um exemplo completo de JSON esperado.

**Sugestão:** Adicionar no final:
```
### EXEMPLO DE OUTPUT ESPERADO

{
  "treinos": [
    {
      "diaSemana": "TERCA",
      "tipoTreino": "INTERVALADO",
      "fcAlvo": "85-95% FCmax",
      "tssPlanejado": 55,
      "intensidadePlanejada": 1.1,
      "percepcaoEsforcoEsperada": 8,
      "justificativaIa": "Treino de VO2max para melhorar capacidade aeróbica máxima, alinhado com objetivo de reduzir tempo na meia maratona. TSB atual (-10) permite estímulo intenso.",
      "duracaoMin": 45,
      "distanciaKm": 8.5,
      "ritmoAlvo": "4:30-5:00/km",
      "etapas": [
        {
          "ordem": 1,
          "tipoEtapa": "AQUECIMENTO",
          "descricaoEtapa": "Corrida progressiva Z1-Z2 + 3x30s acelerações",
          "duracaoMin": 10,
          "distanciaKm": 1.5,
          "fcAlvoEtapa": "60-70% FCmax",
          "repeticoes": 1
        },
        // ... demais etapas
      ]
    }
  ]
}
```

---

## 4. MELHORIAS TÉCNICAS ESPECÍFICAS

### 4.1. Cálculo de TSS Mais Preciso

**Problema atual:** TSS aparece zerado em vários treinos do histórico.

**Sugestão:** Adicionar fórmula de estimativa:
```
### ESTIMATIVA DE TSS (quando não disponível)

Se TSS não estiver disponível, estimar por:

TSS_estimado = (duração_min × IF² × 100) / 60

Onde IF (Intensity Factor) por tipo:
- REGENERATIVO: 0.55-0.65
- CONTINUO Z2: 0.70-0.80
- CONTINUO Z3: 0.80-0.90
- TEMPO_RUN: 0.90-1.00
- INTERVALADO: 0.85-1.05 (média ponderada)
- LONGO: 0.65-0.85 (depende da estrutura)
```

---

### 4.2. Duração em Formato ISO

**Problema:** Duração aparece como `PT49M30S` (ISO 8601) misturado com minutos.

**Sugestão:** Padronizar:
```
### FORMATO DE DURAÇÃO

ENTRADA: Aceitar ISO 8601 (PT1H30M) ou minutos (90)
SAÍDA: Sempre em minutos (number)

Conversão: PT1H30M → 90 minutos
```

---

### 4.3. Validação de Pace Limiar

**Problema:** `Pace Limiar: nu min/km` (valor inválido "nu")

**Sugestão:**
```
### VALIDAÇÃO DE DADOS DE ENTRADA

Se pace limiar = "nu", null, ou inválido:
1. Não usar pace como referência
2. Usar apenas FC para prescrição
3. Adicionar na justificativa: "Pace estimado - recomenda-se teste de limiar"
4. Usar estimativa conservadora baseada no nível:
   - INICIANTE: ~6:30-7:00 min/km
   - INTERMEDIARIO: ~5:30-6:00 min/km
   - AVANCADO: ~4:30-5:00 min/km
```

---

## 5. SUGESTÕES ADICIONAIS

### 5.1. Adicionar Campo de "Foco Técnico"

```
### FOCO TÉCNICO DA SEMANA

Além do treino físico, incluir um foco técnico rotativo:
- Semana 1: Cadência (manter 175-180 ppm)
- Semana 2: Postura (olhar horizonte, ombros relaxados)
- Semana 3: Respiração (ritmo 3:2 ou 2:2)
- Semana 4: Economia (menor oscilação vertical)

Incluir no campo de descrição quando relevante.
```

### 5.2. Adicionar Treinos Complementares

```
### TREINOS COMPLEMENTARES (opcionais)

Se atleta tem 4+ dias disponíveis, sugerir:
- Core/Fortalecimento: 2x por semana, 15-20min
- Mobilidade: pós-treino intenso
- Yoga/Alongamento: dias de regenerativo

Formato: campo adicional "complementar" no JSON
```

### 5.3. Alertas de Semana de Prova

```
### LÓGICA DE TAPER (semanas pré-prova)

Se prova em < 14 dias:
- Semana -2: reduzir volume 20-30%, manter intensidade
- Semana -1: reduzir volume 40-50%, treinos curtos e rápidos
- Semana da prova: apenas shakeout run (20-30min Z2 + strides)

Incluir campo "fasePreProva": true/false
```

---

## 6. RESUMO DAS PRIORIDADES DE IMPLEMENTAÇÃO

| Prioridade | Melhoria | Impacto |
|------------|----------|---------|
| 🔴 Alta | Fallback para zonas zeradas | Crítico - impede treinos |
| 🔴 Alta | Histórico de lesões/restrições | Segurança |
| 🟡 Média | Métricas de recuperação (HRV/sono) | Precisão |
| 🟡 Média | Contexto de mesociclo | Periodização |
| 🟡 Média | Ajustes por condições externas | Realismo |
| 🟢 Baixa | Running dynamics | Otimização |
| 🟢 Baixa | Nutrição para longos | Completude |
| 🟢 Baixa | Foco técnico semanal | Valor agregado |

---

## 7. CONCLUSÃO

O prompt atual é **sólido e bem estruturado**, mas pode evoluir de um sistema de **prescrição baseada em regras** para um sistema de **prescrição verdadeiramente individualizada** incorporando:

1. **Mais dados de entrada** (lesões, HRV, sono, condições externas)
2. **Fallbacks inteligentes** para dados incompletos
3. **Visão de médio prazo** (mesociclo, não apenas semana)
4. **Feedback loop** (aprender com execuções anteriores)
5. **Segurança reforçada** (hierarquia clara de prioridades)

Estas melhorias transformariam o sistema de um "gerador de treinos genéricos com personalização" para um "treinador virtual adaptativo".
