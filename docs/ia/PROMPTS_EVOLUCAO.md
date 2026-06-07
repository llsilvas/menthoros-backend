# Evolução dos Prompts de IA - Menthoros

> **Nota:** Este documento consolida três arquivos originais documentando a evolução do prompt de geração de planos de treino para corredores:
> - `prompt-atual.md` - Versão inicial com 730 linhas
> - `sugestoes-melhorias-prompt.md` - Análise crítica e recomendações
> - `prompt-melhorado.md` - Versão refinada com 779 linhas

---

## 1. Estrutura Geral de Evolução

### Arquivo 1: Prompt Atual (Baseline)
O prompt original é bem estruturado mas apresenta 730 linhas com redundâncias e prompts diluídos ao longo do documento. Cobre aspectos importantes para geração de treinos individualizados, mas com oportunidades de otimização.

### Arquivo 2: Análise e Sugestões de Melhorias
Diagnóstico detalhado dos problemas estruturais e propostas de solução.

### Arquivo 3: Prompt Melhorado
Implementação das principais recomendações (779 linhas) com mudanças na organização e clareza.

---

## 2. Diagnóstico: Problemas Estruturais Identificados

### 2.1 Prompt Muito Longo (Perda de Atenção da IA)

**Problema:** LLMs têm uma "janela de atenção" - quanto mais longo o prompt, menor a probabilidade de processar todas as informações com igual importância.

- Linhas 1-230: Dados do atleta e histórico
- Linhas 231-730: Regras e formato de saída

A IA pode estar "saltando" para as regras e ignorando os dados.

### 2.2 Dados Apresentados Fora de Ordem Lógica

**Problema:** Os dados estão espalhados e repetidos em várias seções:

| Dado | Aparece em |
|------|------------|
| TSB -9.8 | Linha 67, 178, 237 |
| 5-6 semanas progressão | Linha 74, 96, 104, 109, 141 |
| Dias disponíveis | Linha 10, 88, 133 |
| Volume médio | Linha 101, 138, 146-147, 182-187 |

**Impacto:** A IA pode processar uma versão e ignorar outra, ou se confundir com redundância.

### 2.3 Alertas Diluídos no Texto

**Problema:** Alertas críticos estão "enterrados" no meio do prompt:

```
Linha 62: Recomendação: Considerar semana regenerativa
Linha 78: 🟡 PONTOS DE ATENÇÃO: 5 semanas de progressão
Linha 133: 🔴 Mais de 50% dos treinos com RPE ≥8
Linha 162: 🔴 TEMPO_RUN: NUNCA realizado
Linha 163: 🔴 FARTLEK: ausente há 24 dias
```

**Impacto:** A IA pode não "ver" esses alertas como prioridade máxima.

### 2.4 Regras Competindo Entre Si (Sem Hierarquia Clara)

**Problema:** Existem instruções conflitantes:

| Regra A | Regra B | Conflito |
|---------|---------|----------|
| "Incluir FARTLEK" | "Reduzir intensidade" | Fartlek é intenso |
| "TSS Alvo: 100" | "Semana regenerativa -40-50%" | 100 TSS não é regenerativo |
| "Categoria A VO2max" | "RPE alto, reduzir intensidade" | VO2max aumenta RPE |

**Impacto:** A IA escolhe uma regra e ignora as outras.

### 2.5 Instruções "Faça Análise Mental" Não Funcionam

**Problema:** O prompt pede que a IA "analise mentalmente" antes de gerar.

**Impacto:** LLMs não "pensam antes de responder" dessa forma. Se você pede JSON puro no final, a IA vai direto para o JSON.

### 2.6 Seção "Metas Para Esta Semana" Contradiz os Alertas

**Problema Crítico:**
```
Linha 62: Considerar semana regenerativa (reduzir volume em 40-50%)
Linha 234: TSS Alvo Semanal: 100 pontos
```

Se a média recente é ~170 TSS e você quer regenerativa (-40%), o alvo deveria ser ~100. Mas o cálculo mostra CTL 2.2 (muito baixo).

**Impacto:** A IA não sabe se deve seguir o "TSS Alvo calculado" ou a "recomendação de regenerativa".

### 2.7 Dados Fisiológicos Inválidos/Zerados

**Problema:** Os dados de entrada estão corrompidos:

```
Pace Limiar: nu min/km  ← INVÁLIDO
Zonas com 0,00-0,00 min/km ← INUTILIZÁVEIS
TSS total: 0 pontos ← ZERADO
```

**Impacto:** A IA não pode usar zonas de pace, então ignora ou inventa valores.

### 2.8 Formato de Duração Inconsistente

**Problema:**
```
PT49M30S min  ← ISO 8601 misturado com "min"
PT1H2M28S min ← Confuso
```

**Impacto:** A IA pode não parsear corretamente.

---

## 3. Resumo: Causas Raiz

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

## 4. Melhorias Implementadas no Prompt Melhorado

### 4.1 ✅ Alertas Consolidados no Topo

**Antes:** Alertas espalhados nas linhas 62, 78, 133, 162, 163
**Agora:** Consolidados no topo com hierarquia clara

```markdown
## ⛔ ALERTAS OBRIGATÓRIOS (PROCESSE PRIMEIRO)

1. 🔴 FARTLEK: ausente há 25 dias → REINTRODUZIR ESTA SEMANA
2. 🟡 TEMPO_RUN: NUNCA realizado → CONSIDERAR INCLUSÃO
3. 🟡 6 semanas de progressão → SEMANA REGENERATIVA RECOMENDADA
```

**Impacto:** IA agora vê alertas PRIMEIRO.

### 4.2 ✅ Hierarquia de Decisão Clara

**Antes:** Regras conflitantes sem prioridade
**Agora:** 4 níveis explícitos

```markdown
## 🎯 HIERARQUIA DE DECISÃO (resolver conflitos nesta ordem)

NÍVEL 1 - SEGURANÇA (sempre vence):
- Se RPE médio > 7.5 → FORÇAR semana leve
- Se TSB < -25 → APENAS Z1-Z2, máximo 3 treinos

NÍVEL 2 - RECUPERAÇÃO:
- 6 semanas progressão → semana regenerativa (-40-50% volume)
- Se recomendação = regenerativa → REDUZIR volume 40-50%

NÍVEL 3 - VARIABILIDADE:
- Incluir estímulos ausentes há >14 dias
- Alternar categorias de intervalado (não repetir 2 semanas seguidas)
- Se conflitar com N1/N2 → usar versão LEVE do estímulo

NÍVEL 4 - OBJETIVO:
- Alinhar treino-chave com meta do atleta
- Respeitar fase de periodização (BASE/BUILD/ESPECÍFICO/TAPER)
```

**Impacto:** Conflitos agora têm resolução clara.

### 4.3 ✅ TSS Alvo com Justificativa

**Antes:** "TSS Alvo: 100" (sem explicar o porquê)
**Agora:** "TSS Alvo: 55 pontos (reduzido 45% por semana regenerativa)"

**Impacto:** IA entende que o valor já considera a redução.

### 4.4 ✅ Tipo de Semana Explícito

**Antes:** Implícito, a IA tinha que inferir
**Agora:** "Tipo de Semana: REGENERATIVA (redução de carga)"

**Impacto:** Não há ambiguidade sobre a natureza da semana.

### 4.5 ✅ Fallback para Dados Incompletos

**Antes:** Não existia
**Agora:** Seção dedicada

```markdown
## ⚠️ FALLBACKS PARA DADOS INCOMPLETOS

⚠️ **Pace Limiar inválido/zerado** → Usar estimativa por nível
- Pace estimado: 5:30-6:00 min/km
- **USAR APENAS FC para prescrição, não pace

⚠️ **Zonas de pace inutilizáveis** → Usar apenas FC (Z1-Z5 por percentual FCmax)

**Ao prescrever treinos:**
- Se pace inválido: usar formato "Z2 (140-160 bpm)" em vez de incluir pace
- Se FC disponível: priorizar FC sobre pace estimado
- Adicionar na justificativa: "Zonas estimadas - recomenda-se teste de limiar"
```

**Impacto:** IA sabe como proceder quando dados estão zerados.

### 4.6 ✅ Seção de Restrições de Saúde

**Antes:** Não havia contexto de lesões
**Agora:** Seção dedicada

```markdown
## 🚨 RESTRIÇÕES E HISTÓRICO DE SAÚDE

✅ Nenhuma lesão ativa ou histórico de lesões reportado
```

**Impacto:** Define parâmetros de segurança claros.

---

## 5. Problemas que Ainda Persistem

### 5.1 🔴 Prompt Ainda Muito Longo

**Problema:** O prompt melhorado tem 779 linhas vs 730 do original.
- Adicionou seções úteis (+49 linhas)
- Mas NÃO removeu redundâncias

**Impacto:** A "perda de atenção" da IA ainda é um risco.

**Solução necessária:** Remover/condensar seções redundantes:
- Linhas 294-347: "ANÁLISE OBRIGATÓRIA PRÉ-PLANEJAMENTO" - Redundante com hierarquia
- Linhas 348-375: "PRIORIZAÇÃO POR OBJETIVO" - Já coberto na hierarquia
- Muitas regras repetidas entre seções

### 5.2 🔴 Dados Ainda Repetidos

| Dado | Aparece em (linhas) |
|------|---------------------|
| 6 semanas progressão | 27, 40, 104, 109, 141 |
| Dias disponíveis | 10, 133 |
| TSB -9.8 | 97, 224, 285 |
| Volume médio | 101, 146-147, 182-187 |

**Impacto:** IA ainda pode processar versões diferentes.

**Solução:** Consolidar em UMA seção de dados.

### 5.3 🟡 Formato de Duração Ainda Inconsistente

**Problema:** Linhas 123-127 ainda usam formato ISO 8601:
```
- 2026-01-18: LONGO - 8,0 km, PT49M30S min, TSS 0 | RPE 9/10
```

**Solução:** Converter para minutos simples:
```
- 2026-01-18: LONGO - 8,0 km, 50 min, TSS 0 | RPE 9/10
```

### 5.4 🟡 Zonas de Pace Ainda Zeradas

**Problema:** Linhas 70-74 ainda mostram:
```
- Z1 (Recuperação): 0,00-0,00 min/km | 117-117 bpm
```

**Observação:** O fallback foi adicionado, mas os dados originais ainda estão lá. A IA pode se confundir.

**Solução:** Quando pace está zerado, OMITIR o pace da zona:
```
- Z1 (Recuperação): 117 bpm (50-60% FCmax)
- Z2 (Aeróbico): 135 bpm (60-70% FCmax)
```

### 5.5 🟡 "Análise Mental" Ainda Presente

**Problema:** O prompt ainda contém instruções pedindo "análise mental".

**Solução:** Remover esta seção OU forçar Chain of Thought no JSON.

### 5.6 🟢 Conflito Residual: FARTLEK vs SEMANA REGENERATIVA

**Contexto:**
- Alerta: "FARTLEK ausente há 25 dias → REINTRODUZIR"
- Tipo de semana: REGENERATIVA

**Problema:** Fartlek tradicional é intenso (Z3-Z5).

**A hierarquia resolve isso?** Parcialmente. Linha 46 diz: "Se conflitar com N1/N2 → usar versão LEVE do estímulo"

**Sugestão de melhoria:** Ser mais explícito:
```markdown
## ⛔ ALERTAS OBRIGATÓRIOS
1. 🔴 FARTLEK ausente há 25 dias → INCLUIR versão LEVE (Z2-Z3, não Z4-Z5)
```

---

## 6. Comparativo: O que Melhorou

| Aspecto | Antes | Depois | Melhoria |
|---------|-------|--------|----------|
| Alertas no topo | ❌ | ✅ | +++ |
| Hierarquia de decisão | ❌ | ✅ | +++ |
| Fallback dados | ❌ | ✅ | ++ |
| TSS justificado | ❌ | ✅ | ++ |
| Tipo semana explícito | ❌ | ✅ | ++ |
| Seção restrições saúde | ❌ | ✅ | ++ |
| Tamanho do prompt | 730 linhas | 779 linhas | - |
| Dados repetidos | Sim | Sim | = |
| Formato duração | ISO | ISO | = |

---

## 7. Recomendações de Melhorias Críticas

### 7.1 Falta de Contexto de Lesões e Restrições

**Problema:** Embora adicionado no melhorado, precisa de mais detalhes.

**Sugestão aprimorada:**
```
### RESTRIÇÕES E HISTÓRICO DE SAÚDE

**Lesões recentes (últimos 6 meses):** [lista]
**Lesões crônicas/recorrentes:** [lista]
**Limitações de movimento:** [lista]
**Restrições médicas:** [lista]
**Terreno a evitar:** [asfalto/trilha/esteira]

**Regras de Segurança por Lesão:**
- Se houver lesão ativa → máximo Z2, sem intervalados
- Se houver histórico de canelite → evitar superfícies duras, limitar volume
- Se houver fascite plantar → aquecimento estendido, evitar sprints
```

### 7.2 Ausência de Dados de Corrida (Running Dynamics)

**Problema:** O prompt usa apenas FC e pace, ignorando métricas modernas.

**Sugestão:** Adicionar seção de métricas de corrida:
```
### MÉTRICAS DE CORRIDA (Running Dynamics)

**Cadência média:** [passos/min]
**Tempo de contato com solo:** [ms]
**Oscilação vertical:** [cm]
**Rácio vertical:** [%]

**Uso das métricas:**
- Cadência < 170 ppm → incluir drills de cadência no aquecimento
- Oscilação > 10cm → trabalho de economia de corrida
- Tempo de contato > 250ms → foco em strides e técnica
```

### 7.3 Falta de Dados de Sono e HRV

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

### 7.4 Ausência de Nutrição/Estratégia de Abastecimento

**Problema:** Para treinos longos (>90min), não há orientação.

**Sugestão:**
```
### ESTRATÉGIA DE NUTRIÇÃO PARA TREINOS LONGOS

**Para treinos > 60 minutos:**
- Indicar necessidade de hidratação a cada 20-30min
- Sugerir consumo de 30-60g CHO/hora após 60min

**Para longos com simulação de prova:**
- Praticar exatamente o protocolo de prova
- Mesmos produtos, mesmo timing
```

### 7.5 Falta de Progressão de Longo Prazo

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
```

### 7.6 Feedback Loop (Aprendizado do Sistema)

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
```

### 7.7 Lógica de Taper (Semanas Pré-Prova)

**Problema:** Nenhuma menção a ajustes para semanas próximas a competições.

**Sugestão:**
```
### LÓGICA DE TAPER (semanas pré-prova)

Se prova em < 14 dias:
- Semana -2: reduzir volume 20-30%, manter intensidade
- Semana -1: reduzir volume 40-50%, treinos curtos e rápidos
- Semana da prova: apenas shakeout run (20-30min Z2 + strides)

Incluir campo "fasePreProva": true/false
```

---

## 8. Próximos Passos Recomendados

### Prioridade Alta (fazer agora):
1. **Adicionar alerta de RPE alto** na seção de alertas obrigatórios
2. **Condensar o prompt** removendo seções redundantes (meta: <500 linhas)
3. **Converter durações** de ISO 8601 para minutos simples

### Prioridade Média (fazer depois):
4. Consolidar dados em uma única seção (remover repetições)
5. Omitir pace das zonas quando zerado (mostrar só FC)
6. Substituir "análise mental" por campo JSON obrigatório
7. Adicionar contexto de mesociclo (semana no ciclo de 4)

### Prioridade Baixa (opcional):
8. Adicionar métricas de running dynamics (cadência, oscilação)
9. Adicionar dados de HRV e qualidade de sono
10. Adicionar foco técnico semanal rotativo
11. Adicionar estratégia de nutrição para longos

---

## 9. Estrutura Recomendada para Próxima Iteração

```markdown
# GERADOR DE TREINO SEMANAL

## ⛔ ALERTAS OBRIGATÓRIOS (processe PRIMEIRO)
[5-7 alertas máximo, já priorizados]

## 🎯 HIERARQUIA DE DECISÃO
[4 níveis claros]

## 📊 DADOS DO ATLETA (consolidados)
[Tudo em uma única seção, sem repetição]

## 🚨 RESTRIÇÕES E SAÚDE
[Lesões, limitações, contracomentações]

## 📈 ESTADO ATUAL
[Métricas fisiológicas, fadiga, recuperação]

## 📋 HISTÓRICO RECENTE
[Últimos treinos, padrões, tendências]

## 🎯 META DESTA SEMANA
[Uma linha clara: "Semana REGENERATIVA, TSS 100, máx 3 treinos"]

## 📋 REGRAS (compactas)
[Apenas o essencial, sem redundâncias]

## 📤 FORMATO DE SAÍDA
[JSON schema com validações]
```

**Redução estimada:** De 779 linhas para ~400 linhas (49% menor)

---

## 10. Conclusão

O prompt evoluiu de uma estrutura **densa e redundante** (730 linhas) para uma **mais organizada** (779 linhas com melhorias), mas ainda há oportunidades de otimização:

### ✅ Implementado com Sucesso:
- Alertas consolidados no topo
- Hierarquia de decisão clara
- Fallbacks para dados incompletos
- Seção de restrições de saúde
- TSS Alvo com justificativa

### ⚠️ Ainda Necessário:
- Reduzir tamanho removendo redundâncias
- Consolidar dados repetidos
- Adicionar contexto de mesociclo
- Melhorar fallbacks para dados zerados

### 🚀 Visão Futura:
Transformar o sistema de um "gerador de treinos genéricos com personalização" para um **"treinador virtual adaptativo"** que considera:
1. Histórico de lesões e restrições
2. Métricas avançadas (HRV, sono, running dynamics)
3. Progressão de médio prazo (mesociclos)
4. Feedback loop (aprendizado com execuções anteriores)
5. Ajustes contextuais (clima, altitude, nutrição)
