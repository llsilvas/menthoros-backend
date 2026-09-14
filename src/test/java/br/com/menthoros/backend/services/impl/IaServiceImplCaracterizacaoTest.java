package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.services.helper.LlmUsageLogger;
import br.com.menthoros.backend.services.helper.PaceValidator;
import br.com.menthoros.backend.services.helper.PlanoEstruturaReparador;
import br.com.menthoros.backend.services.helper.PlanoResilienceService;
import br.com.menthoros.backend.services.helper.RegraGeracaoTreino;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider.ContextoTreino;
import br.com.menthoros.backend.services.helper.ZonaTreinoService;
import br.com.menthoros.backend.services.prompt.PaceHistoricoFormatter;
import br.com.menthoros.backend.services.prompt.PlanoTreinoPromptBuilder;
import br.com.menthoros.backend.services.quality.PlanQualityChecker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Rede de caracterização de {@code validarENormalizarPlanoGerado} — a composição completa de
 * transformações que {@code geraPlanoSemanalAvancado} aplica à resposta da LLM (expansão →
 * normalização → validação por tipo → FC → triângulo pace×distância×duração). Testar a composição,
 * não os métodos isolados, é a rede de segurança da decomposição
 * (refactor-iaservice-decomposition, achado IA-07: cobertura isolada não detectou IA-02/03/04).
 *
 * <p>Testa via reflexão o método privado, como os demais testes de {@code IaServiceImpl}
 * (`IaServiceImplFcValidationTest`, `IaServiceImplComplianceEstagio1Test`) — o fluxo público completo
 * (`geraPlanoSemanalAvancado`) exige o `ChatClient` fluente do Spring AI, inviável em unit test.</p>
 *
 * <p>Atleta sem FC cadastrada (fcLimiar/fcMaxima null) — a validação de FC por zona fica fora do
 * escopo desta rede (coberta isoladamente na seção 4, achado IA-02).</p>
 */
class IaServiceImplCaracterizacaoTest {

    private IaServiceImpl service;
    private AtletaRepository atletaRepository;
    private Atleta atleta;
    private UUID atletaId;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        atletaId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        atleta = Atleta.builder()
                .id(atletaId)
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .paceLimiar(BigDecimal.valueOf(5.0))
                .build();

        atletaRepository = mock(AtletaRepository.class);
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));

        TreinoHistoricoProvider treinoHistoricoProvider = mock(TreinoHistoricoProvider.class);
        when(treinoHistoricoProvider.prepararContexto(atleta)).thenReturn(
                new ContextoTreino(LocalDate.of(2026, 9, 14), List.of(), List.of(), List.of()));

        PaceHistoricoFormatter paceHistoricoFormatter = mock(PaceHistoricoFormatter.class);
        when(paceHistoricoFormatter.calcularTetoPorTipo(any())).thenReturn(java.util.Map.of());
        when(paceHistoricoFormatter.calcularPisoPorTipo(any())).thenReturn(java.util.Map.of());

        PaceValidator paceValidator = mock(PaceValidator.class);
        when(paceValidator.validar(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));

        PlanoEstruturaReparador estruturaReparador = mock(PlanoEstruturaReparador.class);
        when(estruturaReparador.reparar(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        service = new IaServiceImpl(
                mock(br.com.menthoros.backend.routing.ModelRouter.class),
                mock(PlanoTreinoPromptBuilder.class),
                new br.com.menthoros.backend.services.prompt.LlmJsonSchemaBuilder(),
                atletaRepository,
                mock(RegraGeracaoTreino.class),
                treinoHistoricoProvider,
                paceHistoricoFormatter,
                paceValidator,
                mock(ZonaTreinoService.class),
                mock(PlanQualityChecker.class),
                estruturaReparador,
                new br.com.menthoros.backend.services.helper.TreinoNormalizador(),
                new br.com.menthoros.backend.services.helper.EtapaFcValidator(),
                mock(PlanoResilienceService.class),
                new SimpleMeterRegistry(),
                new LlmUsageLogger(),
                mock(br.com.menthoros.backend.services.helper.PlannerShadowService.class),
                mock(br.com.menthoros.backend.services.helper.PlanoLlmLedgerHook.class)
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    static Stream<TreinoPlanejadoLlmDto> cenarios() {
        return Stream.of(intervalado(), longo(), regenerativo());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cenarios")
    @DisplayName("plano bem-formado atravessa a composição completa sem alterar a estrutura")
    void planoBemFormadoAtravessaComposicaoSemQuebrar(TreinoPlanejadoLlmDto treino) throws Exception {
        PlanoSemanalLlmDto plano = new PlanoSemanalLlmDto(
                30.0, 30.0, null, null, "ATIVO", "base aeróbica", List.of(treino));

        PlanoSemanalLlmDto validado = invoke(plano, atletaId);

        assertThat(validado.treinosPlanejados()).hasSize(1);
        TreinoPlanejadoLlmDto resultado = validado.treinosPlanejados().get(0);
        assertThat(resultado.tipoTreino()).isEqualTo(treino.tipoTreino());
        assertThat(resultado.etapas()).hasSameSizeAs(treino.etapas());
    }

    private static TreinoPlanejadoLlmDto intervalado() {
        return new TreinoPlanejadoLlmDto(
                "SEGUNDA", "INTERVALADO", "150-160 bpm", 60, 1.1, 8,
                "Desenvolver VO2max", "45:00", 5.3, "4:30-5:00/km",
                List.of(
                        new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento leve Z1-Z2", 10, 1.5, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(2, "INTERVALADO", "Tiro 1 - Z5", 4, 1.0, "160-170 bpm", 1, "4:00-4:15/km"),
                        new EtapaTreinoLlmDto(3, "RECUPERACAO", "Recuperação 1 - trote Z2", 2, 0.4, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(4, "INTERVALADO", "Tiro 2 - Z5", 4, 1.0, "160-170 bpm", 1, "4:00-4:15/km"),
                        new EtapaTreinoLlmDto(5, "RECUPERACAO", "Recuperação 2 - trote Z2", 2, 0.4, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(6, "DESAQUECIMENTO", "Desaquecimento leve Z1", 5, 1.0, "120-136 bpm", 1, null)
                ));
    }

    private static TreinoPlanejadoLlmDto longo() {
        return new TreinoPlanejadoLlmDto(
                "SABADO", "LONGO", "136-150 bpm", 90, 0.85, 6,
                "Construir base aeróbica", "75:00", 15.0, "5:30-6:00/km",
                List.of(
                        new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento leve Z1-Z2", 10, 1.5, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(2, "PRINCIPAL", "Longo contínuo Z2-Z3", 60, 12.0, "136-150 bpm", 1, "5:30-6:00/km"),
                        new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Desaquecimento leve Z1", 5, 1.5, "120-136 bpm", 1, null)
                ));
    }

    private static TreinoPlanejadoLlmDto regenerativo() {
        return new TreinoPlanejadoLlmDto(
                "QUARTA", "REGENERATIVO", "115-130 bpm", 25, 0.6, 3,
                "Recuperação ativa", "35:00", 5.0, "6:30-7:00/km",
                List.of(
                        new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento muito leve", 5, 0.7, "115-130 bpm", 1, null),
                        new EtapaTreinoLlmDto(2, "PRINCIPAL", "Trote regenerativo Z1", 25, 3.6, "115-130 bpm", 1, "6:30-7:00/km"),
                        new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Desaquecimento leve", 5, 0.7, "115-130 bpm", 1, null)
                ));
    }

    private PlanoSemanalLlmDto invoke(PlanoSemanalLlmDto plano, UUID atletaId) throws Exception {
        Method m = IaServiceImpl.class.getDeclaredMethod(
                "validarENormalizarPlanoGerado", PlanoSemanalLlmDto.class, UUID.class);
        m.setAccessible(true);
        try {
            return (PlanoSemanalLlmDto) m.invoke(service, plano, atletaId);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }
}
