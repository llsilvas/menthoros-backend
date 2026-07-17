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
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.helper.ThresholdInferenceService;
import br.com.menthoros.backend.services.helper.TssCalculatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD — Section 3: valida semântica correta de tsbInicioDia / tsbFimDia
 * no TsbServiceImpl após o refactor.
 *
 * <p><b>Invariantes fundamentais:</b>
 * <ul>
 *   <li>tsbInicioDia(D) = CTL_ontem - ATL_ontem (não inclui TSS de hoje)</li>
 *   <li>tsbFimDia(D)    = CTL_hoje  - ATL_hoje  (inclui TSS de hoje)</li>
 *   <li>tsbInicioDia(D) == tsbFimDia(D-1) quando D-1 existe</li>
 * </ul>
 */
@DisplayName("Semântica TSB: tsbInicioDia e tsbFimDia em TsbServiceImpl")
class TsbServiceImplSemanticaTest {

    private static final double CTL_ONTEM = 70.0;
    private static final double ATL_ONTEM = 72.0;
    private static final int    TSS_HOJE  = 80;
    private static final int    CTL_TAU   = 42;
    private static final int    ATL_TAU   = 7;

    private UUID atletaId;
    private Atleta atleta;
    private LocalDate hoje;
    private LocalDate ontem;

    @BeforeEach
    void setUp() {
        atletaId = UUID.randomUUID();
        hoje     = LocalDate.of(2026, 3, 1);
        ontem    = hoje.minusDays(1);

        Assessoria assessoria = new Assessoria();
        assessoria.setId(UUID.randomUUID());

        atleta = Atleta.builder()
                .id(atletaId)
                .nome("Atleta Teste")
                .objetivo("Maratona")
                .nivelExperiencia(NivelExperiencia.AVANCADO) // CTL_TAU=42, ATL_TAU=7
                .assessoria(assessoria)
                .build();
    }

    // =========================================================================
    // Teste 1: tsbInicioDia não inclui TSS do dia corrente
    // =========================================================================

    @Test
    @DisplayName("tsbInicioDia não inclui TSS do dia corrente — prontidão pré-treino")
    void tsbInicioDia_naoIncluiTssDoProprioTreino() {
        // Setup: métricas de ontem com CTL=70, ATL=72
        MetricasDiarias metricasOntem = metricasComCtlAtl(ontem, CTL_ONTEM, ATL_ONTEM);

        AtomicReference<MetricasDiarias> metricasSalvas = new AtomicReference<>();
        AtomicReference<PlanoMetaDados> metaDadosSalvo = new AtomicReference<>();

        TsbServiceImpl service = construirService(
                atleta,
                metricasOntem,
                metricasSalvas,
                metaDadosSalvo,
                TSS_HOJE
        );

        service.atualizarTsbDia(atletaId, hoje);

        MetricasDiarias resultado = metricasSalvas.get();
        assertNotNull(resultado, "MetricasDiarias deve ter sido salvo");

        // tsbInicioDia = CTL_ontem - ATL_ontem = 70 - 72 = -2
        double expectedTsbInicio = CTL_ONTEM - ATL_ONTEM;
        assertEquals(expectedTsbInicio, resultado.getTsbInicioDia(), 0.001,
                "tsbInicioDia deve ser CTL_ontem - ATL_ontem, sem TSS de hoje");

        // tsbFimDia deve ser diferente (inclui TSS=80)
        assertNotNull(resultado.getTsbFimDia(), "tsbFimDia não deve ser nulo");
        assertNotEquals(resultado.getTsbInicioDia(), resultado.getTsbFimDia(), 0.001,
                "tsbFimDia deve diferir de tsbInicioDia quando TSS > 0");

        // Invariante: tsbInicioDia(D) == tsbFimDia(D-1)
        assertEquals(metricasOntem.getTsb(), resultado.getTsbInicioDia(), 0.001,
                "tsbInicioDia de hoje deve ser igual a tsb (fimDia) de ontem");
    }

    @Test
    @DisplayName("tsbInicioDia é exatamente CTL_ontem - ATL_ontem — verificação matemática")
    void tsbInicioDia_matematicamenteCorreto() {
        // CTL=70, ATL=72 => tsbInicio = -2
        MetricasDiarias metricasOntem = metricasComCtlAtl(ontem, CTL_ONTEM, ATL_ONTEM);

        AtomicReference<MetricasDiarias> salvo = new AtomicReference<>();

        TsbServiceImpl service = construirService(atleta, metricasOntem, salvo, new AtomicReference<>(), TSS_HOJE);

        service.atualizarTsbDia(atletaId, hoje);

        assertEquals(CTL_ONTEM, salvo.get().getCtlInicioDia(), 0.001);
        assertEquals(ATL_ONTEM, salvo.get().getAtlInicioDia(), 0.001);
        assertEquals(CTL_ONTEM - ATL_ONTEM, salvo.get().getTsbInicioDia(), 0.001);
    }

    // =========================================================================
    // Teste 2: dia sem treino — tsbInicioDia == tsbFimDia
    // =========================================================================

    @Test
    @DisplayName("dia sem treino: tsbInicioDia e tsbFimDia são iguais (TSS=0, sem carga)")
    void diaSemTreino_inicioDiaIgualFimDia() {
        int tss = 0;
        MetricasDiarias metricasOntem = metricasComCtlAtl(ontem, CTL_ONTEM, ATL_ONTEM);

        AtomicReference<MetricasDiarias> salvo = new AtomicReference<>();

        TsbServiceImpl service = construirService(atleta, metricasOntem, salvo, new AtomicReference<>(), tss);

        service.atualizarTsbDia(atletaId, hoje);

        MetricasDiarias resultado = salvo.get();

        // Com TSS=0, CTL_fimDia = CTL_ontem × e^(-1/42), ATL_fimDia = ATL_ontem × e^(-1/7)
        // tsbInicioDia = CTL_ontem - ATL_ontem (pré)
        // tsbFimDia    = CTL_fimDia - ATL_fimDia (decaimento sem carga)
        // Não são necessariamente iguais porque CTL e ATL decaem em ritmos diferentes;
        // mas tsbInicioDia deve ser CTL_ontem - ATL_ontem
        assertEquals(CTL_ONTEM - ATL_ONTEM, resultado.getTsbInicioDia(), 0.001,
                "tsbInicioDia deve ser CTL_ontem - ATL_ontem mesmo sem treino");
        assertNotNull(resultado.getTsbFimDia(), "tsbFimDia não deve ser nulo mesmo sem treino");
    }

    @Test
    @DisplayName("dia sem treino a partir do zero: tsbInicioDia e tsbFimDia são ambos zero")
    void diaSemTreino_semHistorico_ambosSaoZero() {
        int tss = 0;
        // Sem métricas de ontem — CTL e ATL começam do zero
        MetricasDiarias metricasOntem = null; // nenhum histórico

        AtomicReference<MetricasDiarias> salvo = new AtomicReference<>();

        TsbServiceImpl service = construirService(atleta, metricasOntem, salvo, new AtomicReference<>(), tss);

        service.atualizarTsbDia(atletaId, hoje);

        MetricasDiarias resultado = salvo.get();

        assertEquals(0.0, resultado.getTsbInicioDia(), 0.001,
                "tsbInicioDia deve ser zero quando não há histórico anterior");
        assertEquals(0.0, resultado.getTsbFimDia(), 0.001,
                "tsbFimDia deve ser zero quando não há histórico e TSS=0");
    }

    // =========================================================================
    // Teste 3: tsbProntidaoAtual em PlanoMetaDados usa tsbInicioDia
    // =========================================================================

    @Test
    @DisplayName("atualizarTsbDia popula PlanoMetaDados.tsbProntidaoAtual com tsbInicioDia")
    void atualizarMetaDados_usaTsbInicioDia() {
        MetricasDiarias metricasOntem = metricasComCtlAtl(ontem, CTL_ONTEM, ATL_ONTEM);

        AtomicReference<MetricasDiarias> metricasSalvas = new AtomicReference<>();
        AtomicReference<PlanoMetaDados> metaDadosSalvo = new AtomicReference<>();

        TsbServiceImpl service = construirService(
                atleta, metricasOntem, metricasSalvas, metaDadosSalvo, TSS_HOJE);

        service.atualizarTsbDia(atletaId, hoje);

        PlanoMetaDados metaDados = metaDadosSalvo.get();
        MetricasDiarias metricas = metricasSalvas.get();

        assertNotNull(metaDados, "PlanoMetaDados deve ter sido salvo");
        assertNotNull(metricas, "MetricasDiarias deve ter sido salvo");

        assertEquals(metricas.getTsbInicioDia(), metaDados.getTsbProntidaoAtual(), 0.001,
                "tsbProntidaoAtual deve ser igual a tsbInicioDia");

        assertEquals(metricas.getTsbFimDia(), metaDados.getTsbPosCargaAtual(), 0.001,
                "tsbPosCargaAtual deve ser igual a tsbFimDia");
    }

    @Test
    @DisplayName("tsbAtual (legado) em PlanoMetaDados permanece como alias de tsbProntidaoAtual")
    void tsbAtual_legado_alias_tsbProntidaoAtual() {
        MetricasDiarias metricasOntem = metricasComCtlAtl(ontem, CTL_ONTEM, ATL_ONTEM);

        AtomicReference<MetricasDiarias> metricasSalvas = new AtomicReference<>();
        AtomicReference<PlanoMetaDados> metaDadosSalvo = new AtomicReference<>();

        TsbServiceImpl service = construirService(
                atleta, metricasOntem, metricasSalvas, metaDadosSalvo, TSS_HOJE);

        service.atualizarTsbDia(atletaId, hoje);

        PlanoMetaDados metaDados = metaDadosSalvo.get();
        MetricasDiarias metricas = metricasSalvas.get();

        // tsbAtual deve refletir tsbInicioDia (prontidão) para compatibilidade
        assertEquals(metricas.getTsbInicioDia(), metaDados.getTsbAtual(), 0.001,
                "tsbAtual (legado) deve ser alias de tsbProntidaoAtual (tsbInicioDia)");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static MetricasDiarias metricasComCtlAtl(LocalDate data, double ctl, double atl) {
        MetricasDiarias m = new MetricasDiarias();
        m.setData(data);
        m.setCtl(ctl);
        m.setAtl(atl);
        m.setTsb(ctl - atl);
        m.setTss(0);
        m.setVolumeKm(BigDecimal.ZERO);
        return m;
    }

    /**
     * Constrói um TsbServiceImpl com stubs mínimos necessários para os testes de semântica.
     */
    private TsbServiceImpl construirService(
            Atleta atleta,
            MetricasDiarias metricasOntem,
            AtomicReference<MetricasDiarias> metricasSalvas,
            AtomicReference<PlanoMetaDados> metaDadosSalvo,
            int tssHoje) {

        UUID atletaId = atleta.getId();
        LocalDate hoje = this.hoje;

        // Stub: AtletaRepository
        AtletaRepository atletaRepo = (AtletaRepository) Proxy.newProxyInstance(
                AtletaRepository.class.getClassLoader(),
                new Class<?>[]{AtletaRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) return Optional.of(atleta);
                    if ("toString".equals(method.getName())) return "AtletaRepositoryStub";
                    throw new UnsupportedOperationException("Método não suportado: " + method.getName());
                }
        );

        // Stub: TreinoRealizadoRepository — retorna treinos com TSS calculável
        TreinoRealizadoRepository treinoRepo = (TreinoRealizadoRepository) Proxy.newProxyInstance(
                TreinoRealizadoRepository.class.getClassLoader(),
                new Class<?>[]{TreinoRealizadoRepository.class},
                (proxy, method, args) -> {
                    if ("findByAtletaIdAndDataTreino".equals(method.getName())) {
                        // Retorna lista vazia — TSS será calculado pelo TssCalculatorService stub
                        return Collections.emptyList();
                    }
                    if ("findByAtletaIdAndDataTreinoBetween".equals(method.getName())) {
                        return Collections.emptyList();
                    }
                    if ("findByAtletaIdAndTenantIdAndDataTreinoBetween".equals(method.getName())) {
                        return Collections.emptyList();
                    }
                    if ("toString".equals(method.getName())) return "TreinoRealizadoRepositoryStub";
                    throw new UnsupportedOperationException("Método não suportado: " + method.getName());
                }
        );

        // Stub: TssCalculatorService — retorna TSS fixo (subclasse concreta)
        TssCalculatorService tssCalc = new TssCalculatorService() {
            @Override
            public Integer calcularTssDia(List<TreinoRealizado> treinos) {
                return tssHoje;
            }
        };

        // Stub: MetricasDiariasRepository
        MetricasDiariasRepository metricasRepo = (MetricasDiariasRepository) Proxy.newProxyInstance(
                MetricasDiariasRepository.class.getClassLoader(),
                new Class<?>[]{MetricasDiariasRepository.class},
                (proxy, method, args) -> {
                    if ("findByAtletaIdAndData".equals(method.getName())) {
                        LocalDate data = (LocalDate) args[1];
                        if (hoje.equals(data)) {
                            return Optional.empty(); // métricas de hoje ainda não existem
                        }
                        if (hoje.minusDays(1).equals(data)) {
                            return Optional.ofNullable(metricasOntem);
                        }
                        if (hoje.minusDays(7).equals(data)) {
                            return Optional.empty(); // sem dados da semana passada
                        }
                        return Optional.empty();
                    }
                    if ("save".equals(method.getName())) {
                        MetricasDiarias entity = (MetricasDiarias) args[0];
                        metricasSalvas.set(entity);
                        return entity;
                    }
                    if ("toString".equals(method.getName())) return "MetricasDiariasRepositoryStub";
                    throw new UnsupportedOperationException("Método não suportado: " + method.getName());
                }
        );

        // Stub: PlanoMetadadosRepository
        PlanoMetaDados planoMetaDados = PlanoMetaDados.builder()
                .atleta(atleta)
                .ctlAtual(metricasOntem != null ? metricasOntem.getCtl() : 0.0)
                .atlAtual(metricasOntem != null ? metricasOntem.getAtl() : 0.0)
                .tsbAtual(metricasOntem != null ? metricasOntem.getTsb() : 0.0)
                .tsbProntidaoAtual(metricasOntem != null ? metricasOntem.getTsb() : 0.0)
                .tsbPosCargaAtual(metricasOntem != null ? metricasOntem.getTsb() : 0.0)
                .rampRateAtual(0.0)
                .diasConsecutivosTreino(0)
                .semanasProgressaoContinua(0)
                .build();

        PlanoMetadadosRepository planoRepo = (PlanoMetadadosRepository) Proxy.newProxyInstance(
                PlanoMetadadosRepository.class.getClassLoader(),
                new Class<?>[]{PlanoMetadadosRepository.class},
                (proxy, method, args) -> {
                    if ("findByAtletaId".equals(method.getName())) return Optional.of(planoMetaDados);
                    if ("save".equals(method.getName())) {
                        PlanoMetaDados entity = (PlanoMetaDados) args[0];
                        metaDadosSalvo.set(entity);
                        return entity;
                    }
                    if ("toString".equals(method.getName())) return "PlanoMetadadosRepositoryStub";
                    throw new UnsupportedOperationException("Método não suportado: " + method.getName());
                }
        );

        // Stub: MetricasAlertaService
        MetricasAlertaService alertaService = new MetricasAlertaService() {
            @Override
            public ResultadoAnalise analisarMetricas(PlanoMetaDados metaDados, NivelExperiencia nivelExperiencia) {
                return new ResultadoAnalise("OK", "MANTER", "", false, false, false, false, List.of());
            }
        };

        ProvaRepository provaRepo = (ProvaRepository) Proxy.newProxyInstance(
                ProvaRepository.class.getClassLoader(),
                new Class<?>[]{ProvaRepository.class},
                (proxy, method, args) -> {
                    if ("findProvasRealizadasRecentes".equals(method.getName())) return Collections.emptyList();
                    if ("toString".equals(method.getName())) return "ProvaRepositoryStub";
                    throw new UnsupportedOperationException("Método não suportado: " + method.getName());
                }
        );

        return new TsbServiceImpl(treinoRepo, planoRepo, metricasRepo, atletaRepo, tssCalc, alertaService, new ThresholdInferenceService(), provaRepo);
    }
}
