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
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.repository.projection.SerieMetricasAtleta;
import br.com.menthoros.backend.services.TsbService;
import br.com.menthoros.backend.services.helper.TssCalculatorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

/**
 * fix-tsb-rest-days-materialization, design.md R1/R2/D1 contra Postgres real: o recálculo de um
 * intervalo é atômico, é serializado por atleta com advisory lock e fecha o buraco entre a última
 * métrica e o novo treino.
 */
class TsbRecalculoTransacionalIT extends AbstractIntegrationTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired private TsbService tsbService;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired private TreinoRealizadoRepository treinoRealizadoRepository;
    @Autowired private MetricasDiariasRepository metricasDiariasRepository;
    @Autowired private TssCalculatorService tssCalculatorService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean private TsbDiaPersister tsbDiaPersister;

    @Test
    @DisplayName("falha no meio do intervalo desfaz todos os dias já calculados (R1)")
    void falhaNoMeioFazRollbackDoIntervaloInteiro() {
        LocalDate hoje = LocalDate.now(SP);
        LocalDate d3 = hoje.minusDays(3);
        Atleta atleta = seedAtletaComRepouso("Rollback", hoje.minusDays(5), hoje);
        salvarTreinoComEsforco(atleta, d3);

        // D-3 e D-2 já teriam CTL > 0 quando D-1 falha; sem rollback ficariam gravados.
        doThrow(new IllegalStateException("falha simulada"))
                .when(tsbDiaPersister)
                .atualizarDiaTransacional(eq(atleta.getId()), eq(hoje.minusDays(1)), anyBoolean(), any());

        assertThatThrownBy(() -> tsbService.recalcularDesde(atleta.getId(), d3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("falha simulada");

        for (LocalDate dia = d3; !dia.isAfter(hoje); dia = dia.plusDays(1)) {
            assertThat(buscar(atleta, dia).getCtl())
                    .as("CTL de %s deve continuar o de antes do recálculo (rollback)", dia)
                    .isEqualTo(0.0);
        }
    }

    @Test
    @DisplayName("ingestão em curso segura o recálculo concorrente no advisory lock; a métrica final reflete o treino (R2)")
    void recalculoConcorrenteEsperaAIngestao() throws Exception {
        LocalDate hoje = LocalDate.now(SP);
        Atleta atleta = seedAtletaComRepouso("Concorrencia", hoje.minusDays(3), hoje);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch ingestaoComLock = new CountDownLatch(1);
        CountDownLatch liberaIngestao = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // A: ingestão — grava o treino e recalcula na MESMA transação, sem commitar ainda.
            Future<?> ingestao = pool.submit(() -> tx.executeWithoutResult(status -> {
                salvarTreinoComEsforco(atleta, hoje);
                tsbService.recalcularDesde(atleta.getId(), hoje);
                ingestaoComLock.countDown();
                aguardar(liberaIngestao);
            }));
            assertThat(ingestaoComLock.await(30, TimeUnit.SECONDS)).as("A pegou o lock").isTrue();

            // B: recálculo concorrente do mesmo atleta (como faria o job de decaimento).
            Future<?> concorrente = pool.submit(() -> tsbService.recalcularDesde(atleta.getId(), hoje));

            esperarAteHaverAdvisoryLockPendente();
            assertThat(concorrente.isDone())
                    .as("B deve estar bloqueado no advisory lock enquanto A não commita")
                    .isFalse();

            liberaIngestao.countDown();
            ingestao.get(30, TimeUnit.SECONDS);
            concorrente.get(30, TimeUnit.SECONDS);
        } finally {
            liberaIngestao.countDown();
            pool.shutdownNow();
        }

        assertThat(buscar(atleta, hoje).getCtl())
                .as("a métrica final reflete o treino ingerido, nunca o TSS antigo (0)")
                .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("treino após dias sem métrica fecha o buraco com decaimento, sem reiniciar em zero (D1)")
    void treinoAposBuracoMaterializaDiasDeDescanso() {
        LocalDate hoje = LocalDate.now(SP);
        LocalDate d9 = hoje.minusDays(9);
        Atleta atleta = criarAtleta("Buraco");
        salvarTreinoComEsforco(atleta, d9);
        for (LocalDate dia = d9; !dia.isAfter(hoje.minusDays(6)); dia = dia.plusDays(1)) {
            tsbService.atualizarTsbDia(atleta.getId(), dia);
        }
        // D-5..D-2 sem nenhuma linha: o buraco que o gráfico mostrava como "Sem dado no período".
        salvarTreinoComEsforco(atleta, hoje.minusDays(1));

        tsbService.recalcularDesde(atleta.getId(), hoje.minusDays(1));

        for (LocalDate dia = hoje.minusDays(5); !dia.isAfter(hoje); dia = dia.plusDays(1)) {
            assertThat(metricasDiariasRepository.findByAtletaIdAndData(atleta.getId(), dia))
                    .as("linha de %s deve existir (série densa)", dia).isPresent();
        }
        double ctlD5 = buscar(atleta, hoje.minusDays(5)).getCtl();
        double ctlD4 = buscar(atleta, hoje.minusDays(4)).getCtl();
        double ctlD3 = buscar(atleta, hoje.minusDays(3)).getCtl();
        double ctlD2 = buscar(atleta, hoje.minusDays(2)).getCtl();
        double ctlD1 = buscar(atleta, hoje.minusDays(1)).getCtl();
        assertThat(ctlD2).as("nunca reiniciou em zero").isGreaterThan(0.0);
        assertThat(ctlD5).isGreaterThan(ctlD4);
        assertThat(ctlD4).isGreaterThan(ctlD3);
        assertThat(ctlD3).isGreaterThan(ctlD2);
        assertThat(ctlD1).as("o treino de D-1 volta a subir o CTL").isGreaterThan(ctlD2);
    }

    @Test
    @DisplayName("findSeriePorAtleta: buraco no meio da série devolve o primeiro dia ausente")
    void serieComBuraco() {
        LocalDate d0 = LocalDate.now(SP).minusDays(10);
        Atleta atleta = criarAtleta("SerieBuraco");
        tsbService.atualizarTsbDia(atleta.getId(), d0);
        tsbService.atualizarTsbDia(atleta.getId(), d0.plusDays(1));
        tsbService.atualizarTsbDia(atleta.getId(), d0.plusDays(3));

        SerieMetricasAtleta serie = serieDe(atleta);

        assertThat(serie.getPrimeiroBuraco()).isEqualTo(d0.plusDays(2));
        assertThat(serie.getUltimaData()).isEqualTo(d0.plusDays(3));
    }

    @Test
    @DisplayName("findSeriePorAtleta: série densa e atrasada não tem buraco; pendente é ultima+1")
    void serieDensaAtrasada() {
        LocalDate hoje = LocalDate.now(SP);
        Atleta atleta = seedAtletaComRepouso("SerieAtrasada", hoje.minusDays(6), hoje.minusDays(3));

        SerieMetricasAtleta serie = serieDe(atleta);

        assertThat(serie.getPrimeiroBuraco()).isNull();
        assertThat(serie.primeiroDiaPendente(hoje)).contains(hoje.minusDays(2));
    }

    @Test
    @DisplayName("findSeriePorAtleta: série densa e em dia não tem nada pendente")
    void serieEmDia() {
        LocalDate hoje = LocalDate.now(SP);
        Atleta atleta = seedAtletaComRepouso("SerieEmDia", hoje.minusDays(2), hoje);

        SerieMetricasAtleta serie = serieDe(atleta);

        assertThat(serie.getPrimeiroBuraco()).isNull();
        assertThat(serie.primeiroDiaPendente(hoje)).isEmpty();
    }

    @Test
    @DisplayName("existsQueContamEntre: extremos excluídos, CANCELADO não conta, status NULL conta (R6)")
    void existeTreinoQueContamEntre() {
        LocalDate hoje = LocalDate.now(SP);
        LocalDate de = hoje.minusDays(5);
        Atleta atleta = criarAtleta("ExisteEntre");

        salvarTreinoComEsforco(atleta, de);
        salvarTreinoComEsforco(atleta, hoje);
        salvarTreinoComEsforco(atleta, hoje.minusDays(3), StatusSincronizacao.CANCELADO);
        assertThat(treinoRealizadoRepository.existsQueContamEntre(atleta.getId(), de, hoje))
                .as("só treino nos extremos e um CANCELADO dentro: não conta").isFalse();

        salvarTreinoComEsforco(atleta, hoje.minusDays(2), StatusSincronizacao.PENDENTE);
        assertThat(treinoRealizadoRepository.existsQueContamEntre(atleta.getId(), de, hoje)).isTrue();

        Atleta outro = criarAtleta("ExisteEntreNull");
        salvarTreinoComEsforco(outro, hoje.minusDays(1));
        assertThat(treinoRealizadoRepository.existsQueContamEntre(outro.getId(), de, hoje))
                .as("status NULL (FIT/manual) conta").isTrue();
        assertThat(treinoRealizadoRepository.existsQueContamEntre(atleta.getId(), hoje.minusDays(1), hoje))
                .as("intervalo sem dia interior").isFalse();
    }

    @Test
    @DisplayName("seed com linha faltando: sem treino no intervalo decai; com treino no intervalo cai para zero (R6)")
    void seedDefensivoContraPostgres() {
        LocalDate hoje = LocalDate.now(SP);
        Atleta semTreino = criarAtleta("SeedSemTreino");
        salvarTreinoComEsforco(semTreino, hoje.minusDays(5));
        tsbService.atualizarTsbDia(semTreino.getId(), hoje.minusDays(5));
        double ctlBase = buscar(semTreino, hoje.minusDays(5)).getCtl();

        tsbService.atualizarTsbDia(semTreino.getId(), hoje.minusDays(1));

        double seed = buscar(semTreino, hoje.minusDays(1)).getCtlInicioDia();
        assertThat(seed).as("decaiu, não zerou nem manteve").isGreaterThan(0.0).isLessThan(ctlBase);

        Atleta comTreino = criarAtleta("SeedComTreino");
        salvarTreinoComEsforco(comTreino, hoje.minusDays(5));
        tsbService.atualizarTsbDia(comTreino.getId(), hoje.minusDays(5));
        salvarTreinoComEsforco(comTreino, hoje.minusDays(3));

        tsbService.atualizarTsbDia(comTreino.getId(), hoje.minusDays(1));

        assertThat(buscar(comTreino, hoje.minusDays(1)).getCtlInicioDia())
                .as("treino sem linha no intervalo: não publica valor plausível-mas-errado").isZero();
    }

    @Test
    @DisplayName("ingestão após buraco acima do teto: grava 400 dias, não falha, e a execução seguinte converge (R7/R9)")
    void ingestaoComBuracoAcimaDoTeto() {
        LocalDate hoje = LocalDate.now(SP);
        LocalDate ultima = hoje.minusDays(450);
        Atleta atleta = criarAtleta("BuracoLongo");
        salvarTreinoComEsforco(atleta, ultima);
        tsbService.atualizarTsbDia(atleta.getId(), ultima);
        salvarTreinoComEsforco(atleta, hoje);

        long inicio = System.nanoTime();
        tsbService.recalcularDesde(atleta.getId(), hoje);
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);

        // Medido em 2026-10-20: o cenário inteiro (400 dias numa transação + convergência) leva ~1,6 s
        // em Postgres/Testcontainers. 30 s é folga para CI lento, não a expectativa.
        assertThat(ms).as("latência do registrar com buraco no teto").isLessThan(30_000);
        SerieMetricasAtleta aposPrimeira = serieDe(atleta);
        assertThat(aposPrimeira.getUltimaData()).isEqualTo(ultima.plusDays(400));
        assertThat(aposPrimeira.getPrimeiroBuraco()).as("a série criada é densa").isNull();

        // O que o job horário fará: continua do primeiro dia pendente até hoje.
        LocalDate proximo = aposPrimeira.primeiroDiaPendente(hoje).orElseThrow();
        assertThat(proximo).isEqualTo(ultima.plusDays(401));
        tsbService.recalcularDesde(atleta.getId(), proximo, hoje);

        SerieMetricasAtleta aposSegunda = serieDe(atleta);
        assertThat(aposSegunda.getUltimaData()).isEqualTo(hoje);
        assertThat(aposSegunda.primeiroDiaPendente(hoje)).isEmpty();
        assertThat(buscar(atleta, hoje.minusDays(1)).getCtl()).as("nunca reiniciou em zero").isGreaterThan(0.0);
    }

    private SerieMetricasAtleta serieDe(Atleta atleta) {
        return metricasDiariasRepository.findSeriePorAtleta().stream()
                .filter(x -> x.getAtletaId().equals(atleta.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("atleta sem série: " + atleta.getId()));
    }

    private void esperarAteHaverAdvisoryLockPendente() throws InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < limite) {
            Integer pendentes = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND NOT granted", Integer.class);
            if (pendentes != null && pendentes > 0) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("nenhuma transação ficou esperando um advisory lock — a serialização não existe");
    }

    private static void aguardar(CountDownLatch latch) {
        try {
            latch.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private MetricasDiarias buscar(Atleta atleta, LocalDate data) {
        return metricasDiariasRepository.findByAtletaIdAndData(atleta.getId(), data)
                .orElseThrow(() -> new AssertionError("Métrica não materializada para " + data));
    }

    private Atleta seedAtletaComRepouso(String prefixo, LocalDate de, LocalDate ate) {
        Atleta atleta = criarAtleta(prefixo);
        for (LocalDate dia = de; !dia.isAfter(ate); dia = dia.plusDays(1)) {
            tsbService.atualizarTsbDia(atleta.getId(), dia);
        }
        return atleta;
    }

    private Atleta criarAtleta(String prefixo) {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria " + prefixo);
        assessoria.setDominio(prefixo.toLowerCase() + "-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta " + prefixo);
        atleta.setEmail(prefixo.toLowerCase() + "-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Provar o recálculo transacional");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
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
        return atleta;
    }

    private void salvarTreinoComEsforco(Atleta atleta, LocalDate data) {
        salvarTreinoComEsforco(atleta, data, null);
    }

    private void salvarTreinoComEsforco(Atleta atleta, LocalDate data, StatusSincronizacao status) {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setStatusSincronizacao(status);
        treino.setAtleta(atleta);
        treino.setDataTreino(data);
        treino.setDiaSemana(diaSemanaDe(data));
        treino.setTipoTreino(TipoTreino.FACIL);
        treino.setDuracaoMin(Duration.ofMinutes(40));
        treino.setPercepcaoEsforco(8);
        treino.setDistanciaKm(new BigDecimal("8.00"));
        treino.setTssCalculado(tssCalculatorService.calcularTss(treino));
        treinoRealizadoRepository.save(treino);
    }

    private DiaSemana diaSemanaDe(LocalDate data) {
        return switch (data.getDayOfWeek()) {
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
