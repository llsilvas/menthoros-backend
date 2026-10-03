package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.repository.projection.DistanciaDiaProjection;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, contra o schema real, a soma de km por dia usada no perfil do coach: agrega no banco (sem
 * materializar treinos nem a coleção EAGER de sensações), exclui CANCELADO mas conta status NULL,
 * ignora distância nula e respeita o tenant.
 */
@Transactional
class TreinoRealizadoRepositoryDistanciaPorDiaIT extends AbstractIntegrationTest {

    @Autowired
    private TreinoRealizadoRepository treinoRealizadoRepository;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AtletaRepository atletaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("soma por dia, conta NULL, exclui CANCELADO e distância nula, fica no tenant")
    void somaPorDia() {
        Atleta atleta = seedAtleta();
        Atleta outroTenant = seedAtleta();
        LocalDate ontem = LocalDate.now().minusDays(1);
        LocalDate hoje = LocalDate.now();

        salvar(atleta, ontem, "5.5", null);
        salvar(atleta, ontem, "3", StatusSincronizacao.NAO_SINCRONIZADO);
        salvar(atleta, ontem, "12", StatusSincronizacao.CANCELADO);
        salvar(atleta, hoje, null, null);
        salvar(atleta, hoje, "4.25", null);
        salvar(outroTenant, hoje, "99", null);
        entityManager.flush();
        entityManager.clear();

        List<DistanciaDiaProjection> dias = treinoRealizadoRepository.somarDistanciaPorDia(
                atleta.getId(), atleta.getAssessoria().getId(), ontem, hoje);

        Map<LocalDate, BigDecimal> porDia = dias.stream()
                .collect(Collectors.toMap(DistanciaDiaProjection::getDataTreino, DistanciaDiaProjection::getDistanciaKm));
        assertThat(porDia).hasSize(2);
        assertThat(porDia.get(ontem)).isEqualByComparingTo("8.5");
        assertThat(porDia.get(hoje)).isEqualByComparingTo("4.25");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Distancia Test");
        assessoria.setDominio("distancia-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Distancia");
        atleta.setEmail("distancia-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 21km");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    private void salvar(Atleta atleta, LocalDate data, String km, StatusSincronizacao status) {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setAtleta(atleta);
        tr.setTenantId(atleta.getAssessoria().getId());
        tr.setDataTreino(data);
        tr.setDiaSemana(DiaSemana.SABADO);
        tr.setTipoTreino(TipoTreino.TEMPO_RUN);
        tr.setDuracaoMin(Duration.ofMinutes(40));
        tr.setDistanciaKm(km != null ? new BigDecimal(km) : null);
        tr.setFonteDados(FonteDados.MANUAL);
        tr.setStatus(TreinoExecucaoStatus.REALIZADO);
        tr.setStatusSincronizacao(status);
        treinoRealizadoRepository.save(tr);
    }
}
