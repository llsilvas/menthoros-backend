package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.GarminPing;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.GarminPingStatus;
import br.com.menthoros.backend.exception.GarminPingRegistroException;
import br.com.menthoros.backend.repository.GarminPingRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GarminPingServiceImplTest {

    private static final String USER_ID = "garmin-user-1";
    private static final String CALLBACK =
            "https://apis.garmin.com/wellness-api/rest/activityFile?id=123&token=segredo";

    @Mock private GarminPingRepository pingRepository;
    @Mock private IntegracaoExternaRepository integracaoRepository;
    @InjectMocks private GarminPingServiceImpl service;

    private final ObjectMapper mapper = new ObjectMapper();
    private IntegracaoExterna conexao;
    private UUID atletaId;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        atletaId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        conexao = new IntegracaoExterna();
        conexao.setAtleta(Atleta.builder().id(atletaId).build());
        conexao.setTenantId(tenantId);
        conexao.setPlataforma(FonteDados.GARMIN);
        conexao.setExternalAthleteId(USER_ID);
        conexao.setAtivo(true);
    }

    @Nested
    @DisplayName("registrar")
    class Registrar {

        @ParameterizedTest
        @ValueSource(strings = {"RUNNING", "INDOOR_RUNNING", "OBSTACLE_RUN", "STREET_RUNNING", "TRACK_RUNNING",
                "TRAIL_RUNNING", "TREADMILL_RUNNING", "ULTRA_RUN", "VIRTUAL_RUN"})
        @DisplayName("corrida de atleta conectado nasce PENDING com os dados do ping")
        void corridaNascePendente(String activityType) {
            conexaoAtiva();

            service.registrar(List.of(item(activityType)));

            GarminPing salvo = capturarSalvo();
            assertThat(salvo.getStatus()).isEqualTo(GarminPingStatus.PENDING);
            assertThat(salvo.getTenantId()).isEqualTo(tenantId);
            assertThat(salvo.getAthleteId()).isEqualTo(atletaId);
            assertThat(salvo.getGarminUserId()).isEqualTo(USER_ID);
            assertThat(salvo.getSummaryId()).isEqualTo("s1");
            assertThat(salvo.getActivityId()).isEqualTo("a1");
            assertThat(salvo.getCallbackUrl()).isEqualTo(CALLBACK);
            assertThat(salvo.getActivityType()).isEqualTo(activityType);
            assertThat(salvo.getDeviceName()).isEqualTo("Forerunner 970");
            assertThat(salvo.getStartTime()).isEqualTo(Instant.ofEpochSecond(1_730_000_000L));
            assertThat(salvo.isManual()).isFalse();
            assertThat(salvo.getReason()).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"CYCLING", "LAP_SWIMMING", "WALKING", "STRENGTH_TRAINING", ""})
        @DisplayName("modalidade fora de corrida nasce DISCARDED com motivo")
        void naoCorridaDescartada(String activityType) {
            conexaoAtiva();

            service.registrar(List.of(item(activityType)));

            GarminPing salvo = capturarSalvo();
            assertThat(salvo.getStatus()).isEqualTo(GarminPingStatus.DISCARDED);
            assertThat(salvo.getReason()).contains("corrida");
        }

        @Test
        @DisplayName("atividade manual nasce DISCARDED com motivo")
        void manualDescartada() {
            conexaoAtiva();
            ObjectNode item = item("RUNNING");
            item.put("manual", true);

            service.registrar(List.of(item));

            GarminPing salvo = capturarSalvo();
            assertThat(salvo.getStatus()).isEqualTo(GarminPingStatus.DISCARDED);
            assertThat(salvo.isManual()).isTrue();
            assertThat(salvo.getReason()).contains("manual");
        }

        @ParameterizedTest
        @ValueSource(strings = {"TCX", "GPX"})
        @DisplayName("arquivo que não é FIT nasce DISCARDED com motivo")
        void naoFitDescartado(String fileType) {
            conexaoAtiva();
            ObjectNode item = item("RUNNING");
            item.put("fileType", fileType);

            service.registrar(List.of(item));

            GarminPing salvo = capturarSalvo();
            assertThat(salvo.getStatus()).isEqualTo(GarminPingStatus.DISCARDED);
            assertThat(salvo.getReason()).contains("FIT");
        }

        @Test
        @DisplayName("atleta com conexão inativa: DISCARDED com motivo, no tenant da conexão")
        void conexaoInativaDescartada() {
            conexao.setAtivo(false);
            when(integracaoRepository.findActiveByExternalAthleteIdAndPlataforma(USER_ID, FonteDados.GARMIN))
                    .thenReturn(Optional.empty());
            when(integracaoRepository.findFirstByExternalAthleteIdAndPlataformaOrderByAtualizadoEmDesc(
                    USER_ID, FonteDados.GARMIN)).thenReturn(Optional.of(conexao));

            service.registrar(List.of(item("RUNNING")));

            GarminPing salvo = capturarSalvo();
            assertThat(salvo.getStatus()).isEqualTo(GarminPingStatus.DISCARDED);
            assertThat(salvo.getTenantId()).isEqualTo(tenantId);
            assertThat(salvo.getReason()).contains("inativa");
        }

        @Test
        @DisplayName("userId sem conexão registrada não grava nada")
        void userIdDesconhecidoNaoGrava() {
            when(integracaoRepository.findActiveByExternalAthleteIdAndPlataforma(USER_ID, FonteDados.GARMIN))
                    .thenReturn(Optional.empty());
            when(integracaoRepository.findFirstByExternalAthleteIdAndPlataformaOrderByAtualizadoEmDesc(
                    USER_ID, FonteDados.GARMIN)).thenReturn(Optional.empty());

            service.registrar(List.of(item("RUNNING")));

            verifyNoInteractions(pingRepository);
        }

        @ParameterizedTest
        @ValueSource(strings = {"userId", "summaryId", "callbackURL", "startTimeInSeconds"})
        @DisplayName("item sem campo obrigatório é pulado antes de qualquer consulta ou gravação")
        void itemIncompletoPulado(String campo) {
            ObjectNode item = item("RUNNING");
            item.remove(campo);

            service.registrar(List.of(item));

            verifyNoInteractions(pingRepository, integracaoRepository);
        }

        @Test
        @DisplayName("summaryId repetido (UNIQUE) é ignorado e o lote segue")
        void summaryIdRepetidoIgnorado() {
            conexaoAtiva();
            when(pingRepository.save(any(GarminPing.class)))
                    .thenThrow(new DataIntegrityViolationException("dup",
                            new SQLException("duplicate key value violates unique constraint \"uk_garmin_ping_summary_id\"")))
                    .thenAnswer(inv -> inv.getArgument(0));

            ObjectNode segundo = item("RUNNING");
            segundo.put("summaryId", "s2");

            assertThatCode(() -> service.registrar(List.of(item("RUNNING"), segundo))).doesNotThrowAnyException();
            verify(pingRepository, times(2)).save(any(GarminPing.class));
        }

        @Test
        @DisplayName("outra violação de integridade propaga sanitizada, sem causa nem dados da linha")
        void outraViolacaoPropagaSanitizada() {
            conexaoAtiva();
            when(pingRepository.save(any(GarminPing.class)))
                    .thenThrow(new DataIntegrityViolationException("Failing row contains (" + CALLBACK + ")"));

            assertThatThrownBy(() -> service.registrar(List.of(item("RUNNING"))))
                    .isInstanceOf(GarminPingRegistroException.class)
                    .hasNoCause()
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain("segredo"));
        }

        @Test
        @DisplayName("falha de banco propaga sanitizada e interrompe o lote")
        void falhaDeBancoPropaga() {
            conexaoAtiva();
            when(pingRepository.save(any(GarminPing.class))).thenThrow(new QueryTimeoutException("timeout"));
            ObjectNode segundo = item("RUNNING");
            segundo.put("summaryId", "s2");

            assertThatThrownBy(() -> service.registrar(List.of(item("RUNNING"), segundo)))
                    .isInstanceOf(GarminPingRegistroException.class)
                    .hasNoCause();
            verify(pingRepository, times(1)).save(any(GarminPing.class));
        }

        @Test
        @DisplayName("lista vazia ou nula não consulta nada")
        void listaVazia() {
            service.registrar(List.of());
            service.registrar(null);

            verifyNoInteractions(pingRepository, integracaoRepository);
            verify(pingRepository, never()).save(any());
        }
    }

    private void conexaoAtiva() {
        when(integracaoRepository.findActiveByExternalAthleteIdAndPlataforma(USER_ID, FonteDados.GARMIN))
                .thenReturn(Optional.of(conexao));
    }

    private GarminPing capturarSalvo() {
        ArgumentCaptor<GarminPing> captor = ArgumentCaptor.forClass(GarminPing.class);
        verify(pingRepository).save(captor.capture());
        return captor.getValue();
    }

    private ObjectNode item(String activityType) {
        ObjectNode n = mapper.createObjectNode();
        n.put("userId", USER_ID);
        n.put("summaryId", "s1");
        n.put("activityId", "a1");
        n.put("callbackURL", CALLBACK);
        n.put("fileType", "FIT");
        n.put("activityType", activityType);
        n.put("deviceName", "Forerunner 970");
        n.put("startTimeInSeconds", 1_730_000_000L);
        n.put("manual", false);
        return n;
    }
}
