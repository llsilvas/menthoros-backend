package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Migrado de {@code TsbServiceImplRampRateTest}
 * (refactor-threshold-call-outside-transaction, seção 3) — {@code calcularRampRate} saiu de
 * {@code TsbServiceImpl} pra {@code TsbDiaPersister}. Package-private direto, sem reflection.
 */
class TsbDiaPersisterRampRateTest {

    @Test
    /**
     * Documenta o ISSUE-05:
     * o cálculo atual de ramp rate em TsbDiaPersister é absoluto (pontos de CTL/semana).
     *
     * <p>A correção do ISSUE-05 é aplicada na camada de alertas (MetricasAlertaService),
     * que interpreta o ramp rate de forma relativa usando CTL e o delta em pontos.
     *
     * Exemplo do issue:
     * CTL 20 -> 26 em 7 dias = +6 pts (absoluto) = +30% (relativo).
     *
     * Esperado aqui (ramp absoluto): 6.0
     */
    void deveRetornarRampRateAbsolutoQuandoHaHistoricoDeSeteDias() {
        UUID atletaId = UUID.randomUUID();
        LocalDate data = LocalDate.of(2026, 2, 16);

        MetricasDiarias semanaPassada = new MetricasDiarias();
        semanaPassada.setCtl(20.0);

        MetricasDiariasRepository metricasDiariasRepository = repoComMetricasSemanaPassada(atletaId, data, semanaPassada);

        TsbDiaPersister persister = new TsbDiaPersister(
                null, null, metricasDiariasRepository, null, null, null, null);

        double ramp = persister.calcularRampRate(atletaId, data, 26.0);
        assertEquals(6.0, ramp, 0.0001);
    }

    @Test
    /**
     * Complementa o ISSUE-05:
     * quando o CTL anterior é muito baixo, o percentual pode explodir.
     * Nesse caso o comportamento esperado é manter o ramp absoluto.
     *
     * Exemplo: CTL 5 -> 11 = +6 pts.
     * Esperado: 6.0 (absoluto)
     */
    void deveManterRampRateAbsolutoQuandoCtlAnteriorEhMuitoBaixo() {
        UUID atletaId = UUID.randomUUID();
        LocalDate data = LocalDate.of(2026, 2, 16);

        MetricasDiarias semanaPassada = new MetricasDiarias();
        semanaPassada.setCtl(5.0);

        MetricasDiariasRepository metricasDiariasRepository = repoComMetricasSemanaPassada(atletaId, data, semanaPassada);

        TsbDiaPersister persister = new TsbDiaPersister(
                null, null, metricasDiariasRepository, null, null, null, null);

        double ramp = persister.calcularRampRate(atletaId, data, 11.0);
        assertEquals(6.0, ramp, 0.0001);
    }

    private static MetricasDiariasRepository repoComMetricasSemanaPassada(UUID atletaId, LocalDate data, MetricasDiarias semanaPassada) {
        return (MetricasDiariasRepository) Proxy.newProxyInstance(
                MetricasDiariasRepository.class.getClassLoader(),
                new Class<?>[]{MetricasDiariasRepository.class},
                (proxy, method, args) -> {
                    if ("findByAtletaIdAndData".equals(method.getName())
                            && args != null
                            && args.length == 2
                            && atletaId.equals(args[0])
                            && data.minusDays(7).equals(args[1])) {
                        return Optional.of(semanaPassada);
                    }
                    if ("toString".equals(method.getName()) && (args == null || args.length == 0)) {
                        return "MetricasDiariasRepositoryProxy";
                    }
                    throw new UnsupportedOperationException("Método não suportado no stub: " + method);
                }
        );
    }
}
