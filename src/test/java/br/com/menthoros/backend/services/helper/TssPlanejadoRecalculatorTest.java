package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoTssBackupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recálculo e reversão do {@code tssPlanejado} contra o schema real (V74).
 *
 * <p><b>Nomeado {@code *Test}, não {@code *IT}, de propósito:</b> o Surefire só executa {@code *Test},
 * e {@code verify} não roda automaticamente em lugar nenhum neste repo — um {@code *IT} aqui nunca
 * seria exercitado. Mesma escolha de {@code UsuarioLgpdConsentRepositoryTest} e
 * {@code AssinaturaRepositoryTest}, que também sobem Testcontainers.
 *
 * <p>O que estes testes protegem não é o cálculo — isso o
 * {@code TssCalculatorServiceConvergenciaTest} já cobre. É a **reversibilidade**: a operação altera
 * dado que coach e atleta já viram na tela, então precisa ser desfeita a partir do valor guardado,
 * não recomputada.
 */
@Transactional
class TssPlanejadoRecalculatorTest extends AbstractIntegrationTest {

    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired private PlanoSemanalRepository planoSemanalRepository;
    @Autowired private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Autowired private TreinoPlanejadoTssBackupRepository backupRepository;
    @Autowired private TssPlanejadoRecalculator recalculator;

    private Atleta atleta;
    private PlanoSemanal plano;

    @BeforeEach
    void setUp() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Recalc");
        assessoria.setDominio("recalc-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta a = new Atleta();
        a.setNome("Atleta Recalc");
        a.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        a.setAtivo(AtletaStatus.ATIVO);
        a.setAssessoria(assessoria);
        a.setEmail("recalc-" + UUID.randomUUID() + "@test.com");
        atleta = atletaRepository.save(a);

        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(assessoria);
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        meta = planoMetadadosRepository.save(meta);

        PlanoSemanal ps = new PlanoSemanal();
        ps.setAtleta(atleta);
        ps.setAssessoria(assessoria);
        ps.setPlanoMetaDados(meta);
        ps.setSemanaInicio(LocalDate.now());
        ps.setSemanaFim(LocalDate.now().plusDays(6));
        ps.setVolumePlanejadoKm(BigDecimal.valueOf(40));
        ps.setStatus(PlanoStatus.PLANEJADO);
        ps.setReviewStatus(PlanoReviewStatus.AGUARDANDO_REVISAO);
        ps.setObjetivoSemanal("Semana base");
        plano = planoSemanalRepository.save(ps);
    }

    @Nested
    @DisplayName("recalcularTudo")
    class RecalcularTudo {

        @Test
        @DisplayName("grava o valor anterior e substitui pelo da fórmula unificada")
        void gravaSnapshotERecalcula() {
            // 60min RPE 7 na escala antiga era 33; na nova é 81.
            TreinoPlanejado treino = seedTreino(Duration.ofMinutes(60), 7, 33);

            var resultado = recalculator.recalcularTudo();

            assertThat(resultado.recalculados()).isEqualTo(1);
            assertThat(treinoPlanejadoRepository.findById(treino.getId()).orElseThrow()
                    .getTssPlanejado()).isEqualTo(81);
            assertThat(backupRepository.findByTreinoPlanejadoId(treino.getId()).orElseThrow()
                    .getTssPlanejadoAntes()).isEqualTo(33);
        }

        @Test
        @DisplayName("treino sem duração não é tocado — estimar seria pior que deixar antigo")
        void semDuracaoNaoEhTocado() {
            TreinoPlanejado treino = seedTreino(null, 7, 33);

            var resultado = recalculator.recalcularTudo();

            assertThat(resultado.semInputs()).isEqualTo(1);
            assertThat(resultado.recalculados()).isZero();
            assertThat(treinoPlanejadoRepository.findById(treino.getId()).orElseThrow()
                    .getTssPlanejado()).isEqualTo(33);
            assertThat(backupRepository.findByTreinoPlanejadoId(treino.getId())).isEmpty();
        }

        @Test
        @DisplayName("reexecutar NÃO sobrescreve o snapshot com o valor já corrigido")
        void reexecucaoPreservaOSnapshotOriginal() {
            TreinoPlanejado treino = seedTreino(Duration.ofMinutes(60), 7, 33);

            recalculator.recalcularTudo();
            var segunda = recalculator.recalcularTudo();

            // É este o cenário que destruiria a reversibilidade: se o snapshot fosse regravado na
            // segunda passagem, guardaria 81 e o valor original de 33 se perderia para sempre.
            assertThat(segunda.jaComSnapshot()).isEqualTo(1);
            assertThat(segunda.recalculados()).isZero();
            assertThat(backupRepository.findByTreinoPlanejadoId(treino.getId()).orElseThrow()
                    .getTssPlanejadoAntes()).isEqualTo(33);
        }
    }

    @Nested
    @DisplayName("reverter")
    class Reverter {

        @Test
        @DisplayName("restaura o valor original a partir do snapshot")
        void restauraDoSnapshot() {
            TreinoPlanejado treino = seedTreino(Duration.ofMinutes(60), 7, 33);
            recalculator.recalcularTudo();

            int restaurados = recalculator.reverter();

            assertThat(restaurados).isEqualTo(1);
            assertThat(treinoPlanejadoRepository.findById(treino.getId()).orElseThrow()
                    .getTssPlanejado()).isEqualTo(33);
        }

        @Test
        @DisplayName("reverter duas vezes deixa o mesmo estado")
        void reverterEhIdempotente() {
            TreinoPlanejado treino = seedTreino(Duration.ofMinutes(60), 7, 33);
            recalculator.recalcularTudo();

            recalculator.reverter();
            recalculator.reverter();

            assertThat(treinoPlanejadoRepository.findById(treino.getId()).orElseThrow()
                    .getTssPlanejado()).isEqualTo(33);
        }
    }

    private TreinoPlanejado seedTreino(Duration duracao, Integer rpe, Integer tssAntigo) {
        TreinoPlanejado t = new TreinoPlanejado();
        t.setAtleta(atleta);
        t.setPlanoSemanal(plano);
        t.setTipoTreino(TipoTreino.CONTINUO);
        t.setDiaSemana(DiaSemana.SEGUNDA);
        t.setDataTreino(LocalDate.now());
        t.setDuracaoMin(duracao);
        t.setPercepcaoEsforcoEsperada(rpe);
        t.setTssPlanejado(tssAntigo);
        return treinoPlanejadoRepository.save(t);
    }
}
