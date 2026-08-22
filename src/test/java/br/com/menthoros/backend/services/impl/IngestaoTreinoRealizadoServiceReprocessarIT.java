package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.IngestaoTreinoRealizadoService;
import br.com.menthoros.backend.services.helper.TreinoDedupHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT de {@code reprocessar} — o gesto "este treino mudou ou saiu" (design.md D2, D8, D13).
 */
class IngestaoTreinoRealizadoServiceReprocessarIT extends AbstractIntegrationTest {

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
    private TransactionTemplate transactionTemplate;
    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("reprocessar")
    class Reprocessar {

        @Test
        @DisplayName("laps adicionadas mudam tssCalculado e a carga do dia [CA5]")
        void lapsMudamTssECarga() {
            Atleta atleta = seedAtleta("Laps");
            LocalDate data = LocalDate.now().minusDays(1);
            TreinoRealizado treino = novoRealizado(atleta, data);
            TreinoDedupHelper.SaveResult resultado = ingestaoTreinoRealizadoService.registrar(treino, null);
            Integer tssAntes = resultado.treino().getTssCalculado();

            UUID treinoId = resultado.treino().getId();
            // Persiste a etapa pelo lado dono da FK, sem tocar a coleção LAZY de
            // TreinoRealizado.etapasRealizadas fora de uma sessão ativa — é o que um chamador real
            // (ex.: IntervalsIcuLapsBackfillPersister) faz, dentro do seu próprio @Transactional.
            transactionTemplate.executeWithoutResult(status -> {
                TreinoRealizado recarregado = treinoRealizadoRepository.findById(treinoId).orElseThrow();
                EtapaRealizada etapa = new EtapaRealizada();
                etapa.setTreinoRealizado(recarregado);
                etapa.setOrdem(1);
                etapa.setDistanciaKm(new BigDecimal("8.00"));
                etapa.setDuracao(Duration.ofMinutes(40));
                etapa.setFcMedia(175);
                entityManager.persist(etapa);
            });

            ingestaoTreinoRealizadoService.reprocessar(treinoId, null);

            TreinoRealizado apos = treinoRealizadoRepository.findById(treinoId).orElseThrow();
            assertThat(apos.getTssCalculado()).isNotEqualTo(tssAntes);
            MetricasDiarias metricas = metricasDiariasRepository.findByAtletaIdAndData(atleta.getId(), data).orElseThrow();
            assertThat(metricas.getCtl()).isNotNull();
        }

        @Test
        @DisplayName("data mudou — recalcula do dia antigo até hoje [CA6]")
        void dataMudouRecalculaDoisDias() {
            Atleta atleta = seedAtleta("DataMudou");
            LocalDate dataAntiga = LocalDate.now().minusDays(2);
            LocalDate dataNova = LocalDate.now().minusDays(1);
            TreinoRealizado treino = novoRealizado(atleta, dataAntiga);
            TreinoDedupHelper.SaveResult resultado = ingestaoTreinoRealizadoService.registrar(treino, null);

            TreinoRealizado recarregado = treinoRealizadoRepository.findById(resultado.treino().getId()).orElseThrow();
            recarregado.setDataTreino(dataNova);
            treinoRealizadoRepository.save(recarregado);

            ingestaoTreinoRealizadoService.reprocessar(recarregado.getId(), dataAntiga);

            assertThat(metricasDiariasRepository.findByAtletaIdAndData(atleta.getId(), dataAntiga))
                    .as("dia antigo precisa existir recalculado sem o treino")
                    .isPresent();
            assertThat(metricasDiariasRepository.findByAtletaIdAndData(atleta.getId(), dataNova))
                    .as("dia novo precisa refletir o treino")
                    .isPresent();
        }

        @Test
        @DisplayName("cancelado não conta na carga, mas tssCalculado permanece para auditoria [CA7]")
        void canceladoNaoContaTssPermanece() {
            Atleta atleta = seedAtleta("Cancelado");
            LocalDate data = LocalDate.now().minusDays(1);
            TreinoRealizado treino = novoRealizado(atleta, data);
            TreinoDedupHelper.SaveResult resultado = ingestaoTreinoRealizadoService.registrar(treino, null);
            Integer tssAntes = resultado.treino().getTssCalculado();
            assertThat(tssAntes).isNotNull();

            TreinoRealizado recarregado = treinoRealizadoRepository.findById(resultado.treino().getId()).orElseThrow();
            recarregado.setStatusSincronizacao(StatusSincronizacao.CANCELADO);
            treinoRealizadoRepository.save(recarregado);

            ingestaoTreinoRealizadoService.reprocessar(recarregado.getId(), null);

            TreinoRealizado apos = treinoRealizadoRepository.findById(recarregado.getId()).orElseThrow();
            assertThat(apos.getTssCalculado()).as("auditoria: tssCalculado não é apagado").isEqualTo(tssAntes);

            MetricasDiarias metricas = metricasDiariasRepository.findByAtletaIdAndData(atleta.getId(), data).orElseThrow();
            assertThat(metricas.getCtl()).as("cancelado não conta na carga").isEqualTo(0.0);
        }

        @ParameterizedTest
        @EnumSource(value = StatusSincronizacao.class, names = "CANCELADO", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("todo StatusSincronizacao exceto CANCELADO conta na carga [D8]")
        void todoStatusMenosCanceladoContaNaCarga(StatusSincronizacao status) {
            Atleta atleta = seedAtleta("Status" + status.name());
            LocalDate data = LocalDate.now().minusDays(1);
            TreinoRealizado treino = novoRealizado(atleta, data);
            TreinoDedupHelper.SaveResult resultado = ingestaoTreinoRealizadoService.registrar(treino, null);

            TreinoRealizado recarregado = treinoRealizadoRepository.findById(resultado.treino().getId()).orElseThrow();
            recarregado.setStatusSincronizacao(status);
            treinoRealizadoRepository.save(recarregado);

            ingestaoTreinoRealizadoService.reprocessar(recarregado.getId(), null);

            MetricasDiarias metricas = metricasDiariasRepository.findByAtletaIdAndData(atleta.getId(), data).orElseThrow();
            assertThat(metricas.getCtl())
                    .as("statusSincronizacao=" + status + " não é CANCELADO, deve contar na carga")
                    .isNotEqualTo(0.0);
        }

        @Test
        @DisplayName("statusSincronizacao nulo (estado normal de FIT/manual) conta na carga [D8]")
        void statusNuloContaNaCarga() {
            Atleta atleta = seedAtleta("StatusNulo");
            LocalDate data = LocalDate.now().minusDays(1);
            TreinoRealizado treino = novoRealizado(atleta, data);
            TreinoDedupHelper.SaveResult resultado = ingestaoTreinoRealizadoService.registrar(treino, null);
            assertThat(resultado.treino().getStatusSincronizacao()).isNull();

            ingestaoTreinoRealizadoService.reprocessar(resultado.treino().getId(), null);

            MetricasDiarias metricas = metricasDiariasRepository.findByAtletaIdAndData(atleta.getId(), data).orElseThrow();
            assertThat(metricas.getCtl())
                    .as("statusSincronizacao nulo não pode ser tratado como cancelado")
                    .isNotEqualTo(0.0);
        }

        @Test
        @DisplayName("id inexistente lança DomainNotFoundException")
        void idInexistenteLancaExcecao() {
            assertThatThrownBy(() -> ingestaoTreinoRealizadoService.reprocessar(UUID.randomUUID(), null))
                    .isInstanceOf(DomainNotFoundException.class);
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
        atleta.setObjetivo("Provar o reprocessar");
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

    private TreinoRealizado novoRealizado(Atleta atleta, LocalDate data) {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setAtleta(atleta);
        tr.setDataTreino(data);
        tr.setDiaSemana(DiaSemana.SABADO);
        tr.setTipoTreino(TipoTreino.FACIL);
        tr.setDuracaoMin(Duration.ofMinutes(40));
        tr.setDistanciaKm(BigDecimal.valueOf(8));
        tr.setFonteDados(FonteDados.MANUAL);
        tr.setPercepcaoEsforco(7);
        return tr;
    }
}
