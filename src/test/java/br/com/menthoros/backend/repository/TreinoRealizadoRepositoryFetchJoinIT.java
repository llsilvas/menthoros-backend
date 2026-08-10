package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
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
 * Prova, contra o schema real, que as duas queries que alimentam progressão, prompts e projeção
 * trazem o {@code treinoPlanejado} em fetch join.
 *
 * <p>Por que isso vira teste: o relacionamento é {@code @OneToOne(fetch = LAZY)} e os consumidores
 * resolvem o tipo por {@code getTipoTreinoEfetivo()}, que toca o planejado de todo treino da lista.
 * Se alguém trocar o JPQL pelo método derivado (a versão anterior destas duas queries), nada quebra
 * funcionalmente — vira um SELECT por treino, e em janela de 42 dias isso é dezenas de round-trips
 * silenciosos por chamada.</p>
 */
@Transactional
class TreinoRealizadoRepositoryFetchJoinIT extends AbstractIntegrationTest {

    @Autowired
    private TreinoRealizadoRepository treinoRealizadoRepository;
    @Autowired
    private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AtletaRepository atletaRepository;
    @Autowired
    private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired
    private PlanoSemanalRepository planoSemanalRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("findByAtletaAndDataTreinoGreaterThanEqual traz o planejado inicializado")
    void buscaPorAtletaTrazPlanejadoInicializado() {
        Atleta atleta = seedAtleta();
        seedRealizadoVinculado(atleta);
        entityManager.flush();
        entityManager.clear();

        List<TreinoRealizado> resultado = treinoRealizadoRepository
                .findByAtletaAndDataTreinoGreaterThanEqualOrderByDataTreinoDesc(atleta, LocalDate.now().minusDays(42));

        assertThat(resultado).hasSize(1);
        assertThat(Hibernate.isInitialized(resultado.get(0).getTreinoPlanejado()))
                .as("treinoPlanejado precisa vir no fetch join — sem ele é um SELECT por treino")
                .isTrue();
        assertThat(resultado.get(0).getTipoTreinoEfetivo()).isEqualTo(TipoTreino.LONGO);
    }

    @Test
    @DisplayName("findByAtletaIdAndTenantIdAndDataTreinoBetween traz o planejado inicializado")
    void buscaPorPeriodoTrazPlanejadoInicializado() {
        Atleta atleta = seedAtleta();
        seedRealizadoVinculado(atleta);
        entityManager.flush();
        entityManager.clear();

        List<TreinoRealizado> resultado = treinoRealizadoRepository
                .findByAtletaIdAndTenantIdAndDataTreinoBetween(
                        atleta.getId(), atleta.getAssessoria().getId(),
                        LocalDate.now().minusDays(42), LocalDate.now());

        assertThat(resultado).hasSize(1);
        assertThat(Hibernate.isInitialized(resultado.get(0).getTreinoPlanejado()))
                .as("treinoPlanejado precisa vir no fetch join — sem ele é um SELECT por treino")
                .isTrue();
        assertThat(resultado.get(0).getTipoTreinoEfetivo()).isEqualTo(TipoTreino.LONGO);
    }

    @Test
    @DisplayName("LEFT JOIN não descarta o treino avulso, sem planejado vinculado")
    void treinoSemPlanejadoContinuaNoResultado() {
        Atleta atleta = seedAtleta();
        TreinoRealizado avulso = novoRealizado(atleta, TipoTreino.TEMPO_RUN);
        treinoRealizadoRepository.save(avulso);
        entityManager.flush();
        entityManager.clear();

        List<TreinoRealizado> resultado = treinoRealizadoRepository
                .findByAtletaAndDataTreinoGreaterThanEqualOrderByDataTreinoDesc(atleta, LocalDate.now().minusDays(42));

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getTreinoPlanejado()).isNull();
        assertThat(resultado.get(0).getTipoTreinoEfetivo()).isEqualTo(TipoTreino.TEMPO_RUN);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria FetchJoin Test");
        assessoria.setDominio("fetch-join-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta FetchJoin");
        atleta.setEmail("fetch-join-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 21km");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    /** Longão prescrito que o sync classificou como TEMPO_RUN — o cenário do bug de progressão. */
    private void seedRealizadoVinculado(Atleta atleta) {
        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(atleta.getAssessoria());
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        meta = planoMetadadosRepository.save(meta);

        PlanoSemanal plano = new PlanoSemanal();
        plano.setAtleta(atleta);
        plano.setAssessoria(atleta.getAssessoria());
        plano.setPlanoMetaDados(meta);
        plano.setSemanaInicio(LocalDate.now().minusDays(6));
        plano.setSemanaFim(LocalDate.now());
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(40));
        plano.setStatus(PlanoStatus.EM_ANDAMENTO);
        plano.setReviewStatus(PlanoReviewStatus.APROVADO);
        plano.setObjetivoSemanal("Semana de teste");
        plano = planoSemanalRepository.save(plano);

        TreinoPlanejado planejado = new TreinoPlanejado();
        planejado.setPlanoSemanal(plano);
        planejado.setAtleta(atleta);
        planejado.setTenantId(atleta.getAssessoria().getId());
        planejado.setDataTreino(LocalDate.now().minusDays(2));
        planejado.setDiaSemana(DiaSemana.SABADO);
        planejado.setTipoTreino(TipoTreino.LONGO);
        planejado.setDuracaoMin(Duration.ofMinutes(90));
        planejado.setStatusSincronizacao(StatusSincronizacao.NAO_SINCRONIZADO);
        planejado.setTentativasSincronizacao(0);
        planejado = treinoPlanejadoRepository.save(planejado);

        TreinoRealizado realizado = novoRealizado(atleta, TipoTreino.TEMPO_RUN);
        realizado.setTreinoPlanejado(planejado);
        treinoRealizadoRepository.save(realizado);
    }

    private TreinoRealizado novoRealizado(Atleta atleta, TipoTreino tipoInferido) {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setAtleta(atleta);
        tr.setTenantId(atleta.getAssessoria().getId());
        tr.setDataTreino(LocalDate.now().minusDays(2));
        tr.setDiaSemana(DiaSemana.SABADO);
        tr.setTipoTreino(tipoInferido);
        tr.setDuracaoMin(Duration.ofMinutes(88));
        tr.setDistanciaKm(BigDecimal.valueOf(18));
        tr.setFonteDados(FonteDados.STRAVA);
        tr.setStatus(TreinoExecucaoStatus.REALIZADO);
        tr.setReconciliationStatus(ReconciliationStatus.PENDENTE);
        tr.setStatusSincronizacao(StatusSincronizacao.NAO_SINCRONIZADO);
        tr.setFcMedia(158);
        tr.setFcMax(172);
        tr.setPaceMedia(Duration.ofMinutes(5).plusSeconds(40));
        return tr;
    }
}
