package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider.ContextoTreino;
import br.com.menthoros.backend.services.prompt.PaceHistoricoFormatter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Rede de caracterização de {@link PlanoLlmValidator#validarENormalizarPlano} — a composição
 * completa de transformações que {@code IaServiceImpl.geraPlanoSemanalAvancado} aplica à resposta
 * da LLM (expansão → normalização → validação por tipo → FC → triângulo pace×distância×duração).
 * Testar a composição, não os métodos isolados, é a rede de segurança da decomposição
 * (refactor-iaservice-decomposition, achado IA-07: cobertura isolada não detectou IA-02/03/04).
 *
 * <p>Migrado de {@code IaServiceImplCaracterizacaoTest} (seção 1, testava via reflexão o método
 * privado de {@code IaServiceImpl}) para chamada direta ao colaborador (seção 6: a composição
 * inteira migrou pra {@link PlanoLlmValidator}, {@code IaServiceImpl} virou orquestrador fino de
 * chamada LLM).</p>
 *
 * <p>Atleta sem FC cadastrada (fcLimiar/fcMaxima null) — a validação de FC por zona fica fora do
 * escopo desta rede (coberta isoladamente em {@code EtapaFcValidatorTest}, achado IA-02).</p>
 */
@DisplayName("PlanoLlmValidator — caracterização da composição completa")
class PlanoLlmValidatorCaracterizacaoTest {

    private PlanoLlmValidator validator;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        atleta = Atleta.builder()
                .id(java.util.UUID.randomUUID())
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .paceLimiar(BigDecimal.valueOf(5.0))
                .build();

        TreinoHistoricoProvider treinoHistoricoProvider = mock(TreinoHistoricoProvider.class);
        when(treinoHistoricoProvider.prepararContexto(atleta)).thenReturn(
                new ContextoTreino(LocalDate.of(2026, 9, 14), List.of(), List.of(), List.of()));

        PaceHistoricoFormatter paceHistoricoFormatter = mock(PaceHistoricoFormatter.class);
        when(paceHistoricoFormatter.calcularTetoPorTipo(any())).thenReturn(java.util.Map.of());
        when(paceHistoricoFormatter.calcularPisoPorTipo(any())).thenReturn(java.util.Map.of());

        PaceValidator paceValidatorStub = mock(PaceValidator.class);
        when(paceValidatorStub.validar(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));

        PlanoEstruturaReparador estruturaReparador = mock(PlanoEstruturaReparador.class);
        when(estruturaReparador.reparar(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        validator = new PlanoLlmValidator(
                new SimpleMeterRegistry(),
                paceValidatorStub,
                treinoHistoricoProvider,
                paceHistoricoFormatter,
                mock(ZonaTreinoService.class),
                new TreinoNormalizador(new PaceValidator()),
                new EtapaFcValidator(),
                estruturaReparador
        );
    }

    static Stream<TreinoPlanejadoLlmDto> cenarios() {
        return Stream.of(intervalado(), longo(), regenerativo());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cenarios")
    @DisplayName("plano bem-formado atravessa a composição completa sem alterar a estrutura")
    void planoBemFormadoAtravessaComposicaoSemQuebrar(TreinoPlanejadoLlmDto treino) {
        PlanoSemanalLlmDto plano = new PlanoSemanalLlmDto(
                30.0, 30.0, null, null, "ATIVO", "base aeróbica", List.of(treino));

        PlanoSemanalLlmDto validado = validator.validarENormalizarPlano(plano, atleta, atleta.getId());

        assertThat(validado.treinosPlanejados()).hasSize(1);
        TreinoPlanejadoLlmDto resultado = validado.treinosPlanejados().get(0);
        assertThat(resultado.tipoTreino()).isEqualTo(treino.tipoTreino());
        assertThat(resultado.etapas()).hasSameSizeAs(treino.etapas());
    }

    private static TreinoPlanejadoLlmDto intervalado() {
        return new TreinoPlanejadoLlmDto(
                "SEGUNDA", "INTERVALADO", "150-160 bpm", 60, 1.1, 8,
                "Desenvolver VO2max", "45:00", 5.3, "4:30-5:00/km",
                List.of(
                        new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento leve Z1-Z2", 10, 1.5, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(2, "INTERVALADO", "Tiro 1 - Z5", 4, 1.0, "160-170 bpm", 1, "4:00-4:15/km"),
                        new EtapaTreinoLlmDto(3, "RECUPERACAO", "Recuperação 1 - trote Z2", 2, 0.4, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(4, "INTERVALADO", "Tiro 2 - Z5", 4, 1.0, "160-170 bpm", 1, "4:00-4:15/km"),
                        new EtapaTreinoLlmDto(5, "RECUPERACAO", "Recuperação 2 - trote Z2", 2, 0.4, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(6, "DESAQUECIMENTO", "Desaquecimento leve Z1", 5, 1.0, "120-136 bpm", 1, null)
                ));
    }

    private static TreinoPlanejadoLlmDto longo() {
        return new TreinoPlanejadoLlmDto(
                "SABADO", "LONGO", "136-150 bpm", 90, 0.85, 6,
                "Construir base aeróbica", "75:00", 15.0, "5:30-6:00/km",
                List.of(
                        new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento leve Z1-Z2", 10, 1.5, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(2, "PRINCIPAL", "Longo contínuo Z2-Z3", 60, 12.0, "136-150 bpm", 1, "5:30-6:00/km"),
                        new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Desaquecimento leve Z1", 5, 1.5, "120-136 bpm", 1, null)
                ));
    }

    private static TreinoPlanejadoLlmDto regenerativo() {
        return new TreinoPlanejadoLlmDto(
                "QUARTA", "REGENERATIVO", "115-130 bpm", 25, 0.6, 3,
                "Recuperação ativa", "35:00", 5.0, "6:30-7:00/km",
                List.of(
                        new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento muito leve", 5, 0.7, "115-130 bpm", 1, null),
                        new EtapaTreinoLlmDto(2, "PRINCIPAL", "Trote regenerativo Z1", 25, 3.6, "115-130 bpm", 1, "6:30-7:00/km"),
                        new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Desaquecimento leve", 5, 0.7, "115-130 bpm", 1, null)
                ));
    }
}
