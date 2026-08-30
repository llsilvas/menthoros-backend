package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.core.WorkoutAnalysisProperties;
import br.com.menthoros.backend.dto.output.AthleteWorkoutAnalysisOutputDto;
import br.com.menthoros.backend.entity.AnaliseWorkout;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AnaliseStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.repository.AiWorkoutAnalysisRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.WorkoutAnalysisEligibility;
import br.com.menthoros.backend.multitenancy.TenantContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AtletaWorkoutAnalysisServiceImpl")
class AtletaWorkoutAnalysisServiceImplTest {

    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private AiWorkoutAnalysisRepository analiseRepository;

    private WorkoutAnalysisProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private AtletaWorkoutAnalysisServiceImpl service;

    private UUID tenantId;
    private UUID atletaId;
    private UUID treinoId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        treinoId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        properties = new WorkoutAnalysisProperties();
        meterRegistry = new SimpleMeterRegistry();
        service = new AtletaWorkoutAnalysisServiceImpl(
                treinoRealizadoRepository, analiseRepository,
                new WorkoutAnalysisEligibility(properties), properties, meterRegistry,
                Clock.fixed(Instant.parse("2026-08-30T12:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private TreinoRealizado realizado() {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setId(treinoId);
        tr.setTenantId(tenantId);
        tr.setAtleta(Atleta.builder().id(atletaId).build());
        tr.setPercepcaoEsforco(7);
        tr.setDataTreino(LocalDate.now());
        tr.setDuracaoMin(Duration.ofMinutes(58));
        tr.setDistanciaKm(new BigDecimal("11.2"));
        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(tr));
        return tr;
    }

    private AnaliseWorkout completa() {
        AnaliseWorkout a = new AnaliseWorkout();
        a.setTreinoRealizadoId(treinoId);
        a.setTenantId(tenantId);
        a.setStatus(AnaliseStatus.COMPLETED);
        a.setAnalyzedAt(Instant.parse("2026-08-30T11:00:00Z"));
        a.setAtletaReconhecimento("Você segurou o ritmo.");
        a.setAtletaComoFoi("Saiu como planejado.");
        a.setAtletaEsforco("Pesou um pouco mais que o esperado.");
        a.setAtletaProximoTreino("Capriche no sono hoje.");
        return a;
    }

    @Test
    @DisplayName("COMPLETED devolve os quatro textos, os números e carimba a primeira visualização")
    void completedDevolveDto() {
        TreinoRealizado tr = realizado();
        TreinoPlanejado planejado = new TreinoPlanejado();
        planejado.setDuracaoMin(Duration.ofMinutes(61));
        planejado.setDistanciaKm(new BigDecimal("11.0"));
        planejado.setPercepcaoEsforcoEsperada(6);
        tr.setTreinoPlanejado(planejado);
        AnaliseWorkout analise = completa();
        when(analiseRepository.findByTreinoRealizadoIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.of(analise));

        Optional<AthleteWorkoutAnalysisOutputDto> result = service.buscarAnalise(atletaId, treinoId);

        assertThat(result).isPresent();
        AthleteWorkoutAnalysisOutputDto dto = result.get();
        assertThat(dto.status()).isEqualTo(AnaliseStatus.COMPLETED);
        assertThat(dto.comoFoi()).isEqualTo("Saiu como planejado.");
        assertThat(dto.executado().duracaoMin()).isEqualTo(58L);
        assertThat(dto.executado().rpe()).isEqualTo(7);
        assertThat(dto.planejado().rpeEsperado()).isEqualTo(6);
        assertThat(analise.getAtletaPrimeiraVisualizacaoEm()).isEqualTo(Instant.parse("2026-08-30T12:00:00Z"));
        assertThat(meterRegistry.counter("atleta_analise_visualizada_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("segunda visualização não incrementa a métrica nem regrava o carimbo")
    void segundaVisualizacaoNaoConta() {
        realizado();
        AnaliseWorkout analise = completa();
        Instant primeira = Instant.parse("2026-08-29T08:00:00Z");
        analise.setAtletaPrimeiraVisualizacaoEm(primeira);
        when(analiseRepository.findByTreinoRealizadoIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.of(analise));

        service.buscarAnalise(atletaId, treinoId);

        assertThat(analise.getAtletaPrimeiraVisualizacaoEm()).isEqualTo(primeira);
        assertThat(meterRegistry.counter("atleta_analise_visualizada_total").count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("elegível sem linha (janela do @Async) devolve 200 PENDING com os números")
    void elegivelSemLinhaEhPending() {
        realizado();
        when(analiseRepository.findByTreinoRealizadoIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.empty());

        Optional<AthleteWorkoutAnalysisOutputDto> result = service.buscarAnalise(atletaId, treinoId);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(AnaliseStatus.PENDING);
        assertThat(result.get().comoFoi()).isNull();
        assertThat(result.get().executado().duracaoMin()).isEqualTo(58L);
        assertThat(result.get().planejado()).isNull();
    }

    @Test
    @DisplayName("linha PENDING devolve 200 PENDING")
    void linhaPendingEhPending() {
        realizado();
        AnaliseWorkout pendente = new AnaliseWorkout();
        pendente.setStatus(AnaliseStatus.PENDING);
        when(analiseRepository.findByTreinoRealizadoIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.of(pendente));

        assertThat(service.buscarAnalise(atletaId, treinoId))
                .hasValueSatisfying(dto -> assertThat(dto.status()).isEqualTo(AnaliseStatus.PENDING));
    }

    @Test
    @DisplayName("sem RPE não é elegível: 204")
    void semRpeEh204() {
        realizado().setPercepcaoEsforco(null);

        assertThat(service.buscarAnalise(atletaId, treinoId)).isEmpty();
    }

    @Test
    @DisplayName("mais antigo que maxIdadeDias: 204")
    void antigoEh204() {
        realizado().setDataTreino(LocalDate.now().minusDays(31));

        assertThat(service.buscarAnalise(atletaId, treinoId)).isEmpty();
    }

    @Test
    @DisplayName("FAILED: 204")
    void failedEh204() {
        realizado();
        AnaliseWorkout falha = new AnaliseWorkout();
        falha.setStatus(AnaliseStatus.FAILED);
        when(analiseRepository.findByTreinoRealizadoIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.of(falha));

        assertThat(service.buscarAnalise(atletaId, treinoId)).isEmpty();
    }

    @Test
    @DisplayName("COMPLETED com bloco nulo (bloqueado ou anterior à change): 204")
    void blocoNuloEh204() {
        realizado();
        AnaliseWorkout semBloco = completa();
        semBloco.setAtletaComoFoi(null);
        when(analiseRepository.findByTreinoRealizadoIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.of(semBloco));

        assertThat(service.buscarAnalise(atletaId, treinoId)).isEmpty();
        assertThat(meterRegistry.counter("atleta_analise_visualizada_total").count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("kill switch desligado: 204 para qualquer realizado")
    void killSwitchDesligadoEh204() {
        realizado();
        properties.getAthleteMessage().setEnabled(false);
        when(analiseRepository.findByTreinoRealizadoIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.of(completa()));

        assertThat(service.buscarAnalise(atletaId, treinoId)).isEmpty();
    }

    @Test
    @DisplayName("realizado de outro atleta do mesmo tenant: 404")
    void outroAtletaEh404() {
        TreinoRealizado deOutro = realizado();
        deOutro.setAtleta(Atleta.builder().id(UUID.randomUUID()).build());

        assertThatThrownBy(() -> service.buscarAnalise(atletaId, treinoId))
                .isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    @DisplayName("outro tenant (repo não encontra): 404")
    void outroTenantEh404() {
        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarAnalise(atletaId, treinoId))
                .isInstanceOf(DomainNotFoundException.class);
    }
}
