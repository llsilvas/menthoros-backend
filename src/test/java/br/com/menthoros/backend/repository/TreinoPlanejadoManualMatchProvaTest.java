package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.TipoProva;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D6 / 4.3 (prova-no-plano-semanal): {@code findFirstForManualMatch} prefere o treino PROVA
 * vinculado a uma prova de verdade sobre um PROVA simulado do coach no mesmo dia, mesmo quando o
 * simulado foi criado antes.
 */
@Transactional
class TreinoPlanejadoManualMatchProvaTest extends AbstractIntegrationTest {

    @Autowired private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Autowired private ProvaRepository provaRepository;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired private PlanoSemanalRepository planoSemanalRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("PROVA vinculado ganha do PROVA simulado do coach, mesmo criado depois")
    void preferoVinculadoAoSimulado() {
        Atleta atleta = seedAtleta();
        LocalDate data = LocalDate.now().plusDays(3);
        PlanoSemanal plano = seedPlano(atleta, data);
        Prova prova = seedProva(atleta, data);

        TreinoPlanejado simulado = treino(plano, atleta, data, null);
        simulado.setCriadoEm(LocalDateTime.now().minusHours(2)); // criado antes
        treinoPlanejadoRepository.save(simulado);

        TreinoPlanejado vinculado = treino(plano, atleta, data, prova);
        vinculado.setCriadoEm(LocalDateTime.now()); // criado depois
        treinoPlanejadoRepository.save(vinculado);

        entityManager.flush();
        entityManager.clear();

        var resultado = treinoPlanejadoRepository.findFirstForManualMatch(
                atleta.getId(), atleta.getAssessoria().getId(), data, TipoTreino.PROVA,
                List.of(TreinoExecucaoStatus.PENDENTE, TreinoExecucaoStatus.PERDIDO));

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getId()).isEqualTo(vinculado.getId());
        assertThat(resultado.get().getProva()).isNotNull();
    }

    @Test
    @DisplayName("sem PROVA vinculado, devolve o mais antigo (comportamento anterior preservado)")
    void semVinculadoDevolveMaisAntigo() {
        Atleta atleta = seedAtleta();
        LocalDate data = LocalDate.now().plusDays(3);
        PlanoSemanal plano = seedPlano(atleta, data);

        TreinoPlanejado maisAntigo = treino(plano, atleta, data, null);
        maisAntigo.setCriadoEm(LocalDateTime.now().minusHours(2));
        treinoPlanejadoRepository.save(maisAntigo);

        TreinoPlanejado maisNovo = treino(plano, atleta, data, null);
        maisNovo.setCriadoEm(LocalDateTime.now());
        treinoPlanejadoRepository.save(maisNovo);

        entityManager.flush();
        entityManager.clear();

        var resultado = treinoPlanejadoRepository.findFirstForManualMatch(
                atleta.getId(), atleta.getAssessoria().getId(), data, TipoTreino.PROVA,
                List.of(TreinoExecucaoStatus.PENDENTE, TreinoExecucaoStatus.PERDIDO));

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getId()).isEqualTo(maisAntigo.getId());
    }

    // ---- helpers ----

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Manual Match Prova");
        assessoria.setDominio("manual-match-prova-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Manual Match Prova");
        atleta.setEmail("manual-match-prova-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    private PlanoSemanal seedPlano(Atleta atleta, LocalDate diaNaSemana) {
        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(atleta.getAssessoria());
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        meta = planoMetadadosRepository.save(meta);

        PlanoSemanal plano = new PlanoSemanal();
        plano.setAtleta(atleta);
        plano.setAssessoria(atleta.getAssessoria());
        plano.setPlanoMetaDados(meta);
        plano.setSemanaInicio(diaNaSemana.minusDays(3));
        plano.setSemanaFim(diaNaSemana.plusDays(3));
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(40));
        plano.setStatus(PlanoStatus.EM_ANDAMENTO);
        plano.setReviewStatus(PlanoReviewStatus.APROVADO);
        plano.setObjetivoSemanal("Semana de teste do match manual");
        return planoSemanalRepository.save(plano);
    }

    private Prova seedProva(Atleta atleta, LocalDate dataProva) {
        Prova prova = Prova.builder()
                .nomeProva("Prova vinculada")
                .dataProva(dataProva)
                .distancia(DistanciaProva.KM_10)
                .distanciaKm(BigDecimal.valueOf(10.0))
                .tipoProva(TipoProva.CORRIDA_RUA)
                .statusProva(ProvaStatus.PLANEJADA)
                .build();
        prova.setAtleta(atleta);
        prova.setAssessoria(atleta.getAssessoria());
        return provaRepository.save(prova);
    }

    private TreinoPlanejado treino(PlanoSemanal plano, Atleta atleta, LocalDate data, Prova prova) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setPlanoSemanal(plano);
        treino.setAtleta(atleta);
        treino.setTenantId(atleta.getAssessoria().getId());
        treino.setDataTreino(data);
        treino.setDiaSemana(DiaSemana.SEGUNDA);
        treino.setTipoTreino(TipoTreino.PROVA);
        treino.setStatusTreino(TreinoExecucaoStatus.PENDENTE);
        treino.setDuracaoMin(Duration.ofMinutes(50));
        treino.setDistanciaKm(BigDecimal.valueOf(10.0));
        treino.setProva(prova);
        return treino;
    }
}
