package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.AtletaService;
import br.com.menthoros.backend.services.TsbService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Equivalencia entre os dois caminhos de entrada do recalculo — a diferenca que originou o bug.
 *
 * <p>Antes desta change, o caminho do controller e o do onboarding tinham contextos transacionais
 * diferentes: o do controller abria transacao propria, o do onboarding <b>participava</b> da
 * transacao do chamador (400+ dias numa transacao ainda maior). Com o chunking, o metodo deixa de
 * depender do contexto do chamador: os blocos comitam sozinhos nos dois casos.</p>
 *
 * <p>Automatiza a task 4.2 (que pedia teste manual) e cobre a 4.2b.</p>
 */
class TsbRecalculoCaminhosIT extends AbstractIntegrationTest {

    private static final int DIAS = 45;

    @Autowired
    private TsbService tsbService;
    @Autowired
    private AtletaService atletaService;
    @Autowired
    private TransactionTemplate transactionTemplate;
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

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("equivalencia entre caminhos de entrada")
    class Equivalencia {

        @Test
        @DisplayName("controller e onboarding produzem exatamente o mesmo historico [CA2]")
        void doisCaminhosMesmoResultado() {
            Atleta viaController = seedAtletaComHistorico();
            Atleta viaOnboarding = seedAtletaComHistorico();

            // Caminho 1 — AtletaController -> AtletaServiceImpl.recalcularMetricasAtleta,
            // sem transacao ambiente.
            TenantContext.setTenantId(viaController.getAssessoria().getId());
            atletaService.recalcularMetricasAtleta(viaController.getId());
            TenantContext.clear();

            // Caminho 2 — OnboardingServiceImpl.montarContexto e @Transactional; o recalculo roda
            // DENTRO da transacao do chamador. Com REQUIRES_NEW os blocos comitam mesmo assim.
            TenantContext.setTenantId(viaOnboarding.getAssessoria().getId());
            transactionTemplate.execute(status -> {
                tsbService.recalcularHistoricoCompleto(viaOnboarding.getId());
                return null;
            });
            TenantContext.clear();

            Map<Integer, double[]> a = snapshotPorDia(viaController.getId());
            Map<Integer, double[]> b = snapshotPorDia(viaOnboarding.getId());

            assertThat(a).as("o caminho do controller precisa materializar os %d dias", DIAS).hasSize(DIAS);
            assertThat(b.keySet()).as("mesmos dias nos dois caminhos").isEqualTo(a.keySet());

            a.forEach((dia, esperado) -> assertThat(b.get(dia))
                    .as("dia %d tem de ser identico nos dois caminhos", dia)
                    .containsExactly(esperado));
        }
    }

    @Nested
    @DisplayName("atomicidade do onboarding")
    class AtomicidadeOnboarding {

        @Test
        @DisplayName("falha do chamador APOS o recalculo nao desfaz o historico [CA8, 4.2b]")
        void falhaDoChamadorNaoDesfazHistorico() {
            Atleta atleta = seedAtletaComHistorico();
            TenantContext.setTenantId(atleta.getAssessoria().getId());

            // Simula montarContexto falhando depois do recalculo — no confidence score ou ao
            // persistir o snapshot de baseline.
            assertThatThrownBy(() -> transactionTemplate.execute(status -> {
                tsbService.recalcularHistoricoCompleto(atleta.getId());
                throw new IllegalStateException("falha do onboarding apos o recalculo");
            })).isInstanceOf(IllegalStateException.class);

            // Este e o efeito aceito na Decisao 4: a transacao do onboarding reverte, mas as
            // metricas ja ficaram comitadas. E quebra de atomicidade de um fluxo que antes era
            // atomico — documentada, nao acidental. O teste existe para que uma mudanca futura
            // nesse contrato seja uma decisao explicita, nao uma surpresa.
            assertThat(metricasDiariasRepository.findByAtletaIdOrderByDataAsc(atleta.getId()))
                    .as("os blocos comitam independentemente da transacao do chamador")
                    .hasSize(DIAS);
        }
    }

    // ---- helpers ----

    /** Indexado por offset do dia, para comparar atletas diferentes com o mesmo calendario. */
    private Map<Integer, double[]> snapshotPorDia(UUID atletaId) {
        LocalDate base = LocalDate.now().minusDays(DIAS - 1L);
        Map<Integer, double[]> out = new LinkedHashMap<>();
        for (MetricasDiarias m : metricasDiariasRepository.findByAtletaIdOrderByDataAsc(atletaId)) {
            int offset = (int) (m.getData().toEpochDay() - base.toEpochDay());
            out.put(offset, new double[]{m.getCtl(), m.getAtl(), m.getTsb(), m.getRampRate()});
        }
        return out;
    }

    private Atleta seedAtletaComHistorico() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Caminhos");
        assessoria.setDominio("caminhos-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Caminhos");
        atleta.setEmail("caminhos-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Comparar os dois caminhos de recalculo");
        atleta.setNivelExperiencia(NivelExperiencia.AVANCADO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta.setCtlTimeConstant(42);
        atleta.setAtlTimeConstant(7);
        atleta = atletaRepository.save(atleta);

        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(assessoria);
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        planoMetadadosRepository.save(meta);

        // Calendario identico para os dois atletas: mesmo TSS nos mesmos offsets.
        LocalDate base = LocalDate.now().minusDays(DIAS - 1L);
        for (int i = 0; i < DIAS; i++) {
            // Primeiro e ultimo dia SEMPRE tem treino: o intervalo de recalculo e delimitado por
            // eles, entao um dia de descanso na borda encurtaria a janela e o teste compararia
            // menos dias do que pretende.
            boolean descanso = i % 3 == 2 && i != 0 && i != DIAS - 1;
            if (!descanso) {
                salvarTreino(atleta, base.plusDays(i), 30 + (i % 7) * 10);
            }
        }
        return atleta;
    }

    private void salvarTreino(Atleta atleta, LocalDate data, int tssAlvo) {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setAtleta(atleta);
        treino.setDataTreino(data);
        treino.setDiaSemana(diaSemanaDe(data));
        treino.setTipoTreino(TipoTreino.FACIL);
        treino.setDuracaoMin(Duration.ofMinutes(tssAlvo * 3L / 5L));
        treino.setPercepcaoEsforco(8);
        treino.setDistanciaKm(new BigDecimal("10.00"));
        treinoRealizadoRepository.save(treino);
    }

    private DiaSemana diaSemanaDe(LocalDate data) {
        DayOfWeek dow = data.getDayOfWeek();
        return switch (dow) {
            case MONDAY -> DiaSemana.SEGUNDA;
            case TUESDAY -> DiaSemana.TERCA;
            case WEDNESDAY -> DiaSemana.QUARTA;
            case THURSDAY -> DiaSemana.QUINTA;
            case FRIDAY -> DiaSemana.SEXTA;
            case SATURDAY -> DiaSemana.SABADO;
            case SUNDAY -> DiaSemana.DOMINGO;
        };
    }
}
