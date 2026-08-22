package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.services.IngestaoTreinoRealizadoService;
import br.com.menthoros.backend.events.TreinoRegistradoEvent;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.helper.TreinoDedupHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT de {@code registrar} — o módulo único de ingestão de treino realizado (D1-D13, design.md).
 */
@RecordApplicationEvents
class IngestaoTreinoRealizadoServiceRegistrarIT extends AbstractIntegrationTest {

    @Autowired
    private IngestaoTreinoRealizadoService ingestaoTreinoRealizadoService;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AtletaRepository atletaRepository;
    @Autowired
    private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired
    private TreinoRealizadoRepository treinoRealizadoRepository;
    @Autowired
    private MetricasDiariasRepository metricasDiariasRepository;
    @Autowired
    private ApplicationEvents applicationEvents;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("registrar — toda fonte grava TSS e carga do dia [CA1, CA3, CA10]")
    class TodaFonteGravaTss {

        @ParameterizedTest
        @EnumSource(value = FonteDados.class, names = {"MANUAL", "STRAVA", "INTERVALS_ICU"})
        @DisplayName("tssCalculado não-nulo e carga do dia reflete o treino, sem TenantContext")
        void gravaTssECarga(FonteDados fonte) {
            Atleta atleta = seedAtleta("Fonte" + fonte);
            LocalDate data = LocalDate.now().minusDays(1);
            TreinoRealizado treino = novoRealizado(atleta, data, fonte);

            TreinoDedupHelper.SaveResult resultado = ingestaoTreinoRealizadoService
                    .registrar(treino, fonte == FonteDados.MANUAL ? null : "ext-" + UUID.randomUUID());

            assertThat(resultado.treino().getTssCalculado()).isNotNull();
            MetricasDiarias metricas = metricasDiariasRepository
                    .findByAtletaIdAndData(atleta.getId(), data)
                    .orElseThrow();
            assertThat(metricas.getCtl()).isNotNull();
            assertThat(applicationEvents.stream(TreinoRegistradoEvent.class)
                    .filter(e -> e.treinoRealizadoId().equals(resultado.treino().getId())))
                    .hasSize(1);
        }

        @Test
        @DisplayName("FIT com TSS de dispositivo preserva o valor — não recalcula [D3.1]")
        void preservaTssDeDispositivo() {
            Atleta atleta = seedAtleta("FitDispositivo");
            LocalDate data = LocalDate.now().minusDays(1);
            TreinoRealizado treino = novoRealizado(atleta, data, FonteDados.MANUAL);
            treino.setTssCalculado(77);
            treino.setMetodoCalculoTss("DISPOSITIVO");

            TreinoDedupHelper.SaveResult resultado = ingestaoTreinoRealizadoService.registrar(treino, null);

            assertThat(resultado.treino().getTssCalculado()).isEqualTo(77);
            assertThat(resultado.treino().getMetodoCalculoTss()).isEqualTo("DISPOSITIVO");
        }

        @Test
        @DisplayName("FIT sem TSS de dispositivo calcula via TssCalculatorService [D3.1]")
        void calculaQuandoNaoTemDispositivo() {
            Atleta atleta = seedAtleta("FitSemDispositivo");
            LocalDate data = LocalDate.now().minusDays(1);
            TreinoRealizado treino = novoRealizado(atleta, data, FonteDados.MANUAL);

            TreinoDedupHelper.SaveResult resultado = ingestaoTreinoRealizadoService.registrar(treino, null);

            assertThat(resultado.treino().getTssCalculado()).isNotNull().isNotEqualTo(0);
        }
    }

    @Nested
    @DisplayName("registrar — deduplicação [CA2, CA2b, CA4]")
    class Deduplicacao {

        @Test
        @DisplayName("entidade nova com externalId existente não cria segunda linha, não publica evento na segunda chamada")
        void duplicataNaoCriaLinha() {
            Atleta atleta = seedAtleta("Duplicata");
            LocalDate data = LocalDate.now().minusDays(1);
            String externalId = "ext-" + UUID.randomUUID();

            TreinoRealizado primeiro = novoRealizado(atleta, data, FonteDados.STRAVA);
            TreinoDedupHelper.SaveResult resultado1 = ingestaoTreinoRealizadoService.registrar(primeiro, externalId);
            assertThat(resultado1.inserted()).isTrue();

            TreinoRealizado segundo = novoRealizado(atleta, data, FonteDados.STRAVA);
            TreinoDedupHelper.SaveResult resultado2 = ingestaoTreinoRealizadoService.registrar(segundo, externalId);

            assertThat(resultado2.inserted()).isFalse();
            assertThat(resultado2.treino().getId()).isEqualTo(resultado1.treino().getId());
            assertThat(treinoRealizadoRepository.findByAtletaIdAndDataTreino(atleta.getId(), data)).hasSize(1);
            assertThat(applicationEvents.stream(TreinoRegistradoEvent.class)
                    .filter(e -> e.treinoRealizadoId().equals(resultado1.treino().getId())))
                    .hasSize(1);
        }

        @Test
        @DisplayName("entidade gerenciada (find-or-new + merge) é UPDATE, não publica evento — re-sync do Strava")
        void entidadeGerenciadaNaoPublicaEvento() {
            Atleta atleta = seedAtleta("Gerenciada");
            LocalDate data = LocalDate.now().minusDays(1);
            String externalId = "ext-" + UUID.randomUUID();

            TreinoRealizado original = novoRealizado(atleta, data, FonteDados.STRAVA);
            TreinoDedupHelper.SaveResult resultadoOriginal = ingestaoTreinoRealizadoService.registrar(original, externalId);
            UUID treinoId = resultadoOriginal.treino().getId();

            // find-or-new + registrar precisam estar na MESMA transação do chamador (D6) — como
            // StravaActivityServiceImpl.syncSingleActivityById faz na prática: um único método
            // @Transactional que recarrega, mescla e chama o ingestor. Sem isso, a entidade
            // recarregada fica detached e TssCalculatorService.calcularTss (que toca
            // etapasRealizadas, LAZY) lança LazyInitializationException.
            TreinoDedupHelper.SaveResult resultado = transactionTemplate.execute(status -> {
                TreinoRealizado gerenciado = treinoRealizadoRepository.findById(treinoId).orElseThrow();
                gerenciado.setFcMedia(160);
                return ingestaoTreinoRealizadoService.registrar(gerenciado, externalId);
            });

            assertThat(resultado.treino().getFcMedia()).isEqualTo(160);
            assertThat(applicationEvents.stream(TreinoRegistradoEvent.class)
                    .filter(e -> e.treinoRealizadoId().equals(treinoId)))
                    .as("re-sync (UPDATE) não deve publicar um segundo evento")
                    .hasSize(1);
            assertThat(treinoRealizadoRepository.findByAtletaIdAndDataTreino(atleta.getId(), data)).hasSize(1);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private Atleta seedAtleta(String prefixo) {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria " + prefixo);
        assessoria.setDominio(prefixo.toLowerCase() + "-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta " + prefixo);
        atleta.setEmail(prefixo.toLowerCase() + "-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Provar o ingestor");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);

        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(assessoria);
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        planoMetadadosRepository.save(meta);

        return atleta;
    }

    private TreinoRealizado novoRealizado(Atleta atleta, LocalDate data, FonteDados fonte) {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setAtleta(atleta);
        tr.setDataTreino(data);
        tr.setDiaSemana(DiaSemana.SABADO);
        tr.setTipoTreino(TipoTreino.FACIL);
        tr.setDuracaoMin(Duration.ofMinutes(40));
        tr.setDistanciaKm(BigDecimal.valueOf(8));
        tr.setFonteDados(fonte);
        tr.setPercepcaoEsforco(7);
        return tr;
    }
}
