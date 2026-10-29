package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** V104 (garmin-connection): a coluna {@code refresh_token_expira_em} existe e o campo faz round-trip. */
@Transactional
class IntegracaoExternaRefreshTokenExpiraEmTest extends AbstractIntegrationTest {

    @Autowired
    private AtletaRepository atletaRepository;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private IntegracaoExternaRepository integracaoExternaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("grava e lê refreshTokenExpiraEm")
    void roundTrip() {
        Instant expira = Instant.now().plus(90, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        IntegracaoExterna conexao = seedIntegracaoGarmin();
        conexao.setRefreshTokenExpiraEm(expira);
        integracaoExternaRepository.save(conexao);
        entityManager.flush();
        entityManager.clear();

        IntegracaoExterna lida = integracaoExternaRepository.findById(conexao.getId()).orElseThrow();

        assertThat(lida.getRefreshTokenExpiraEm()).isEqualTo(expira);
    }

    private IntegracaoExterna seedIntegracaoGarmin() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Garmin V104");
        assessoria.setDominio("garmin-v104-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Garmin V104");
        atleta.setEmail("garmin-v104-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);

        IntegracaoExterna integracao = new IntegracaoExterna();
        integracao.setAtleta(atleta);
        integracao.setTenantId(assessoria.getId());
        integracao.setPlataforma(FonteDados.GARMIN);
        integracao.setAccessToken("token-fake");
        return integracaoExternaRepository.save(integracao);
    }
}
