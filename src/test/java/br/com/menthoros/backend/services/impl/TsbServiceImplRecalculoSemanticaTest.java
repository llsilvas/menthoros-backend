package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.ResultadoAnalise;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.PlanoMetadadosService;
import br.com.menthoros.backend.services.helper.AthleteThresholdUpdater;
import br.com.menthoros.backend.testsupport.TsbRecalculoExecutorInline;
import br.com.menthoros.backend.services.helper.ThresholdInferenceService;
import br.com.menthoros.backend.services.helper.TssCalculatorService;
import br.com.menthoros.backend.testsupport.ProvaRepositoryTestStub;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD — Section 4: valida refactors de recálculo histórico.
 *
 * <ul>
 *   <li>Guard: sem treinos → retorno sem exceção (sem executar recalculo).</li>
 *   <li>determinarDataInicio usa data do primeiro treino real, não janela fixa.</li>
 * </ul>
 */
@DisplayName("Semântica TSB: recálculo histórico — Section 4")
class TsbServiceImplRecalculoSemanticaTest {

    // =========================================================================
    // Teste 1: atleta sem treinos não lança exceção
    // =========================================================================

    @Test
    @DisplayName("recalcularHistoricoCompleto para atleta sem treinos não lança exceção")
    void recalcularHistorico_semTreinos_naoLancaExcecao() {
        UUID atletaId = UUID.randomUUID();

        Assessoria assessoria = new Assessoria();
        assessoria.setId(UUID.randomUUID());

        Atleta atleta = Atleta.builder()
                .id(atletaId)
                .nome("Atleta Sem Treino")
                .objetivo("Teste")
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .assessoria(assessoria)
                .build();

        PlanoMetaDados planoMetaDados = PlanoMetaDados.builder()
                .atleta(atleta)
                .ctlAtual(0.0)
                .atlAtual(0.0)
                .tsbAtual(0.0)
                .tsbProntidaoAtual(0.0)
                .tsbPosCargaAtual(0.0)
                .rampRateAtual(0.0)
                .diasConsecutivosTreino(0)
                .semanasProgressaoContinua(0)
                .build();

        TsbServiceImpl service = construirServiceSemTreinos(atletaId, atleta, planoMetaDados);

        assertDoesNotThrow(() -> service.recalcularHistoricoCompleto(atletaId),
                "recalcularHistoricoCompleto não deve lançar exceção para atleta sem treinos");
    }

    // =========================================================================
    // Teste 2: atleta com treino antigo → data de início correta
    // =========================================================================

    @Test
    @DisplayName("recalcularHistoricoCompleto com primeiro treino há 8 meses usa essa data como início")
    void recalcularHistorico_comTreino8MesesAtras_usaDataCorreta() {
        UUID atletaId = UUID.randomUUID();

        LocalDate primeiroTreino = LocalDate.now().minusMonths(8);

        Assessoria assessoria = new Assessoria();
        assessoria.setId(UUID.randomUUID());

        Atleta atleta = Atleta.builder()
                .id(atletaId)
                .nome("Atleta Veterano")
                .objetivo("Teste")
                .nivelExperiencia(NivelExperiencia.AVANCADO)
                .assessoria(assessoria)
                .build();

        PlanoMetaDados planoMetaDados = PlanoMetaDados.builder()
                .atleta(atleta)
                .ctlAtual(0.0)
                .atlAtual(0.0)
                .tsbAtual(0.0)
                .tsbProntidaoAtual(0.0)
                .tsbPosCargaAtual(0.0)
                .rampRateAtual(0.0)
                .diasConsecutivosTreino(0)
                .semanasProgressaoContinua(0)
                .build();

        // Captura os dias processados para verificar que o intervalo começa em primeiroTreino
        java.util.Set<LocalDate> diasAtualizados = new java.util.TreeSet<>();

        TsbServiceImpl service = construirServiceComPrimeiroTreino(
                atletaId, atleta, planoMetaDados, primeiroTreino, diasAtualizados);

        service.recalcularHistoricoCompleto(atletaId);

        // O dia de início deve estar dentro do intervalo: primeiroTreino ± 1 dia (janela de processamento)
        assertFalse(diasAtualizados.isEmpty(), "Deve ter processado pelo menos um dia");

        LocalDate primeiroProcessado = ((java.util.TreeSet<LocalDate>) diasAtualizados).first();

        // Verifica que o primeiro dia processado é aproximadamente o primeiroTreino
        // (pode ser o próprio dia ou muito próximo)
        assertTrue(
                !primeiroProcessado.isAfter(primeiroTreino.plusDays(1)),
                "Primeiro dia processado (" + primeiroProcessado + ") deve ser <= primeiroTreino + 1 dia (" + primeiroTreino.plusDays(1) + ")"
        );

        // Garante que NÃO usou janela fixa de 3 meses
        LocalDate janelaFixa3Meses = LocalDate.now().minusMonths(3);
        assertTrue(
                primeiroProcessado.isBefore(janelaFixa3Meses),
                "Intervalo deve começar antes dos últimos 3 meses, já que o treino é mais antigo. " +
                "Primeiro processado: " + primeiroProcessado + ", Janela 3 meses: " + janelaFixa3Meses
        );
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private TsbServiceImpl construirServiceSemTreinos(UUID atletaId, Atleta atleta, PlanoMetaDados planoMetaDados) {
        AtletaRepository atletaRepo = atletaRepoStub(atleta);

        TreinoRealizadoRepository treinoRepo = (TreinoRealizadoRepository) Proxy.newProxyInstance(
                TreinoRealizadoRepository.class.getClassLoader(),
                new Class<?>[]{TreinoRealizadoRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findDataPrimeiroTreino".equals(name)) return null; // sem primeiro treino
                    if ("findByAtletaIdOrderByDataTreinoDesc".equals(name)) return Collections.emptyList();
                    if ("findQueContamByAtletaIdAndDataTreino".equals(name)) return Collections.emptyList();
                    if ("findByAtletaIdAndDataTreinoBetween".equals(name)) return Collections.emptyList();
                    if ("findByAtletaIdAndTenantIdAndDataTreinoBetween".equals(name)) return Collections.emptyList();
                    if ("toString".equals(name)) return "TreinoRealizadoRepositoryStub";
                    throw new UnsupportedOperationException("Método não suportado: " + name);
                }
        );

        MetricasDiariasRepository metricasRepo = (MetricasDiariasRepository) Proxy.newProxyInstance(
                MetricasDiariasRepository.class.getClassLoader(),
                new Class<?>[]{MetricasDiariasRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findByAtletaIdOrderByDataAsc".equals(name)) return Collections.emptyList();
                    if ("deleteByAtletaId".equals(name)) return null;
                    if ("flush".equals(name)) return null;
                    // Sem metricas pre-existentes: os limites do intervalo sao nulos.
                    if ("findDataPrimeiraMetrica".equals(name)) return null;
                    if ("findDataUltimaMetrica".equals(name)) return null;
                    if ("toString".equals(name)) return "MetricasDiariasRepositoryStub";
                    throw new UnsupportedOperationException("Método não suportado: " + name);
                }
        );

        AtomicReference<PlanoMetaDados> salvo = new AtomicReference<>();
        PlanoMetadadosRepository planoRepo = planoRepoStub(planoMetaDados, salvo);

        MetricasAlertaService alertaService = alertaServiceStub();
        TssCalculatorService tssCalc = new TssCalculatorService();

        return new TsbServiceImpl(treinoRepo, planoRepo, metricasRepo, atletaRepo, tssCalc, alertaService,
                new AthleteThresholdUpdater(treinoRepo, ProvaRepositoryTestStub.semProvas(), new ThresholdInferenceService()),
                new TsbRecalculoExecutorInline(), planoMetadadosServiceStub(planoMetaDados));
    }

    private TsbServiceImpl construirServiceComPrimeiroTreino(
            UUID atletaId,
            Atleta atleta,
            PlanoMetaDados planoMetaDados,
            LocalDate primeiroTreino,
            java.util.Set<LocalDate> diasAtualizados) {

        AtletaRepository atletaRepo = atletaRepoStub(atleta);

        TreinoRealizado ultimoTreinoEntity = new TreinoRealizado();
        ultimoTreinoEntity.setDataTreino(LocalDate.now().minusDays(1));

        TreinoRealizadoRepository treinoRepo = (TreinoRealizadoRepository) Proxy.newProxyInstance(
                TreinoRealizadoRepository.class.getClassLoader(),
                new Class<?>[]{TreinoRealizadoRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findDataPrimeiroTreino".equals(name)) return primeiroTreino;
                    if ("findByAtletaIdOrderByDataTreinoDesc".equals(name)) return List.of(ultimoTreinoEntity);
                    if ("findQueContamByAtletaIdAndDataTreino".equals(name)) return Collections.emptyList();
                    if ("findByAtletaIdAndDataTreinoBetween".equals(name)) return Collections.emptyList();
                    if ("findByAtletaIdAndTenantIdAndDataTreinoBetween".equals(name)) return Collections.emptyList();
                    if ("toString".equals(name)) return "TreinoRealizadoRepositoryStub";
                    throw new UnsupportedOperationException("Método não suportado: " + name);
                }
        );

        MetricasDiariasRepository metricasRepo = (MetricasDiariasRepository) Proxy.newProxyInstance(
                MetricasDiariasRepository.class.getClassLoader(),
                new Class<?>[]{MetricasDiariasRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findByAtletaIdOrderByDataAsc".equals(name)) return Collections.emptyList();
                    if ("deleteByAtletaId".equals(name)) return null;
                    if ("flush".equals(name)) return null;
                    if ("findByAtletaIdAndData".equals(name)) {
                        LocalDate data = (LocalDate) args[1];
                        diasAtualizados.add(data); // registra qual dia foi buscado
                        return Optional.empty();
                    }
                    if ("save".equals(name)) {
                        MetricasDiarias m = (MetricasDiarias) args[0];
                        if (m.getData() != null) diasAtualizados.add(m.getData());
                        return m;
                    }
                    // Sem metricas pre-existentes: os limites do intervalo sao nulos.
                    if ("findDataPrimeiraMetrica".equals(name)) return null;
                    if ("findDataUltimaMetrica".equals(name)) return null;
                    if ("toString".equals(name)) return "MetricasDiariasRepositoryStub";
                    throw new UnsupportedOperationException("Método não suportado: " + name);
                }
        );

        AtomicReference<PlanoMetaDados> salvo = new AtomicReference<>();
        PlanoMetadadosRepository planoRepo = planoRepoStub(planoMetaDados, salvo);

        // Último dia processado (com atleta definido para evitar NPE em atualizarMetaDados)
        MetricasDiarias ultimaMetrica = new MetricasDiarias();
        ultimaMetrica.setAtleta(atleta);
        ultimaMetrica.setData(LocalDate.now().minusDays(1));
        ultimaMetrica.setCtl(50.0);
        ultimaMetrica.setAtl(55.0);
        ultimaMetrica.setTsb(-5.0);
        ultimaMetrica.setTss(0);
        ultimaMetrica.setCtlInicioDia(49.0);
        ultimaMetrica.setAtlInicioDia(54.0);
        ultimaMetrica.setTsbInicioDia(-5.0);
        ultimaMetrica.setCtlFimDia(50.0);
        ultimaMetrica.setAtlFimDia(55.0);
        ultimaMetrica.setTsbFimDia(-5.0);
        ultimaMetrica.setRampRate(0.0);
        ultimaMetrica.setTreinosRealizados(0);

        MetricasDiariasRepository metricasRepoComUltima = (MetricasDiariasRepository) Proxy.newProxyInstance(
                MetricasDiariasRepository.class.getClassLoader(),
                new Class<?>[]{MetricasDiariasRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    LocalDate dataOntem = LocalDate.now().minusDays(1);
                    if ("findByAtletaIdOrderByDataAsc".equals(name)) return Collections.emptyList();
                    if ("deleteByAtletaId".equals(name)) return null;
                    if ("flush".equals(name)) return null;
                    if ("findByAtletaIdAndData".equals(name)) {
                        LocalDate data = (LocalDate) args[1];
                        diasAtualizados.add(data);
                        // Retorna a última métrica quando requisitada ao final do recálculo
                        if (dataOntem.equals(data)) {
                            return Optional.of(ultimaMetrica);
                        }
                        return Optional.empty();
                    }
                    if ("save".equals(name)) {
                        MetricasDiarias m = (MetricasDiarias) args[0];
                        if (m.getData() != null) diasAtualizados.add(m.getData());
                        return m;
                    }
                    if ("findByAtletaIdOrderByDataAsc".equals(name)) return Collections.emptyList();
                    // Sem metricas pre-existentes: os limites do intervalo sao nulos.
                    if ("findDataPrimeiraMetrica".equals(name)) return null;
                    if ("findDataUltimaMetrica".equals(name)) return null;
                    if ("toString".equals(name)) return "MetricasDiariasRepositoryStub";
                    throw new UnsupportedOperationException("Método não suportado: " + name);
                }
        );

        MetricasAlertaService alertaService = alertaServiceStub();
        TssCalculatorService tssCalc = new TssCalculatorService();

        return new TsbServiceImpl(treinoRepo, planoRepo, metricasRepoComUltima, atletaRepo, tssCalc, alertaService,
                new AthleteThresholdUpdater(treinoRepo, ProvaRepositoryTestStub.semProvas(), new ThresholdInferenceService()),
                new TsbRecalculoExecutorInline(), planoMetadadosServiceStub(planoMetaDados));
    }

    private static AtletaRepository atletaRepoStub(Atleta atleta) {
        return (AtletaRepository) Proxy.newProxyInstance(
                AtletaRepository.class.getClassLoader(),
                new Class<?>[]{AtletaRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) return Optional.of(atleta);
                    if ("toString".equals(method.getName())) return "AtletaRepositoryStub";
                    throw new UnsupportedOperationException("Método não suportado: " + method.getName());
                }
        );
    }

    private static PlanoMetadadosRepository planoRepoStub(PlanoMetaDados planoMetaDados,
                                                          AtomicReference<PlanoMetaDados> salvo) {
        return (PlanoMetadadosRepository) Proxy.newProxyInstance(
                PlanoMetadadosRepository.class.getClassLoader(),
                new Class<?>[]{PlanoMetadadosRepository.class},
                (proxy, method, args) -> {
                    if ("findByAtletaId".equals(method.getName())) return Optional.of(planoMetaDados);
                    if ("save".equals(method.getName())) {
                        PlanoMetaDados entity = (PlanoMetaDados) args[0];
                        salvo.set(entity);
                        return entity;
                    }
                    if ("toString".equals(method.getName())) return "PlanoMetadadosRepositoryStub";
                    throw new UnsupportedOperationException("Método não suportado: " + method.getName());
                }
        );
    }

    private static PlanoMetadadosService planoMetadadosServiceStub(PlanoMetaDados planoMetaDados) {
        return new PlanoMetadadosService() {
            @Override
            public PlanoMetaDados buscarOuCriarMetadados(Atleta atleta) {
                return planoMetaDados;
            }

            @Override
            public PlanoMetaDados buscarPorAtletaId(UUID atletaId) {
                return planoMetaDados;
            }
        };
    }

    private static MetricasAlertaService alertaServiceStub() {
        return new MetricasAlertaService() {
            @Override
            public ResultadoAnalise analisarMetricas(PlanoMetaDados metaDados, NivelExperiencia nivelExperiencia) {
                return new ResultadoAnalise("OK", "MANTER", "", false, false, false, false, List.of());
            }
        };
    }

}
