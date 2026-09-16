package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.compliance.SchemaVersion;
import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.dto.llm.v2.BlocoDto;
import br.com.menthoros.backend.dto.llm.v2.Papel;
import br.com.menthoros.backend.dto.llm.v2.PlanoSemanalLlmDtoV2;
import br.com.menthoros.backend.dto.llm.v2.TreinoPlanejadoLlmDtoV2;
import br.com.menthoros.backend.dto.llm.v2.UnidadeQuantidade;
import br.com.menthoros.backend.dto.llm.v2.Zona;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.services.prompt.constraint.Constraint;
import br.com.menthoros.backend.services.quality.PlanQualityChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("EvalDeterministicGrader")
class EvalDeterministicGraderTest {

    @Mock
    private PlannerShadowService plannerShadowService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SessionResolver sessionResolver =
            new SessionResolver(new ZoneResolver(new ZonaTreinoService()), new TssCalculatorService());
    private final PlanQualityChecker checkerDireto = new PlanQualityChecker(new SimpleMeterRegistry());

    private EvalDeterministicGrader grader;

    @BeforeEach
    void setUp() {
        grader = new EvalDeterministicGrader(objectMapper, sessionResolver, plannerShadowService);
    }

    @Nested
    @DisplayName("avaliar")
    class Avaliar {

        @Test
        @DisplayName("v1 — mesmas violações que chamada direta a PlanQualityChecker.check")
        void v1MesmasViolacoesQueChamadaDireta() throws Exception {
            Constraint constraintDias = Constraint.diasPermitidos("só segunda", List.of(DiaSemana.SEGUNDA));
            EtapaTreinoLlmDto etapa = new EtapaTreinoLlmDto(1, "AQUECIMENTO", "aquecimento", 10, 1.0,
                    "107-121 bpm", 1, "6:00/km");
            TreinoPlanejadoLlmDto treino = new TreinoPlanejadoLlmDto("TERCA", "CONTINUO", "121-133 bpm",
                    40, 0.8, 5, "justificativa", "44:00", 7.0, "5:30/km", List.of(etapa));
            PlanoSemanalLlmDto plano = new PlanoSemanalLlmDto(20.0, 20.0, 5.0, 5.0, "PLANEJADO",
                    "objetivo", List.of(treino));
            String json = objectMapper.writeValueAsString(plano);

            var resultado = grader.avaliar(json, SchemaVersion.CURRENT, List.of(constraintDias), null,
                    new AthleteZones(190, 160, BigDecimal.valueOf(5.0)), new Atleta(), LocalDate.now());

            var esperado = checkerDireto.check(plano, List.of(constraintDias));
            assertThat(resultado.violacoesQualidade()).isEqualTo(esperado);
            assertThat(resultado.violacoesCompliance()).isEmpty();
            verifyNoInteractions(plannerShadowService);
        }

        @Test
        @DisplayName("v2 — resolve via SessionResolver antes de checar, mesmas violações que a chamada direta")
        void v2ResolveViaSessionResolverAntesDeChecar() throws Exception {
            BlocoDto bloco = new BlocoDto(Papel.PRINCIPAL, 1, BigDecimal.valueOf(30), UnidadeQuantidade.MIN,
                    Zona.Z2, null);
            TreinoPlanejadoLlmDtoV2 treinoV2 = new TreinoPlanejadoLlmDtoV2("TERCA", "CONTINUO",
                    "justificativa", List.of(bloco));
            PlanoSemanalLlmDtoV2 planoV2 = PlanoSemanalLlmDtoV2.builder()
                    .volumePlanejadoKm(20.0).volumeAlvoKm(20.0).tsbInicio(5.0).tsbFim(5.0)
                    .status("PLANEJADO").objetivoSemanal("objetivo")
                    .treinosPlanejados(List.of(treinoV2)).build();
            String json = objectMapper.writeValueAsString(planoV2);
            AthleteZones zonas = new AthleteZones(190, 160, BigDecimal.valueOf(5.0));

            Constraint constraintDias = Constraint.diasPermitidos("só segunda", List.of(DiaSemana.SEGUNDA));

            var resultado = grader.avaliar(json, SchemaVersion.V2, List.of(constraintDias), null, zonas,
                    new Atleta(), LocalDate.now());

            PlanoSemanalLlmDto planoResolvidoEsperado = sessionResolver.resolverPlano(planoV2, zonas);
            var esperado = checkerDireto.check(planoResolvidoEsperado, List.of(constraintDias));
            assertThat(resultado.violacoesQualidade()).isEqualTo(esperado);
        }

        @Test
        @DisplayName("sem skeleton — não chama PlannerShadowService (compliance vazio)")
        void semSkeletonNaoChamaPlannerShadowService() throws Exception {
            PlanoSemanalLlmDto plano = new PlanoSemanalLlmDto(20.0, 20.0, 5.0, 5.0, "PLANEJADO",
                    "objetivo", List.of());
            String json = objectMapper.writeValueAsString(plano);

            var resultado = grader.avaliar(json, SchemaVersion.CURRENT, List.of(), null,
                    new AthleteZones(190, 160, BigDecimal.valueOf(5.0)), new Atleta(), LocalDate.now());

            assertThat(resultado.violacoesCompliance()).isEmpty();
            verify(plannerShadowService, never()).checkPreRedistribution(
                    ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
        }

        @Test
        @DisplayName("JSON inválido lança IllegalArgumentException, não deixa passar em silêncio")
        void jsonInvalidoLanca() {
            assertThat(catchThrowable(() ->
                    grader.avaliar("{ isso não é json", SchemaVersion.CURRENT, List.of(), null,
                            new AthleteZones(190, 160, BigDecimal.valueOf(5.0)), new Atleta(), LocalDate.now())))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
