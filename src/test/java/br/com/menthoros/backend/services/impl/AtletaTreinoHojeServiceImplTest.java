package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.TreinoHojeDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.EtapaTreino;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.MotivoPulo;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.helper.AtletaHojeResolver;
import br.com.menthoros.backend.services.helper.EtapaAlvoResolver;
import br.com.menthoros.backend.services.helper.IntervalsIcuFcAlvoResolver;
import br.com.menthoros.backend.services.helper.ZonaTreinoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AtletaTreinoHojeServiceImpl")
class AtletaTreinoHojeServiceImplTest {

    /** 03:50Z do dia 27 = 23:50 do dia 26 em Manaus — "hoje" do atleta é 26. */
    private static final Instant MADRUGADA_UTC = Instant.parse("2026-08-27T03:50:00Z");
    private static final LocalDate HOJE_DO_ATLETA = LocalDate.of(2026, 8, 26);

    @Mock private AtletaRepository atletaRepository;
    @Mock private TreinoPlanejadoRepository treinoPlanejadoRepository;

    private AtletaTreinoHojeServiceImpl service;
    private UUID tenantId;
    private UUID atletaId;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        atleta = Atleta.builder().id(atletaId).fcLimiar(170).timezone("America/Manaus").build();
        Clock clock = Clock.fixed(MADRUGADA_UTC, ZoneOffset.UTC);
        service = new AtletaTreinoHojeServiceImpl(
                atletaRepository, treinoPlanejadoRepository,
                new AtletaHojeResolver(clock),
                new EtapaAlvoResolver(new IntervalsIcuFcAlvoResolver(new ZonaTreinoService())));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("devolve o planejado de hoje (fuso do atleta) com etapas e alvos resolvidos")
    void treinoDeHoje() {
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
        TreinoPlanejado tp = planejado(HOJE_DO_ATLETA);
        tp.setEtapas(List.of(
                EtapaTreino.builder().ordem(1).tipoEtapa("AQUECIMENTO").duracaoMin(10).build(),
                EtapaTreino.builder().ordem(2).tipoEtapa("INTERVALADO").duracaoMin(4)
                        .fcAlvoEtapa("85-89%").ritmoAlvo("4:30-4:45").build()));
        when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(atletaId, HOJE_DO_ATLETA, HOJE_DO_ATLETA))
                .thenReturn(List.of(tp));

        TreinoHojeDto dto = service.getTreinoHoje(atletaId).orElseThrow();

        assertThat(dto.hoje()).isEqualTo(HOJE_DO_ATLETA);
        assertThat(dto.id()).isEqualTo(tp.getId());
        assertThat(dto.tipoTreino()).isEqualTo("INTERVALADO");
        assertThat(dto.duracaoMin()).isEqualTo(45);
        assertThat(dto.statusTreino()).isEqualTo("PENDENTE");
        assertThat(dto.etapas()).hasSize(2);
        assertThat(dto.etapas().get(0).alvoPrimario()).isEqualTo(TreinoHojeDto.AlvoPrimario.NENHUM);
        assertThat(dto.etapas().get(1).alvoPrimario()).isEqualTo(TreinoHojeDto.AlvoPrimario.FC);
        assertThat(dto.etapas().get(1).textoSecundario()).isEqualTo("4:30-4:45");
    }

    @Test
    @DisplayName("sem planejado hoje → vazio")
    void semTreinoHoje() {
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
        when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(atletaId, HOJE_DO_ATLETA, HOJE_DO_ATLETA))
                .thenReturn(List.of());

        assertThat(service.getTreinoHoje(atletaId)).isEmpty();
    }

    @Test
    @DisplayName("treino sem etapas → lista omitida (null), duração zero → omitida")
    void semEtapas() {
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
        TreinoPlanejado tp = planejado(HOJE_DO_ATLETA);
        tp.setDuracaoMin(Duration.ZERO);
        when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(atletaId, HOJE_DO_ATLETA, HOJE_DO_ATLETA))
                .thenReturn(List.of(tp));

        TreinoHojeDto dto = service.getTreinoHoje(atletaId).orElseThrow();

        assertThat(dto.etapas()).isNull();
        assertThat(dto.duracaoMin()).isNull();
    }

    @Test
    @DisplayName("atleta fora do tenant → 404 antes de qualquer leitura de plano")
    void atletaForaDoTenant() {
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTreinoHoje(atletaId)).isInstanceOf(DomainNotFoundException.class);
        verify(treinoPlanejadoRepository, never()).findByAtletaIdAndDataBetween(any(), any(), any());
    }

    @Test
    @DisplayName("treino pulado expõe motivo e carimbo")
    void treinoPuladoExpoeMotivo() {
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
        TreinoPlanejado tp = planejado(HOJE_DO_ATLETA);
        tp.setStatusTreino(TreinoExecucaoStatus.PERDIDO);
        tp.setMotivoPulo(MotivoPulo.DOR);
        tp.setPuladoEm(LocalDateTime.of(2026, 8, 26, 22, 0));
        when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(atletaId, HOJE_DO_ATLETA, HOJE_DO_ATLETA))
                .thenReturn(List.of(tp));

        TreinoHojeDto dto = service.getTreinoHoje(atletaId).orElseThrow();

        assertThat(dto.statusTreino()).isEqualTo("PERDIDO");
        assertThat(dto.motivoPulo()).isEqualTo("DOR");
        assertThat(dto.puladoEm()).isEqualTo(LocalDateTime.of(2026, 8, 26, 22, 0));
    }

    @Nested
    @DisplayName("pularHoje")
    class PularHoje {

        @Test
        @DisplayName("marca PERDIDO com motivo e carimbo, sem criar realizado")
        void pulaComMotivo() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            TreinoPlanejado tp = planejado(HOJE_DO_ATLETA);
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(atletaId, HOJE_DO_ATLETA, HOJE_DO_ATLETA))
                    .thenReturn(List.of(tp));
            when(treinoPlanejadoRepository.save(tp)).thenReturn(tp);

            TreinoHojeDto dto = service.pularHoje(atletaId, MotivoPulo.SEM_TEMPO);

            assertThat(tp.getStatusTreino()).isEqualTo(TreinoExecucaoStatus.PERDIDO);
            assertThat(tp.getMotivoPulo()).isEqualTo(MotivoPulo.SEM_TEMPO);
            // clock fixo em 03:50Z; o carimbo é LocalDateTime no fuso do atleta (23:50 em Manaus)
            assertThat(tp.getPuladoEm()).isEqualTo(LocalDateTime.of(2026, 8, 26, 23, 50));
            assertThat(dto.statusTreino()).isEqualTo("PERDIDO");
            assertThat(dto.motivoPulo()).isEqualTo("SEM_TEMPO");
            verify(treinoPlanejadoRepository).save(tp);
        }

        @Test
        @DisplayName("motivo é opcional")
        void pulaSemMotivo() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            TreinoPlanejado tp = planejado(HOJE_DO_ATLETA);
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(atletaId, HOJE_DO_ATLETA, HOJE_DO_ATLETA))
                    .thenReturn(List.of(tp));
            when(treinoPlanejadoRepository.save(tp)).thenReturn(tp);

            TreinoHojeDto dto = service.pularHoje(atletaId, null);

            assertThat(tp.getStatusTreino()).isEqualTo(TreinoExecucaoStatus.PERDIDO);
            assertThat(tp.getMotivoPulo()).isNull();
            assertThat(tp.getPuladoEm()).isNotNull();
            assertThat(dto.motivoPulo()).isNull();
        }

        @Test
        @DisplayName("sem treino hoje → regra de negócio (422)")
        void semTreinoHoje() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(atletaId, HOJE_DO_ATLETA, HOJE_DO_ATLETA))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> service.pularHoje(atletaId, MotivoPulo.CANSADO))
                    .isInstanceOf(DomainRuleViolationException.class);
            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("treino já REALIZADO não pode ser pulado (422), nada muda")
        void jaRealizado() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            TreinoPlanejado tp = planejado(HOJE_DO_ATLETA);
            tp.setStatusTreino(TreinoExecucaoStatus.REALIZADO);
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(atletaId, HOJE_DO_ATLETA, HOJE_DO_ATLETA))
                    .thenReturn(List.of(tp));

            assertThatThrownBy(() -> service.pularHoje(atletaId, MotivoPulo.DOR))
                    .isInstanceOf(DomainRuleViolationException.class);
            assertThat(tp.getStatusTreino()).isEqualTo(TreinoExecucaoStatus.REALIZADO);
            assertThat(tp.getMotivoPulo()).isNull();
            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("atleta fora do tenant → 404, nada lido nem salvo")
        void tenant() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.pularHoje(atletaId, MotivoPulo.DOR))
                    .isInstanceOf(DomainNotFoundException.class);
            verify(treinoPlanejadoRepository, never()).findByAtletaIdAndDataBetween(any(), any(), any());
            verify(treinoPlanejadoRepository, never()).save(any());
        }
    }

    private TreinoPlanejado planejado(LocalDate data) {
        TreinoPlanejado tp = new TreinoPlanejado();
        tp.setId(UUID.randomUUID());
        tp.setDataTreino(data);
        tp.setTipoTreino(TipoTreino.INTERVALADO);
        tp.setDescricao("2x(4' forte)");
        tp.setDuracaoMin(Duration.ofMinutes(45));
        tp.setZonaAlvo("Z4");
        tp.setStatusTreino(TreinoExecucaoStatus.PENDENTE);
        tp.setAtleta(atleta);
        return tp;
    }
}
