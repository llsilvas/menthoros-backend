package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoTreino;
import br.com.menthoros.backend.entity.SeasonWeek;
import br.com.menthoros.backend.enums.SeasonPlanStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integração real (Testcontainers + V117) de {@link SeasonWeekRepository}.
 */
@Transactional
class SeasonWeekRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private SeasonWeekRepository seasonWeekRepository;

    @PersistenceContext
    private EntityManager em;

    private SeasonPlanFixtures fx;
    private PlanoTreino plano;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        fx = new SeasonPlanFixtures(em);
        Atleta atleta = fx.atleta(fx.assessoria());
        plano = fx.planoTreino(atleta, fx.prova(atleta), SeasonPlanStatus.PROPOSED);
        tenantId = atleta.getAssessoria().getId();
    }

    @Test
    @DisplayName("lê as semanas da temporada em ordem, dentro do tenant")
    void lePorTemporadaEmOrdem() {
        fx.semana(plano, 2);
        fx.semana(plano, 1);
        em.flush();
        em.clear();

        assertThat(seasonWeekRepository
                .findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(plano.getId(), tenantId))
                .extracting(SeasonWeek::getWeekIndex)
                .containsExactly(1, 2);
    }

    @Test
    @DisplayName("semanas de outro tenant não aparecem")
    void isolaTenant() {
        fx.semana(plano, 1);
        em.flush();

        assertThat(seasonWeekRepository
                .findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(plano.getId(), UUID.randomUUID()))
                .isEmpty();
    }

    @Test
    @DisplayName("recusa duas semanas com o mesmo índice na mesma temporada")
    void recusaIndiceRepetido() {
        fx.semana(plano, 1);
        em.flush();

        assertThatThrownBy(() -> {
            fx.semana(plano, 1);
            seasonWeekRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
