package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.input.DadosPlanoDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.dto.output.MetricasSemanaisMedias;
import br.com.menthoros.backend.dto.output.PadroesTreino;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import br.com.menthoros.backend.mapper.PlanoSemanalMapper;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.mapper.TreinoMapperImpl;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.services.MetricasAgregadasService;
import br.com.menthoros.backend.services.PlanoReviewService;
import br.com.menthoros.backend.services.impl.MetricasAlertaService;
import br.com.menthoros.backend.services.onboarding.OnboardingService;
import br.com.menthoros.backend.services.plano.ProvaNoPlanoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D2/2.2 (prova-no-plano-semanal): {@code PlanGenerationPersister} chama
 * {@code ProvaNoPlanoService.garantirProvasNaSemana} entre a redistribuição e a validação, e usa
 * a lista final de treinos — não o {@code volumePlanejadoKm} bruto do LLM — para os metadados.
 */
@ExtendWith(MockitoExtension.class)
class PlanGenerationPersisterProvaTest {

    @Mock private PlanoSemanalRepository planoSemanalRepository;
    @Mock private PlanoMetadadosRepository planoMetadadosRepository;
    @Mock private PlanoSemanalMapper planoSemanalMapper;
    @Mock private RedistribuicaoTreinoHelper redistribuicaoHelper;
    @Mock private MetricasAlertaService metricasAlertaService;
    @Mock private MetricasAgregadasService metricasAgregadasService;
    @Mock private PlannerShadowService plannerShadowService;
    @Mock private OnboardingService onboardingService;
    @Mock private PlanoReviewService planoReviewService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ProvaNoPlanoService provaNoPlanoService;

    private final TreinoMapper treinoMapper = new TreinoMapperImpl(null, null);

    private PlanGenerationPersister persister;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        persister = new PlanGenerationPersister(
                planoSemanalRepository, planoMetadadosRepository, treinoMapper, planoSemanalMapper,
                redistribuicaoHelper, metricasAlertaService, metricasAgregadasService,
                plannerShadowService, onboardingService, planoReviewService, eventPublisher,
                provaNoPlanoService);

        lenient().when(planoSemanalRepository.existePlanoAtivoNaSemana(any(), any(), any())).thenReturn(false);
        lenient().when(planoSemanalRepository.findTopByAtletaIdOrderBySemanaInicioDesc(any())).thenReturn(Optional.empty());
        lenient().when(planoSemanalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // possuiBaseline=false + migrateExistingEnabled=false (default do campo, sem Spring)
        // faz resolverOnboardingContext parar em Optional.empty() sem chamar montarContexto.
        lenient().when(onboardingService.possuiBaseline(any(), any())).thenReturn(false);
        lenient().when(plannerShadowService.aplicarShadow(any(), any(), any(), any(), any(), eq(false), any()))
                .thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("persist — garantia da prova")
    class GarantiaDaProva {

        @Test
        @DisplayName("chama garantirProvasNaSemana com o atleta e o período do plano, depois da redistribuição")
        void chamaGarantirProvasNaSemanaComPeriodoCorreto() {
            Atleta atleta = atletaComAssessoria();
            LocalDate semanaInicio = LocalDate.now();
            TreinoPlanejadoLlmDto longoNoDomingo = treinoDto("DOMINGO", "LONGO", 15.0);
            TreinoPlanejadoLlmDto provaNoDomingo = treinoDto("DOMINGO", "PROVA", 21.1);

            when(provaNoPlanoService.garantirProvasNaSemana(anyList(), eq(atleta), eq(semanaInicio), eq(semanaInicio.plusDays(6))))
                    .thenReturn(List.of(provaNoDomingo));

            PlanoSemanalLlmDto planoDto = planoDtoCom(List.of(longoNoDomingo), 15.0);
            PlanoMetaDados metaDadosSemId = new PlanoMetaDados(); // id nulo → prepararMetadados não mexe em collaborators extras
            DadosPlanoDto dadosPlano = dadosPlanoDto(atleta, metaDadosSemId);
            when(planoSemanalMapper.toEntity(planoDto)).thenReturn(new PlanoSemanal());

            PlanGenerationContext ctx = new PlanGenerationContext(dadosPlano, null, semanaInicio, null, null);

            PlanoSemanal salvo = persister.persist(planoDto, ctx, ModoGeracaoPlano.PROXIMA_SEMANA);

            verify(provaNoPlanoService).garantirProvasNaSemana(eq(List.of(longoNoDomingo)), eq(atleta),
                    eq(semanaInicio), eq(semanaInicio.plusDays(6)));

            assertThat(salvo.getTreinosPlanejados()).hasSize(1);
            assertThat(salvo.getTreinosPlanejados().getFirst().getTipoTreino().name()).isEqualTo("PROVA");
        }

        @Test
        @DisplayName("volumePlanejadoKm do plano inclui a distância da prova garantida")
        void volumeDoPlanoIncluiProva() {
            Atleta atleta = atletaComAssessoria();
            LocalDate semanaInicio = LocalDate.now();
            TreinoPlanejadoLlmDto continuo = treinoDto("SEGUNDA", "CONTINUO", 8.0);
            TreinoPlanejadoLlmDto provaGarantida = treinoDto("DOMINGO", "PROVA", 21.1);

            when(provaNoPlanoService.garantirProvasNaSemana(anyList(), any(), any(), any()))
                    .thenReturn(List.of(continuo, provaGarantida));

            PlanoSemanalLlmDto planoDto = planoDtoCom(List.of(continuo), 8.0);
            DadosPlanoDto dadosPlano = dadosPlanoDto(atleta, new PlanoMetaDados());
            when(planoSemanalMapper.toEntity(planoDto)).thenReturn(new PlanoSemanal());

            PlanGenerationContext ctx = new PlanGenerationContext(dadosPlano, null, semanaInicio, null, null);

            PlanoSemanal salvo = persister.persist(planoDto, ctx, ModoGeracaoPlano.PROXIMA_SEMANA);

            assertThat(salvo.getVolumePlanejadoKm()).isEqualByComparingTo(BigDecimal.valueOf(8.0 + 21.1));
        }

        @Test
        @DisplayName("metadados usam o volume recalculado da lista final, não o volumePlanejadoKm bruto do LLM")
        void metadadosUsamVolumeRecalculado() {
            Atleta atleta = atletaComAssessoria();
            LocalDate semanaInicio = LocalDate.now();
            TreinoPlanejadoLlmDto continuo = treinoDto("SEGUNDA", "CONTINUO", 8.0);
            TreinoPlanejadoLlmDto provaGarantida = treinoDto("DOMINGO", "PROVA", 21.1);

            when(provaNoPlanoService.garantirProvasNaSemana(anyList(), any(), any(), any()))
                    .thenReturn(List.of(continuo, provaGarantida));

            // LLM declarou 8.0 (sem a prova) — o valor errado que o Major do DoR apontou.
            PlanoSemanalLlmDto planoDto = planoDtoCom(List.of(continuo), 8.0);

            PlanoMetaDados metaDados = new PlanoMetaDados();
            UUID metaDadosId = UUID.randomUUID();
            metaDados.setId(metaDadosId);
            DadosPlanoDto dadosPlano = dadosPlanoDto(atleta, metaDados);
            when(planoSemanalMapper.toEntity(planoDto)).thenReturn(new PlanoSemanal());

            when(planoMetadadosRepository.findByIdAndTenantId(metaDadosId, tenantId)).thenReturn(Optional.of(metaDados));
            when(metricasAgregadasService.calcularMetricasSemanais(atleta.getId(), 6))
                    .thenReturn(new MetricasSemanaisMedias(BigDecimal.ZERO, 0, 0.0));
            when(metricasAgregadasService.calcularPadroesTreino(atleta.getId()))
                    .thenReturn(new PadroesTreino(0, 0));
            when(metricasAlertaService.analisarMetricas(any()))
                    .thenReturn(new br.com.menthoros.backend.dto.output.ResultadoAnalise(
                            "OK", "Manter", null, false, false, false, false, List.of()));
            when(planoMetadadosRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PlanGenerationContext ctx = new PlanGenerationContext(dadosPlano, null, semanaInicio, null, null);

            persister.persist(planoDto, ctx, ModoGeracaoPlano.PROXIMA_SEMANA);

            ArgumentCaptor<PlanoMetaDados> captor = ArgumentCaptor.forClass(PlanoMetaDados.class);
            verify(planoMetadadosRepository).save(captor.capture());
            assertThat(captor.getValue().getVolumePlanejado()).isEqualByComparingTo(BigDecimal.valueOf(8.0 + 21.1));
        }
    }

    // ---- helpers ----

    private Atleta atletaComAssessoria() {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        Atleta atleta = new Atleta();
        atleta.setId(UUID.randomUUID());
        atleta.setAssessoria(assessoria);
        return atleta;
    }

    private TreinoPlanejadoLlmDto treinoDto(String diaSemana, String tipoTreino, double distanciaKm) {
        return new TreinoPlanejadoLlmDto(diaSemana, tipoTreino, null, null, null, null, null,
                "01:00:00", distanciaKm, null, null);
    }

    private PlanoSemanalLlmDto planoDtoCom(List<TreinoPlanejadoLlmDto> treinos, double volumeDeclaradoPeloLlm) {
        return PlanoSemanalLlmDto.builder()
                .volumePlanejadoKm(volumeDeclaradoPeloLlm)
                .volumeAlvoKm(volumeDeclaradoPeloLlm)
                .status("PLANEJADO")
                .objetivoSemanal("Semana de teste")
                .treinosPlanejados(treinos)
                .build();
    }

    private DadosPlanoDto dadosPlanoDto(Atleta atleta, PlanoMetaDados metaDados) {
        return new DadosPlanoDto(atleta, LocalDate.now(), null, List.of(), metaDados);
    }
}
