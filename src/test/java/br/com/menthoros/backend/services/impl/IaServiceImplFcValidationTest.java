package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.exception.LLMException;
import br.com.menthoros.backend.services.helper.LlmUsageLogger;
import br.com.menthoros.backend.services.helper.ZonaTreinoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Testes de {@code validarEstrutura3Etapas} em IaServiceImpl. Os testes de validação FC por
 * zona (parseFcRange/zonaEsperadaFC/validarFcEtapa) migraram para
 * {@code EtapaFcValidatorTest} (refactor-iaservice-decomposition, seção 4).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IaServiceImpl — validarEstrutura3Etapas")
class IaServiceImplFcValidationTest {

    private IaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new IaServiceImpl(
                mock(br.com.menthoros.backend.routing.ModelRouter.class),
                mock(br.com.menthoros.backend.services.prompt.PlanoTreinoPromptBuilder.class),
                new br.com.menthoros.backend.services.prompt.LlmJsonSchemaBuilder(),
                mock(br.com.menthoros.backend.repository.AtletaRepository.class),
                mock(br.com.menthoros.backend.services.helper.RegraGeracaoTreino.class),
                mock(br.com.menthoros.backend.services.helper.TreinoHistoricoProvider.class),
                mock(br.com.menthoros.backend.services.prompt.PaceHistoricoFormatter.class),
                mock(br.com.menthoros.backend.services.helper.PaceValidator.class),
                mock(ZonaTreinoService.class),
                mock(br.com.menthoros.backend.services.quality.PlanQualityChecker.class),
                mock(br.com.menthoros.backend.services.helper.PlanoEstruturaReparador.class),
                new br.com.menthoros.backend.services.helper.TreinoNormalizador(),
                new br.com.menthoros.backend.services.helper.EtapaFcValidator(),
                mock(br.com.menthoros.backend.services.helper.PlanoResilienceService.class),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                new LlmUsageLogger(),
                mock(br.com.menthoros.backend.services.helper.PlannerShadowService.class),
                mock(br.com.menthoros.backend.services.helper.PlanoLlmLedgerHook.class)
        );
    }

    @Nested
    @DisplayName("validarEstrutura3Etapas (caracterização do hard-fail atual)")
    class Estrutura3Etapas {

        @Test
        @DisplayName("≠ 3 etapas → LLMException (hoje derruba o plano inteiro)")
        void numeroEtapasErrado() {
            var treino = treino("REGENERATIVO", etapa("AQUECIMENTO"), etapa("PRINCIPAL")); // 2 etapas
            assertThatThrownBy(() -> service.validarEstrutura3Etapas(treino, "REGENERATIVO", "atleta", true))
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("3 etapas na ordem canônica → ok")
        void ordemCanonica() {
            var treino = treino("REGENERATIVO", etapa("AQUECIMENTO"), etapa("PRINCIPAL"), etapa("DESAQUECIMENTO"));
            assertThatNoException().isThrownBy(
                    () -> service.validarEstrutura3Etapas(treino, "REGENERATIVO", "atleta", true));
        }

        @Test
        @DisplayName("fora de ordem com validarOrdem=true → LLMException")
        void ordemTrocada() {
            var treino = treino("REGENERATIVO", etapa("PRINCIPAL"), etapa("AQUECIMENTO"), etapa("DESAQUECIMENTO"));
            assertThatThrownBy(() -> service.validarEstrutura3Etapas(treino, "REGENERATIVO", "atleta", true))
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("LONGO (validarOrdem=false) checa só a contagem, não a ordem")
        void longoSoContagem() {
            var treino = treino("LONGO", etapa("PRINCIPAL"), etapa("AQUECIMENTO"), etapa("DESAQUECIMENTO"));
            assertThatNoException().isThrownBy(
                    () -> service.validarEstrutura3Etapas(treino, "LONGO", "atleta", false));
        }

        private EtapaTreinoLlmDto etapa(String tipo) {
            return new EtapaTreinoLlmDto(1, tipo, "x", 10, 1.0, null, 1, null);
        }

        private TreinoPlanejadoLlmDto treino(String tipo, EtapaTreinoLlmDto... etapas) {
            return new TreinoPlanejadoLlmDto("SEGUNDA", tipo, null, null, null, null, null, null, null, null, List.of(etapas));
        }
    }
}
