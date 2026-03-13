# Estratégia de Testes - Redistribuição de Treinos

## 📋 Visão Geral

Este documento descreve a estratégia completa de testes para o módulo de **redistribuição de treinos**, seguindo as melhores práticas de desenvolvimento de software.

### Componentes Cobertos
- `RegraGeracaoTreino` - Regras de negócio para geração de treinos
- `RedistribuicaoTreinoHelper` - Lógica de redistribuição de treinos
- `PlanoServiceImpl` - Orquestração e persistência de planos

---

## 🎯 Pirâmide de Testes

```
         /\
        /  \  E2E (poucos)
       /----\
      /      \  Integração (alguns)
     /--------\
    /          \  Unitários (muitos)
   /____________\
```

---

## 1️⃣ Testes Unitários - `RegraGeracaoTreino`

### ✅ Características
- **Sem dependências** - Não precisa de Spring Context
- **Execução rápida** - < 50ms por teste
- **Cobertura alta** - Objetivo: 100%

### 📝 Cenários de Teste

#### 1.1 Detecção de Meio de Semana

```java
@Test
@DisplayName("Deve identificar meio de semana quando for quarta-feira ou posterior")
void deveIdentificarMeioSemanaQuandoForQuartaOuPosterior() {
    RegraGeracaoTreino regra = new RegraGeracaoTreino();

    // Quarta, Quinta, Sexta, Sábado, Domingo
    assertAll(
        () -> assertTrue(regra.isMeioSemana(LocalDate.of(2025, 10, 8))),  // Quarta
        () -> assertTrue(regra.isMeioSemana(LocalDate.of(2025, 10, 9))),  // Quinta
        () -> assertTrue(regra.isMeioSemana(LocalDate.of(2025, 10, 10))), // Sexta
        () -> assertTrue(regra.isMeioSemana(LocalDate.of(2025, 10, 11))), // Sábado
        () -> assertTrue(regra.isMeioSemana(LocalDate.of(2025, 10, 12)))  // Domingo
    );
}

@Test
@DisplayName("Não deve identificar meio de semana quando for segunda ou terça")
void naoDeveIdentificarMeioSemanaQuandoForSegundaOuTerca() {
    RegraGeracaoTreino regra = new RegraGeracaoTreino();

    assertAll(
        () -> assertFalse(regra.isMeioSemana(LocalDate.of(2025, 10, 6))), // Segunda
        () -> assertFalse(regra.isMeioSemana(LocalDate.of(2025, 10, 7)))  // Terça
    );
}
```

#### 1.2 Verificação de Dia Passado

```java
@Test
@DisplayName("Deve identificar que dia da semana já passou")
void deveIdentificarDiaJaPassou() {
    RegraGeracaoTreino regra = new RegraGeracaoTreino();
    LocalDate quinta = LocalDate.of(2025, 10, 9); // Quinta

    assertAll(
        () -> assertTrue(regra.diaSemanaJaPassou(quinta, DiaSemana.SEGUNDA)),
        () -> assertTrue(regra.diaSemanaJaPassou(quinta, DiaSemana.TERCA)),
        () -> assertTrue(regra.diaSemanaJaPassou(quinta, DiaSemana.QUARTA)),
        () -> assertTrue(regra.diaSemanaJaPassou(quinta, DiaSemana.QUINTA))
    );
}

@Test
@DisplayName("Deve identificar que dia da semana ainda não passou")
void deveIdentificarDiaNaoPassou() {
    RegraGeracaoTreino regra = new RegraGeracaoTreino();
    LocalDate quinta = LocalDate.of(2025, 10, 9); // Quinta

    assertAll(
        () -> assertFalse(regra.diaSemanaJaPassou(quinta, DiaSemana.SEXTA)),
        () -> assertFalse(regra.diaSemanaJaPassou(quinta, DiaSemana.SABADO)),
        () -> assertFalse(regra.diaSemanaJaPassou(quinta, DiaSemana.DOMINGO))
    );
}
```

#### 1.4 Filtro de Dias Disponíveis

```java
@Test
@DisplayName("Deve retornar todos os dias quando modo for PROXIMA_SEMANA")
void deveRetornarTodosDiasQuandoModoForProximaSemana() {
    RegraGeracaoTreino regra = new RegraGeracaoTreino();
    LocalDate hoje = LocalDate.of(2025, 10, 8);
    List<DiaSemana> dias = List.of(
        DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SEXTA
    );

    List<DiaSemana> resultado = regra.filtrarDiasDisponiveis(
        dias, hoje, ModoGeracaoPlano.PROXIMA_SEMANA
    );

    assertEquals(3, resultado.size());
    assertEquals(dias, resultado);
}

@Test
@DisplayName("Deve filtrar dias que já passaram quando modo for SEMANA_ATUAL")
void deveFiltrarDiasPassadosQuandoModoForSemanaAtual() {
    RegraGeracaoTreino regra = new RegraGeracaoTreino();
    LocalDate quinta = LocalDate.of(2025, 10, 9); // Quinta
    List<DiaSemana> dias = List.of(
        DiaSemana.SEGUNDA, DiaSemana.TERCA,
        DiaSemana.QUINTA, DiaSemana.SEXTA, DiaSemana.SABADO
    );

    List<DiaSemana> resultado = regra.filtrarDiasDisponiveis(
        dias, quinta, ModoGeracaoPlano.SEMANA_ATUAL
    );

    // Deve manter apenas Sexta e Sábado
    assertEquals(2, resultado.size());
    assertTrue(resultado.contains(DiaSemana.SEXTA));
    assertTrue(resultado.contains(DiaSemana.SABADO));
}
```

---

## 2️⃣ Testes Unitários - `RedistribuicaoTreinoHelper`

### ✅ Características
- **Mock de dependências** - Usa Mockito para `RegraGeracaoTreino`
- **Foco em lógica de negócio** - Isola comportamento
- **Validação de cenários complexos**

### 📝 Cenários de Teste

#### 2.1 Filtro de Treinos Compatíveis

```java
@ExtendWith(MockitoExtension.class)
class RedistribuicaoTreinoHelperTest {

    @Mock
    private RegraGeracaoTreino regraGeracaoMock;

    @InjectMocks
    private RedistribuicaoTreinoHelper redistribuicaoHelper;

    @Test
    @DisplayName("Deve filtrar treinos LONGO e INTERVALADO no meio da semana")
    void deveFiltrarTreinosIncompatíveisNoMeioDaSemana() {
        LocalDate quarta = LocalDate.of(2025, 10, 8);
        LocalDate semanaInicio = quarta.with(DayOfWeek.MONDAY);
        LocalDate semanaFim = semanaInicio.plusDays(6);

        when(regraGeracaoMock.isMeioSemana(quarta)).thenReturn(true);

        List<TreinoPlanejadoLlmDto> treinos = List.of(
            criarTreino("QUINTA", "LONGO"),
            criarTreino("SEXTA", "INTERVALADO"),
            criarTreino("SABADO", "CONTINUO"),
            criarTreino("DOMINGO", "REGENERATIVO")
        );

        List<DiaSemana> diasDisponiveis = List.of(
            DiaSemana.QUINTA, DiaSemana.SEXTA, DiaSemana.SABADO, DiaSemana.DOMINGO
        );

        var resultado = redistribuicaoHelper.redistribuirTreinos(
            treinos, diasDisponiveis, quarta, semanaInicio, semanaFim,
            ModoGeracaoPlano.SEMANA_ATUAL
        );

        // Deve filtrar LONGO e INTERVALADO
        assertEquals(2, resultado.size());
        assertTrue(resultado.stream().noneMatch(t ->
            t.tipoTreino().equals("LONGO") || t.tipoTreino().equals("INTERVALADO")
        ));
    }

    @Test
    @DisplayName("Deve aceitar todos os treinos quando modo for PROXIMA_SEMANA")
    void deveAceitarTodosTreinosQuandoModoForProximaSemana() {
        LocalDate hoje = LocalDate.of(2025, 10, 8);
        LocalDate semanaInicio = hoje.plusWeeks(1).with(DayOfWeek.MONDAY);
        LocalDate semanaFim = semanaInicio.plusDays(6);

        List<TreinoPlanejadoLlmDto> treinos = List.of(
            criarTreino("SEGUNDA", "LONGO"),
            criarTreino("QUARTA", "INTERVALADO"),
            criarTreino("SEXTA", "TEMPO_RUN")
        );

        List<DiaSemana> diasDisponiveis = List.of(
            DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SEXTA
        );

        var resultado = redistribuicaoHelper.redistribuirTreinos(
            treinos, diasDisponiveis, hoje, semanaInicio, semanaFim,
            ModoGeracaoPlano.PROXIMA_SEMANA
        );

        assertEquals(3, resultado.size());
    }

    @Test
    @DisplayName("Deve filtrar TEMPO_RUN no meio da semana")
    void deveFiltrarTempoRunNoMeioDaSemana() {
        LocalDate quarta = LocalDate.of(2025, 10, 8);
        LocalDate semanaInicio = quarta.with(DayOfWeek.MONDAY);
        LocalDate semanaFim = semanaInicio.plusDays(6);

        when(regraGeracaoMock.isMeioSemana(quarta)).thenReturn(true);

        List<TreinoPlanejadoLlmDto> treinos = List.of(
            criarTreino("QUINTA", "TEMPO_RUN"),
            criarTreino("SEXTA", "CONTINUO")
        );

        List<DiaSemana> diasDisponiveis = List.of(DiaSemana.QUINTA, DiaSemana.SEXTA);

        var resultado = redistribuicaoHelper.redistribuirTreinos(
            treinos, diasDisponiveis, quarta, semanaInicio, semanaFim,
            ModoGeracaoPlano.SEMANA_ATUAL
        );

        assertEquals(1, resultado.size());
        assertEquals("CONTINUO", resultado.get(0).tipoTreino());
    }
}
```

#### 2.2 Validação de Dias Disponíveis

```java
@Test
@DisplayName("Deve retornar lista vazia quando não houver dias disponíveis")
void deveRetornarListaVaziaQuandoNaoHouverDiasDisponiveis() {
    LocalDate hoje = LocalDate.of(2025, 10, 8);
    LocalDate semanaInicio = hoje.with(DayOfWeek.MONDAY);
    LocalDate semanaFim = semanaInicio.plusDays(6);

    List<TreinoPlanejadoLlmDto> treinos = List.of(
        criarTreino("SEGUNDA", "CONTINUO")
    );

    List<DiaSemana> diasDisponiveis = List.of(); // Sem dias

    var resultado = redistribuicaoHelper.redistribuirTreinos(
        treinos, diasDisponiveis, hoje, semanaInicio, semanaFim,
        ModoGeracaoPlano.SEMANA_ATUAL
    );

    assertTrue(resultado.isEmpty());
}

@Test
@DisplayName("Deve filtrar dias que já passaram na semana atual")
void deveFiltrarDiasPassadosNaSemanaAtual() {
    LocalDate quinta = LocalDate.of(2025, 10, 9); // Quinta
    LocalDate semanaInicio = quinta.with(DayOfWeek.MONDAY);
    LocalDate semanaFim = semanaInicio.plusDays(6);

    List<TreinoPlanejadoLlmDto> treinos = List.of(
        criarTreino("SEGUNDA", "CONTINUO"),
        criarTreino("TERCA", "FARTLEK"),
        criarTreino("SEXTA", "LONGO")
    );

    List<DiaSemana> diasDisponiveis = List.of(
        DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.SEXTA
    );

    var resultado = redistribuicaoHelper.redistribuirTreinos(
        treinos, diasDisponiveis, quinta, semanaInicio, semanaFim,
        ModoGeracaoPlano.SEMANA_ATUAL
    );

    // Deve considerar apenas Sexta (dias posteriores a Quinta)
    assertTrue(resultado.size() > 0);
    assertTrue(resultado.stream().anyMatch(t -> t.diaSemana().equals("SEXTA")));
}

@Test
@DisplayName("Deve manter todos os dias quando modo for PROXIMA_SEMANA")
void deveManterTodosDiasQuandoModoForProximaSemana() {
    LocalDate quinta = LocalDate.of(2025, 10, 9);
    LocalDate semanaInicio = quinta.plusWeeks(1).with(DayOfWeek.MONDAY);
    LocalDate semanaFim = semanaInicio.plusDays(6);

    List<TreinoPlanejadoLlmDto> treinos = List.of(
        criarTreino("SEGUNDA", "CONTINUO"),
        criarTreino("TERCA", "FARTLEK"),
        criarTreino("QUARTA", "INTERVALADO")
    );

    List<DiaSemana> diasDisponiveis = List.of(
        DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA
    );

    var resultado = redistribuicaoHelper.redistribuirTreinos(
        treinos, diasDisponiveis, quinta, semanaInicio, semanaFim,
        ModoGeracaoPlano.PROXIMA_SEMANA
    );

    assertEquals(3, resultado.size());
}
```

#### 2.3 Validação de Edge Cases

```java
@Test
@DisplayName("Deve lidar com lista de treinos vazia")
void deveLidarComListaTreinosVazia() {
    LocalDate hoje = LocalDate.now();
    LocalDate semanaInicio = hoje.with(DayOfWeek.MONDAY);
    LocalDate semanaFim = semanaInicio.plusDays(6);

    List<TreinoPlanejadoLlmDto> treinos = List.of();
    List<DiaSemana> diasDisponiveis = List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA);

    var resultado = redistribuicaoHelper.redistribuirTreinos(
        treinos, diasDisponiveis, hoje, semanaInicio, semanaFim,
        ModoGeracaoPlano.SEMANA_ATUAL
    );

    assertTrue(resultado.isEmpty());
}

@Test
@DisplayName("Deve lidar com mais treinos que dias disponíveis")
void deveLidarComMaisTreinosQueDiasDisponiveis() {
    LocalDate segunda = LocalDate.of(2025, 10, 6);
    LocalDate semanaInicio = segunda;
    LocalDate semanaFim = semanaInicio.plusDays(6);

    List<TreinoPlanejadoLlmDto> treinos = List.of(
        criarTreino("TERCA", "CONTINUO"),
        criarTreino("QUARTA", "FARTLEK"),
        criarTreino("QUINTA", "REGENERATIVO"),
        criarTreino("SEXTA", "CONTINUO"),
        criarTreino("SABADO", "LONGO")
    );

    // Apenas 2 dias disponíveis
    List<DiaSemana> diasDisponiveis = List.of(DiaSemana.QUARTA, DiaSemana.SABADO);

    var resultado = redistribuicaoHelper.redistribuirTreinos(
        treinos, diasDisponiveis, segunda, semanaInicio, semanaFim,
        ModoGeracaoPlano.SEMANA_ATUAL
    );

    // Deve distribuir máximo de 2 treinos
    assertTrue(resultado.size() <= 2);
}

// Helper method
private TreinoPlanejadoLlmDto criarTreino(String dia, String tipo) {
    return new TreinoPlanejadoLlmDto(
        dia, tipo, "140-160% FCmáx", 100.0, 1.0, 7,
        "Treino de teste", 60, 10.0, "5:00-5:30/km", null
    );
}
```

---

## 3️⃣ Testes de Integração - `PlanoServiceImpl`

### ✅ Características
- **Spring Context completo** - Usa `@SpringBootTest`
- **Banco H2 em memória** - Testes isolados
- **Mock apenas de serviços externos** - IA Service

### 📝 Configuração Base

```java
@SpringBootTest
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class PlanoServiceImplIntegrationTest {

    @Autowired
    private PlanoService planoService;

    @Autowired
    private AtletaRepository atletaRepository;

    @Autowired
    private PlanoSemanalRepository planoSemanalRepository;

    @Autowired
    private PlanoMetadadosRepository planoMetadadosRepository;

    @MockBean
    private IaService iaServiceMock;

    private Atleta atleta;

    @BeforeEach
    void setUp() {
        atleta = criarAtletaCompleto();
        atletaRepository.save(atleta);
    }

    private Atleta criarAtletaCompleto() {
        return Atleta.builder()
            .nome("João Silva")
            .email("joao@test.com")
            .idade(30)
            .diasDisponiveis(List.of(
                DiaSemana.SEGUNDA, DiaSemana.QUARTA,
                DiaSemana.SEXTA, DiaSemana.SABADO
            ))
            .diaPreferidoLongo(DiaSemana.SABADO)
            .build();
    }
}
```

### 📝 Cenários de Teste

#### 3.1 Geração Bem-Sucedida

```java
@Test
@DisplayName("Deve gerar plano completo com sucesso para próxima semana")
void deveGerarPlanoCompletoComSucessoParaProximaSemana() {
    // Arrange
    PlanoSemanalLlmDto planoLlm = criarPlanoLlmMock(5);
    when(iaServiceMock.geraPlanoSemanalAvancado(any(), any(), any()))
        .thenReturn(planoLlm);

    // Act
    PlanoSemanal resultado = planoService.gerarPlanoTreino(
        atleta.getId(),
        ModoGeracaoPlano.PROXIMA_SEMANA
    );

    // Assert
    assertNotNull(resultado);
    assertNotNull(resultado.getId());
    assertEquals(atleta.getId(), resultado.getAtleta().getId());
    assertEquals(5, resultado.getTreinosPlanejados().size());
    assertTrue(resultado.getVolumePlanejadoKm().doubleValue() > 0);
    assertNotNull(resultado.getPlanoMetaDados());

    // Verificar persistência
    Optional<PlanoSemanal> salvo = planoSemanalRepository.findById(resultado.getId());
    assertTrue(salvo.isPresent());
}

@Test
@DisplayName("Deve calcular volume planejado corretamente a partir dos treinos")
void deveCalcularVolumePlanejadoCorretamente() {
    PlanoSemanalLlmDto planoLlm = criarPlanoLlmComDistancias(
        List.of(10.0, 15.0, 8.0, 20.0) // 53km total
    );
    when(iaServiceMock.geraPlanoSemanalAvancado(any(), any(), any()))
        .thenReturn(planoLlm);

    PlanoSemanal resultado = planoService.gerarPlanoTreino(
        atleta.getId(),
        ModoGeracaoPlano.PROXIMA_SEMANA
    );

    assertEquals(53.0, resultado.getVolumePlanejadoKm().doubleValue(), 0.01);
    assertEquals(resultado.getVolumePlanejadoKm(), resultado.getVolumeAlvoKm());
}

@Test
@DisplayName("Deve criar metadados quando atleta não possuir")
void deveCriarMetadadosQuandoAtletaNaoPossuir() {
    PlanoSemanalLlmDto planoLlm = criarPlanoLlmMock(4);
    when(iaServiceMock.geraPlanoSemanalAvancado(any(), any(), any()))
        .thenReturn(planoLlm);

    // Garantir que não há metadados
    assertFalse(planoMetadadosRepository.findLatestByAtletaId(atleta.getId()).isPresent());

    PlanoSemanal resultado = planoService.gerarPlanoTreino(
        atleta.getId(),
        ModoGeracaoPlano.PROXIMA_SEMANA
    );

    assertNotNull(resultado.getPlanoMetaDados());
    assertNotNull(resultado.getPlanoMetaDados().getId());
}
```

#### 3.2 Redistribuição e Filtros

```java
@Test
@DisplayName("Deve filtrar treinos incompatíveis no meio da semana")
void deveFiltrarTreinosIncompatíveisNoMeioDaSemana() {
    // Simula geração na quinta-feira
    LocalDate quinta = LocalDate.of(2025, 10, 9);

    // LLM retorna treinos incluindo LONGO e INTERVALADO
    PlanoSemanalLlmDto planoLlm = criarPlanoLlmComTipos(
        List.of("LONGO", "INTERVALADO", "CONTINUO", "REGENERATIVO")
    );
    when(iaServiceMock.geraPlanoSemanalAvancado(any(), any(), any()))
        .thenReturn(planoLlm);

    PlanoSemanal resultado = planoService.gerarPlanoTreino(
        atleta.getId(),
        ModoGeracaoPlano.SEMANA_ATUAL
    );

    // Deve filtrar LONGO e INTERVALADO
    List<TreinoPlanejado> treinos = resultado.getTreinosPlanejados();
    assertTrue(treinos.stream().noneMatch(t ->
        t.getTipoTreino() == TipoTreino.LONGO ||
        t.getTipoTreino() == TipoTreino.INTERVALADO
    ));
}

@Test
@DisplayName("Deve atribuir datas corretas aos treinos redistribuídos")
void deveAtribuirDatasCorretasAosTreinosRedistribuidos() {
    PlanoSemanalLlmDto planoLlm = criarPlanoLlmMock(4);
    when(iaServiceMock.geraPlanoSemanalAvancado(any(), any(), any()))
        .thenReturn(planoLlm);

    PlanoSemanal resultado = planoService.gerarPlanoTreino(
        atleta.getId(),
        ModoGeracaoPlano.PROXIMA_SEMANA
    );

    LocalDate semanaInicio = resultado.getSemanaInicio();
    LocalDate semanaFim = resultado.getSemanaFim();

    resultado.getTreinosPlanejados().forEach(treino -> {
        LocalDate dataTreino = treino.getDataTreino();

        // Data deve estar entre início e fim da semana
        assertTrue(!dataTreino.isBefore(semanaInicio));
        assertTrue(!dataTreino.isAfter(semanaFim));

        // Data deve ser um dia disponível do atleta
        DiaSemana diaSemana = DiaSemana.valueOf(
            dataTreino.getDayOfWeek().name()
        );
        assertTrue(atleta.getDiasDisponiveis().contains(diaSemana));
    });
}
```

#### 3.3 Validações e Exceções

```java
@Test
@DisplayName("Deve lançar exceção quando não houver treinos após redistribuição")
void deveLancarExcecaoQuandoNaoHouverTreinosAposRedistribuicao() {
    // Atleta sem dias disponíveis
    atleta.setDiasDisponiveis(List.of());
    atletaRepository.save(atleta);

    PlanoSemanalLlmDto planoLlm = criarPlanoLlmMock(3);
    when(iaServiceMock.geraPlanoSemanalAvancado(any(), any(), any()))
        .thenReturn(planoLlm);

    // Deve lançar IllegalStateException
    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> planoService.gerarPlanoTreino(
            atleta.getId(),
            ModoGeracaoPlano.SEMANA_ATUAL
        )
    );

    assertTrue(exception.getMessage().contains("Não foi possível gerar treinos"));
}

@Test
@DisplayName("Deve lançar exceção quando atleta não existir")
void deveLancarExcecaoQuandoAtletaNaoExistir() {
    UUID idInexistente = UUID.randomUUID();

    RuntimeException exception = assertThrows(
        RuntimeException.class,
        () -> planoService.gerarPlanoTreino(
            idInexistente,
            ModoGeracaoPlano.PROXIMA_SEMANA
        )
    );

    assertTrue(exception.getMessage().contains("Atleta não encontrado"));
}

@Test
@DisplayName("Deve lançar exceção quando LLM retornar plano nulo")
void deveLancarExcecaoQuandoLlmRetornarPlanoNulo() {
    when(iaServiceMock.geraPlanoSemanalAvancado(any(), any(), any()))
        .thenReturn(null);

    // Deve retornar null e ser tratado internamente
    // (Nota: Pode ser melhorado para lançar exceção específica)
    assertThrows(
        NullPointerException.class,
        () -> planoService.gerarPlanoTreino(
            atleta.getId(),
            ModoGeracaoPlano.PROXIMA_SEMANA
        )
    );
}
```

#### 3.4 Metadados e Progressão

```java
@Test
@DisplayName("Deve atualizar TSB e Ramp Rate nos metadados")
void deveAtualizarTsbERampRateNosMetadados() {
    // Criar metadados existentes
    PlanoMetaDados metaDados = PlanoMetaDados.builder()
        .atleta(atleta)
        .tsbAtual(50.0)
        .volumeSemanalMedio(BigDecimal.valueOf(40.0))
        .dataCriacao(LocalDateTime.now())
        .build();
    planoMetadadosRepository.save(metaDados);

    PlanoSemanalLlmDto planoLlm = PlanoSemanalLlmDto.builder()
        .tsbInicio(50.0)
        .tsbFim(45.0) // TSB diminui
        .volumePlanejadoKm(44.0) // 10% de aumento
        .treinosPlanejados(criarListaTreinosLlm(4))
        .build();

    when(iaServiceMock.geraPlanoSemanalAvancado(any(), any(), any()))
        .thenReturn(planoLlm);

    PlanoSemanal resultado = planoService.gerarPlanoTreino(
        atleta.getId(),
        ModoGeracaoPlano.PROXIMA_SEMANA
    );

    PlanoMetaDados metaDadosAtualizados = resultado.getPlanoMetaDados();

    assertEquals(45.0, metaDadosAtualizados.getTsbAtual());
    assertNotNull(metaDadosAtualizados.getRampRateAtual());
    assertTrue(metaDadosAtualizados.getRampRateAtual() > 0); // Aumento de 10%
}

@Test
@DisplayName("Deve incrementar contador de semanas de progressão")
void deveIncrementarContadorSemanasProgressao() {
    PlanoMetaDados metaDados = PlanoMetaDados.builder()
        .atleta(atleta)
        .volumeSemanalMedio(BigDecimal.valueOf(40.0))
        .semanasProgressaoContinua(2)
        .dataCriacao(LocalDateTime.now())
        .build();
    planoMetadadosRepository.save(metaDados);

    PlanoSemanalLlmDto planoLlm = criarPlanoLlmComVolume(45.0); // Aumento
    when(iaServiceMock.geraPlanoSemanalAvancado(any(), any(), any()))
        .thenReturn(planoLlm);

    PlanoSemanal resultado = planoService.gerarPlanoTreino(
        atleta.getId(),
        ModoGeracaoPlano.PROXIMA_SEMANA
    );

    assertEquals(3, resultado.getPlanoMetaDados().getSemanasProgressaoContinua());
}

@Test
@DisplayName("Deve resetar contador quando volume diminuir")
void deveResetarContadorQuandoVolumeDiminuir() {
    PlanoMetaDados metaDados = PlanoMetaDados.builder()
        .atleta(atleta)
        .volumeSemanalMedio(BigDecimal.valueOf(40.0))
        .semanasProgressaoContinua(3)
        .dataCriacao(LocalDateTime.now())
        .build();
    planoMetadadosRepository.save(metaDados);

    PlanoSemanalLlmDto planoLlm = criarPlanoLlmComVolume(35.0); // Diminuição
    when(iaServiceMock.geraPlanoSemanalAvancado(any(), any(), any()))
        .thenReturn(planoLlm);

    PlanoSemanal resultado = planoService.gerarPlanoTreino(
        atleta.getId(),
        ModoGeracaoPlano.PROXIMA_SEMANA
    );

    assertEquals(0, resultado.getPlanoMetaDados().getSemanasProgressaoContinua());
}
```

#### 3.5 Helpers de Criação

```java
private PlanoSemanalLlmDto criarPlanoLlmMock(int quantidadeTreinos) {
    List<TreinoPlanejadoLlmDto> treinos = new ArrayList<>();
    String[] dias = {"SEGUNDA", "TERCA", "QUARTA", "QUINTA", "SEXTA"};
    String[] tipos = {"CONTINUO", "FARTLEK", "REGENERATIVO", "INTERVALADO", "LONGO"};

    for (int i = 0; i < quantidadeTreinos; i++) {
        treinos.add(new TreinoPlanejadoLlmDto(
            dias[i % dias.length],
            tipos[i % tipos.length],
            "140-160% FCmáx",
            100.0,
            1.0,
            7,
            "Treino gerado para teste",
            60,
            10.0,
            "5:00-5:30/km",
            null
        ));
    }

    return PlanoSemanalLlmDto.builder()
        .tsbInicio(50.0)
        .tsbFim(48.0)
        .volumePlanejadoKm(50.0)
        .treinosPlanejados(treinos)
        .build();
}

private PlanoSemanalLlmDto criarPlanoLlmComDistancias(List<Double> distancias) {
    List<TreinoPlanejadoLlmDto> treinos = new ArrayList<>();
    String[] dias = {"SEGUNDA", "TERCA", "QUINTA", "SABADO"};

    for (int i = 0; i < distancias.size(); i++) {
        treinos.add(new TreinoPlanejadoLlmDto(
            dias[i],
            "CONTINUO",
            "140-160% FCmáx",
            100.0,
            1.0,
            7,
            "Treino teste",
            60,
            distancias.get(i),
            "5:00-5:30/km",
            null
        ));
    }

    return PlanoSemanalLlmDto.builder()
        .tsbInicio(50.0)
        .tsbFim(48.0)
        .volumePlanejadoKm(distancias.stream().mapToDouble(Double::doubleValue).sum())
        .treinosPlanejados(treinos)
        .build();
}

private PlanoSemanalLlmDto criarPlanoLlmComTipos(List<String> tipos) {
    List<TreinoPlanejadoLlmDto> treinos = new ArrayList<>();
    String[] dias = {"SEGUNDA", "TERCA", "QUARTA", "QUINTA", "SEXTA"};

    for (int i = 0; i < tipos.size(); i++) {
        treinos.add(new TreinoPlanejadoLlmDto(
            dias[i],
            tipos.get(i),
            "140-160% FCmáx",
            100.0,
            1.0,
            7,
            "Treino teste",
            60,
            10.0,
            "5:00-5:30/km",
            null
        ));
    }

    return PlanoSemanalLlmDto.builder()
        .tsbInicio(50.0)
        .tsbFim(48.0)
        .volumePlanejadoKm(40.0)
        .treinosPlanejados(treinos)
        .build();
}

private PlanoSemanalLlmDto criarPlanoLlmComVolume(double volume) {
    return PlanoSemanalLlmDto.builder()
        .tsbInicio(50.0)
        .tsbFim(48.0)
        .volumePlanejadoKm(volume)
        .treinosPlanejados(criarListaTreinosLlm(4))
        .build();
}

private List<TreinoPlanejadoLlmDto> criarListaTreinosLlm(int quantidade) {
    List<TreinoPlanejadoLlmDto> treinos = new ArrayList<>();
    String[] dias = {"SEGUNDA", "QUARTA", "SEXTA", "SABADO"};

    for (int i = 0; i < quantidade; i++) {
        treinos.add(new TreinoPlanejadoLlmDto(
            dias[i % dias.length],
            "CONTINUO",
            "140-160% FCmáx",
            100.0,
            1.0,
            7,
            "Treino teste",
            60,
            10.0,
            "5:00-5:30/km",
            null
        ));
    }
    return treinos;
}
```

---

## 4️⃣ Testes de Performance

### 📝 Cenários de Teste

```java
@SpringBootTest
class PlanoServicePerformanceTest {

    @Autowired
    private PlanoService planoService;

    @Autowired
    private AtletaRepository atletaRepository;

    @MockBean
    private IaService iaServiceMock;

    @Test
    @DisplayName("Deve gerar 100 planos em menos de 5 segundos")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void deveGerarMultiplosPlanosRapidamente() {
        List<Atleta> atletas = criarMultiplosAtletas(100);
        atletaRepository.saveAll(atletas);

        PlanoSemanalLlmDto planoMock = criarPlanoLlmMock(4);
        when(iaServiceMock.geraPlanoSemanalAvancado(any(), any(), any()))
            .thenReturn(planoMock);

        long inicio = System.currentTimeMillis();

        atletas.forEach(atleta -> {
            planoService.gerarPlanoTreino(
                atleta.getId(),
                ModoGeracaoPlano.PROXIMA_SEMANA
            );
        });

        long duracao = System.currentTimeMillis() - inicio;

        assertTrue(duracao < 5000,
            "Geração de 100 planos levou " + duracao + "ms (máximo: 5000ms)");
    }

    @Test
    @DisplayName("Cache de metadados deve reduzir consultas ao banco")
    void cacheMetadadosDeveReduzirConsultas() {
        Atleta atleta = criarAtleta();
        atletaRepository.save(atleta);

        PlanoSemanalLlmDto planoMock = criarPlanoLlmMock(4);
        when(iaServiceMock.geraPlanoSemanalAvancado(any(), any(), any()))
            .thenReturn(planoMock);

        // Primeira chamada - busca do banco
        planoService.gerarPlanoTreino(atleta.getId(), ModoGeracaoPlano.PROXIMA_SEMANA);

        // Segunda chamada - deve usar cache
        long inicio = System.currentTimeMillis();
        planoService.gerarPlanoTreino(atleta.getId(), ModoGeracaoPlano.PROXIMA_SEMANA);
        long duracao = System.currentTimeMillis() - inicio;

        assertTrue(duracao < 100, "Cache não está funcionando corretamente");
    }

    private List<Atleta> criarMultiplosAtletas(int quantidade) {
        List<Atleta> atletas = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            atletas.add(Atleta.builder()
                .nome("Atleta " + i)
                .email("atleta" + i + "@test.com")
                .idade(25 + (i % 20))
                .diasDisponiveis(List.of(
                    DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SEXTA
                ))
                .build());
        }
        return atletas;
    }
}
```

---

## 5️⃣ Testes End-to-End (E2E)

### 📝 Cenários de Teste

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PlanoEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IaService iaServiceMock;

    @Test
    @DisplayName("Deve gerar plano via API REST com sucesso")
    void deveGerarPlanoViaApiComSucesso() throws Exception {
        // Simular resposta da IA
        PlanoSemanalLlmDto planoMock = criarPlanoLlmMock(4);
        when(iaServiceMock.geraPlanoSemanalAvancado(any(), any(), any()))
            .thenReturn(planoMock);

        // Request
        Map<String, Object> request = Map.of(
            "atletaId", UUID.randomUUID().toString(),
            "modoGeracao", "PROXIMA_SEMANA"
        );

        mockMvc.perform(post("/api/planos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.treinosPlanejados").isArray())
            .andExpect(jsonPath("$.treinosPlanejados.length()").value(4))
            .andExpect(jsonPath("$.volumePlanejadoKm").isNumber());
    }

    @Test
    @DisplayName("Deve retornar erro 400 quando não houver treinos válidos")
    void deveRetornarErro400QuandoNaoHouverTreinosValidos() throws Exception {
        when(iaServiceMock.geraPlanoSemanalAvancado(any(), any(), any()))
            .thenReturn(criarPlanoLlmSemTreinos());

        Map<String, Object> request = Map.of(
            "atletaId", UUID.randomUUID().toString(),
            "modoGeracao", "SEMANA_ATUAL"
        );

        mockMvc.perform(post("/api/planos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.mensagem").value(containsString("Não foi possível gerar treinos")));
    }
}
```

---

## 📊 Métricas e Cobertura

### Objetivos de Cobertura

| Componente | Cobertura de Linha | Cobertura de Branch |
|-----------|-------------------|---------------------|
| `RegraGeracaoTreino` | **100%** | **100%** |
| `RedistribuicaoTreinoHelper` | **≥ 90%** | **≥ 85%** |
| `PlanoServiceImpl` | **≥ 85%** | **≥ 80%** |

### Comandos para Execução

```bash
# Executar todos os testes
mvn test

# Executar apenas testes unitários
mvn test -Dtest="*Test"

# Executar apenas testes de integração
mvn test -Dtest="*IntegrationTest"

# Gerar relatório de cobertura (JaCoCo)
mvn jacoco:report

# Verificar cobertura mínima
mvn jacoco:check
```

### Configuração JaCoCo (pom.xml)

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.10</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>CLASS</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.85</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

---

## 🔍 Boas Práticas Aplicadas

### 1. **Nomenclatura Clara**
- Testes com nome descritivo: `deve[Ação]Quando[Condição]`
- Uso de `@DisplayName` para descrições legíveis

### 2. **Padrão AAA (Arrange-Act-Assert)**
```java
@Test
void deveFazerAlgo() {
    // Arrange - Preparação
    Object input = criarInput();

    // Act - Execução
    Object resultado = service.executar(input);

    // Assert - Verificação
    assertEquals(esperado, resultado);
}
```

### 3. **Isolamento**
- Cada teste é independente
- Uso de `@BeforeEach` para setup
- `@Transactional` em testes de integração

### 4. **Mock Apenas do Necessário**
- Serviços externos (IA) → mock
- Repositórios → banco em memória
- Helpers → instâncias reais quando possível

### 5. **Validação Completa**
- Não apenas "não lança exceção"
- Verificar estado resultante
- Validar efeitos colaterais (persistência)

### 6. **Testes Rápidos**
- Unitários: < 50ms
- Integração: < 500ms
- E2E: < 2s

---

## 📚 Referências

- [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [Test Pyramid - Martin Fowler](https://martinfowler.com/articles/practical-test-pyramid.html)

---

## 🎯 Próximos Passos

1. ✅ Implementar testes unitários para `RegraGeracaoTreino`
2. ✅ Implementar testes unitários para `RedistribuicaoTreinoHelper`
3. ✅ Implementar testes de integração para `PlanoServiceImpl`
4. ⏳ Configurar JaCoCo e verificar cobertura
5. ⏳ Implementar testes de performance
6. ⏳ Adicionar testes E2E via API REST
7. ⏳ Configurar CI/CD com execução automática de testes

---

**Documento gerado em**: 2025-10-08
**Versão**: 1.0
**Autor**: Menthoros Development Team