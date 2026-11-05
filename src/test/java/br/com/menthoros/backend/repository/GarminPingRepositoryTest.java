package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.GarminPing;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.GarminPingStatus;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GarminPingRepository: fila de pings activityFiles")
@Transactional
class GarminPingRepositoryTest extends AbstractIntegrationTest {

    @Autowired private GarminPingRepository repository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private EntityManager entityManager;

    private Atleta atleta;
    private Instant agora;

    @BeforeEach
    void setup() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria GarminPing Test");
        assessoria.setDominio("garmin-ping-test-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        atleta = new Atleta();
        atleta.setNome("Atleta GarminPing");
        atleta.setEmail("garmin-ping-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Testar fila");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);

        agora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("summaryId repetido viola a UNIQUE")
        void summaryIdRepetidoViolaUnique() {
            repository.saveAndFlush(ping(GarminPingStatus.PENDING, agora, agora));
            GarminPing repetido = ping(GarminPingStatus.PENDING, agora, agora);
            repetido.setSummaryId(repository.findAll().get(0).getSummaryId());

            assertThatThrownBy(() -> repository.saveAndFlush(repetido))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("findPendingOldestFirst")
    class FindPendingOldestFirst {

        @Test
        @DisplayName("devolve só pendentes vencidos dentro da janela, mais antigo primeiro, até o limite")
        void filtraEOrdena() {
            GarminPing recente = repository.save(ping(GarminPingStatus.PENDING, agora.minus(1, ChronoUnit.HOURS), agora));
            GarminPing antigo = repository.save(ping(GarminPingStatus.PENDING, agora.minus(23, ChronoUnit.HOURS), agora));
            GarminPing medio = repository.save(ping(GarminPingStatus.PENDING, agora.minus(5, ChronoUnit.HOURS), agora));
            repository.save(ping(GarminPingStatus.PENDING, agora.minus(2, ChronoUnit.HOURS), agora.plusSeconds(600)));
            repository.save(ping(GarminPingStatus.PENDING, agora.minus(25, ChronoUnit.HOURS), agora));
            repository.save(ping(GarminPingStatus.SYNCING, agora.minus(3, ChronoUnit.HOURS), agora));
            repository.save(ping(GarminPingStatus.ERROR, agora.minus(4, ChronoUnit.HOURS), agora));

            List<GarminPing> resultado = repository.findPendingOldestFirst(
                    agora, agora.minus(24, ChronoUnit.HOURS), PageRequest.of(0, 2));

            assertThat(resultado).extracting(GarminPing::getId).containsExactly(antigo.getId(), medio.getId());
            assertThat(resultado).extracting(GarminPing::getId).doesNotContain(recente.getId());
        }
    }

    @Nested
    @DisplayName("findExpired")
    class FindExpired {

        @Test
        @DisplayName("devolve pendentes criados antes do início da janela")
        void devolvePendentesForaDaJanela() {
            GarminPing expirado = repository.save(ping(GarminPingStatus.PENDING, agora.minus(25, ChronoUnit.HOURS), agora));
            repository.save(ping(GarminPingStatus.PENDING, agora.minus(23, ChronoUnit.HOURS), agora));
            repository.save(ping(GarminPingStatus.PROCESSED, agora.minus(30, ChronoUnit.HOURS), agora));

            List<GarminPing> resultado = repository.findExpired(agora.minus(24, ChronoUnit.HOURS));

            assertThat(resultado).extracting(GarminPing::getId).containsExactly(expirado.getId());
        }
    }

    @Nested
    @DisplayName("findStuckSyncing")
    class FindStuckSyncing {

        @Test
        @DisplayName("devolve SYNCING iniciados antes do limite")
        void devolveSyncingPresos() {
            GarminPing preso = ping(GarminPingStatus.SYNCING, agora.minus(1, ChronoUnit.HOURS), agora);
            preso.setSyncingSince(agora.minus(11, ChronoUnit.MINUTES));
            preso = repository.save(preso);
            GarminPing emAndamento = ping(GarminPingStatus.SYNCING, agora.minus(1, ChronoUnit.HOURS), agora);
            emAndamento.setSyncingSince(agora.minus(2, ChronoUnit.MINUTES));
            repository.save(emAndamento);

            List<GarminPing> resultado = repository.findStuckSyncing(agora.minus(10, ChronoUnit.MINUTES));

            assertThat(resultado).extracting(GarminPing::getId).containsExactly(preso.getId());
        }
    }

    @Nested
    @DisplayName("deleteByStatusInAndCreatedAtBefore")
    class DeleteByStatusInAndCreatedAtBefore {

        @Test
        @DisplayName("apaga só os status pedidos criados antes do corte")
        void apagaFiltrado() {
            repository.save(ping(GarminPingStatus.PROCESSED, agora.minus(31, ChronoUnit.DAYS), agora));
            repository.save(ping(GarminPingStatus.DISCARDED, agora.minus(31, ChronoUnit.DAYS), agora));
            GarminPing recente = repository.save(ping(GarminPingStatus.PROCESSED, agora.minus(29, ChronoUnit.DAYS), agora));
            GarminPing erro = repository.save(ping(GarminPingStatus.ERROR, agora.minus(31, ChronoUnit.DAYS), agora));
            entityManager.flush();

            int apagados = repository.deleteByStatusInAndCreatedAtBefore(
                    List.of(GarminPingStatus.PROCESSED, GarminPingStatus.DISCARDED), agora.minus(30, ChronoUnit.DAYS));
            entityManager.clear();

            assertThat(apagados).isEqualTo(2);
            assertThat(repository.findAll()).extracting(GarminPing::getId)
                    .containsExactlyInAnyOrder(recente.getId(), erro.getId());
        }
    }

    private GarminPing ping(GarminPingStatus status, Instant createdAt, Instant nextAttemptAt) {
        GarminPing p = new GarminPing();
        p.setTenantId(atleta.getAssessoria().getId());
        p.setAthleteId(atleta.getId());
        p.setGarminUserId("garmin-user-1");
        p.setSummaryId("summary-" + UUID.randomUUID());
        p.setActivityId("activity-1");
        p.setCallbackUrl("https://apis.garmin.com/wellness-api/rest/activityFile?id=1&token=t");
        p.setActivityType("RUNNING");
        p.setFileType("FIT");
        p.setDeviceName("Forerunner 970");
        p.setStartTime(createdAt);
        p.setManual(false);
        p.setStatus(status);
        p.setNextAttemptAt(nextAttemptAt);
        p.setCreatedAt(createdAt);
        return p;
    }
}
