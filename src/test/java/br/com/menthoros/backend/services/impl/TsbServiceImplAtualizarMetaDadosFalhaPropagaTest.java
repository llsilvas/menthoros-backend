package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.ResultadoAnalise;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.PlanoMetadadosService;
import br.com.menthoros.backend.services.helper.AthleteThresholdUpdater;
import br.com.menthoros.backend.services.helper.ThresholdInferenceService;
import br.com.menthoros.backend.testsupport.ProvaRepositoryTestStub;
import br.com.menthoros.backend.testsupport.TsbRecalculoExecutorInline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Prova, no nível de unidade, a garantia de atomicidade citada em
 * {@code fix-progressao-continua-incremental} (CA1/Riscos): uma falha dentro de
 * {@code recalcularSemanasProgressao} (agora chamado por {@code atualizarMetaDados}, no caminho
 * incremental) propaga sem ser capturada — nada no código novo engole a exceção
 * silenciosamente. A reversão real da transação é responsabilidade do Spring
 * (`@Transactional` em cima de {@code recalcularDesde}), não testada aqui — esse nível é coberto
 * por {@code TsbServiceProgressaoContinuaIT} (CA1).
 */
class TsbServiceImplAtualizarMetaDadosFalhaPropagaTest {

    @Test
    @DisplayName("recalcularDesde propaga exceção de recalcularSemanasProgressao sem capturá-la")
    void recalcularDesde_falhaNoRecalculoDoStreak_propaga() {
        UUID atletaId = UUID.randomUUID();

        Assessoria assessoria = new Assessoria();
        assessoria.setId(UUID.randomUUID());

        Atleta atleta = Atleta.builder()
                .id(atletaId)
                .nome("Atleta Falha Streak")
                .objetivo("Teste")
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .assessoria(assessoria)
                .build();

        LocalDate hoje = LocalDate.now();

        TreinoRealizadoRepository treinoRepo = (TreinoRealizadoRepository) Proxy.newProxyInstance(
                TreinoRealizadoRepository.class.getClassLoader(),
                new Class<?>[]{TreinoRealizadoRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findQueContamByAtletaIdAndDataTreino".equals(name)) return Collections.emptyList();
                    if ("findByAtletaIdAndTenantIdAndDataTreinoBetween".equals(name)) return Collections.emptyList();
                    if ("findByAtletaIdAndDataTreinoBetween".equals(name)) return Collections.emptyList();
                    if ("toString".equals(name)) return "TreinoRealizadoRepositoryStub";
                    throw new UnsupportedOperationException("Método não suportado: " + name);
                }
        );

        MetricasDiariasRepository metricasRepo = (MetricasDiariasRepository) Proxy.newProxyInstance(
                MetricasDiariasRepository.class.getClassLoader(),
                new Class<?>[]{MetricasDiariasRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findDataUltimaMetrica".equals(name)) return null; // fim = hoje
                    if ("findByAtletaIdAndData".equals(name)) return Optional.empty();
                    if ("save".equals(name)) return args[0];
                    if ("toString".equals(name)) return "MetricasDiariasRepositoryStub";
                    throw new UnsupportedOperationException("Método não suportado: " + name);
                }
        );

        AtletaRepository atletaRepo = (AtletaRepository) Proxy.newProxyInstance(
                AtletaRepository.class.getClassLoader(),
                new Class<?>[]{AtletaRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) return Optional.of(atleta);
                    if ("toString".equals(method.getName())) return "AtletaRepositoryStub";
                    throw new UnsupportedOperationException("Método não suportado: " + method.getName());
                }
        );

        PlanoMetaDados metaDados = PlanoMetaDados.builder()
                .atleta(atleta)
                .semanasProgressaoContinua(0)
                .build();

        PlanoMetadadosService planoMetadadosService = new PlanoMetadadosService() {
            @Override
            public PlanoMetaDados buscarOuCriarMetadados(Atleta a) {
                return metaDados;
            }

            @Override
            public PlanoMetaDados buscarPorAtletaId(UUID id) {
                return metaDados;
            }
        };

        RuntimeException falhaEsperada = new RuntimeException("falha simulada no recálculo do streak");

        PlanoMetadadosRepository planoRepo = (PlanoMetadadosRepository) Proxy.newProxyInstance(
                PlanoMetadadosRepository.class.getClassLoader(),
                new Class<?>[]{PlanoMetadadosRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    // save (chamado dentro de atualizarMetaDados, antes do streak) segue normal;
                    // findByAtletaId (usado só por recalcularSemanasProgressao) falha.
                    if ("save".equals(name)) return args[0];
                    if ("findByAtletaId".equals(name)) throw falhaEsperada;
                    if ("toString".equals(name)) return "PlanoMetadadosRepositoryStub";
                    throw new UnsupportedOperationException("Método não suportado: " + name);
                }
        );

        MetricasAlertaService alertaServiceStub = new MetricasAlertaService() {
            @Override
            public ResultadoAnalise analisarMetricas(PlanoMetaDados md, NivelExperiencia nivel) {
                return new ResultadoAnalise("OK", "MANTER", "", false, false, false, false, List.of());
            }
        };

        TsbServiceImpl service = new TsbServiceImpl(
                treinoRepo,
                planoRepo,
                metricasRepo,
                atletaRepo,
                alertaServiceStub,
                new AthleteThresholdUpdater(treinoRepo, ProvaRepositoryTestStub.semProvas(), new ThresholdInferenceService()),
                new TsbRecalculoExecutorInline(),
                planoMetadadosService
        );

        RuntimeException propagada = assertThrows(RuntimeException.class,
                () -> service.recalcularDesde(atletaId, hoje));
        org.junit.jupiter.api.Assertions.assertSame(falhaEsperada, propagada,
                "a exceção de recalcularSemanasProgressao deve propagar sem ser capturada/trocada");
    }
}
