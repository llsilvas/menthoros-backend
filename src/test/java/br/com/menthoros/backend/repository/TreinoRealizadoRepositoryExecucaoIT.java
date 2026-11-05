package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TempoEmZonas;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Colunas da V111 (coach-ver-treino-realizado, D1): ida e volta contra o schema real. */
@Transactional
class TreinoRealizadoRepositoryExecucaoIT extends AbstractIntegrationTest {

    @Autowired
    private TreinoRealizadoRepository treinoRealizadoRepository;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AtletaRepository atletaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("persiste velocidade máxima, temperaturas e tempo em zonas")
    void persisteCamposDeExecucao() {
        TreinoRealizado treino = novoRealizado(seedAtleta());
        treino.setVelocidadeMaxKmh(new BigDecimal("12.19"));
        treino.setTemperaturaMinC(new BigDecimal("19.0"));
        treino.setTemperaturaMaxC(new BigDecimal("27.0"));
        treino.setTempoEmZonas(new TempoEmZonas(TempoEmZonas.FONTE_FIT_RECORDS, List.of(
                new TempoEmZonas.Zona("Z1", 93, 111, 4),
                new TempoEmZonas.Zona("Z5", 167, null, 15))));
        UUID id = treinoRealizadoRepository.save(treino).getId();
        entityManager.flush();
        entityManager.clear();

        TreinoRealizado lido = treinoRealizadoRepository.findById(id).orElseThrow();

        assertThat(lido.getVelocidadeMaxKmh()).isEqualByComparingTo("12.19");
        assertThat(lido.getTemperaturaMinC()).isEqualByComparingTo("19.0");
        assertThat(lido.getTemperaturaMaxC()).isEqualByComparingTo("27.0");
        assertThat(lido.getTempoEmZonas().fonte()).isEqualTo("FIT_RECORDS");
        assertThat(lido.getTempoEmZonas().zonas())
                .containsExactly(new TempoEmZonas.Zona("Z1", 93, 111, 4), new TempoEmZonas.Zona("Z5", 167, null, 15));
    }

    @Test
    @DisplayName("campos novos ausentes ficam nulos")
    void camposAusentesFicamNulos() {
        UUID id = treinoRealizadoRepository.save(novoRealizado(seedAtleta())).getId();
        entityManager.flush();
        entityManager.clear();

        TreinoRealizado lido = treinoRealizadoRepository.findById(id).orElseThrow();

        assertThat(lido.getVelocidadeMaxKmh()).isNull();
        assertThat(lido.getTemperaturaMinC()).isNull();
        assertThat(lido.getTemperaturaMaxC()).isNull();
        assertThat(lido.getTempoEmZonas()).isNull();
    }

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Execucao Test");
        assessoria.setDominio("execucao-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Execucao");
        atleta.setEmail("execucao-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 21km");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    private TreinoRealizado novoRealizado(Atleta atleta) {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setAtleta(atleta);
        tr.setTenantId(atleta.getAssessoria().getId());
        tr.setDataTreino(LocalDate.now().minusDays(1));
        tr.setDiaSemana(DiaSemana.SABADO);
        tr.setTipoTreino(TipoTreino.LONGO);
        tr.setDuracaoMin(Duration.ofMinutes(100));
        tr.setDistanciaKm(BigDecimal.valueOf(15));
        tr.setFonteDados(FonteDados.GARMIN);
        tr.setStatus(TreinoExecucaoStatus.REALIZADO);
        return tr;
    }
}
