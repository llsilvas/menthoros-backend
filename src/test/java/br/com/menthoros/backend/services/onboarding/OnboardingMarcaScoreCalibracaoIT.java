package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.AthleteBaselineState;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PerfilOnboardingAtleta;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DispositivoMarca;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AthleteBaselineStateRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PerfilOnboardingAtletaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guarda da change add-watch-brand-enum-prior: marca de prior 3 (Apple, Amazfit, Samsung) nao pontua
 * "Fonte confiavel", entao o atleta sem historico, com onboarding completo + FC maxima + pace
 * limiar, fica em 35 (tier C). So o bonus de coach o promove a B e, sendo B, a calibracao COMECA.
 * Um GARMIN (prior 1) chega a 50 (B); com o bonus vira A e a calibracao NAO comeca. Se alguem
 * promover uma marca nova a prior 2, os cenarios AMAZFIT/SAMSUNG quebram aqui.
 *
 * <p>Roda o {@code montarContexto} real (scorer, tier e snapshot em {@code AthleteBaselineState}).
 */
@DisplayName("Marca do relogio -> score -> tier -> calibracao (montarContexto real)")
class OnboardingMarcaScoreCalibracaoIT extends AbstractIntegrationTest {

    @Autowired private OnboardingService onboardingService;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private PerfilOnboardingAtletaRepository perfilRepository;
    @Autowired private AthleteBaselineStateRepository baselineStateRepository;

    private UUID tenantId;

    @BeforeEach
    void seedTenant() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Marca Score");
        assessoria.setDominio("marca-score-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        tenantId = assessoriaRepository.save(assessoria).getId();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void limpaTenant() {
        TenantContext.clear();
    }

    @ParameterizedTest
    @EnumSource(value = DispositivoMarca.class, names = {"AMAZFIT", "SAMSUNG"})
    @DisplayName("marca nova + bonus de coach: score 35, tier C -> B, calibracao iniciada")
    void marcaNovaComBonusDeCoach(DispositivoMarca marca) {
        UUID atletaId = seedAtleta(marca, true);

        onboardingService.montarContexto(atletaId, tenantId);

        AthleteBaselineState estado = estado(atletaId);
        assertThat(estado.getConfidenceScore()).isEqualTo(35);
        assertThat(estado.getConfidenceTier()).isEqualTo("B");
        assertThat(estado.getCalibracaoIniciadaEm()).isNotNull();
    }

    @Test
    @DisplayName("controle GARMIN + bonus de coach: score 50, tier B -> A, calibracao NAO iniciada")
    void garminComBonusDeCoach() {
        UUID atletaId = seedAtleta(DispositivoMarca.GARMIN, true);

        onboardingService.montarContexto(atletaId, tenantId);

        AthleteBaselineState estado = estado(atletaId);
        assertThat(estado.getConfidenceScore()).isEqualTo(50);
        assertThat(estado.getConfidenceTier()).isEqualTo("A");
        assertThat(estado.getCalibracaoIniciadaEm()).isNull();
    }

    @Test
    @DisplayName("controle GARMIN sem bonus: score 50, tier B, calibracao iniciada")
    void garminSemBonus() {
        UUID atletaId = seedAtleta(DispositivoMarca.GARMIN, false);

        onboardingService.montarContexto(atletaId, tenantId);

        AthleteBaselineState estado = estado(atletaId);
        assertThat(estado.getConfidenceScore()).isEqualTo(50);
        assertThat(estado.getConfidenceTier()).isEqualTo("B");
        assertThat(estado.getCalibracaoIniciadaEm()).isNotNull();
    }

    private AthleteBaselineState estado(UUID atletaId) {
        return baselineStateRepository.findByAtletaIdAndTenantId(atletaId, tenantId).orElseThrow();
    }

    private UUID seedAtleta(DispositivoMarca marca, boolean preenchidoPorCoach) {
        Assessoria assessoria = assessoriaRepository.findById(tenantId).orElseThrow();
        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Score");
        atleta.setEmail("score-" + UUID.randomUUID() + "@menthoros.test");
        atleta.setObjetivo("Correr uma maratona");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta.setFcMaxima(185);
        atleta.setPaceLimiar(new BigDecimal("4.5"));
        atleta = atletaRepository.save(atleta);

        PerfilOnboardingAtleta perfil = new PerfilOnboardingAtleta();
        Atleta ref = new Atleta();
        ref.setId(atleta.getId());
        perfil.setAtleta(ref);
        perfil.setTenantId(tenantId);
        perfil.setStatus("COMPLETO");
        perfil.setPreenchidoPorCoach(preenchidoPorCoach);
        perfil.setDispositivoMarca(marca);
        perfilRepository.save(perfil);
        return atleta.getId();
    }
}
