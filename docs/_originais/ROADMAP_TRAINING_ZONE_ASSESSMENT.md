# Roadmap de Implementação: Sistema de Avaliação de Zonas de Treinamento

## 📋 Visão Geral

Implementação de funcionalidade completa para avaliação de zonas de treinamento através de protocolos de teste (3K, 5K, 20min, 30min, Cooper), integrada ao sistema Menthoros de geração de planos de treino com IA.

**Objetivo**: Permitir que atletas realizem testes de campo para determinar suas zonas de treinamento personalizadas, melhorando a precisão dos planos gerados pela IA.

---

## 🎯 Entregas Principais

1. **Backend API**: Endpoints REST para CRUD de testes e cálculo de zonas
2. **Banco de Dados**: Schema para armazenar histórico de testes e zonas
3. **Motor de Cálculo**: Algoritmos de interpretação de cada protocolo
4. **Integração IA**: Uso de resultados para personalizar planos de treino
5. **Análise de Tendências**: Evolução de performance ao longo do tempo

---

## 📅 Fases de Implementação

### **FASE 1: Foundation (Semana 1-2)** 🏗️

#### 1.1. Modelagem de Dados
**Prioridade**: 🔴 Crítica
**Estimativa**: 3 dias

**Entidades a criar:**

```java
// src/main/java/com/menthoros/entity/AvaliacaoZona.java
@Entity
@Table(name = "avaliacao_zona")
public class AvaliacaoZona {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne
    @JoinColumn(name = "atleta_id")
    private Atleta atleta;

    @Enumerated(EnumType.STRING)
    private ProtocoloTeste protocolo; // TRES_K, CINCO_K, VINTE_MIN, etc

    private LocalDateTime dataAvaliacao;
    private Duration tempoTotal;
    private Integer distanciaMetros; // para Cooper
    private Integer fcMedia;
    private Integer fcMaxima;
    private Integer fcLimiar; // calculado

    @Convert(converter = PaceConverter.class)
    private Pace paceMedia;

    @Convert(converter = PaceConverter.class)
    private Pace paceLimiar; // calculado

    @ElementCollection
    private List<Integer> splits; // splits de 1km

    private String analiseConsistencia;
    private Integer vdotEstimado;

    @Embedded
    private ZonasTreinamento zonas; // Z1-Z5 calculadas

    private String observacoes;
    private Boolean ativa; // última avaliação ativa
}

// src/main/java/com/menthoros/entity/ZonasTreinamento.java
@Embeddable
public class ZonasTreinamento {
    // Zonas de FC
    private Integer fcZona1Min;
    private Integer fcZona1Max;
    private Integer fcZona2Min;
    private Integer fcZona2Max;
    private Integer fcZona3Min;
    private Integer fcZona3Max;
    private Integer fcZona4Min;
    private Integer fcZona4Max;
    private Integer fcZona5Min;

    // Zonas de Pace (em segundos por km)
    @Convert(converter = PaceConverter.class)
    private Pace paceZona1; // recuperação
    @Convert(converter = PaceConverter.class)
    private Pace paceZona2; // aeróbico leve
    @Convert(converter = PaceConverter.class)
    private Pace paceZona3; // tempo run
    @Convert(converter = PaceConverter.class)
    private Pace paceZona4; // limiar
    @Convert(converter = PaceConverter.class)
    private Pace paceZona5; // VO2max
}

// src/main/java/com/menthoros/enums/ProtocoloTeste.java
public enum ProtocoloTeste {
    TRES_K("3K", 3000, Duration.ofMinutes(11), Duration.ofMinutes(18)),
    CINCO_K("5K", 5000, Duration.ofMinutes(15), Duration.ofMinutes(30)),
    VINTE_MIN("20min", null, Duration.ofMinutes(20), Duration.ofMinutes(20)),
    TRINTA_MIN("30min", null, Duration.ofMinutes(30), Duration.ofMinutes(30)),
    COOPER("Cooper 12min", null, Duration.ofMinutes(12), Duration.ofMinutes(12));

    private String nome;
    private Integer distanciaFixa; // null para testes baseados em tempo
    private Duration duracaoMin;
    private Duration duracaoMax;
}
```

**Migration SQL:**
```sql
-- src/main/resources/db/migration/V15__Create_avaliacao_zona_tables.sql
CREATE TABLE avaliacao_zona (
    id BIGSERIAL PRIMARY KEY,
    atleta_id BIGINT NOT NULL REFERENCES atleta(id),
    protocolo VARCHAR(20) NOT NULL,
    data_avaliacao TIMESTAMP NOT NULL,
    tempo_total INTERVAL NOT NULL,
    distancia_metros INTEGER,
    fc_media INTEGER,
    fc_maxima INTEGER,
    fc_limiar INTEGER NOT NULL,
    pace_media_segundos INTEGER,
    pace_limiar_segundos INTEGER NOT NULL,
    splits INTEGER[],
    analise_consistencia TEXT,
    vdot_estimado INTEGER,

    -- Zonas de FC
    fc_zona1_min INTEGER NOT NULL,
    fc_zona1_max INTEGER NOT NULL,
    fc_zona2_min INTEGER NOT NULL,
    fc_zona2_max INTEGER NOT NULL,
    fc_zona3_min INTEGER NOT NULL,
    fc_zona3_max INTEGER NOT NULL,
    fc_zona4_min INTEGER NOT NULL,
    fc_zona4_max INTEGER NOT NULL,
    fc_zona5_min INTEGER NOT NULL,

    -- Zonas de Pace
    pace_zona1_segundos INTEGER NOT NULL,
    pace_zona2_segundos INTEGER NOT NULL,
    pace_zona3_segundos INTEGER NOT NULL,
    pace_zona4_segundos INTEGER NOT NULL,
    pace_zona5_segundos INTEGER NOT NULL,

    observacoes TEXT,
    ativa BOOLEAN DEFAULT true,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_splits_size CHECK (array_length(splits, 1) BETWEEN 1 AND 12)
);

CREATE INDEX idx_avaliacao_zona_atleta ON avaliacao_zona(atleta_id);
CREATE INDEX idx_avaliacao_zona_data ON avaliacao_zona(data_avaliacao DESC);
CREATE UNIQUE INDEX idx_avaliacao_zona_ativa ON avaliacao_zona(atleta_id) WHERE ativa = true;
```

**Critérios de Aceite:**
- ✅ Entidades criadas com validações corretas
- ✅ Migration executada com sucesso
- ✅ Testes unitários das entidades
- ✅ Repository criado e testado

---

#### 1.2. DTOs e Mappers
**Prioridade**: 🔴 Crítica
**Estimativa**: 2 dias

```java
// src/main/java/com/menthoros/dto/input/AvaliacaoZonaInputDto.java
public record AvaliacaoZonaInputDto(
    @NotNull ProtocoloTeste protocolo,
    @NotNull LocalDateTime dataAvaliacao,
    @NotNull String tempoTotal, // formato "MM:SS" ou "HH:MM:SS"
    Integer distanciaMetros, // obrigatório para Cooper
    Integer fcMedia,
    Integer fcMaxima,
    @NotNull String paceMedia, // formato "MM:SS"
    List<String> splits, // formato ["MM:SS", "MM:SS", ...]
    String observacoes
) {
    // Validações customizadas
    public AvaliacaoZonaInputDto {
        if (protocolo == ProtocoloTeste.COOPER && distanciaMetros == null) {
            throw new IllegalArgumentException("Distância obrigatória para protocolo Cooper");
        }
    }
}

// src/main/java/com/menthoros/dto/output/AvaliacaoZonaOutputDto.java
public record AvaliacaoZonaOutputDto(
    Long id,
    Long atletaId,
    String atletaNome,
    ProtocoloTeste protocolo,
    LocalDateTime dataAvaliacao,
    String tempoTotal,
    Integer distanciaMetros,
    Integer fcMedia,
    Integer fcMaxima,
    Integer fcLimiar,
    String paceMedia,
    String paceLimiar,
    List<SplitDto> splits,
    String analiseConsistencia,
    Integer vdotEstimado,
    ZonasTreinamentoDto zonas,
    String observacoes,
    Boolean ativa
) {}

public record ZonasTreinamentoDto(
    ZonaDto zona1,
    ZonaDto zona2,
    ZonaDto zona3,
    ZonaDto zona4,
    ZonaDto zona5
) {}

public record ZonaDto(
    Integer numero,
    String nome,
    String descricao,
    FaixaFcDto fc,
    String pace
) {}

public record FaixaFcDto(Integer min, Integer max) {}
public record SplitDto(Integer numero, String distancia, String tempo, String pace) {}

// src/main/java/com/menthoros/mapper/AvaliacaoZonaMapper.java
@Mapper(componentModel = "spring")
public interface AvaliacaoZonaMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "atleta", source = "atleta")
    @Mapping(target = "tempoTotal", expression = "java(parseDuration(dto.tempoTotal()))")
    @Mapping(target = "paceMedia", expression = "java(parsePace(dto.paceMedia()))")
    @Mapping(target = "splits", expression = "java(parseSplits(dto.splits()))")
    @Mapping(target = "fcLimiar", ignore = true) // calculado pelo service
    @Mapping(target = "paceLimiar", ignore = true) // calculado pelo service
    @Mapping(target = "zonas", ignore = true) // calculado pelo service
    @Mapping(target = "analiseConsistencia", ignore = true)
    @Mapping(target = "vdotEstimado", ignore = true)
    @Mapping(target = "ativa", constant = "true")
    AvaliacaoZona toEntity(AvaliacaoZonaInputDto dto, Atleta atleta);

    @Mapping(target = "atletaId", source = "atleta.id")
    @Mapping(target = "atletaNome", source = "atleta.nome")
    @Mapping(target = "tempoTotal", expression = "java(formatDuration(entity.getTempoTotal()))")
    @Mapping(target = "paceMedia", expression = "java(formatPace(entity.getPaceMedia()))")
    @Mapping(target = "paceLimiar", expression = "java(formatPace(entity.getPaceLimiar()))")
    @Mapping(target = "splits", expression = "java(formatSplits(entity.getSplits(), entity.getTempoTotal()))")
    @Mapping(target = "zonas", source = "zonas")
    AvaliacaoZonaOutputDto toDto(AvaliacaoZona entity);
}
```

**Critérios de Aceite:**
- ✅ DTOs com validações Bean Validation
- ✅ Mappers MapStruct funcionais
- ✅ Conversões de formato (Duration, Pace, Splits)
- ✅ Testes unitários dos mappers

---

### **FASE 2: Motor de Cálculo (Semana 3)** 🧮

#### 2.1. Service de Cálculo de Zonas
**Prioridade**: 🔴 Crítica
**Estimativa**: 5 dias

```java
// src/main/java/com/menthoros/services/CalculadoraZonasService.java
@Service
public class CalculadoraZonasService {

    /**
     * Calcula FC no limiar anaeróbico baseado no protocolo
     */
    public Integer calcularFcLimiar(ProtocoloTeste protocolo, Duration tempo, Integer fcMedia) {
        return switch (protocolo) {
            case TRES_K -> calcularFcLimiarTresK(tempo, fcMedia);
            case CINCO_K -> calcularFcLimiarCincoK(tempo, fcMedia);
            case VINTE_MIN -> calcularFcLimiarVinteMin(fcMedia);
            case TRINTA_MIN -> calcularFcLimiarTrintaMin(fcMedia);
            case COOPER -> calcularFcLimiarCooper(fcMedia);
        };
    }

    private Integer calcularFcLimiarTresK(Duration tempo, Integer fcMedia) {
        long minutos = tempo.toMinutes();
        double fatorAjuste;

        if (minutos < 10.5) fatorAjuste = 0.91;
        else if (minutos < 12.5) fatorAjuste = 0.93;
        else if (minutos < 14.5) fatorAjuste = 0.95;
        else if (minutos < 16.5) fatorAjuste = 0.96;
        else fatorAjuste = 0.97;

        return (int) Math.round(fcMedia * fatorAjuste);
    }

    /**
     * Calcula Pace no limiar anaeróbico
     */
    public Pace calcularPaceLimiar(ProtocoloTeste protocolo, Duration tempo, Integer distanciaMetros) {
        return switch (protocolo) {
            case TRES_K -> calcularPaceLimiarTresK(tempo);
            case CINCO_K -> calcularPaceLimiarCincoK(tempo);
            case VINTE_MIN -> calcularPaceLimiarTempoBased(tempo, distanciaMetros);
            case TRINTA_MIN -> calcularPaceLimiarTempoBased(tempo, distanciaMetros);
            case COOPER -> calcularPaceLimiarCooper(distanciaMetros);
        };
    }

    /**
     * Calcula as 5 zonas de treinamento (FC e Pace)
     */
    public ZonasTreinamento calcularZonas(Integer fcLimiar, Pace paceLimiar, Integer fcMaxima) {
        ZonasTreinamento zonas = new ZonasTreinamento();

        // Zona 1 (Recuperação): 60-70% LTHR
        zonas.setFcZona1Min((int) Math.round(fcLimiar * 0.60));
        zonas.setFcZona1Max((int) Math.round(fcLimiar * 0.70));
        zonas.setPaceZona1(paceLimiar.multiply(1.25)); // 25% mais lento

        // Zona 2 (Aeróbico Leve): 70-85% LTHR
        zonas.setFcZona2Min((int) Math.round(fcLimiar * 0.70));
        zonas.setFcZona2Max((int) Math.round(fcLimiar * 0.85));
        zonas.setPaceZona2(paceLimiar.multiply(1.15)); // 15% mais lento

        // Zona 3 (Tempo Run): 85-95% LTHR
        zonas.setFcZona3Min((int) Math.round(fcLimiar * 0.85));
        zonas.setFcZona3Max((int) Math.round(fcLimiar * 0.95));
        zonas.setPaceZona3(paceLimiar.multiply(1.05)); // 5% mais lento

        // Zona 4 (Limiar): 95-105% LTHR
        zonas.setFcZona4Min((int) Math.round(fcLimiar * 0.95));
        zonas.setFcZona4Max((int) Math.round(fcLimiar * 1.05));
        zonas.setPaceZona4(paceLimiar); // pace do limiar

        // Zona 5 (VO2max): 105%+ LTHR ou até FCmax
        zonas.setFcZona5Min((int) Math.round(fcLimiar * 1.05));
        zonas.setPaceZona5(paceLimiar.multiply(0.92)); // 8% mais rápido

        return zonas;
    }

    /**
     * Analisa consistência dos splits
     */
    public String analisarSplits(ProtocoloTeste protocolo, List<Integer> splits) {
        if (splits == null || splits.isEmpty()) {
            return "Sem dados de splits";
        }

        return switch (protocolo) {
            case TRES_K -> analisarSplitsTresK(splits);
            case CINCO_K -> analisarSplitsCincoK(splits);
            default -> analisarSplitsGenerico(splits);
        };
    }

    private String analisarSplitsTresK(List<Integer> splits) {
        if (splits.size() != 3) {
            return "Dados insuficientes (esperado 3 splits de 1km)";
        }

        int primeiro = splits.get(0);
        int segundo = splits.get(1);
        int terceiro = splits.get(2);

        double variacaoMaxima = calcularCoeficienteVariacao(splits);
        int variacaoPrimeiroUltimo = Math.abs(terceiro - primeiro);

        if (terceiro < segundo && segundo < primeiro && variacaoPrimeiroUltimo > 10) {
            return "Excelente - negative split progressivo (pacing perfeito)";
        } else if (variacaoMaxima < 5) {
            return "Muito bom - pacing constante (even pace)";
        } else if (primeiro < segundo && segundo < terceiro && variacaoPrimeiroUltimo > 30) {
            return "Atenção - positive split acentuado (largou muito rápido)";
        } else if (variacaoMaxima > 15) {
            return "Irregular - grandes variações de ritmo";
        } else {
            return "Aceitável - pequenas variações de ritmo";
        }
    }

    /**
     * Calcula VDOT baseado no tempo e distância
     */
    public Integer calcularVdot(ProtocoloTeste protocolo, Duration tempo, Integer distanciaMetros) {
        // Implementação baseada nas tabelas de Jack Daniels
        double velocidadeMS = distanciaMetros / (double) tempo.getSeconds();
        double percentVO2max = calcularPercentVO2max(tempo.toMinutes());
        double vo2 = (-4.60 + 0.182258 * velocidadeMS * 60 + 0.000104 * Math.pow(velocidadeMS * 60, 2)) / percentVO2max;
        return (int) Math.round(vo2);
    }
}
```

**Arquivos a criar:**
- `CalculadoraZonasService.java`: Lógica de cálculo principal
- `ProtocoloStrategy.java`: Interface para estratégias de protocolo
- `TresKProtocoloStrategy.java`, `CincoKProtocoloStrategy.java`, etc.
- `VdotCalculator.java`: Cálculos VDOT (Jack Daniels)

**Critérios de Aceite:**
- ✅ Cálculos de FC limiar para todos os protocolos
- ✅ Cálculos de Pace limiar para todos os protocolos
- ✅ Geração correta das 5 zonas (FC e Pace)
- ✅ Análise de splits funcionando
- ✅ Cálculo de VDOT implementado
- ✅ Testes unitários com casos edge (100% cobertura)
- ✅ Validação com dados reais de assessorias

---

#### 2.2. Service Principal de Avaliação
**Prioridade**: 🔴 Crítica
**Estimativa**: 3 dias

```java
// src/main/java/com/menthoros/services/AvaliacaoZonaService.java
@Service
@RequiredArgsConstructor
public class AvaliacaoZonaService {

    private final AvaliacaoZonaRepository repository;
    private final AtletaRepository atletaRepository;
    private final CalculadoraZonasService calculadora;
    private final AvaliacaoZonaMapper mapper;

    /**
     * Cria nova avaliação de zona
     */
    @Transactional
    public AvaliacaoZonaOutputDto criar(Long atletaId, AvaliacaoZonaInputDto dto) {
        Atleta atleta = atletaRepository.findById(atletaId)
            .orElseThrow(() -> new EntityNotFoundException("Atleta não encontrado"));

        // Desativa avaliações anteriores
        repository.desativarAvaliacoesAtleta(atletaId);

        // Converte para entidade
        AvaliacaoZona avaliacao = mapper.toEntity(dto, atleta);

        // Calcula métricas derivadas
        Integer fcLimiar = calculadora.calcularFcLimiar(
            dto.protocolo(),
            parseDuration(dto.tempoTotal()),
            dto.fcMedia()
        );
        avaliacao.setFcLimiar(fcLimiar);

        Pace paceLimiar = calculadora.calcularPaceLimiar(
            dto.protocolo(),
            parseDuration(dto.tempoTotal()),
            dto.distanciaMetros()
        );
        avaliacao.setPaceLimiar(paceLimiar);

        // Calcula zonas de treinamento
        ZonasTreinamento zonas = calculadora.calcularZonas(
            fcLimiar,
            paceLimiar,
            dto.fcMaxima()
        );
        avaliacao.setZonas(zonas);

        // Analisa splits
        if (dto.splits() != null && !dto.splits().isEmpty()) {
            String analise = calculadora.analisarSplits(
                dto.protocolo(),
                parseSplits(dto.splits())
            );
            avaliacao.setAnaliseConsistencia(analise);
        }

        // Calcula VDOT
        Integer vdot = calculadora.calcularVdot(
            dto.protocolo(),
            parseDuration(dto.tempoTotal()),
            getDistanciaMetros(dto)
        );
        avaliacao.setVdotEstimado(vdot);

        // Salva
        AvaliacaoZona saved = repository.save(avaliacao);

        return mapper.toDto(saved);
    }

    /**
     * Busca avaliação ativa do atleta
     */
    public Optional<AvaliacaoZonaOutputDto> buscarAvaliacaoAtiva(Long atletaId) {
        return repository.findByAtletaIdAndAtivaTrue(atletaId)
            .map(mapper::toDto);
    }

    /**
     * Histórico de avaliações do atleta
     */
    public List<AvaliacaoZonaOutputDto> buscarHistorico(Long atletaId, LocalDate inicio, LocalDate fim) {
        return repository.findByAtletaIdAndDataAvaliacaoBetween(
            atletaId,
            inicio.atStartOfDay(),
            fim.atTime(23, 59, 59)
        ).stream()
        .map(mapper::toDto)
        .toList();
    }

    /**
     * Análise de evolução (comparação temporal)
     */
    public EvolucaoZonasDto analisarEvolucao(Long atletaId, ProtocoloTeste protocolo) {
        List<AvaliacaoZona> avaliacoes = repository.findByAtletaIdAndProtocoloOrderByDataAvaliacaoDesc(
            atletaId,
            protocolo
        );

        if (avaliacoes.size() < 2) {
            throw new IllegalStateException("Mínimo de 2 avaliações necessárias para análise");
        }

        AvaliacaoZona atual = avaliacoes.get(0);
        AvaliacaoZona anterior = avaliacoes.get(1);

        return EvolucaoZonasDto.builder()
            .protocoloTeste(protocolo)
            .dataAnterior(anterior.getDataAvaliacao())
            .dataAtual(atual.getDataAvaliacao())
            .evolucaoTempo(calcularEvolucaoTempo(anterior, atual))
            .evolucaoFcLimiar(atual.getFcLimiar() - anterior.getFcLimiar())
            .evolucaoPaceLimiar(calcularEvolucaoPace(anterior.getPaceLimiar(), atual.getPaceLimiar()))
            .evolucaoVdot(atual.getVdotEstimado() - anterior.getVdotEstimado())
            .analise(gerarAnaliseEvolucao(anterior, atual))
            .build();
    }
}
```

**Critérios de Aceite:**
- ✅ CRUD completo de avaliações
- ✅ Desativação automática de avaliações antigas
- ✅ Cálculo automático de todas as métricas
- ✅ Busca de avaliação ativa funcionando
- ✅ Histórico com filtros de data
- ✅ Análise de evolução comparativa
- ✅ Testes de integração com banco H2

---

### **FASE 3: API REST (Semana 4)** 🌐

#### 3.1. Controllers
**Prioridade**: 🟡 Alta
**Estimativa**: 3 dias

```java
// src/main/java/com/menthoros/controller/AvaliacaoZonaController.java
@RestController
@RequestMapping("/api/atletas/{atletaId}/avaliacoes-zona")
@RequiredArgsConstructor
@Tag(name = "Avaliações de Zona", description = "Gerenciamento de testes e zonas de treinamento")
public class AvaliacaoZonaController {

    private final AvaliacaoZonaService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar nova avaliação de zona")
    public AvaliacaoZonaOutputDto criar(
        @PathVariable Long atletaId,
        @Valid @RequestBody AvaliacaoZonaInputDto dto
    ) {
        return service.criar(atletaId, dto);
    }

    @GetMapping("/ativa")
    @Operation(summary = "Buscar avaliação ativa atual")
    public ResponseEntity<AvaliacaoZonaOutputDto> buscarAtiva(@PathVariable Long atletaId) {
        return service.buscarAvaliacaoAtiva(atletaId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Histórico de avaliações")
    public List<AvaliacaoZonaOutputDto> listarHistorico(
        @PathVariable Long atletaId,
        @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate inicio,
        @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate fim
    ) {
        LocalDate dataInicio = inicio != null ? inicio : LocalDate.now().minusYears(1);
        LocalDate dataFim = fim != null ? fim : LocalDate.now();
        return service.buscarHistorico(atletaId, dataInicio, dataFim);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar avaliação específica")
    public AvaliacaoZonaOutputDto buscarPorId(
        @PathVariable Long atletaId,
        @PathVariable Long id
    ) {
        return service.buscarPorId(id);
    }

    @GetMapping("/evolucao/{protocolo}")
    @Operation(summary = "Análise de evolução por protocolo")
    public EvolucaoZonasDto analisarEvolucao(
        @PathVariable Long atletaId,
        @PathVariable ProtocoloTeste protocolo
    ) {
        return service.analisarEvolucao(atletaId, protocolo);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar observações de avaliação")
    public AvaliacaoZonaOutputDto atualizarObservacoes(
        @PathVariable Long atletaId,
        @PathVariable Long id,
        @RequestBody ObservacoesDto dto
    ) {
        return service.atualizarObservacoes(id, dto.observacoes());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Excluir avaliação")
    public void excluir(
        @PathVariable Long atletaId,
        @PathVariable Long id
    ) {
        service.excluir(id);
    }
}

// Controller adicional para calculadora
@RestController
@RequestMapping("/api/calculadoras/zonas")
@RequiredArgsConstructor
@Tag(name = "Calculadoras", description = "Utilitários de cálculo")
public class CalculadoraZonasController {

    private final CalculadoraZonasService calculadora;

    @PostMapping("/simular")
    @Operation(summary = "Simular cálculo de zonas (sem salvar)")
    public SimulacaoZonasDto simular(@Valid @RequestBody SimulacaoInputDto dto) {
        // Permite ao usuário testar cálculos antes de criar avaliação oficial
        Integer fcLimiar = calculadora.calcularFcLimiar(dto.protocolo(), dto.tempo(), dto.fcMedia());
        Pace paceLimiar = calculadora.calcularPaceLimiar(dto.protocolo(), dto.tempo(), dto.distancia());
        ZonasTreinamento zonas = calculadora.calcularZonas(fcLimiar, paceLimiar, dto.fcMaxima());

        return new SimulacaoZonasDto(fcLimiar, paceLimiar, zonas);
    }

    @PostMapping("/equivalencias")
    @Operation(summary = "Calcular equivalências entre protocolos")
    public EquivalenciasDto calcularEquivalencias(@Valid @RequestBody TempoDistanciaDto dto) {
        // Ex: tempo de 3K -> estimativa de 5K, 10K, etc.
        return calculadora.calcularEquivalencias(dto.protocolo(), dto.tempo());
    }
}
```

**Endpoints principais:**
- `POST /api/atletas/{id}/avaliacoes-zona` - Criar avaliação
- `GET /api/atletas/{id}/avaliacoes-zona/ativa` - Avaliação ativa
- `GET /api/atletas/{id}/avaliacoes-zona` - Histórico
- `GET /api/atletas/{id}/avaliacoes-zona/evolucao/{protocolo}` - Evolução
- `POST /api/calculadoras/zonas/simular` - Simulação (sem salvar)

**Critérios de Aceite:**
- ✅ Todos os endpoints documentados (OpenAPI)
- ✅ Validações funcionando (Bean Validation)
- ✅ Tratamento de erros adequado
- ✅ Testes de integração (MockMvc)
- ✅ Segurança (apenas atleta dono pode acessar)

---

### **FASE 4: Integração com IA (Semana 5)** 🤖

#### 4.1. Uso de Zonas na Geração de Planos
**Prioridade**: 🟡 Alta
**Estimativa**: 4 dias

**Modificações no serviço de IA:**

```java
// src/main/java/com/menthoros/services/impl/IaServiceImpl.java
@Service
public class IaServiceImpl {

    private final AvaliacaoZonaService avaliacaoService;

    public PlanoTreinoOutputDto gerarPlano(PlanoTreinoInputDto input, Long atletaId) {
        // Busca avaliação de zona ativa (se existir)
        Optional<AvaliacaoZonaOutputDto> avaliacaoAtiva =
            avaliacaoService.buscarAvaliacaoAtiva(atletaId);

        // Monta prompt com zonas personalizadas
        String promptPersonalizado = avaliacaoAtiva
            .map(av -> buildPromptComZonas(input, av))
            .orElseGet(() -> buildPromptPadrao(input));

        // ... resto da geração
    }

    private String buildPromptComZonas(PlanoTreinoInputDto input, AvaliacaoZonaOutputDto avaliacao) {
        return """
            DADOS DE AVALIAÇÃO DE ZONA PERSONALIZADA:

            Protocolo realizado: %s em %s
            Data da avaliação: %s

            ZONAS DE FREQUÊNCIA CARDÍACA:
            - Zona 1 (Recuperação): %d-%d bpm
            - Zona 2 (Aeróbico Leve): %d-%d bpm
            - Zona 3 (Tempo Run): %d-%d bpm
            - Zona 4 (Limiar): %d-%d bpm
            - Zona 5 (VO2max): %d+ bpm

            ZONAS DE PACE:
            - Zona 1: %s /km (fácil)
            - Zona 2: %s /km (confortável)
            - Zona 3: %s /km (moderado)
            - Zona 4: %s /km (forte, no limiar)
            - Zona 5: %s /km (muito forte)

            VDOT estimado: %d

            INSTRUÇÕES:
            Use EXATAMENTE essas zonas para prescrever intensidades nos treinos.
            Não use percentuais genéricos - use os valores calculados acima.

            [... resto do prompt com dados do input ...]
            """.formatted(
                avaliacao.protocolo(),
                avaliacao.tempoTotal(),
                avaliacao.dataAvaliacao(),
                // ... todos os valores das zonas
            );
    }
}
```

**Modificações no prompt builder:**

```java
// src/main/java/com/menthoros/services/prompt/PlanoTreinoPromptBuilder.java
public class PlanoTreinoPromptBuilder {

    public String buildPrompt(PlanoTreinoInputDto input, Optional<AvaliacaoZonaOutputDto> avaliacao) {
        StringBuilder prompt = new StringBuilder();

        // Seção de contexto do atleta
        prompt.append("PERFIL DO ATLETA:\n");
        prompt.append(buildPerfilAtleta(input));

        // NOVO: Seção de zonas personalizadas
        if (avaliacao.isPresent()) {
            prompt.append("\n\n");
            prompt.append("ZONAS PERSONALIZADAS (uso obrigatório):\n");
            prompt.append(buildSecaoZonasPersonalizadas(avaliacao.get()));
        } else {
            prompt.append("\n\n");
            prompt.append("NOTA: Atleta ainda não possui avaliação de zona.\n");
            prompt.append("Use zonas genéricas baseadas em FC máxima ou percepção de esforço.\n");
        }

        // ... resto do prompt

        return prompt.toString();
    }
}
```

**Critérios de Aceite:**
- ✅ Prompt inclui zonas quando disponíveis
- ✅ Planos gerados respeitam zonas personalizadas
- ✅ Fallback para zonas genéricas funciona
- ✅ Testes validando personalização
- ✅ Comparação qualitativa: plano com/sem zonas

---

#### 4.2. RAG com Histórico de Avaliações
**Prioridade**: 🟢 Média
**Estimativa**: 3 dias

**Vetorização de avaliações:**

```java
// src/main/java/com/menthoros/services/AvaliacaoZonaVetorizacaoService.java
@Service
@RequiredArgsConstructor
public class AvaliacaoZonaVetorizacaoService {

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;

    /**
     * Vetoriza nova avaliação para uso em RAG
     */
    @Async
    public void vetorizarAvaliacao(AvaliacaoZona avaliacao) {
        String textoAvaliacao = gerarTextoAvaliacao(avaliacao);

        List<Double> embedding = embeddingModel.embed(textoAvaliacao);

        Document doc = Document.builder()
            .content(textoAvaliacao)
            .metadata(Map.of(
                "tipo", "avaliacao_zona",
                "atletaId", avaliacao.getAtleta().getId(),
                "protocolo", avaliacao.getProtocolo().name(),
                "dataAvaliacao", avaliacao.getDataAvaliacao().toString(),
                "vdot", avaliacao.getVdotEstimado(),
                "fcLimiar", avaliacao.getFcLimiar()
            ))
            .embedding(embedding)
            .build();

        vectorStore.add(List.of(doc));
    }

    private String gerarTextoAvaliacao(AvaliacaoZona av) {
        return """
            Avaliação de Zona de Treinamento
            Atleta: %s
            Protocolo: %s realizado em %s
            Resultado: tempo de %s
            FC no limiar: %d bpm
            Pace no limiar: %s /km
            VDOT: %d
            Análise de splits: %s
            Zonas calculadas:
            - Z1: %d-%d bpm / %s /km
            - Z2: %d-%d bpm / %s /km
            - Z3: %d-%d bpm / %s /km
            - Z4: %d-%d bpm / %s /km
            - Z5: %d+ bpm / %s /km
            Observações: %s
            """.formatted(
                av.getAtleta().getNome(),
                av.getProtocolo(),
                av.getDataAvaliacao(),
                av.getTempoTotal(),
                av.getFcLimiar(),
                av.getPaceLimiar(),
                av.getVdotEstimado(),
                av.getAnaliseConsistencia(),
                // ... todas as zonas
                av.getObservacoes()
            );
    }

    /**
     * Busca avaliações similares para contexto
     */
    public List<AvaliacaoZona> buscarAvaliacoesSimilares(String query, Long atletaId, int k) {
        List<Double> queryEmbedding = embeddingModel.embed(query);

        List<Document> similares = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .embedding(queryEmbedding)
                .topK(k)
                .filterExpression("atletaId == " + atletaId)
                .build()
        );

        return similares.stream()
            .map(doc -> (Long) doc.getMetadata().get("avaliacaoId"))
            .map(avaliacaoRepository::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }
}
```

**Uso no prompt:**

```java
// Enriquece prompt com histórico relevante
List<AvaliacaoZona> historicoRelevante = vetorizacaoService.buscarAvaliacoesSimilares(
    "evoluções de performance para periodização",
    atletaId,
    3
);

if (!historicoRelevante.isEmpty()) {
    prompt.append("\n\nHISTÓRICO DE EVOLUÇÃO:\n");
    historicoRelevante.forEach(av -> {
        prompt.append("- %s: VDOT %d, Pace limiar %s\n".formatted(
            av.getDataAvaliacao(), av.getVdotEstimado(), av.getPaceLimiar()
        ));
    });
}
```

**Critérios de Aceite:**
- ✅ Avaliações vetorizadas automaticamente
- ✅ Busca por similaridade funcionando
- ✅ Contexto histórico enriquece prompts
- ✅ Performance aceitável (< 200ms busca)

---

### **FASE 5: Análises Avançadas (Semana 6)** 📊

#### 5.1. Dashboard de Evolução
**Prioridade**: 🟢 Média
**Estimativa**: 3 dias

```java
// src/main/java/com/menthoros/services/AnaliseEvolucaoService.java
@Service
public class AnaliseEvolucaoService {

    /**
     * Gera relatório completo de evolução
     */
    public RelatorioEvolucaoDto gerarRelatorio(Long atletaId, Period periodo) {
        LocalDateTime inicio = LocalDateTime.now().minus(periodo);
        List<AvaliacaoZona> avaliacoes = repository
            .findByAtletaIdAndDataAvaliacaoAfterOrderByDataAvaliacaoAsc(atletaId, inicio);

        return RelatorioEvolucaoDto.builder()
            .atletaId(atletaId)
            .periodo(periodo)
            .numeroAvaliacoes(avaliacoes.size())
            .evolucaoVdot(calcularEvolucaoVdot(avaliacoes))
            .evolucaoFcLimiar(calcularEvolucaoFcLimiar(avaliacoes))
            .evolucaoPaceLimiar(calcularEvolucaoPaceLimiar(avaliacoes))
            .graficos(gerarDadosGraficos(avaliacoes))
            .insights(gerarInsightsIA(avaliacoes))
            .recomendacoes(gerarRecomendacoes(avaliacoes))
            .build();
    }

    /**
     * Usa IA para gerar insights sobre evolução
     */
    private String gerarInsightsIA(List<AvaliacaoZona> avaliacoes) {
        String contexto = avaliacoes.stream()
            .map(this::formatarAvaliacaoParaIA)
            .collect(Collectors.joining("\n"));

        String prompt = """
            Analise a evolução deste atleta e gere insights:

            %s

            Forneça:
            1. Tendências identificadas
            2. Pontos fortes
            3. Áreas de melhoria
            4. Próximos passos recomendados
            """.formatted(contexto);

        return chatClient.call(prompt);
    }
}
```

**Endpoints:**
- `GET /api/atletas/{id}/avaliacoes-zona/relatorio?periodo=6M` - Relatório de evolução
- `GET /api/atletas/{id}/avaliacoes-zona/graficos` - Dados para gráficos

**Critérios de Aceite:**
- ✅ Cálculo de tendências (regressão linear)
- ✅ Detecção de platôs e regressões
- ✅ Insights gerados por IA
- ✅ Dados formatados para gráficos frontend

---

#### 5.2. Alertas e Recomendações
**Prioridade**: 🟢 Baixa
**Estimativa**: 2 dias

```java
// src/main/java/com/menthoros/services/AlertasZonaService.java
@Service
public class AlertasZonaService {

    @Scheduled(cron = "0 0 8 * * *") // Diariamente às 8h
    public void verificarAvaliacoesDesatualizadas() {
        LocalDateTime limite = LocalDateTime.now().minusMonths(3);

        List<Atleta> atletasDesatualizados = atletaRepository
            .findAtletasComAvaliacaoAntigaOuSemAvaliacao(limite);

        atletasDesatualizados.forEach(atleta -> {
            notificacaoService.enviar(Notificacao.builder()
                .atletaId(atleta.getId())
                .tipo(TipoNotificacao.ALERTA_AVALIACAO_ZONA)
                .titulo("Hora de atualizar suas zonas de treinamento!")
                .mensagem("Sua última avaliação tem mais de 3 meses. Fazer um novo teste ajudará a ajustar seus treinos.")
                .acaoSugerida("/avaliacoes-zona/nova")
                .build()
            );
        });
    }

    /**
     * Sugere quando fazer nova avaliação baseado em progresso
     */
    public SugestaoAvaliacaoDto sugerirNovaAvaliacao(Long atletaId) {
        Optional<AvaliacaoZona> ultimaAvaliacao = repository.findUltimaAvaliacao(atletaId);

        if (ultimaAvaliacao.isEmpty()) {
            return SugestaoAvaliacaoDto.builder()
                .deveFazer(true)
                .motivo("Você ainda não possui avaliação de zona")
                .urgencia("ALTA")
                .build();
        }

        AvaliacaoZona ultima = ultimaAvaliacao.get();
        long diasDesdeUltima = ChronoUnit.DAYS.between(
            ultima.getDataAvaliacao(),
            LocalDateTime.now()
        );

        // Verifica se houve treinos de qualidade recentes
        int treinosQualidadeRecentes = treinoRepository
            .countByAtletaIdAndTipoInAndDataAfter(
                atletaId,
                List.of(TipoTreino.INTERVALO, TipoTreino.TEMPO_RUN, TipoTreino.LONGO),
                ultima.getDataAvaliacao()
            );

        if (diasDesdeUltima > 90) {
            return SugestaoAvaliacaoDto.builder()
                .deveFazer(true)
                .motivo("Última avaliação há %d dias (ideal: máximo 90 dias)".formatted(diasDesdeUltima))
                .urgencia("ALTA")
                .build();
        } else if (treinosQualidadeRecentes > 40) {
            return SugestaoAvaliacaoDto.builder()
                .deveFazer(true)
                .motivo("Você fez %d treinos de qualidade desde a última avaliação. Provavelmente melhorou!".formatted(treinosQualidadeRecentes))
                .urgencia("MÉDIA")
                .protocoloSugerido(ultima.getProtocolo()) // Usar mesmo protocolo para comparar
                .build();
        }

        return SugestaoAvaliacaoDto.builder()
            .deveFazer(false)
            .motivo("Suas zonas ainda estão atualizadas")
            .build();
    }
}
```

**Critérios de Aceite:**
- ✅ Alertas automáticos para avaliações antigas
- ✅ Sugestões inteligentes de quando reavaliar
- ✅ Notificações configuráveis

---

## 🧪 Testes e Qualidade (Contínuo)

### Cobertura de Testes
- **Unit Tests**: 80%+ cobertura
  - Todos os cálculos (CalculadoraZonasService)
  - Mappers (AvaliacaoZonaMapper)
  - Lógica de negócio (AvaliacaoZonaService)

- **Integration Tests**: Endpoints principais
  - Criar avaliação (happy path + validações)
  - Buscar avaliação ativa
  - Histórico e evolução
  - Integração com IA

- **E2E Tests**: Fluxos críticos
  - Atleta faz teste 3K → Zonas calculadas → Plano gerado usa zonas

### Validações com Dados Reais
- Comparar cálculos com planilhas de assessorias brasileiras
- Validar equivalências 3K ↔ 5K com dados históricos
- Testar edge cases (tempos muito rápidos/lentos)

---

## 📦 Entregáveis por Fase

| Fase | Duração | Entregáveis | Dependências |
|------|---------|-------------|--------------|
| **1. Foundation** | 2 semanas | Entities, DTOs, Migrations, Repositories | - |
| **2. Motor de Cálculo** | 1 semana | CalculadoraZonasService, Strategies, Testes | Fase 1 |
| **3. API REST** | 1 semana | Controllers, Validações, Docs OpenAPI | Fase 1, 2 |
| **4. Integração IA** | 1 semana | Prompts personalizados, RAG | Fase 1, 2, 3 |
| **5. Análises Avançadas** | 1 semana | Dashboard, Alertas, Insights | Fase 1, 2, 3 |

**Total: 6 semanas (~1.5 meses)**

---

## ⚠️ Riscos e Mitigações

| Risco | Impacto | Probabilidade | Mitigação |
|-------|---------|---------------|-----------|
| Cálculos imprecisos de zonas | 🔴 Alto | Média | Validar com especialistas + dados reais |
| Performance em queries de histórico | 🟡 Médio | Baixa | Índices adequados + paginação |
| Integração IA não melhora planos | 🟡 Médio | Média | Testes A/B comparativos |
| Adoção baixa pelos atletas | 🟡 Médio | Média | Gamificação + notificações + educação |
| Complexidade para usuário iniciante | 🟢 Baixo | Alta | Wizards guiados + sugestões automáticas |

---

## 🎓 Educação e Onboarding

### Materiais a Criar
1. **Tutorial interativo**: "Como fazer seu primeiro teste de zona"
2. **Vídeo explicativo**: "Por que zonas personalizadas importam"
3. **FAQ**: Perguntas comuns sobre protocolos
4. **Tooltips**: Explicações inline no app

### Conteúdo Educacional
- Diferença entre protocolos (quando usar cada um)
- Como interpretar resultados
- Importância de reavaliar periodicamente
- Dicas para executar testes corretamente

---

## 📈 Métricas de Sucesso

### KPIs Técnicos
- ✅ Cobertura de testes > 80%
- ✅ Latência p95 < 500ms (cálculos)
- ✅ Latência p95 < 2s (geração de plano com IA)
- ✅ Zero bugs críticos em produção

### KPIs de Produto
- 📊 **Taxa de adoção**: 40%+ dos atletas fazem pelo menos 1 avaliação nos primeiros 30 dias
- 📊 **Reavaliação**: 60%+ fazem segunda avaliação em 90 dias
- 📊 **Satisfação**: NPS > 50 para feature de zonas
- 📊 **Impacto**: Planos com zonas têm 25%+ mais aderência

### Métricas de Negócio
- 💰 Redução de 20% em churn (retenção melhor com personalização)
- 💰 Aumento de 15% em conversão trial → pago
- 💰 Diferencial competitivo claro vs concorrentes

---

## 🚀 Próximos Passos (Pós-MVP)

### Fase 6: Expansão (Futuro)
- **Protocolos adicionais**: Rampa incremental, FTP ciclismo, etc.
- **Integração com wearables**: Importar dados de Garmin/Polar
- **Testes guiados por áudio**: App mobile com coach virtual
- **Comparação com pares**: Benchmarking anônimo
- **Certificação de testes**: Validação por profissional

### Fase 7: IA Avançada (Futuro)
- **Predição de resultados**: Estimar tempo de prova futuro
- **Detecção de overtraining**: Alertas preventivos
- **Ajuste dinâmico**: Recalcular zonas sem teste (baseado em treinos)

---

## 📞 Stakeholders e Responsabilidades

| Papel | Responsabilidade | Pessoa |
|-------|------------------|--------|
| **Product Owner** | Priorização, validação de requisitos | [Nome] |
| **Tech Lead** | Arquitetura, code review | [Nome] |
| **Backend Developer** | Implementação (Spring Boot) | [Nome] |
| **Data Scientist** | Validação de cálculos, algoritmos | [Nome] |
| **QA Engineer** | Testes, validação com dados reais | [Nome] |
| **Especialista Corrida** | Validação de protocolos, educação | [Nome] |

---

## ✅ Checklist de Finalização

### Antes de ir para Produção
- [ ] Todos os testes passando (unit + integration + e2e)
- [ ] Validação com 3+ assessorias esportivas brasileiras
- [ ] Documentação completa (OpenAPI + README)
- [ ] Monitoramento configurado (métricas + alertas)
- [ ] Rollback plan definido
- [ ] Feature flag configurada (rollout gradual)
- [ ] Conteúdo educacional publicado
- [ ] Suporte treinado para responder dúvidas

### Go-Live Gradual
1. **Semana 1**: Beta fechado com 10 atletas voluntários
2. **Semana 2-3**: Expansão para 100 usuários (early adopters)
3. **Semana 4**: Análise de feedback + ajustes
4. **Semana 5**: Release geral (100% dos usuários)

---

## 📚 Referências

- **Cientificas**:
  - Jack Daniels - "Daniels' Running Formula"
  - Joe Friel - "The Triathlete's Training Bible"
  - Renato Dutra - Estudos sobre testes de campo no Brasil

- **Técnicas**:
  - Spring Boot 3.x Documentation
  - Spring AI Reference Guide
  - TrainingPeaks Zone Calculator

---

**Documento vivo**: Este roadmap deve ser atualizado conforme o projeto evolui. Revisar quinzenalmente em retrospectivas.

**Última atualização**: [Data] | **Versão**: 1.0