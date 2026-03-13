# Comparação: Arquitetura Atual vs Arquitetura Proposta - Menthoros

## Documento de Análise Técnica
**Data:** 08/02/2026  
**Autor:** Leandro - Senior Software Engineer  
**Contexto:** Reestruturação do modelo de dados para treinos realizados

---

## 1. Visão Geral

### Arquitetura Atual
A estrutura atual de treinos realizados no Menthoros armazena dados de forma agregada, sem granularidade de etapas e repetições individuais.

### Arquitetura Proposta
Introdução de modelo hierárquico com três níveis: **Treino → Etapas → Repetições**, permitindo análise detalhada e comparação planejado vs realizado.

---

## 2. Modelo de Dados

### 2.1 Arquitetura Atual
```java
@Entity
@Table(name = "treinos_realizados")
public class TreinoRealizado {
    @Id
    private Long id;
    
    private Long usuarioId;
    private LocalDateTime dataHoraInicio;
    private LocalDateTime dataHoraFim;
    
    @ManyToOne
    private TreinoProgramado treinoProgramado;
    
    // Dados agregados do treino completo
    private Double distanciaKm;
    private Integer duracaoSegundos;
    private String paceMedia;
    private Integer cadenciaMedia;
    private Integer elevacaoGanho;
    private Integer fcMedia;
    private Integer fcMaxima;
    
    @Enumerated(EnumType.STRING)
    private SensacaoTreino sensacao;
    private Integer rpe;
    private String observacoes;
    
    @Enumerated(EnumType.STRING)
    private OrigemDados origem;
}
```

**Características:**
- ✅ Simples e direto
- ✅ Baixa complexidade de desenvolvimento
- ❌ Sem detalhamento de etapas
- ❌ Impossível analisar progressão em intervalados
- ❌ Não registra dados de recuperação entre repetições
- ❌ Comparação limitada com treino planejado

---

### 2.2 Arquitetura Proposta
```java
@Entity
@Table(name = "treinos_realizados")
public class TreinoRealizado {
    @Id
    private Long id;
    
    private Long usuarioId;
    private LocalDateTime dataHoraInicio;
    private LocalDateTime dataHoraFim;
    
    @ManyToOne
    private TreinoProgramado treinoProgramado;
    
    // Métricas gerais (calculadas a partir das etapas)
    private Double distanciaKm;
    private Integer duracaoSegundos;
    private String paceMedia;
    private Integer cadenciaMedia;
    private Integer elevacaoGanho;
    private Integer fcMedia;
    private Integer fcMaxima;
    
    // Avaliação do atleta
    @Enumerated(EnumType.STRING)
    private SensacaoTreino sensacao;
    private Integer rpe;
    private String observacoes;
    
    @Enumerated(EnumType.STRING)
    private OrigemDados origem;
    
    // NOVO: Relacionamento hierárquico
    @OneToMany(mappedBy = "treinoRealizado", cascade = CascadeType.ALL)
    private List<EtapaTreinoRealizada> etapas;
    
    @CreationTimestamp
    private LocalDateTime criadoEm;
}

@Entity
@Table(name = "etapas_treino_realizadas")
public class EtapaTreinoRealizada {
    @Id
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "treino_realizado_id")
    private TreinoRealizado treinoRealizado;
    
    @ManyToOne
    @JoinColumn(name = "etapa_programada_id")
    private EtapaTreinoProgramada etapaProgramada;
    
    private Integer ordem;
    
    @Enumerated(EnumType.STRING)
    private TipoEtapa tipo;
    
    // Comparação planejado vs realizado
    private String pacePlanejado;
    private String paceRealizado;
    private Double distanciaPlanejadaKm;
    private Double distanciaRealizadaKm;
    private Integer duracaoPlanejadaSegundos;
    private Integer duracaoRealizadaSegundos;
    
    // Métricas da etapa
    private Integer fcMedia;
    private Integer fcMaxima;
    private Integer cadenciaMedia;
    
    // NOVO: Repetições individuais para intervalados
    @OneToMany(mappedBy = "etapaRealizada", cascade = CascadeType.ALL)
    private List<RepeticaoRealizada> repeticoes;
    
    private LocalDateTime inicio;
    private LocalDateTime fim;
}

@Entity
@Table(name = "repeticoes_realizadas")
public class RepeticaoRealizada {
    @Id
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "etapa_realizada_id")
    private EtapaTreinoRealizada etapaRealizada;
    
    private Integer numero;
    
    // Dados do esforço
    private Double distanciaKm;
    private Integer duracaoSegundos;
    private String pace;
    private Integer fcMedia;
    private Integer fcMaxima;
    private Integer cadenciaMedia;
    
    // Dados da recuperação
    private Integer duracaoRecuperacaoSegundos;
    private String paceRecuperacao;
    private Integer fcRecuperacao;
    
    private LocalDateTime inicio;
    private LocalDateTime fim;
}
```

**Características:**
- ✅ Granularidade detalhada para análises profundas
- ✅ Comparação planejado vs realizado por etapa
- ✅ Análise de progressão em intervalados
- ✅ Registro de recuperação entre repetições
- ✅ Dados estruturados para ML/IA
- ⚠️ Maior complexidade de desenvolvimento
- ⚠️ Mais relacionamentos e queries

---

## 3. Análise Comparativa

### 3.1 Capacidades de Análise

| Análise | Atual | Proposta |
|---------|-------|----------|
| **Métricas gerais do treino** | ✅ Sim | ✅ Sim |
| **Progressão em longões** | ⚠️ Limitada | ✅ Completa |
| **Decaimento em intervalados** | ❌ Não | ✅ Sim |
| **Consistência de pace** | ❌ Não | ✅ Sim |
| **Recuperação cardíaca** | ❌ Não | ✅ Sim |
| **Comparação com planejado** | ⚠️ Apenas total | ✅ Por etapa |
| **Análise de fadiga intra-treino** | ❌ Não | ✅ Sim |
| **Efficiency Factor (EF)** | ⚠️ Apenas geral | ✅ Por etapa/repetição |
| **Drift cardíaco** | ❌ Não | ✅ Sim |

### 3.2 Exemplos Práticos

#### Treino Intervalado: 10x1000m

**Dados que APENAS a arquitetura proposta captura:**
```
Etapa: Intervalos 10x1000m
├─ Repetição 1: 1000m em 4:15 (FC 168) → Rec: 90s (FC → 142) ✅
├─ Repetição 2: 1000m em 4:18 (FC 172) → Rec: 90s (FC → 145) ✅
├─ Repetição 3: 1000m em 4:20 (FC 174) → Rec: 90s (FC → 148) ✅
└─ ...

Análises possíveis:
- Decaimento: (4:20 - 4:15) / 4:15 = 2% ✅
- Coeficiente de variação: 1.8% (excelente consistência) ✅
- Recuperação cardíaca piorando: 142 → 145 → 148 (sinal de fadiga) ✅
- Comparação com semana anterior: melhorou 3s/km na média ✅
```

**Arquitetura atual captura:**
```
Treino: 10km em 45min (pace 4:30/km, FC média 165)
Análises possíveis:
- Pace médio geral ⚠️
- FC média geral ⚠️
- Sem detalhamento ❌
```

---

## 4. Impacto Técnico

### 4.1 Banco de Dados

#### Estrutura

**Atual:**
- 1 tabela principal
- Relacionamento simples 1:N com TreinoProgramado

**Proposta:**
- 3 tabelas relacionadas
- Hierarquia: TreinoRealizado → EtapaTreinoRealizada → RepeticaoRealizada

#### Volume de Dados Estimado

**Cenário: 1000 usuários ativos, média 5 treinos/semana**

| Métrica | Atual | Proposta | Diferença |
|---------|-------|----------|-----------|
| **Registros/mês** | 20.000 treinos | 20.000 treinos<br>+ 80.000 etapas<br>+ 120.000 repetições | +10x registros |
| **Tamanho médio/registro** | ~500 bytes | Treino: 500 bytes<br>Etapa: 300 bytes<br>Repetição: 200 bytes | - |
| **Armazenamento/mês** | ~10 MB | ~58 MB | +5.8x |
| **Armazenamento/ano** | ~120 MB | ~700 MB | Insignificante |

**Conclusão:** Mesmo com 10x mais registros, o volume de dados continua extremamente gerenciável para PostgreSQL.

#### Índices Necessários

**Atual:**
```sql
CREATE INDEX idx_treinos_usuario_data 
ON treinos_realizados(usuario_id, data_hora_inicio DESC);
```

**Proposta:**
```sql
-- Índices principais
CREATE INDEX idx_treinos_usuario_data 
ON treinos_realizados(usuario_id, data_hora_inicio DESC);

CREATE INDEX idx_etapas_treino 
ON etapas_treino_realizadas(treino_realizado_id, ordem);

CREATE INDEX idx_etapas_tipo 
ON etapas_treino_realizadas(tipo);

CREATE INDEX idx_repeticoes_etapa 
ON repeticoes_realizadas(etapa_realizada_id, numero);

-- Índice para comparação planejado vs realizado
CREATE INDEX idx_etapas_programada 
ON etapas_treino_realizadas(etapa_programada_id);
```

### 4.2 Performance de Queries

#### Query Simples: Listar treinos do usuário

**Atual:**
```java
// 1 query
List<TreinoRealizado> treinos = treinoRepository
    .findByUsuarioIdOrderByDataHoraInicioDesc(usuarioId);
```

**Proposta:**
```java
// 1 query com JOIN FETCH
@Query("SELECT t FROM TreinoRealizado t " +
       "LEFT JOIN FETCH t.etapas e " +
       "LEFT JOIN FETCH e.repeticoes " +
       "WHERE t.usuarioId = :usuarioId " +
       "ORDER BY t.dataHoraInicio DESC")
List<TreinoRealizado> findByUsuarioIdWithDetails(@Param("usuarioId") Long usuarioId);
```

**Impacto:** Minimal com paginação adequada.

#### Query Complexa: Análise de progressão em intervalados

**Atual:**
```java
// ❌ IMPOSSÍVEL com a estrutura atual
// Requer lógica manual externa ou dados não estruturados
```

**Proposta:**
```java
@Query("""
    SELECT new com.menthoros.dto.ProgressaoIntervaladoDTO(
        t.dataHoraInicio,
        AVG(r.duracaoSegundos),
        STDDEV(r.duracaoSegundos),
        MIN(r.pace),
        MAX(r.pace)
    )
    FROM TreinoRealizado t
    JOIN t.etapas e
    JOIN e.repeticoes r
    WHERE t.usuarioId = :usuarioId
      AND e.tipo = 'INTERVALO'
      AND e.etapaProgramada.id = :etapaProgramadaId
      AND t.dataHoraInicio >= :dataInicio
    GROUP BY t.dataHoraInicio
    ORDER BY t.dataHoraInicio
""")
List<ProgressaoIntervaladoDTO> analisarProgressao(
    @Param("usuarioId") Long usuarioId,
    @Param("etapaProgramadaId") Long etapaProgramadaId,
    @Param("dataInicio") LocalDateTime dataInicio
);
```

**Impacto:** Query mais complexa, mas possibilita análises impossíveis anteriormente.

### 4.3 API REST

#### Atual: Endpoint de registro de treino
```java
@PostMapping("/treinos")
public ResponseEntity<TreinoRealizado> registrar(
    @RequestBody TreinoRealizadoDTO dto
) {
    TreinoRealizado treino = treinoService.salvar(dto);
    return ResponseEntity.ok(treino);
}
```

**Payload:**
```json
{
  "treinoProgramadoId": 123,
  "dataHoraInicio": "2026-02-08T06:00:00",
  "dataHoraFim": "2026-02-08T07:15:00",
  "distanciaKm": 12.5,
  "duracaoSegundos": 4500,
  "paceMedia": "6:00",
  "fcMedia": 155,
  "fcMaxima": 178,
  "sensacao": "BOM",
  "rpe": 7
}
```

#### Proposta: Endpoint com etapas e repetições
```java
@PostMapping("/treinos")
public ResponseEntity<TreinoRealizado> registrar(
    @RequestBody @Valid TreinoRealizadoComEtapasDTO dto
) {
    TreinoRealizado treino = treinoService.salvarComEtapas(dto);
    return ResponseEntity.ok(treino);
}
```

**Payload:**
```json
{
  "treinoProgramadoId": 123,
  "dataHoraInicio": "2026-02-08T06:00:00",
  "dataHoraFim": "2026-02-08T07:15:00",
  "sensacao": "BOM",
  "rpe": 7,
  "etapas": [
    {
      "ordem": 1,
      "etapaProgramadaId": 456,
      "tipo": "AQUECIMENTO",
      "distanciaRealizadaKm": 2.0,
      "duracaoRealizadaSegundos": 720,
      "paceRealizado": "6:00",
      "fcMedia": 140
    },
    {
      "ordem": 2,
      "etapaProgramadaId": 457,
      "tipo": "INTERVALO",
      "repeticoes": [
        {
          "numero": 1,
          "distanciaKm": 1.0,
          "duracaoSegundos": 255,
          "pace": "4:15",
          "fcMedia": 168,
          "fcMaxima": 172,
          "duracaoRecuperacaoSegundos": 90,
          "fcRecuperacao": 142
        },
        {
          "numero": 2,
          "distanciaKm": 1.0,
          "duracaoSegundos": 258,
          "pace": "4:18",
          "fcMedia": 172,
          "fcMaxima": 176,
          "duracaoRecuperacaoSegundos": 90,
          "fcRecuperacao": 145
        }
        // ... mais 8 repetições
      ]
    },
    {
      "ordem": 3,
      "etapaProgramadaId": 458,
      "tipo": "DESAQUECIMENTO",
      "distanciaRealizadaKm": 1.5,
      "duracaoRealizadaSegundos": 540,
      "paceRealizado": "6:00",
      "fcMedia": 135
    }
  ]
}
```

**Comparação:**
- **Atual:** ~200 bytes payload
- **Proposta:** ~1.5 KB payload (para 10 repetições)
- **Impacto:** Aceitável, considerando os benefícios analíticos

---

## 5. Capacidades de Análise Detalhadas

### 5.1 Métricas Impossíveis na Arquitetura Atual

#### 1. Decaimento de Performance
```java
@Service
public class AnaliseService {
    
    // ❌ Impossível com arquitetura atual
    // ✅ Possível com arquitetura proposta
    public DecaimentoDTO calcularDecaimento(Long treinoId) {
        TreinoRealizado treino = treinoRepository.findById(treinoId)
            .orElseThrow();
        
        EtapaTreinoRealizada etapaIntervalo = treino.getEtapas().stream()
            .filter(e -> e.getTipo() == TipoEtapa.INTERVALO)
            .findFirst()
            .orElseThrow();
        
        List<RepeticaoRealizada> reps = etapaIntervalo.getRepeticoes();
        
        double paceFirst = converterPaceParaSegundos(reps.get(0).getPace());
        double paceLast = converterPaceParaSegundos(reps.get(reps.size()-1).getPace());
        
        double decaimento = ((paceLast - paceFirst) / paceFirst) * 100;
        
        String avaliacao;
        if (decaimento < 3) avaliacao = "EXCELENTE - Resistência de elite";
        else if (decaimento < 5) avaliacao = "MUITO BOM - Boa resistência";
        else if (decaimento < 8) avaliacao = "BOM - Resistência adequada";
        else if (decaimento < 12) avaliacao = "REGULAR - Precisa melhorar base aeróbica";
        else avaliacao = "RUIM - Volume/intensidade inadequados";
        
        return new DecaimentoDTO(decaimento, avaliacao);
    }
}
```

#### 2. Consistência de Pace
```java
public ConsistenciaDTO calcularConsistencia(Long treinoId) {
    // Coeficiente de variação das repetições
    EtapaTreinoRealizada etapa = // ... buscar etapa intervalada
    
    double[] paces = etapa.getRepeticoes().stream()
        .mapToDouble(r -> converterPaceParaSegundos(r.getPace()))
        .toArray();
    
    double media = Arrays.stream(paces).average().orElse(0);
    double variancia = Arrays.stream(paces)
        .map(p -> Math.pow(p - media, 2))
        .average()
        .orElse(0);
    double desvioPadrao = Math.sqrt(variancia);
    double cv = (desvioPadrao / media) * 100;
    
    String avaliacao;
    if (cv < 2) avaliacao = "EXCELENTE - Pace muito consistente";
    else if (cv < 4) avaliacao = "BOM - Boa consistência";
    else if (cv < 6) avaliacao = "REGULAR - Variação moderada";
    else avaliacao = "RUIM - Muito irregular, ajustar intensidade";
    
    return new ConsistenciaDTO(cv, avaliacao);
}
```

#### 3. Eficiência de Recuperação Cardíaca
```java
public RecuperacaoCardiacaDTO analisarRecuperacao(Long treinoId) {
    EtapaTreinoRealizada etapa = // ... buscar etapa intervalada
    
    List<Integer> temposRecuperacao = etapa.getRepeticoes().stream()
        .map(r -> {
            int fcEsforco = r.getFcMaxima();
            int fcRecuperacao = r.getFcRecuperacao();
            int quedaFC = fcEsforco - fcRecuperacao;
            int tempoRec = r.getDuracaoRecuperacaoSegundos();
            return quedaFC; // queda em 90s, por exemplo
        })
        .collect(Collectors.toList());
    
    // Análise: queda de FC deveria ser >25 bpm em 90s
    double quedaMedia = temposRecuperacao.stream()
        .mapToInt(Integer::intValue)
        .average()
        .orElse(0);
    
    String avaliacao;
    if (quedaMedia > 30) avaliacao = "EXCELENTE - Sistema cardiovascular muito eficiente";
    else if (quedaMedia > 25) avaliacao = "BOM - Boa capacidade de recuperação";
    else if (quedaMedia > 20) avaliacao = "REGULAR - Precisa melhorar base aeróbica";
    else avaliacao = "RUIM - Considerar reduzir intensidade ou aumentar tempo de recuperação";
    
    return new RecuperacaoCardiacaDTO(quedaMedia, avaliacao);
}
```

#### 4. Drift Cardíaco em Longões
```java
public DriftCardiacoDTO calcularDrift(Long treinoId) {
    TreinoRealizado treino = treinoRepository.findById(treinoId)
        .orElseThrow();
    
    List<EtapaTreinoRealizada> etapasLongRun = treino.getEtapas().stream()
        .filter(e -> e.getTipo() == TipoEtapa.RITMO || e.getTipo() == TipoEtapa.LONG_RUN)
        .collect(Collectors.toList());
    
    int meio = etapasLongRun.size() / 2;
    
    double fcPrimeiraMetade = etapasLongRun.subList(0, meio).stream()
        .mapToInt(EtapaTreinoRealizada::getFcMedia)
        .average()
        .orElse(0);
    
    double fcSegundaMetade = etapasLongRun.subList(meio, etapasLongRun.size()).stream()
        .mapToInt(EtapaTreinoRealizada::getFcMedia)
        .average()
        .orElse(0);
    
    double drift = ((fcSegundaMetade - fcPrimeiraMetade) / fcPrimeiraMetade) * 100;
    
    String avaliacao;
    if (drift < 3) avaliacao = "EXCELENTE - Acoplamento aeróbico perfeito";
    else if (drift < 5) avaliacao = "BOM - Bom condicionamento aeróbico";
    else if (drift < 8) avaliacao = "MODERADO - Considerar reduzir pace ou melhorar hidratação";
    else avaliacao = "ALTO - Pace muito rápido para base aeróbica atual";
    
    return new DriftCardiacoDTO(drift, avaliacao);
}
```

### 5.2 Comparação com Treino Planejado
```java
public ComparacaoPlanejadomDTO comparar(Long treinoRealizadoId) {
    TreinoRealizado realizado = treinoRepository.findByIdWithEtapas(treinoRealizadoId)
        .orElseThrow();
    
    List<ComparacaoEtapaDTO> comparacoes = realizado.getEtapas().stream()
        .map(etapaRealizada -> {
            EtapaTreinoProgramada planejada = etapaRealizada.getEtapaProgramada();
            
            double pacePlanejadoSeg = converterPaceParaSegundos(planejada.getPaceAlvo());
            double paceRealizadoSeg = converterPaceParaSegundos(etapaRealizada.getPaceRealizado());
            
            double diferencaSeg = paceRealizadoSeg - pacePlanejadoSeg;
            double percentualDiferenca = (diferencaSeg / pacePlanejadoSeg) * 100;
            
            String status;
            if (Math.abs(percentualDiferenca) < 3) status = "DENTRO_META";
            else if (diferencaSeg < 0) status = "ACIMA_META"; // mais rápido
            else status = "ABAIXO_META"; // mais lento
            
            return ComparacaoEtapaDTO.builder()
                .tipoEtapa(etapaRealizada.getTipo())
                .pacePlanejado(planejada.getPaceAlvo())
                .paceRealizado(etapaRealizada.getPaceRealizado())
                .diferencaSegundos(diferencaSeg)
                .percentualDiferenca(percentualDiferenca)
                .status(status)
                .build();
        })
        .collect(Collectors.toList());
    
    return new ComparacaoPlanejadomDTO(comparacoes);
}
```

---

## 6. Estratégia de Migração

### 6.1 Abordagem Incremental

**Fase 1: Preparação (Sprint 1)**
- Criar novas tabelas sem remover as atuais
- Implementar DTOs e mapeamentos
- Desenvolver endpoints paralelos

**Fase 2: Dual Write (Sprint 2)**
- Novos treinos salvos em ambas estruturas
- Treinos antigos permanecem apenas na estrutura atual
- Testes A/B com usuários beta

**Fase 3: Migração de Dados (Sprint 3)**
- Script de migração para treinos históricos
- Validação de integridade
- Rollback preparado

**Fase 4: Cutover (Sprint 4)**
- Desativar endpoints antigos
- Remover código legacy
- Monitoramento intensivo

### 6.2 Script de Migração
```sql
-- Migração de treinos históricos (estrutura simplificada)
-- Cada treino antigo vira um treino com 1 etapa única

INSERT INTO etapas_treino_realizadas (
    treino_realizado_id,
    ordem,
    tipo,
    pace_realizado,
    distancia_realizada_km,
    duracao_realizada_segundos,
    fc_media,
    fc_maxima,
    cadencia_media,
    inicio,
    fim
)
SELECT 
    tr.id,
    1 as ordem,
    'RITMO' as tipo,
    tr.pace_media,
    tr.distancia_km,
    tr.duracao_segundos,
    tr.fc_media,
    tr.fc_maxima,
    tr.cadencia_media,
    tr.data_hora_inicio,
    tr.data_hora_fim
FROM treinos_realizados tr
WHERE NOT EXISTS (
    SELECT 1 FROM etapas_treino_realizadas e 
    WHERE e.treino_realizado_id = tr.id
);
```

### 6.3 Compatibilidade com Dados Antigos
```java
@Service
public class TreinoCompatibilidadeService {
    
    public TreinoRealizadoDTO buscar(Long id) {
        TreinoRealizado treino = treinoRepository.findById(id)
            .orElseThrow();
        
        // Treinos novos: tem etapas detalhadas
        if (!treino.getEtapas().isEmpty()) {
            return mapearComEtapas(treino);
        }
        
        // Treinos antigos: dados agregados apenas
        return mapearLegacy(treino);
    }
}
```

---

## 7. Análise de Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| **Complexidade aumenta bugs** | Média | Alto | Testes automatizados abrangentes, code review rigoroso |
| **Performance degradada** | Baixa | Médio | Índices adequados, paginação, caching |
| **Migração de dados falha** | Baixa | Alto | Scripts testados em staging, rollback preparado |
| **Usuários rejeitam complexidade** | Baixa | Médio | UX simplificado, entrada de dados opcional |
| **Overhead de desenvolvimento** | Alta | Médio | Desenvolvimento incremental, reutilizar código |

---

## 8. Retorno sobre Investimento (ROI)

### 8.1 Custos

**Desenvolvimento:**
- Modelagem e migrações: 8 horas
- Camada de serviço: 16 horas
- API REST: 12 horas
- Testes: 10 horas
- Migração de dados: 6 horas
- **Total:** ~52 horas (~1.5 sprints de 2 semanas)

**Infraestrutura:**
- Armazenamento adicional: desprezível (< $1/mês)
- Processamento: sem impacto significativo

### 8.2 Benefícios

**Imediatos:**
- ✅ Análises profundas impossíveis antes
- ✅ Comparação detalhada planejado vs realizado
- ✅ Diferencial competitivo vs apps concorrentes

**Médio Prazo:**
- ✅ Base para features de IA/ML
- ✅ Insights preditivos de performance
- ✅ Recomendações personalizadas de treino

**Longo Prazo:**
- ✅ Dados estruturados para pesquisa acadêmica
- ✅ Parcerias com treinadores profissionais
- ✅ Monetização via analytics premium

---

## 9. Casos de Uso Habilitados

### 9.1 Para o Atleta

**Dashboard de Progressão:**
```
Treino: 10x400m
┌─────────────────────────────────────┐
│ Semana 1: Pace médio 1:35 (CV: 4%)  │
│ Semana 2: Pace médio 1:33 (CV: 2%)  │ ✅ Melhoria
│ Semana 3: Pace médio 1:31 (CV: 1%)  │ ✅ Melhoria + Consistência
└─────────────────────────────────────┘
```

**Alerta Inteligente:**
```
⚠️ Seu decaimento no intervalo de hoje foi 12%, 
   acima da média histórica de 5%. 
   
Possíveis causas:
- Pace muito agressivo nas primeiras repetições
- Base aeróbica precisa ser desenvolvida
- Recuperação insuficiente entre treinos

Sugestão: Reduzir pace-alvo em 5s/km no próximo treino.
```

### 9.2 Para o Treinador (Feature Futura)

**Análise de Atletas:**
```java
@GetMapping("/treinador/atletas/{atletaId}/analise")
public AnaliseTreinadorDTO analisar(@PathVariable Long atletaId) {
    // Média de decaimento em intervalados últimos 30 dias
    // Tendência de drift cardíaco em longões
    // Comparação planejado vs realizado (% cumprimento)
    // Zonas de treino mais deficientes
}
```

### 9.3 Para IA/ML

**Predição de Performance:**
```python
# Features extraídas da estrutura proposta
features = [
    'pace_medio_ultimos_10_treinos',
    'decaimento_medio_intervalados',
    'drift_cardiaco_medio_longoes',
    'consistencia_pace_cv',
    'recuperacao_fc_media',
    'percentual_cumprimento_plano'
]

# Modelo treinado para prever tempo de prova
modelo.predict(features) # → "21km em 1:32:45 com 85% confiança"
```

---

## 10. Benchmarking de Mercado

| App | Etapas Detalhadas | Repetições | Comparação | Análise IA |
|-----|-------------------|------------|------------|------------|
| **Strava** | ⚠️ Limitado | ❌ Não | ⚠️ Básica | ❌ Não |
| **Garmin Connect** | ✅ Sim | ✅ Sim | ✅ Sim | ⚠️ Limitada |
| **TrainingPeaks** | ✅ Sim | ✅ Sim | ✅ Sim | ⚠️ Limitada |
| **Nike Run Club** | ❌ Não | ❌ Não | ❌ Não | ❌ Não |
| **Runkeeper** | ❌ Não | ❌ Não | ⚠️ Básica | ❌ Não |
| **Menthoros (Atual)** | ❌ Não | ❌ Não | ⚠️ Básica | ✅ Planejado |
| **Menthoros (Proposta)** | ✅ Sim | ✅ Sim | ✅ Completa | ✅ Sim |

**Conclusão:** A arquitetura proposta coloca o Menthoros no nível de apps profissionais como TrainingPeaks e Garmin Connect.

---

## 11. Recomendação Final

### ✅ RECOMENDADO: Implementar Arquitetura Proposta

**Justificativa:**

1. **Alinhamento com Visão do Produto**
   - Menthoros quer ser uma ferramenta de análise profunda, não apenas rastreador
   - IA/ML requer dados granulares e estruturados
   - Diferencial competitivo claro

2. **Viabilidade Técnica**
   - Complexidade gerenciável (~1.5 sprints)
   - Sem impacto de infraestrutura
   - Migração de dados segura

3. **ROI Positivo**
   - Investimento: 52 horas desenvolvimento
   - Retorno: Capacidades analíticas profissionais
   - Habilitação de features futuras (IA, treinadores, premium)

4. **Riscos Mitigáveis**
   - Desenvolvimento incremental
   - Rollback preparado
   - UX pode permanecer simples (complexidade no backend)

### Próximos Passos

1. **Imediato:** Aprovar arquitetura proposta
2. **Sprint 1:** Implementar modelo de dados + DTOs
3. **Sprint 2:** Endpoints REST + Service layer
4. **Sprint 3:** Migração dados históricos + Testes
5. **Sprint 4:** Deploy + Monitoramento

---

## 12. Apêndices

### A. Exemplo Completo de Payload

Ver seção 4.3 para payload JSON completo.

### B. Queries de Análise

Exemplos fornecidos nas seções 4.2 e 5.1.

### C. Referências

- TrainingPeaks Data Model: https://help.trainingpeaks.com
- Garmin FIT SDK: https://developer.garmin.com
- Running Analytics Best Practices (Daniels, Jack): "Running Formula"

---

**Documento elaborado por:** Leandro - Senior Software Engineer  
**Data:** 08/02/2026  
**Versão:** 1.0  
**Status:** Aguardando Aprovação