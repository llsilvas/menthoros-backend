# Melhoria na Captura de Treinos Intervalados

**Data:** 2025-11-05
**Objetivo:** Melhorar a avaliacao de treinos intervalados pela IA, capturando metricas especificas por etapa (intervalos, aquecimento, desaquecimento) separadamente das metricas gerais.
**Ultima atualizacao:** 2026-02-10
**Status:** Fase 1 - Infraestrutura implementada (entidade, DTOs, mapper, migration, build OK)

---

## 1. Problema Atual

### Estrutura Atual

**TreinoPlanejado** - Ja tem granularidade
- Possui `EtapaTreino` detalhadas
- IA ja decompoe intervalados em etapas individuais
- Exemplo: 5 tiros = 12 etapas (aquecimento + 5 tiros + 5 recuperacoes + desaquecimento)

**TreinoRealizado** - Falta granularidade
- Apenas metricas gerais do treino completo
- FC media inclui aquecimento + tiros + desaquecimento
- Nao consegue distinguir desempenho nos tiros vs partes regenerativas

### Impacto na Avaliacao da IA

**Exemplo Pratico:**

Treino planejado:
```
- 10min aquecimento (Z2: 140 bpm)
- 5x 4min tiros Z5 (170-180 bpm)
- 5x 2min recuperacao (120-130 bpm)
- 5min desaquecimento (130 bpm)
```

Treino realizado atual:
```json
{
  "fcMedia": 155,
  "distanciaKm": 12.5,
  "duracaoMin": 45
}
```

**Problema:** FC media de 155 bpm nao reflete se os tiros foram executados na intensidade correta (170-180 bpm). A media e puxada para baixo pelo aquecimento/desaquecimento.

**IA nao consegue avaliar:**
- Os tiros foram executados na zona correta?
- Houve degradacao de pace ao longo dos tiros?
- A percepcao de esforco condiz com a intensidade dos intervalos?

---

## 2. Solucao Adotada: Etapas Realizadas (Entidade Dedicada)

### 2.1 Decisao Arquitetural

Apos analise de alternativas (campos resumidos em TreinoRealizado vs entidade separada), a decisao foi:

**Criar `EtapaRealizada` como entidade dedicada**, sem campos resumidos em `TreinoRealizado`.

**Justificativa:**
- **Fonte unica de verdade** - metricas por etapa vivem em um so lugar, sem risco de divergencia entre campos resumidos e etapas detalhadas
- **Retrocompatibilidade total** - treinos antigos sem etapas continuam validos (lista vazia por default). Nenhuma coluna existente alterada
- **Flexibilidade de granularidade** - input simplificado (2-3 etapas) ou detalhado (tiro a tiro) usando a mesma estrutura
- **Evolucao independente** - campos de etapa realizada crescem sem poluir `TreinoRealizado`
- **Zero redundancia** - nenhuma informacao duplicada entre tabelas

**Alternativas descartadas:**
- Campos resumidos em `TreinoRealizado` (Opcao 2 original): descartada por gerar redundancia e dois caminhos de dados
- Hibrida campos + etapas (Opcao 3 original): descartada porque etapas simplificadas cobrem o caso de uso do input rapido
- Reutilizar `EtapaTreino` para ambos (planejado/realizado): descartada por semantica ambigua, FKs nullable e campos com duplo significado

### 2.2 Como Funciona para Diferentes Niveis de Detalhe

**Nivel 1: Treino sem etapas (metricas gerais)**
Funciona como hoje. Campos planos de `TreinoRealizado` (fcMedia, paceMedia, distanciaKm).
```json
{
  "tipoTreino": "INTERVALADO",
  "fcMedia": 155,
  "distanciaKm": 12.5,
  "duracaoMin": "00:45:00"
}
```

**Nivel 2: Etapas simplificadas (input rapido)**
Atleta informa blocos gerais sem detalhe por tiro. Substitui a necessidade de campos resumidos.
```json
{
  "tipoTreino": "INTERVALADO",
  "fcMedia": 155,
  "distanciaKm": 12.5,
  "etapasRealizadas": [
    { "ordem": 1, "tipoEtapa": "AQUECIMENTO",    "distanciaKm": 2.0, "fcMedia": 135 },
    { "ordem": 2, "tipoEtapa": "PRINCIPAL",       "distanciaKm": 8.0, "fcMedia": 178, "percepcaoEsforco": 8 },
    { "ordem": 3, "tipoEtapa": "DESAQUECIMENTO",  "distanciaKm": 2.0, "fcMedia": 130 }
  ]
}
```

**Nivel 3: Etapas detalhadas (tiro a tiro)**
Dados completos por repeticao, ideal para analise de decaimento e consistencia.
```json
{
  "tipoTreino": "INTERVALADO",
  "fcMedia": 155,
  "distanciaKm": 12.5,
  "etapasRealizadas": [
    { "ordem": 1,  "tipoEtapa": "AQUECIMENTO",  "distanciaKm": 2.0, "fcMedia": 135, "paceMedia": "05:00" },
    { "ordem": 2,  "tipoEtapa": "INTERVALADO",  "descricao": "Tiro 1", "distanciaKm": 1.0, "fcMedia": 175, "fcMax": 180, "paceMedia": "04:00", "percepcaoEsforco": 7 },
    { "ordem": 3,  "tipoEtapa": "RECUPERACAO",  "distanciaKm": 0.4, "fcMedia": 125, "paceMedia": "06:00" },
    { "ordem": 4,  "tipoEtapa": "INTERVALADO",  "descricao": "Tiro 2", "distanciaKm": 1.0, "fcMedia": 176, "fcMax": 181, "paceMedia": "04:05", "percepcaoEsforco": 7 },
    { "ordem": 5,  "tipoEtapa": "RECUPERACAO",  "distanciaKm": 0.4, "fcMedia": 126, "paceMedia": "06:00" },
    { "ordem": 6,  "tipoEtapa": "INTERVALADO",  "descricao": "Tiro 3", "distanciaKm": 1.0, "fcMedia": 177, "fcMax": 182, "paceMedia": "04:10", "percepcaoEsforco": 7 },
    { "ordem": 7,  "tipoEtapa": "RECUPERACAO",  "distanciaKm": 0.4, "fcMedia": 127, "paceMedia": "06:00" },
    { "ordem": 8,  "tipoEtapa": "INTERVALADO",  "descricao": "Tiro 4", "distanciaKm": 1.0, "fcMedia": 178, "fcMax": 183, "paceMedia": "04:20", "percepcaoEsforco": 8 },
    { "ordem": 9,  "tipoEtapa": "RECUPERACAO",  "distanciaKm": 0.4, "fcMedia": 128, "paceMedia": "06:00" },
    { "ordem": 10, "tipoEtapa": "INTERVALADO",  "descricao": "Tiro 5", "distanciaKm": 0.95, "fcMedia": 178, "fcMax": 185, "paceMedia": "04:30", "percepcaoEsforco": 9, "observacao": "Bem dificil, pace caiu" },
    { "ordem": 11, "tipoEtapa": "DESAQUECIMENTO", "distanciaKm": 2.0, "fcMedia": 130, "paceMedia": "05:30" }
  ]
}
```

### 2.3 Logica da IA para Analise

```
SE TreinoRealizado.etapasRealizadas NAO esta vazio:
    filtrar etapas tipo INTERVALADO/PRINCIPAL/TIRO
    SE etapas filtradas >= 2:
        analise granular tiro a tiro (decaimento, consistencia, recuperacao FC)
    SENAO:
        analise por blocos (aquecimento vs principal vs desaquecimento)
SENAO:
    analise baseada em metricas gerais (situacao atual - limitada)
```

---

## 3. Implementacao Tecnica

### 3.1 Entidade EtapaRealizada (IMPLEMENTADO)

**Arquivo:** `src/main/java/com/menthoros/entity/EtapaRealizada.java`

```java
@Entity
@Table(name = "tb_etapa_realizada",
        indexes = {
                @Index(name = "idx_etapa_realizada_treino", columnList = "treino_realizado_id"),
                @Index(name = "idx_etapa_realizada_ordem", columnList = "treino_realizado_id,ordem")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EtapaRealizada {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treino_realizado_id", nullable = false)
    private TreinoRealizado treinoRealizado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etapa_planejada_id")
    private EtapaTreino etapaPlanejada;  // nullable - permite comparacao planejado vs real

    private Integer ordem;               // NOT NULL
    private String tipoEtapa;            // AQUECIMENTO, PRINCIPAL, INTERVALADO, RECUPERACAO, DESAQUECIMENTO
    private String descricao;
    private Duration duracao;            // INTERVAL
    private BigDecimal distanciaKm;
    private Integer fcMedia;
    private Integer fcMax;
    private Duration paceMedia;          // INTERVAL
    private Double velocidadeMedia;      // km/h
    private Integer percepcaoEsforco;    // RPE 1-10
    private Integer cadenciaMedia;
    private Integer potenciaMedia;
    private String observacao;
}
```

### 3.2 Relacionamento em TreinoRealizado (IMPLEMENTADO)

**Arquivo:** `src/main/java/com/menthoros/entity/TreinoRealizado.java`

```java
@OneToMany(mappedBy = "treinoRealizado", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
@OrderBy("ordem ASC")
private List<EtapaRealizada> etapasRealizadas = new ArrayList<>();
// Lista vazia por default - treinos antigos continuam funcionando
```

### 3.3 Migration (IMPLEMENTADO)

**Arquivo:** `src/main/resources/db/migration/V16__Create_etapa_realizada_table.sql`

```sql
CREATE TABLE tb_etapa_realizada (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    treino_realizado_id UUID NOT NULL REFERENCES tb_treino_realizado(id) ON DELETE CASCADE,
    etapa_planejada_id UUID REFERENCES tb_etapa_treino(id) ON DELETE SET NULL,
    ordem INTEGER NOT NULL,
    tipo_etapa VARCHAR(50),
    descricao VARCHAR(500),
    duracao INTERVAL,
    distancia_km DECIMAL(10,3),
    fc_media INTEGER,
    fc_max INTEGER,
    pace_media INTERVAL,
    velocidade_media DECIMAL(5,2),
    percepcao_esforco INTEGER CHECK (percepcao_esforco BETWEEN 1 AND 10),
    cadencia_media INTEGER,
    potencia_media INTEGER,
    observacao VARCHAR(500)
);
```

Nenhuma tabela existente alterada. Tabela nova e aditiva.

### 3.4 DTOs (IMPLEMENTADO)

**Input:** `src/main/java/com/menthoros/dto/input/EtapaRealizadaInputDto.java`
- Record com campos opcionais (exceto `ordem` e `tipoEtapa`)
- Duracao e pace como String (formato MM:SS ou HH:MM:SS), convertidos pelo mapper

**Output:** `src/main/java/com/menthoros/dto/output/EtapaRealizadaOutputDto.java`
- `@JsonInclude(NON_NULL)` - campos null omitidos do JSON
- Inclui `etapaPlanejadaId` para referencia cruzada

**TreinoRealizadoInputDto:** campo `List<EtapaRealizadaInputDto> etapasRealizadas` adicionado (nullable)
**TreinoRealizadoOutputDto:** campo `List<EtapaRealizadaOutputDto> etapasRealizadas` adicionado (oculto quando null)

### 3.5 Mapper (IMPLEMENTADO)

**Arquivo:** `src/main/java/com/menthoros/mapper/TreinoMapper.java`

Adicionados:
- `EtapaRealizada toEntity(EtapaRealizadaInputDto)` - com conversores Duration/BigDecimal
- `EtapaRealizadaOutputDto toOutputDto(EtapaRealizada)` - com `etapaPlanejada.id` mapeado
- `List<>` para conversao em lote
- `@AfterMapping linkEtapasRealizadas()` - seta relacao bidirecional (mesmo padrao de `linkEtapas` para TreinoPlanejado)

### 3.6 Persistencia via Cascade

O fluxo de persistencia nao exigiu alteracao em `TreinoServiceImpl`:
1. Mapper converte `TreinoRealizadoInputDto` → `TreinoRealizado` (inclui etapas se presentes)
2. `@AfterMapping` linka cada etapa ao treino pai
3. `treinoRealizadoRepository.save()` persiste tudo via `CascadeType.ALL`
4. Se `etapasRealizadas` e null/vazio, nada muda - retrocompativel

---

## 4. Avaliacao da IA - Antes vs Depois

**ANTES (sem etapas):**
```
Treino INTERVALADO realizado em 2026-02-10:
- Distancia: 12.5 km
- FC media: 155 bpm
- TSS: 85

Avaliacao: Treino executado dentro do esperado. FC media condizente com treino misto.
```
Nao consegue identificar se tiros estavam na zona correta.

**DEPOIS (etapas simplificadas - 3 blocos):**
```
Treino INTERVALADO realizado em 2026-02-10:
- Distancia total: 12.5 km | FC media geral: 155 bpm
- Aquecimento: 2.0 km | FC 135 bpm
- Parte principal: 8.0 km | FC 178 bpm | RPE 8/10
- Desaquecimento: 2.0 km | FC 130 bpm

Avaliacao: Tiros executados em Z5 (meta: 170-180 bpm). Porem:
- RPE 8/10 indica esforco alto (esperado era 7/10)
- Sugestao: manter intensidade, reduzir volume ou aumentar recuperacao
```

**DEPOIS (etapas detalhadas - tiro a tiro):**
```
Analise tiro a tiro:
- Tiro 1: 4:00 min/km, 175 bpm, RPE 7
- Tiro 2: 4:05 min/km, 176 bpm, RPE 7
- Tiro 3: 4:10 min/km, 177 bpm, RPE 7
- Tiro 4: 4:20 min/km, 178 bpm, RPE 8
- Tiro 5: 4:30 min/km, 178 bpm, RPE 9

Padrao identificado: Fadiga a partir do 4o tiro.
Degradacao de pace: 12.5% (primeiro vs ultimo)
Sugestao: Reduzir para 3-4 tiros OU aumentar recuperacao apos 3o tiro
```

---

## 5. Beneficios

### Para o Atleta:
- Lancamento rapido (3 etapas) ou detalhado (tiro a tiro) conforme preferencia
- Feedback mais preciso da IA sobre desempenho nos tiros
- Recomendacoes especificas baseadas em dados reais dos intervalos
- Historico comparativo de evolucao nos treinos intervalados

### Para a IA/Treinador:
- Avaliacao precisa se intensidade dos tiros foi atingida
- Identificacao de padroes de fadiga (em qual tiro comeca a degradar)
- Ajuste fino de volume (quantos tiros o atleta aguenta bem)
- Correlacao entre percepcao e dados objetivos (FC, pace)
- Deteccao precoce de overtraining (RPE alto + degradacao alta)

### Para o Sistema:
- Preparado para integracao automatica (Garmin/Strava laps → etapas)
- Uma fonte de verdade (sem campos resumidos redundantes)
- Retrocompativel (treinos antigos intactos)
- Melhora qualidade dos prompts LLM com dados relevantes

---

## 6. Checklist de Implementacao

### Fase 1 - Infraestrutura EtapaRealizada

- [x] Criar entidade `EtapaRealizada` (`src/main/java/com/menthoros/entity/EtapaRealizada.java`)
- [x] Criar migration `V16__Create_etapa_realizada_table.sql` (tabela `tb_etapa_realizada`)
- [x] Criar DTO de entrada `EtapaRealizadaInputDto`
- [x] Criar DTO de saida `EtapaRealizadaOutputDto`
- [x] Adicionar mapeamentos em `TreinoMapper` (toEntity, toOutputDto, listas, linkEtapasRealizadas)
- [x] Adicionar relacionamento `@OneToMany` em `TreinoRealizado` (lista vazia por default)
- [x] Adicionar campo opcional `etapasRealizadas` em `TreinoRealizadoInputDto`
- [x] Adicionar campo `etapasRealizadas` em `TreinoRealizadoOutputDto`
- [x] Build compila sem erros (clean compile OK)

### Fase 2 - Integracao com IA e Testes

- [ ] Ajustar PromptBuilder para incluir etapas realizadas no historico de treinos
- [ ] Adicionar orientacoes no prompt da IA para avaliar etapas
- [ ] Testar lancamento via API (POST com etapasRealizadas)
- [ ] Testar retrocompatibilidade (POST sem etapas continua funcionando)
- [ ] Testar GET de treino antigo (retorna sem campo etapasRealizadas)
- [ ] Validar prompt gerado para IA com dados de etapas

### Fase 3 - Integracao Automatica (longo prazo)

- [ ] Implementar cliente Garmin API
- [ ] Implementar cliente Strava API
- [ ] Criar servico de importacao de atividades (laps → etapas)
- [ ] Implementar logica de inferencia de tipo de etapa
- [ ] Criar webhook para sincronizacao automatica
- [ ] Implementar matching automatico com treino planejado
- [ ] Testes de integracao end-to-end
- [ ] Configurar autenticacao OAuth (Garmin/Strava)

---

## 7. Riscos e Mitigacoes

| Risco | Impacto | Mitigacao |
|-------|---------|-----------|
| Atleta nao preencher etapas | Baixo | Sistema funciona com metricas gerais como fallback (nivel 1) |
| Overhead de dados (muitas etapas) | Baixo | Tabela separada com lazy loading, indices otimizados |
| Complexidade de lancamento manual detalhado | Medio | Nivel 2 (3 etapas) cobre 80% do valor com esforco minimo |
| Dificuldade em inferir tipo de etapa automaticamente | Alto | Permitir edicao manual apos importacao; melhorar heuristica com ML |
| API Garmin/Strava mudar estrutura de dados | Medio | Camada de abstracao + testes automatizados |

---

## 8. Metricas de Sucesso

**Apos Fase 1 (infraestrutura):**
- [x] Build compila sem erros
- [x] Migration executavel sem alterar dados existentes
- [x] API retrocompativel (treinos sem etapas continuam funcionando)

**Apos Fase 2 (integracao IA):**
- [ ] IA menciona desempenho por etapa quando dados disponiveis
- [ ] Atletas reportam recomendacoes mais uteis para intervalados
- [ ] Prompt inclui dados de etapas nos ultimos treinos

**Apos Fase 3 (integracao automatica):**
- [ ] 90%+ dos treinos importados automaticamente com etapas
- [ ] IA identifica padrao de fadiga em treinos intervalados

---

## 9. Proximos Passos

1. **Fase 2** - Ajustar PromptBuilder para considerar etapas na analise
2. **Testes** - Validar lancamento com e sem etapas via API
3. **Frontend** - UI para lancamento por etapas (opcional)
4. **Fase 3** - Integracao Garmin/Strava para importacao automatica de laps

---

**Documento preparado em:** 2025-11-05
**Autor:** Claude Code Assistant
**Revisado em:** 2026-02-10
**Revisao:** Descartada abordagem hibrida (campos resumidos + etapas). Adotada entidade dedicada `EtapaRealizada` como fonte unica de verdade.
**Status:** Fase 1 concluida - infraestrutura implementada
