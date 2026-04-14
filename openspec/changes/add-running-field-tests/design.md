## Context

O Menthoros gera planos semanais personalizados, mas não trata o teste de campo como elemento operacional do ciclo de treino. Na prática do treinador, testes como o **Teste de 3K** (padrão brasileiro) e o **Cooper 12 min** servem para recalibrar ritmos, revisar zonas e dar segurança para a prescrição das semanas seguintes.

Sem essa capability, o treinador improvisa o teste como treino comum ou como prova — o que prejudica a clareza do planejamento, a substituição correta de um treino de qualidade e o reaproveitamento do resultado para atualizar parâmetros fisiológicos do atleta.

Este change consolida o escopo de dois changes anteriores (`add-running-field-tests` e `teste-limiar-3k`) em uma capability unificada. O `teste-limiar-3k` é arquivado — suas decisões e fórmulas estão incorporadas aqui.

**Contexto multi-tenancy:** diferentes assessorias podem adotar protocolos distintos. O protocolo é configurado por assessoria — não por atleta.

## Goals / Non-Goals

**Goals:**
- Tratar o teste de campo como capability explícita do produto (não como treino comum)
- Suportar dois protocolos: `TRES_KM` (padrão) e `COOPER_12MIN`
- Configurar o protocolo padrão por assessoria via campo em `Assessoria`
- Definir interface de extensibilidade para novos protocolos sem reescrever regras genéricas
- Agendar o teste substituindo um treino planejado da semana, com regras de encaixe seguro
- Capturar resultado estruturado e calcular `paceLimiar` por protocolo
- Emitir alertas de proximidade e prazo vencido do próximo teste
- Expor histórico de testes do atleta

**Non-Goals:**
- Protocolos de laboratório (VO2max direto, Balke, teste de Cooper em bicicleta)
- Protocolo `5 minutos` — descartado por ora
- Override do protocolo por atleta — o protocolo é sempre da assessoria
- Integração automática com Garmin/Strava para importar resultado
- Relatório PDF de evolução
- Alterar o modelo de cálculo de TSS existente

## Decisions

### D1: `TipoTreino.TESTE_CAMPO` — tipo genérico para qualquer teste de campo

**Decisão:** Adicionar `TESTE_CAMPO` ao enum `TipoTreino`, com fatorImpacto `1.35` e zonaFcAlvo `"Zona 4-5 (Limiar/VO2max)"`. O tipo não amarra ao protocolo específico — o vínculo protocolo → atleta passa pela assessoria.

**Rationale:** Separa "o que é esta sessão de treino" (um teste de campo) de "qual protocolo foi usado" (3K ou Cooper). O tipo genérico permite que o fluxo existente de `TreinoPlanejado` e `TreinoRealizado` funcione sem bifurcações por protocolo.

---

### D2: `ProtocoloTeste` enum — TRES_KM e COOPER_12MIN

**Decisão:** Criar enum `ProtocoloTeste` com os valores `TRES_KM` e `COOPER_12MIN`. Cada valor carrega metadados fixos:

| Protocolo | Referência | Fatoração |
|---|---|---|
| `TRES_KM` | Distância fixa (3 km) | pace médio × 1.05 → paceLimiar |
| `COOPER_12MIN` | Tempo fixo (12 min) | distância percorrida → VO2max → paceLimiar |

**Rationale:** Enum tipado evita strings soltas no código. Metadados no enum permitem que a lógica genérica (alertas, histórico, agendamento) não precise de switch por protocolo — só os calculadores específicos precisam.

---

### D3: Interface `FieldTestProtocol` — boundary de extensibilidade

**Decisão:** Criar interface `FieldTestProtocol` com três contratos:

```java
interface FieldTestProtocol {
    ProtocoloTeste getProtocolo();
    List<EtapaTreino> buildEtapasTreino(Atleta atleta);
    ParametrosFisiologicosCalculados calcularParametros(ResultadoTesteDto resultado);
}
```

Implementações concretas: `TresKmProtocol` e `CooperProtocol`.

**Rationale:** Toda lógica genérica (agendamento, alertas, histórico) opera sobre `FieldTestProtocol`. Adicionar um novo protocolo no futuro = nova implementação + novo valor no enum. Nenhuma regra genérica é alterada.

**Localização:** `services/fieldtest/` — novo pacote dedicado à capability.

---

### D4: `Assessoria.protocoloTestePadrao` — protocolo por assessoria, sem override por atleta

**Decisão:** Adicionar campo `protocoloTestePadrao: ProtocoloTeste` em `Assessoria` com default `TRES_KM`. O protocolo é sempre resolvido via `atleta.getAssessoria().getProtocoloTestePadrao()`. Não existe campo de protocolo no atleta.

**Rationale:** O protocolo reflete a metodologia da assessoria, não uma preferência individual. Centralizar em `Assessoria` elimina ambiguidade e mantém consistência entre todos os atletas de uma assessoria.

---

### D5: O teste substitui exatamente um treino planejado da semana

**Decisão:** O agendamento do teste exige `substituiTreinoPlanejadoId` (obrigatório). O treino substituído é marcado como `CANCELADO_POR_AVALIACAO`. O sistema prioriza substituição de `INTERVALADO` ou `TEMPO_RUN`. Substituição de `LONGO` exige confirmação explícita.

**Rationale:** O teste gera carga fisiológica relevante e não pode ser somado à semana arbitrariamente. Ocupar um slot real preserva a integridade da periodização.

---

### D6: Regras de encaixe seguro

**Decisão:** O sistema deve sinalizar (WARN, não bloquear) agendamentos que violem:
- Menos de 24h de treino leve antes do teste
- Teste seguido de treino intenso em menos de 24h
- Dois testes em menos de 4 semanas
- Teste no mesmo bloco de 48h do treino longo

**Rationale:** O treinador decide o timing certo — o sistema dá visibilidade ao risco mas não bloqueia. Bloquear seria paternalista para um profissional que conhece seu atleta.

---

### D7: Fórmula 3K — Jack Daniels adaptado

**Decisão:** `paceLimiar = pace3K × 1.05` (5% mais lento que o pace médio do teste), arredondado para segundos inteiros.

Zonas derivadas do resultado:
| Zona | Fator sobre pace3K |
|---|---|
| Z1 (Recuperação) | × 1.45 |
| Z2 (Aeróbico) | × 1.25 |
| Z3 (Moderado) | × 1.12 |
| Z4 (Limiar) | × 1.05 |
| Z5 (VO2max) | × 0.98 |

**Rationale:** Relação amplamente usada por treinadores brasileiros, alinhada com Daniels. O 3K captura um ritmo ligeiramente acima do limiar — o fator 1.05 ajusta para o pace de limiar real.

---

### D8: Fórmula Cooper 12min

**Decisão:** Estimar o paceLimiar a partir da distância percorrida em 12 minutos usando a fórmula de Cooper para VO2max, com conversão para pace de limiar via fórmula de Daniels (VO2max → vVO2max → 88% vVO2max → paceLimiar).

A implementação específica fica em `CooperProtocol.calcularParametros()`. O contrato de saída (`ParametrosFisiologicosCalculados`) é idêntico ao do 3K.

**Alternativa considerada:** tabela de equivalência distância → pace. Rejeitada — menos precisa e não escala para variações individuais.

---

### D9: Armazenamento do resultado — campos em `TreinoRealizado` + config em `Assessoria`

**Decisão:**
- Campos `teste_*` opcionais em `tb_treino_realizado` (NULL para treinos normais)
- `protocolo_teste_padrao` em `tb_assessoria`
- `data_ultimo_teste_campo` e `periodicidade_teste_meses` em `tb_plano_meta_dados`

**Campos em `tb_treino_realizado`:**
- `teste_protocolo` (VARCHAR) — qual protocolo foi usado
- `teste_tempo_total_segundos` (INT)
- `teste_distancia_km` (DECIMAL) — distância real percorrida (relevante para Cooper)
- `teste_pace_media_seg_por_km` (INT)
- `teste_temperatura_c` (INT, opcional)
- `teste_tipo_superficie` (VARCHAR, opcional)
- `teste_observacoes` (TEXT, opcional)

**Rationale:** Campos diretos em `TreinoRealizado` são simples, tipados e pesquisáveis sem joins extras. Campos são NULL para treinos normais — sem custo no caminho principal.

---

### D10: Alertas — dois níveis, calculados na consulta

**Decisão:** Dois alertas distintos, calculados no momento da consulta (não persistidos):

| Nível | Condição | Tipo |
|---|---|---|
| `AVISO` | dias restantes ≤ 14 | `TESTE_CAMPO_PROXIMO` |
| `URGENTE` | prazo atingido ou ultrapassado | `TESTE_CAMPO_VENCIDO` |

Ambos incluem `diasRestantes` (negativo quando vencido) e `dataProximoTeste`.

**Rationale:** Alertas calculados na consulta evitam estado persistido que pode ficar desatualizado. A janela de 14 dias é suficiente para o treinador planejar a semana do teste com antecedência.

## Risks / Trade-offs

- **[Risco] Resultado de Cooper com pacing irregular** → O atleta pode variar muito o ritmo nos 12 min, tornando a estimativa de VO2max imprecisa. Mitigação: capturar `qualityFlag` e exibir aviso ao treinador se a variação de pace for alta.

- **[Risco] Fator 1.05 (3K) impreciso para atletas extremos** → Para pace < 3:30/km ou > 7:00/km a relação pode não ser linear. Aceitar imprecisão pela simplicidade; treinador pode sobrescrever manualmente.

- **[Risco] Treinador agenda teste sem respeitar as regras de encaixe** → Mitigação: alertas são WARNs visíveis, não bloqueios. O treinador é responsável pela decisão final.

- **[Trade-off] Cooper implementado desde o início mas menos usado** → Implementar os dois protocolos agora evita refactor futuro. O custo incremental de adicionar Cooper junto com 3K é menor do que fazer depois.

## Migration Plan

1. Migration SQL: `protocolo_teste_padrao` em `tb_assessoria` (default `TRES_KM`)
2. Migration SQL: campos `teste_*` em `tb_treino_realizado` (todos nullable)
3. Migration SQL: `data_ultimo_teste_campo` e `periodicidade_teste_meses` em `tb_plano_meta_dados`
4. Novo pacote `services/fieldtest/` com interface e implementações
5. Novo enum `ProtocoloTeste`
6. Novo valor `TESTE_CAMPO` em `TipoTreino`
7. Deploy zero-downtime — campos opcionais, código existente não alterado

**Rollback:** Remover colunas adicionadas (sem dados críticos antes do primeiro teste registrado).

## Open Questions

- O treinador deve poder sobrescrever manualmente o `paceLimiar` calculado após o teste? (Assumindo sim — confirmar com produto)
- O histórico de testes deve ser visível ao atleta no app mobile ou apenas ao treinador? (Fora do escopo desta mudança)
- A frequência recomendada entre testes (atualmente 3 meses) deve ser configurável por assessoria no futuro?
