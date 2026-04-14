## Context

O Menthoros gera planos de treino personalizados para atletas de corrida gerenciados por assessorias esportivas. A qualidade da prescrição depende diretamente do **pace de limiar anaeróbico** do atleta — o ritmo sustentável no limite do limiar de lactato. Atualmente esse dado é informado manualmente no cadastro do atleta e nunca é reavaliado sistematicamente.

O **Teste de 3K** é o protocolo dominante no Brasil para aferir o limiar. O atleta percorre 3km em esforço máximo controlado, e o pace médio é usado como referência para calcular todas as zonas de treino (Z1 a Z5). A periodicidade recomendada é trimestral (a cada 3 meses).

A mudança introduz suporte nativo ao teste: prescrição estruturada no plano, captura dos resultados e atualização automática do pace de limiar do atleta.

## Goals / Non-Goals

**Goals:**
- Adicionar `TESTE_LIMIAR` como tipo de treino com estrutura de etapas predefinida (aquecimento → strides → 3K máximo → desaquecimento).
- Capturar o resultado do teste (`tempo3k`, `paceMediaTeste`, `paceLimiarCalculado`, condições) no `TreinoRealizado`.
- Calcular automaticamente o `paceLimiar` do atleta a partir do resultado e persistir no `PlanoMetaDados`.
- Expor histórico de testes do atleta via API para visualização de evolução.
- Permitir que o treinador inclua ou não o Teste de 3K ao gerar o plano semanal — nunca inserção automática.
- Emitir alertas antecipados (proximidade) e de prazo vencido para o próximo teste do atleta.

**Non-Goals:**
- Suporte a outros protocolos de teste (Cooper, Balke, VO2max laboratorial) — fora do escopo desta mudança.
- Integração com dispositivos Garmin/Strava para importação automática do resultado do teste.
- Geração de relatório PDF de evolução do atleta.
- Alteração do modelo de cálculo de TSS existente.

## Decisions

### D1: Onde armazenar os dados específicos do teste no `TreinoRealizado`?

**Decisão:** Adicionar campos opcionais diretamente em `tb_treino_realizado` com prefixo `teste_`.

**Alternativas consideradas:**
- *Tabela separada `tb_resultado_teste_limiar`*: Mais normalizado, mas cria join obrigatório para um caso de uso raro e aumenta complexidade sem ganho real.
- *Campo JSON `metadados_teste`*: Flexível, mas perde tipagem e dificulta queries de histórico/evolução.
- *Campos diretos em `TreinoRealizado`* (escolhido): Simples, tipado, pesquisável. Campos são `NULL` para treinos normais — sem custo para o caminho principal.

### D2: Como calcular o `paceLimiar` a partir do Teste de 3K?

**Decisão:** Usar a fórmula de Jack Daniels adaptada para o contexto brasileiro: `paceLimiar = pace3K * 1.05` (5% mais lento que o pace médio do teste), arredondado para segundos inteiros.

Essa relação (pace de limiar ≈ pace de 3K + 5%) é amplamente usada por treinadores brasileiros e está alinhada com a metodologia de Daniels, que define o pace de limiar entre 86-88% do VO2max, correspondendo aproximadamente ao pace de 5K. O 3K captura um ritmo ligeiramente acima do limiar, justificando o fator de 5%.

**Campos derivados calculados no momento do registro:**
- `paceZ1`: pace3K * 1.45 (recuperação ativa)
- `paceZ2`: pace3K * 1.25 (base aeróbica)
- `paceZ3`: pace3K * 1.12 (moderado)
- `paceZ4` (limiar): pace3K * 1.05
- `paceZ5`: pace3K * 0.98 (VO2max / intervalado)

**Alternativas consideradas:**
- *Fórmula de Riegel / calculadoras de equivalência de distância*: Mais preciso mas complexo e dependente da distância e condições do percurso.
- *VDOT de Jack Daniels*: Requer tabela de lookup ou fórmula não-linear; adiciona complexidade sem diferença prática no contexto de treinamento amador.

### D3: Como inserir o teste no plano semanal?

**Decisão:** O teste é inserido **somente quando o treinador explicitar a intenção** no payload de geração do plano (`incluirTesteLimiar: true`). O sistema nunca insere automaticamente. Quando incluído, cria um `TreinoPlanejado` do tipo `TESTE_LIMIAR` substituindo o treino de qualidade da semana, com a IA posicionando-o na terça ou quarta-feira (após folga de segunda), nunca após treino pesado.

A prescrição das etapas é fixa (não gerada pela IA) para garantir consistência metodológica:
1. Aquecimento: 20 min, Zona 1-2, ritmo leve + 4x 80m progressivos
2. Teste: 3km em esforço máximo controlado (saída conservadora, progressão nos 500m finais)
3. Desaquecimento: 10-15 min, Zona 1, trote leve

**Alternativas consideradas:**
- *IA decide automaticamente quando incluir o teste*: Perde o controle do treinador — o teste impacta a semana inteira (fadiga pré, recuperação pós) e quem decide o timing certo é o treinador, não o algoritmo.
- *IA gera livremente as etapas do teste*: Inconsistência no protocolo entre atletas, impossibilitando comparação.
- *Endpoint separado `POST /testes-limiar`*: Cria duplicação de lógica com `TreinoPlanejado` existente.

### D4: Onde armazenar a periodicidade e data do último teste?

**Decisão:** Dois campos em `PlanoMetaDados`: `dataUltimoTesteLimiar` (LocalDate) e `periodicidadeTesteMeses` (Integer, default 3).

### D5: Como estruturar os alertas de proximidade do teste?

**Decisão:** Dois níveis de alerta distintos, calculados no momento da consulta do plano/atleta:

| Nível | Condição | Tipo de alerta |
|---|---|---|
| `AVISO` | dias restantes ≤ 14 (2 semanas) | `TESTE_LIMIAR_PROXIMO` |
| `URGENTE` | prazo já atingido ou ultrapassado | `TESTE_LIMIAR_VENCIDO` |

A janela de aviso antecipado (14 dias) é fixa por ora — suficiente para o treinador planejar a semana do teste com antecedência. Ambos os alertas incluem `diasRestantes` (negativo quando vencido) e `dataProximoTeste` para exibição na UI.

**Alternativas consideradas:**
- *Alerta único sem distinção de proximidade vs vencido*: Perde o senso de urgência — o treinador não consegue priorizar atletas que já passaram do prazo.
- *Janela de aviso configurável por assessoria*: Adiciona complexidade sem valor imediato. Pode ser evoluído depois.

## Risks / Trade-offs

- **[Risco] Atletas com pace de limiar desatualizado no momento da migração** → Mitigation: O campo `paceLimiar` em `PlanoMetaDados` permanece como estava (informado manualmente). O novo cálculo só sobrescreve após o primeiro teste registrado via nova feature.

- **[Risco] Condições do percurso afetam o resultado** (calor, vento, altitude, terreno) → Mitigation: Capturar campos opcionais de condições (`temperaturaC`, `tipoSuperficie`) para contextualizar a interpretação. A análise de evolução deve filtrar por condições similares no futuro.

- **[Risco] Atleta não finaliza os 3km exatos** (lesão durante o teste, teste em percurso diferente) → Mitigation: Capturar `distanciaTesteKm` real percorrida. O cálculo do pace normaliza para pace/km independente da distância percorrida, permitindo testes em 2.4km ou 4km com menor precisão.

- **[Trade-off] Fator 1.05 é uma aproximação** → Para atletas muito velozes (pace < 3:30/km) ou iniciantes (pace > 7:00/km) a relação pode não ser linear. Aceitar essa imprecisão pela simplicidade; a IA pode ajustar via feedback do treinador.

## Migration Plan

1. **Migration SQL**: Adicionar colunas `teste_*` em `tb_treino_realizado` e `data_ultimo_teste_limiar` + `periodicidade_teste_meses` em `tb_plano_meta_dados` — todas nullable/com default, sem breaking change.
2. **Deploy**: Zero-downtime — campos opcionais, código existente não é alterado.
3. **Rollback**: Remover as colunas adicionadas (dado que nenhum resultado foi registrado ainda).

## Open Questions

- O treinador deve poder sobrescrever manualmente o `paceLimiar` calculado automaticamente? (Sim, provavelmente — a confirmar com o produto)
- O histórico de testes deve ser acessível pelo atleta via app mobile ou apenas pelo treinador no painel? (Fora do escopo desta mudança, mas impacta controle de acesso)
