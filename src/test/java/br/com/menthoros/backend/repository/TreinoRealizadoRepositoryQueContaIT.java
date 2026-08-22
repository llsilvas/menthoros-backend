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

/**
 * Prova, contra o schema real, do predicado "treino que conta" (D8): {@code CANCELADO} nunca
 * conta, mas {@code statusSincronizacao} nulo — o estado normal de FIT/manual, que nunca setam o
 * campo — precisa continuar contando. {@code <> CANCELADO} sozinho em SQL/JPQL excluiria NULL
 * (achado do DoR, Codex #1).
 */
@Transactional
class TreinoRealizadoRepositoryQueContaIT extends AbstractIntegrationTest {

    @Autowired
    private TreinoRealizadoRepository treinoRealizadoRepository;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AtletaRepository atletaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("findQueContamByAtletaIdAndDataTreino conta o NULL e exclui o CANCELADO")
    void nullContaCanceladoNao() {
        Atleta atleta = seedAtleta();
        LocalDate data = LocalDate.now().minusDays(1);

        TreinoRealizado semStatus = novoRealizado(atleta, data);
        // status_sincronizacao permanece NULL — caminho FIT/manual hoje

        TreinoRealizado cancelado = novoRealizado(atleta, data);
        cancelado.setStatusSincronizacao(StatusSincronizacao.CANCELADO);

        treinoRealizadoRepository.save(semStatus);
        treinoRealizadoRepository.save(cancelado);
        entityManager.flush();
        entityManager.clear();

        List<TreinoRealizado> resultado = treinoRealizadoRepository
                .findQueContamByAtletaIdAndDataTreino(atleta.getId(), data);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getStatusSincronizacao()).isNull();
    }

    @Test
    @DisplayName("findQueContamByAtletaIdAndDataTreino conta o NAO_SINCRONIZADO")
    void naoSincronizadoConta() {
        Atleta atleta = seedAtleta();
        LocalDate data = LocalDate.now().minusDays(1);

        TreinoRealizado naoSincronizado = novoRealizado(atleta, data);
        naoSincronizado.setStatusSincronizacao(StatusSincronizacao.NAO_SINCRONIZADO);
        treinoRealizadoRepository.save(naoSincronizado);
        entityManager.flush();
        entityManager.clear();

        List<TreinoRealizado> resultado = treinoRealizadoRepository
                .findQueContamByAtletaIdAndDataTreino(atleta.getId(), data);

        assertThat(resultado).hasSize(1);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria QueConta Test");
        assessoria.setDominio("que-conta-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta QueConta");
        atleta.setEmail("que-conta-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 21km");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    private TreinoRealizado novoRealizado(Atleta atleta, LocalDate data) {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setAtleta(atleta);
        tr.setTenantId(atleta.getAssessoria().getId());
        tr.setDataTreino(data);
        tr.setDiaSemana(DiaSemana.SABADO);
        tr.setTipoTreino(TipoTreino.TEMPO_RUN);
        tr.setDuracaoMin(Duration.ofMinutes(40));
        tr.setDistanciaKm(BigDecimal.valueOf(8));
        tr.setFonteDados(FonteDados.MANUAL);
        tr.setStatus(TreinoExecucaoStatus.REALIZADO);
        return tr;
    }
}
