package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.testsupport.TsbRecalculoExecutorInline;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testes para ISSUE-06: diasConsecutivosTreino deve ser calculado
 * em tempo real antes da análise de alertas.
 *
 * <p>Valida o método {@code contarDiasConsecutivosTreino(UUID, LocalDate, boolean)}
 * que foi adicionado em {@link TsbServiceImpl}.
 */
class TsbServiceImplDiasConsecutivosTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 2, 16);

    @Test
    void deveRetornarZeroQuandoHojeNaoTemTreino() throws Exception {
        var service = criarService(Set.of());

        int result = invocarContar(service, HOJE, false);

        assertEquals(0, result);
    }

    @Test
    void deveRetornarUmQuandoSomenteHojeTemTreino() throws Exception {
        // Hoje tem treino, ontem não
        var service = criarService(Set.of());

        int result = invocarContar(service, HOJE, true);

        assertEquals(1, result);
    }

    @Test
    void deveRetornarDoisQuandoHojeEOntemTemTreino() throws Exception {
        // Ontem (dia 15) tem treino, dia 14 não
        var service = criarService(Set.of(HOJE.minusDays(1)));

        int result = invocarContar(service, HOJE, true);

        assertEquals(2, result);
    }

    @Test
    void deveRetornarCincoParaCincoDiasConsecutivos() throws Exception {
        // Hoje + 4 dias anteriores = 5 consecutivos
        var diasComTreino = Set.of(
                HOJE.minusDays(1),
                HOJE.minusDays(2),
                HOJE.minusDays(3),
                HOJE.minusDays(4)
        );
        var service = criarService(diasComTreino);

        int result = invocarContar(service, HOJE, true);

        assertEquals(5, result);
    }

    @Test
    void deveRetornarSeisParaAlertaCritico() throws Exception {
        // 6 dias consecutivos = threshold DIAS_CONSECUTIVOS_CRITICO
        var diasComTreino = Set.of(
                HOJE.minusDays(1),
                HOJE.minusDays(2),
                HOJE.minusDays(3),
                HOJE.minusDays(4),
                HOJE.minusDays(5)
        );
        var service = criarService(diasComTreino);

        int result = invocarContar(service, HOJE, true);

        assertEquals(6, result);
    }

    @Test
    void deveInterromperContagemNoDiaDeDescanso() throws Exception {
        // Hoje, ontem, anteontem = treino. 3 dias atrás = descanso. 4 dias atrás = treino.
        // Esperado: 3 (não 4, porque o gap interrompe)
        var diasComTreino = Set.of(
                HOJE.minusDays(1),
                HOJE.minusDays(2),
                // dia -3 sem treino (gap)
                HOJE.minusDays(4)
        );
        var service = criarService(diasComTreino);

        int result = invocarContar(service, HOJE, true);

        assertEquals(3, result);
    }

    @Test
    void deveRespeitarLimiteDeQuatorzeDias() throws Exception {
        // 20 dias consecutivos: o método deve parar em 15 (hoje + 14 lookback)
        var diasComTreino = new java.util.HashSet<LocalDate>();
        for (int i = 1; i <= 20; i++) {
            diasComTreino.add(HOJE.minusDays(i));
        }
        var service = criarService(diasComTreino);

        int result = invocarContar(service, HOJE, true);

        assertEquals(15, result); // 1 (hoje) + 14 (max lookback)
    }

    // ===== Helpers =====

    private TsbServiceImpl criarService(Set<LocalDate> diasComTreino) {
        TreinoRealizadoRepository repo = (TreinoRealizadoRepository) Proxy.newProxyInstance(
                TreinoRealizadoRepository.class.getClassLoader(),
                new Class<?>[]{TreinoRealizadoRepository.class},
                (proxy, method, args) -> {
                    if ("findByAtletaIdAndDataTreino".equals(method.getName())
                            && args != null && args.length == 2) {
                        LocalDate data = (LocalDate) args[1];
                        if (diasComTreino.contains(data)) {
                            return List.of(new TreinoRealizado());
                        }
                        return Collections.emptyList();
                    }
                    if ("findByAtletaIdAndDataTreinoBetween".equals(method.getName())
                            && args != null && args.length == 3) {
                        LocalDate inicio = (LocalDate) args[1];
                        LocalDate fim = (LocalDate) args[2];
                        return diasComTreino.stream()
                                .filter(data -> !data.isBefore(inicio) && !data.isAfter(fim))
                                .map(data -> {
                                    TreinoRealizado treino = new TreinoRealizado();
                                    treino.setDataTreino(data);
                                    return treino;
                                })
                                .toList();
                    }
                    if ("findByAtletaIdAndTenantIdAndDataTreinoBetween".equals(method.getName())) {
                        return Collections.emptyList();
                    }
                    if ("toString".equals(method.getName())) {
                        return "TreinoRealizadoRepositoryProxy";
                    }
                    throw new UnsupportedOperationException("Método não suportado: " + method);
                }
        );

        return new TsbServiceImpl(repo, null, null, null, null, null, null,
                new TsbRecalculoExecutorInline());
    }

    private int invocarContar(TsbServiceImpl service, LocalDate data, boolean hojeTemTreino) throws Exception {
        Method m = TsbServiceImpl.class.getDeclaredMethod(
                "contarDiasConsecutivosTreino", UUID.class, LocalDate.class, boolean.class);
        m.setAccessible(true);
        return (int) m.invoke(service, UUID.randomUUID(), data, hojeTemTreino);
    }
}
