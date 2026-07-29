package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
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
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.CoachAttentionQueueService;
import br.com.menthoros.backend.services.CoachDashboardService;
import br.com.menthoros.backend.services.MetricasAgregadasService;
import br.com.menthoros.backend.services.TsbService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato da janela de historico misto (Decisao 6).
 *
 * <p>Com chunking, durante o recalculo o historico fica parte novo e parte antigo, e essa mistura e
 * <b>visivel</b> para os leitores — antes, na transacao unica, ficava invisivel ate o commit final.
 * A decisao foi <b>servir o dado disponivel, sem bloqueio</b>: nenhum dos cinco leitores e alterado,
 * e nenhum deles pode quebrar por causa da janela.</p>
 *
 * <p>Este teste e o que prova essa decisao. Sem ele, "aceito o risco" seria implicito.</p>
 */
class TsbRecalculoJanelaMistaIT extends AbstractIntegrationTest {

    @Autowired
    private TsbService tsbService;
    @Autowired
    private AtletaProgressService atletaProgressService;
    @Autowired
    private CoachDashboardService coachDashboardService;
    @Autowired
    private CoachAttentionQueueService coachAttentionQueueService;
    @Autowired
    private MetricasAgregadasService metricasAgregadasService;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AtletaRepository atletaRepository;
    @Autowired
    private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired
    private TreinoRealizadoRepository treinoRealizadoRepository;

    private static final int DIAS = 90;

    @Test
    @DisplayName("os 5 leitores respondem durante o recalculo, sem excecao e sem bloqueio")
    void leitoresSobrevivemAJanelaMista() throws Exception {
        Atleta atleta = seedAtletaComHistorico();
        UUID atletaId = atleta.getId();
        UUID tenantId = atleta.getAssessoria().getId();

        AtomicBoolean recalculando = new AtomicBoolean(true);
        AtomicInteger leiturasConcluidas = new AtomicInteger();
        List<Throwable> falhas = new CopyOnWriteArrayList<>();

        Thread leitor = new Thread(() -> {
            // TenantContext e ThreadLocal: os leitores do coach resolvem o tenant por ele.
            TenantContext.setTenantId(tenantId);
            try {
                while (recalculando.get()) {
                    for (Runnable leitura : leituras(atletaId)) {
                        try {
                            leitura.run();
                            leiturasConcluidas.incrementAndGet();
                        } catch (Throwable t) {
                            falhas.add(t);
                        }
                    }
                    // Pausa entre ciclos: sem ela o laco vira uma tempestade de leitura que disputa
                    // o pool com as transacoes REQUIRES_NEW dos blocos. Isso exercita exaustao de
                    // pool — o risco da Decisao 4, que e premissa a medir — e nao o contrato deste
                    // teste, que e "leitura na janela mista devolve dado valido". Com 50ms ainda
                    // sobram ~100 ciclos sobre um recalculo de 90 dias.
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } finally {
                TenantContext.clear();
            }
        }, "leitor-janela-mista");

        leitor.start();
        try {
            tsbService.recalcularHistoricoCompleto(atletaId);
        } finally {
            recalculando.set(false);
            leitor.join(30_000);
        }

        assertThat(leiturasConcluidas.get())
                .as("o leitor precisa ter exercitado os endpoints durante o recalculo")
                .isPositive();
        List<String> resumo = falhas.stream()
                .map(t -> t.getClass().getName() + ": " + t.getMessage())
                .distinct()
                .toList();
        assertThat(resumo)
                .as("nenhum leitor pode quebrar na janela de historico misto (%d leituras ok)",
                        leiturasConcluidas.get())
                .isEmpty();
    }

    /** Os cinco leitores reais da janela, conforme mapeado no proposal.md. */
    private List<Runnable> leituras(UUID atletaId) {
        LocalDate hoje = LocalDate.now();
        List<Runnable> l = new ArrayList<>();
        l.add(() -> atletaProgressService.getHistoricoPmc(atletaId, hoje.minusDays(DIAS), hoje));
        l.add(() -> atletaProgressService.getHome(atletaId));
        l.add(() -> coachDashboardService.getRoster());
        l.add(() -> coachAttentionQueueService.getAttentionQueue());
        l.add(() -> metricasAgregadasService.calcularMetricasSemanais(atletaId, 4));
        return l;
    }

    // ---- fixtures ----

    private Atleta seedAtletaComHistorico() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Janela Mista");
        assessoria.setDominio("janela-mista-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Janela Mista");
        atleta.setEmail("janela-mista-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Validar leitura durante o recalculo");
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

        LocalDate base = LocalDate.now().minusDays(DIAS - 1L);
        for (int i = 0; i < DIAS; i++) {
            if (i % 3 != 2) {
                salvarTreino(atleta, base.plusDays(i));
            }
        }
        return atleta;
    }

    private void salvarTreino(Atleta atleta, LocalDate data) {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setAtleta(atleta);
        treino.setDataTreino(data);
        treino.setDiaSemana(diaSemanaDe(data));
        treino.setTipoTreino(TipoTreino.FACIL);
        treino.setDuracaoMin(Duration.ofMinutes(36));
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
