package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.services.helper.PlanoResilienceService.ChamadaLlm;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider.ContextoTreino;
import br.com.menthoros.backend.services.prompt.PaceHistoricoFormatter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code plan-generation-repair-turn}, task 4.4 — revalidação íntegra do turno de reparo. Composição
 * real de {@link PlanoResilienceService} + {@link PlanoLlmValidator} (sem {@code IaServiceImpl}, que
 * exigiria fixtures de prompt/skeleton inviáveis em unit test): a 2ª resposta da LLM corrige o
 * treino apontado na violação da 1ª, mas quebra um treino <b>diferente</b> que passava antes — a
 * revalidação roda sobre o plano inteiro devolvido pela 2ª tentativa, sem assumir que só o treino
 * apontado mudou, e rejeita de novo.
 */
@DisplayName("Turno de reparo — revalidação íntegra (não assume que só o treino apontado mudou)")
class RepairTurnRevalidationIntegrationTest {

    private static final UUID ATLETA_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private Atleta atleta;
    private PlanoResilienceService resilience;
    private PlanoLlmValidator validador;

    @BeforeEach
    void setUp() {
        atleta = Atleta.builder()
                .id(ATLETA_ID)
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .paceLimiar(BigDecimal.valueOf(5.0))
                .build();
        resilience = new PlanoResilienceService(new SimpleMeterRegistry());

        TreinoHistoricoProvider treinoHistoricoProvider = mock(TreinoHistoricoProvider.class);
        when(treinoHistoricoProvider.prepararContexto(atleta)).thenReturn(
                new ContextoTreino(LocalDate.of(2026, 9, 14), List.of(), List.of(), List.of()));
        PaceHistoricoFormatter paceHistoricoFormatter = mock(PaceHistoricoFormatter.class);
        when(paceHistoricoFormatter.calcularTetoPorTipo(any())).thenReturn(java.util.Map.of());
        when(paceHistoricoFormatter.calcularPisoPorTipo(any())).thenReturn(java.util.Map.of());

        validador = new PlanoLlmValidator(treinoHistoricoProvider, paceHistoricoFormatter,
                mock(ZonaTreinoService.class),
                new NormalizacaoDeTreino(
                        new TreinoNormalizador(new PaceValidator()),
                        new EtapaFcValidator(),
                        new PlanoEstruturaReparador(new SimpleMeterRegistry()),
                        new PaceValidator(),
                        new SimpleMeterRegistry()),
                new WeeklyCoverageValidator(), new LongRunAnchor());
    }

    @Test
    @DisplayName("2ª resposta corrige SEGUNDA mas quebra QUINTA de um jeito novo → validar lança de novo, falha final é 422")
    void segundaRespostaQuebraOutroTreino() {
        // 1ª tentativa: SEGUNDA inválida (repeticoes=2), QUINTA válida.
        PlanoSemanalLlmDto primeiraResposta = plano(treinoInvalido("SEGUNDA"), treinoValido("QUINTA"));
        // 2ª tentativa (turno de reparo): SEGUNDA corrigida, mas agora é QUINTA que quebra —
        // se a revalidação assumisse "só SEGUNDA mudou", este plano passaria sem checar QUINTA.
        PlanoSemanalLlmDto segundaResposta = plano(treinoValido("SEGUNDA"), treinoInvalido("QUINTA"));
        int[] chamadas = {0};

        assertThatThrownBy(() -> resilience.gerarComResiliencia(
                t -> {
                    chamadas[0]++;
                    return new ChamadaLlm(chamadas[0] == 1 ? primeiraResposta : segundaResposta, "{}");
                },
                p -> validador.validarENormalizarPlano(p, atleta, ATLETA_ID),
                "gere o plano"))
                .isInstanceOf(DomainRuleViolationException.class);

        // as 2 tentativas foram gastas — a 2ª não foi aceita cegamente só porque o treino
        // apontado pela 1ª violação (SEGUNDA) estava corrigido; QUINTA foi revalidada e rejeitou.
        assertThat(chamadas[0]).isEqualTo(2);
    }

    // ---------- arranjo ----------

    private static PlanoSemanalLlmDto plano(TreinoPlanejadoLlmDto... treinos) {
        return new PlanoSemanalLlmDto(30.0, 30.0, null, null, "ATIVO", "base aeróbica", List.of(treinos), List.of());
    }

    private static TreinoPlanejadoLlmDto treinoValido(String dia) {
        return new TreinoPlanejadoLlmDto(dia, "FACIL", "130-145 bpm", 40, 0.7, 4,
                "Rodagem fácil", "40:00", 6.5, "6:00-6:30/km",
                List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 5, 0.8, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(2, "PRINCIPAL", "Rodagem confortável", 30, 5.0, "130-145 bpm", 1, "6:00-6:30/km"),
                        new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Caminhada", 5, 0.7, "120-136 bpm", 1, null)));
    }

    /** repeticoes=2 é rejeitado por validar-repeticoes (cauda comum, família-agnóstico). */
    private static TreinoPlanejadoLlmDto treinoInvalido(String dia) {
        return new TreinoPlanejadoLlmDto(dia, "FACIL", "130-145 bpm", 40, 0.7, 4,
                "Rodagem fácil", "40:00", 6.5, "6:00-6:30/km",
                List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 5, 0.8, "120-136 bpm", 2, null)));
    }
}
