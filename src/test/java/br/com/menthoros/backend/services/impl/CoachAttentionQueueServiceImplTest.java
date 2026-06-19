package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.CoachAttentionItemOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.MotivoAtencao;
import br.com.menthoros.backend.enums.Severidade;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.helper.CoachAttentionSignalEvaluator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CoachAttentionQueueServiceImpl")
class CoachAttentionQueueServiceImplTest {

    @Mock private AtletaRepository atletaRepository;
    @Mock private MetricasDiariasRepository metricasDiariasRepository;
    @Mock private PlanoMetadadosRepository planoMetadadosRepository;
    @Mock private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;

    private CoachAttentionQueueServiceImpl service;
    private UUID tenantId;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-18T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new CoachAttentionQueueServiceImpl(
                atletaRepository, metricasDiariasRepository, planoMetadadosRepository,
                treinoPlanejadoRepository, treinoRealizadoRepository,
                new CoachAttentionSignalEvaluator(), CLOCK);
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("getAttentionQueue")
    class GetAttentionQueue {

        @Test
        @DisplayName("atleta com TSB crítico → item FADIGA CRITICA")
        void fadigaCritica() {
            Atleta atleta = atletaAtivo("Ana", true);
            roster(atleta);
            comTsb(atleta, -40.0);
            comPlanoSemAlertas(atleta);
            semPerdidos(atleta);
            semAtividade(atleta);

            List<CoachAttentionItemOutputDto> fila = service.getAttentionQueue();

            assertThat(fila).singleElement().satisfies(item -> {
                assertThat(item.primaryReason()).isEqualTo(MotivoAtencao.FADIGA);
                assertThat(item.severity()).isEqualTo(Severidade.CRITICA);
                assertThat(item.priorityScore()).isEqualTo(350);
                assertThat(item.suggestedAction()).isNotBlank();
                assertThat(item.generatedAt()).isEqualTo(CLOCK.instant());
                assertThat(item.evidence()).isNotEmpty();
            });
        }

        @Test
        @DisplayName("motivos diferentes no mesmo atleta → 1 item; vence maior peso na mesma severidade, evidências consolidadas")
        void consolidaMotivos() {
            Atleta atleta = atletaAtivo("Bia", true);
            roster(atleta);
            comTsb(atleta, -32.0);              // fadiga ALTA
            comPlano(atleta, true, false, false, false);  // sobrecarga ALTA
            semPerdidos(atleta);
            semAtividade(atleta);

            List<CoachAttentionItemOutputDto> fila = service.getAttentionQueue();

            assertThat(fila).singleElement().satisfies(item -> {
                assertThat(item.severity()).isEqualTo(Severidade.ALTA);
                assertThat(item.primaryReason()).isEqualTo(MotivoAtencao.FADIGA); // peso 50 > sobrecarga 40
                assertThat(item.evidence()).hasSizeGreaterThanOrEqualTo(2); // TSB + sobrecarga
            });
        }

        @Test
        @DisplayName("atleta cujo único sinal é MEDIA (zonas vencidas) → não aparece (corte ALTA/CRITICA)")
        void corteSeveridade() {
            Atleta atleta = atletaAtivo("Caio", false); // precisaTestes=true → zonas vencidas (MEDIA)
            roster(atleta);
            semTsb(atleta);
            comPlanoSemAlertas(atleta);
            semPerdidos(atleta);
            semAtividade(atleta);

            assertThat(service.getAttentionQueue()).isEmpty();
        }

        @Test
        @DisplayName("atleta sem plano ativo → item SEM_PLANO (ALTA)")
        void semPlano() {
            Atleta atleta = atletaAtivo("Dora", true);
            roster(atleta);
            semTsb(atleta);
            when(planoMetadadosRepository.findByAtletaId(atleta.getId())).thenReturn(Optional.empty());
            semPerdidos(atleta);
            semAtividade(atleta);

            assertThat(service.getAttentionQueue()).singleElement().satisfies(item -> {
                assertThat(item.primaryReason()).isEqualTo(MotivoAtencao.SEM_PLANO);
                assertThat(item.severity()).isEqualTo(Severidade.ALTA);
            });
        }

        @Test
        @DisplayName("ordena CRITICA antes de ALTA")
        void ordena() {
            Atleta critico = atletaAtivo("Aaa", true);
            Atleta alto = atletaAtivo("Zzz", true);
            roster(critico, alto);
            comTsb(critico, -40.0);              // CRITICA
            comPlanoSemAlertas(critico);
            semPerdidos(critico);
            semAtividade(critico);
            semTsb(alto);
            when(planoMetadadosRepository.findByAtletaId(alto.getId())).thenReturn(Optional.empty()); // SEM_PLANO ALTA
            semPerdidos(alto);
            semAtividade(alto);

            List<CoachAttentionItemOutputDto> fila = service.getAttentionQueue();

            assertThat(fila).extracting(CoachAttentionItemOutputDto::severity)
                    .containsExactly(Severidade.CRITICA, Severidade.ALTA);
        }

        @Test
        @DisplayName("consulta o roster restrito ao tenant do contexto (isolamento)")
        void isolamentoTenant() {
            Atleta atleta = atletaAtivo("Ana", true);
            roster(atleta);
            comTsb(atleta, -40.0);
            comPlanoSemAlertas(atleta);
            semPerdidos(atleta);
            semAtividade(atleta);

            service.getAttentionQueue();

            verify(atletaRepository).findAllByTenantIdOrderByNome(tenantId);
        }

        @Test
        @DisplayName("atleta INATIVO (paused) não entra na fila")
        void ignoraInativo() {
            Atleta inativo = Atleta.builder().id(UUID.randomUUID()).nome("Eva")
                    .ativo(AtletaStatus.INATIVO).build();
            roster(inativo);

            assertThat(service.getAttentionQueue()).isEmpty();
        }
    }

    // ===== Helpers de montagem =====

    private void roster(Atleta... atletas) {
        when(atletaRepository.findAllByTenantIdOrderByNome(tenantId)).thenReturn(List.of(atletas));
    }

    private Atleta atletaAtivo(String nome, boolean testesEmDia) {
        Atleta.AtletaBuilder b = Atleta.builder().id(UUID.randomUUID()).nome(nome).ativo(AtletaStatus.ATIVO);
        if (testesEmDia) {
            b.dataUltimoTesteFc(LocalDate.now()).dataUltimoTestePace(LocalDate.now());
        }
        return b.build();
    }

    private void comTsb(Atleta atleta, double tsb) {
        MetricasDiarias m = org.mockito.Mockito.mock(MetricasDiarias.class);
        when(m.getTsb()).thenReturn(tsb);
        when(metricasDiariasRepository.findLatestByAtletaId(atleta.getId())).thenReturn(Optional.of(m));
    }

    private void semTsb(Atleta atleta) {
        when(metricasDiariasRepository.findLatestByAtletaId(atleta.getId())).thenReturn(Optional.empty());
    }

    private void comPlanoSemAlertas(Atleta atleta) {
        comPlano(atleta, false, false, false, false);
    }

    private void comPlano(Atleta atleta, boolean sobrecarga, boolean necessitaDescanso,
                          boolean rampAlto, boolean diasConsecutivos) {
        PlanoMetaDados plano = org.mockito.Mockito.mock(PlanoMetaDados.class);
        when(plano.getAlertaSobrecarga()).thenReturn(sobrecarga);
        when(plano.getAlertaNecessitaDescanso()).thenReturn(necessitaDescanso);
        when(plano.getAlertaRampAlto()).thenReturn(rampAlto);
        when(plano.getAlertaDiasConsecutivos()).thenReturn(diasConsecutivos);
        when(planoMetadadosRepository.findByAtletaId(atleta.getId())).thenReturn(Optional.of(plano));
    }

    private void semPerdidos(Atleta atleta) {
        when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(eq(atleta.getId()), any(), any()))
                .thenReturn(List.of());
    }

    private void semAtividade(Atleta atleta) {
        lenient().when(treinoRealizadoRepository.findTopByAtletaIdOrderByDataTreinoDesc(atleta.getId()))
                .thenReturn(Optional.<TreinoRealizado>empty());
    }
}
