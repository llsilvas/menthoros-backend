## Why

A análise do `BACKLOG_ASSERTIVIDADE_GERACAO_TREINO.md` apontou um problema recorrente (P3-B): a distribuição de carga dentro da semana tem padrões pouco naturais — dias consecutivos de alta TSS, longo seguido de intervalado no dia seguinte, ou "dia leve" caindo em dia que o atleta tem janela de treino mais larga. O motor gera semanas tecnicamente corretas em volume total, mas com distribuição que treinador humano trocaria em 30 segundos de revisão.

Este fix é operacional: aplicar regras determinísticas de distribuição (hard/easy, espaçamento de sessões-chave, respeito à disponibilidade do atleta) antes de passar ao LLM, para que as semanas geradas já venham com "backbone" coerente. Também endereça reclamação frequente ("por que tem intervalado na quarta se meu dia livre é sábado?").

## What Changes

- **Novo serviço `DistribuicaoSemanalService`**: recebe lista de sessões-chave planejadas para a semana e devolve distribuição dia-a-dia respeitando regras
- **Regras determinísticas codificadas**:
  1. Longo não pode ser no dia seguinte a intervalado e vice-versa (mínimo 1 dia fácil entre sessões-chave)
  2. Dias de alta carga devem ser preferencialmente nos dias de maior `disponibilidadeMinutosPorDia` do atleta (campo `Atleta.disponibilidadeSemanal`)
  3. Padrão hard/easy: duas sessões consecutivas não podem ambas ter `nivelEsforco` ≥ ALTO
  4. Recuperação ativa ou dia off SHALL ocorrer 24h antes de sessão-chave
- **Integração com `PlanoSemanalService`**: após o LLM propor estrutura, o motor valida/reordena aplicando as regras antes de persistir
- **Adicionar campo `Atleta.disponibilidadeSemanal`**: mapa `DiaSemana → Integer (minutos disponíveis)` — se ainda não existir
- **Endpoint de revalidação**: `POST /api/planos-semanais/{id}/rebalancear` — permite ao treinador recalcular distribuição sem regenerar o plano todo

## Capabilities

### New Capabilities

- `weekly-load-distribution`: aplicação de regras determinísticas de distribuição de carga dentro da semana, garantindo espaçamento adequado entre sessões-chave e alinhamento com disponibilidade do atleta.

### Modified Capabilities

<!-- Não altera requisitos de capabilities existentes; age como camada de pós-processamento determinístico sobre o output do LLM. -->

## Impact

**Entidades e banco:**
- Campo `disponibilidadeSemanal` em `Atleta` (JSONB ou tabela auxiliar `tb_atleta_disponibilidade` com FK) — verificar se já existe
- Sem nova tabela principal; lógica é puramente de serviço

**APIs:**
- `POST /api/planos-semanais/{id}/rebalancear` — rebalanceia semana existente sem regenerar
- `PUT /api/atletas/{id}/disponibilidade` — atualiza mapa de disponibilidade
- Sem breaking changes

**Regras implementadas:**
- `isAlta(TreinoPlanejado)` define se o treino é sessão-chave (LONGO, INTERVALADO, TEMPO_RUN, PROVA_SIMULADA)
- `espacamentoMinimoEntre(TipoTreino a, TipoTreino b)`: retorna mínimo de dias fáceis entre dois tipos
- `scoreDia(Atleta, DiaSemana, TreinoPlanejado)`: score de adequação (disponibilidade × coerência hard/easy)
- Algoritmo: permutação com custo penalizado; backtracking simples para 7 dias é O(7!) = 5040, aceitável

**Integração com LLM:**
- Instrução adicional no prompt: "O motor pós-processará sua distribuição aplicando regras de espaçamento. Concentre-se na **lista de sessões e volume total**, não na ordem dos dias."
- Contexto inclui `disponibilidadeSemanal` para dar transparência ao LLM

**Compatibilidade:**
- Planos gerados antes desta change não têm campo `disponibilidadeSemanal` no contexto; o rebalanceador usa defaults (`weekdays=60min, sábado=120min, domingo=180min`) como fallback

## Riscos e mitigações

- **Rebalanceamento muda ordem mas quebra expectativas do atleta**: mitigar expondo log de "sessões reordenadas" no output do rebalanceamento
- **Algoritmo pode gerar distribuição "tecnicamente correta" mas esteticamente pior**: mitigar com função de custo que penaliza também grandes quebras vs. distribuição original (menor variação > rearranjo total)
- **Atleta sem disponibilidade cadastrada**: usar defaults agressivos e logar WARN

## Referências

- **BACKLOG_ASSERTIVIDADE_GERACAO_TREINO.md** item P3-B — origem do problema
- **Pfitzinger, P. & Douglas, S. (2009)** — "Advanced Marathoning" (padrão hard/easy)
- **Daniels, J. (2014)** — "Daniels' Running Formula" (recomendações de espaçamento entre qualidades)
