package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Kudos;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.MotivoKudos;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.services.impl.KudosServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Janela de tempo de {@link KudosRepository#findRecentesByAtletaIdAndTenantId} — mudança
 * {@code kudos-janela-recentes}: "kudos recentes" deixa de ser um LIMIT por contagem e passa a
 * ser uma janela de tempo ({@code createdAt >= desde}); sem isso, um kudo de meses atrás ficava
 * preso como "recente" até 10 mais novos o empurrarem para fora.
 */
@Transactional
class KudosRepositoryTest extends AbstractIntegrationTest {

    @Autowired private KudosRepository kudosRepository;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private static final Instant AGORA = Instant.parse("2026-08-27T12:00:00Z");
    private static final Instant DESDE = AGORA.minus(KudosServiceImpl.JANELA_KUDOS_RECENTES_DIAS, ChronoUnit.DAYS);

    private static final MotivoKudos[] MOTIVOS = MotivoKudos.values();

    private Assessoria assessoria;
    private Atleta atleta;
    private Usuario coach;
    private int proximoMotivo = 0;

    @BeforeEach
    void setUp() {
        assessoria = new Assessoria();
        assessoria.setNome("Assessoria Test Kudos");
        assessoria.setDominio("kudos-test-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        atleta = new Atleta();
        atleta.setNome("Atleta Kudos");
        atleta.setEmail("atleta-kudos-" + UUID.randomUUID() + "@test.com");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);

        coach = new Usuario();
        coach.setId(UUID.randomUUID());
        coach.setKeycloakId("coach-" + UUID.randomUUID());
        coach.setEmail("coach-kudos@test.com");
        coach.setNome("Coach Kudos");
        coach.setRole(UserRole.TECNICO);
        coach.setAssessoria(assessoria);
        coach = usuarioRepository.save(coach);
    }

    @Test
    @DisplayName("kudo de 6 dias aparece, de 8 dias não; de exatamente 7 dias aparece (limite inclusivo)")
    void janelaDeSeteDias() {
        Kudos dentroDaJanela = salvarKudoEm(AGORA.minus(6, ChronoUnit.DAYS));
        Kudos noLimite = salvarKudoEm(DESDE);
        salvarKudoEm(AGORA.minus(8, ChronoUnit.DAYS)); // fora da janela — não deve aparecer

        List<Kudos> resultado = kudosRepository.findRecentesByAtletaIdAndTenantId(
                atleta.getId(), assessoria.getId(), DESDE);

        assertThat(resultado).extracting(Kudos::getId)
                .containsExactlyInAnyOrder(dentroDaJanela.getId(), noLimite.getId());
    }

    @Test
    @DisplayName("resultado vem mais recente primeiro")
    void ordenadoPorCreatedAtDesc() {
        Kudos maisAntigo = salvarKudoEm(AGORA.minus(5, ChronoUnit.DAYS));
        Kudos maisRecente = salvarKudoEm(AGORA.minus(1, ChronoUnit.DAYS));

        List<Kudos> resultado = kudosRepository.findRecentesByAtletaIdAndTenantId(
                atleta.getId(), assessoria.getId(), DESDE);

        assertThat(resultado).extracting(Kudos::getId)
                .containsExactly(maisRecente.getId(), maisAntigo.getId());
    }

    @Test
    @DisplayName("sem kudos na janela: lista vazia")
    void semKudosNaJanela() {
        salvarKudoEm(AGORA.minus(30, ChronoUnit.DAYS));

        List<Kudos> resultado = kudosRepository.findRecentesByAtletaIdAndTenantId(
                atleta.getId(), assessoria.getId(), DESDE);

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("kudo de outro tenant não aparece")
    void isolamentoPorTenant() {
        salvarKudoEm(AGORA.minus(1, ChronoUnit.DAYS));
        UUID outroTenant = UUID.randomUUID();

        List<Kudos> resultado = kudosRepository.findRecentesByAtletaIdAndTenantId(
                atleta.getId(), outroTenant, DESDE);

        assertThat(resultado).isEmpty();
    }

    /**
     * {@code @PrePersist} grava {@code createdAt = Instant.now()} e a coluna é
     * {@code updatable = false} — backdatar exige SQL nativo pós-insert, não o setter.
     */
    private Kudos salvarKudoEm(Instant createdAt) {
        // uk_kudos_atleta_coach_motivo_data força motivo distinto por kudo neste fixture —
        // `data` vem do @PrePersist como LocalDate.now(), igual para todos os kudos do teste.
        MotivoKudos motivo = MOTIVOS[proximoMotivo++ % MOTIVOS.length];
        Kudos kudos = Kudos.builder()
                .atleta(atleta).coach(coach).motivo(motivo)
                .tenantId(assessoria.getId())
                .build();
        kudos = kudosRepository.save(kudos);
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE tb_kudos SET created_at = :ts WHERE id = :id")
                .setParameter("ts", createdAt)
                .setParameter("id", kudos.getId())
                .executeUpdate();
        entityManager.clear();
        return kudos;
    }
}
