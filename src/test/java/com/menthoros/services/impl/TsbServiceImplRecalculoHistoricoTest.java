package com.menthoros.services.impl;

import com.menthoros.dto.output.ResultadoAnalise;
import com.menthoros.entity.Atleta;
import com.menthoros.entity.MetricasDiarias;
import com.menthoros.entity.PlanoMetaDados;
import com.menthoros.entity.TreinoRealizado;
import com.menthoros.enums.NivelExperiencia;
import com.menthoros.repository.AtletaRepository;
import com.menthoros.repository.MetricasDiariasRepository;
import com.menthoros.repository.PlanoMetadadosRepository;
import com.menthoros.repository.TreinoRealizadoRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TsbServiceImplRecalculoHistoricoTest {

    @Test
    void deveDeterminarIntervaloPeloHistoricoRelevanteSemIrAteHoje() throws Exception {
        UUID atletaId = UUID.randomUUID();

        TreinoRealizado treinoMaisRecente = new TreinoRealizado();
        treinoMaisRecente.setDataTreino(LocalDate.of(2026, 2, 5));

        TreinoRealizadoRepository treinoRepo = treinoRepoParaIntervalo(atletaId,
                LocalDate.of(2026, 1, 10), List.of(treinoMaisRecente));

        TsbServiceImpl service = new TsbServiceImpl(
                treinoRepo,
                null,
                null,
                null,
                null,
                null
        );

        List<MetricasDiarias> backup = List.of(
                metrica(LocalDate.of(2026, 1, 1)),
                metrica(LocalDate.of(2026, 1, 20))
        );

        Method m = TsbServiceImpl.class.getDeclaredMethod("determinarIntervaloRecalculo", UUID.class, List.class);
        m.setAccessible(true);
        Object intervalo = m.invoke(service, atletaId, backup);

        Method inicio = intervalo.getClass().getDeclaredMethod("inicio");
        Method fim = intervalo.getClass().getDeclaredMethod("fim");
        assertEquals(LocalDate.of(2026, 1, 1), inicio.invoke(intervalo));
        assertEquals(LocalDate.of(2026, 2, 5), fim.invoke(intervalo));
    }

    @Test
    void deveZerarMetaDadosQuandoNaoHouverHistoricoRelevante() {
        UUID atletaId = UUID.randomUUID();
        Atleta atleta = Atleta.builder()
                .id(atletaId)
                .nome("Teste")
                .objetivo("Teste")
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .build();

        PlanoMetaDados metaDados = PlanoMetaDados.builder()
                .atleta(atleta)
                .ctlAtual(42.0)
                .atlAtual(30.0)
                .tsbAtual(12.0)
                .rampRateAtual(5.0)
                .diasConsecutivosTreino(4)
                .semanasProgressaoContinua(3)
                .build();

        AtomicReference<PlanoMetaDados> salvo = new AtomicReference<>();
        AtomicInteger saves = new AtomicInteger();

        TsbServiceImpl service = new TsbServiceImpl(
                treinoRepoParaIntervalo(atletaId, null, Collections.emptyList()),
                planoRepo(metaDados, saves, salvo),
                metricasRepoSemHistorico(atletaId),
                atletaRepo(atleta),
                null,
                metricasAlertaServiceStub()
        );

        service.recalcularHistoricoCompleto(atletaId);

        PlanoMetaDados metaSalvo = salvo.get();
        assertNotNull(metaSalvo);
        assertEquals(0.0, metaSalvo.getCtlAtual());
        assertEquals(0.0, metaSalvo.getAtlAtual());
        assertEquals(0.0, metaSalvo.getTsbAtual());
        assertEquals(0.0, metaSalvo.getRampRateAtual());
        assertEquals(0, metaSalvo.getDiasConsecutivosTreino());
        assertEquals(0, metaSalvo.getSemanasProgressaoContinua());
        assertEquals(1, saves.get());
    }

    private static MetricasDiarias metrica(LocalDate data) {
        MetricasDiarias m = new MetricasDiarias();
        m.setData(data);
        return m;
    }

    private static TreinoRealizadoRepository treinoRepoParaIntervalo(UUID atletaId, LocalDate primeiroTreino,
                                                                     List<TreinoRealizado> treinosDesc) {
        return (TreinoRealizadoRepository) Proxy.newProxyInstance(
                TreinoRealizadoRepository.class.getClassLoader(),
                new Class<?>[]{TreinoRealizadoRepository.class},
                (proxy, method, args) -> {
                    if ("findDataPrimeiroTreino".equals(method.getName()) && atletaId.equals(args[0])) {
                        return primeiroTreino;
                    }
                    if ("findByAtletaIdOrderByDataTreinoDesc".equals(method.getName()) && atletaId.equals(args[0])) {
                        return treinosDesc;
                    }
                    if ("toString".equals(method.getName())) {
                        return "TreinoRealizadoRepositoryProxy";
                    }
                    throw new UnsupportedOperationException("Método não suportado: " + method);
                }
        );
    }

    private static MetricasDiariasRepository metricasRepoSemHistorico(UUID atletaId) {
        return (MetricasDiariasRepository) Proxy.newProxyInstance(
                MetricasDiariasRepository.class.getClassLoader(),
                new Class<?>[]{MetricasDiariasRepository.class},
                (proxy, method, args) -> {
                    if ("findByAtletaIdOrderByDataAsc".equals(method.getName()) && atletaId.equals(args[0])) {
                        return List.of();
                    }
                    if ("deleteByAtletaId".equals(method.getName()) && atletaId.equals(args[0])) {
                        return null;
                    }
                    if ("flush".equals(method.getName())) {
                        return null;
                    }
                    if ("toString".equals(method.getName())) {
                        return "MetricasDiariasRepositoryProxy";
                    }
                    throw new UnsupportedOperationException("Método não suportado: " + method);
                }
        );
    }

    private static PlanoMetadadosRepository planoRepo(PlanoMetaDados metaDados, AtomicInteger saves,
                                                      AtomicReference<PlanoMetaDados> salvo) {
        return (PlanoMetadadosRepository) Proxy.newProxyInstance(
                PlanoMetadadosRepository.class.getClassLoader(),
                new Class<?>[]{PlanoMetadadosRepository.class},
                (proxy, method, args) -> {
                    if ("findByAtletaId".equals(method.getName())) {
                        return Optional.of(metaDados);
                    }
                    if ("save".equals(method.getName())) {
                        PlanoMetaDados entity = (PlanoMetaDados) args[0];
                        saves.incrementAndGet();
                        salvo.set(entity);
                        return entity;
                    }
                    if ("toString".equals(method.getName())) {
                        return "PlanoMetadadosRepositoryProxy";
                    }
                    throw new UnsupportedOperationException("Método não suportado: " + method);
                }
        );
    }

    private static AtletaRepository atletaRepo(Atleta atleta) {
        return (AtletaRepository) Proxy.newProxyInstance(
                AtletaRepository.class.getClassLoader(),
                new Class<?>[]{AtletaRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        return Optional.of(atleta);
                    }
                    if ("toString".equals(method.getName())) {
                        return "AtletaRepositoryProxy";
                    }
                    throw new UnsupportedOperationException("Método não suportado: " + method);
                }
        );
    }

    private static MetricasAlertaService metricasAlertaServiceStub() {
        return new MetricasAlertaService() {
            @Override
            public ResultadoAnalise analisarMetricas(PlanoMetaDados metaDados, NivelExperiencia nivelExperiencia) {
                return new ResultadoAnalise(
                        "OK",
                        "MANTER",
                        "",
                        false,
                        false,
                        false,
                        false,
                        List.of()
                );
            }
        };
    }
}
