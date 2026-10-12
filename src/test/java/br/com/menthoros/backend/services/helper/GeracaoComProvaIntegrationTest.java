package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.PlanoNaoConformeException;
import br.com.menthoros.backend.services.helper.PlanoResilienceService.ChamadaLlm;
import br.com.menthoros.backend.services.prompt.PaceHistoricoFormatter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * fix-planner-ignora-prova-na-semana, task 6.1 — integração {@code PlanoResilienceService} +
 * {@code PlanoLlmValidator} (sem mocks nos dois: é o caminho real do retry) reproduzindo a geração
 * real do Leandro (2026-10-11, `generation_request_id c69de808…`): atleta SEG/TER/QUI/SÁB,
 * `dia_preferido_longo=SÁBADO`, prova preparatória no domingo.
 *
 * <p>Tentativa 1 (histórica): a LLM prescreveu a prova no domingo com tipo errado (LONGO). Com o
 * fix, esse treino é descartado antes da validação (task 2.2) e a PROVA entra no lugar — a rejeição
 * real que sobra é a cobertura do sábado (falta alheia ao bug, continua existindo). Tentativa 2
 * (histórica, byte-a-byte): a LLM moveu o LONGO para o sábado, a véspera da prova — antes disso
 * passava sem violação; com o fix, vira {@code INTENSOS_ADJACENTES}. Como
 * {@code PlanoResilienceService.MAX_TENTATIVAS=2} e as duas tentativas históricas se esgotam sem uma
 * resposta segura, o resultado correto agora é a falha explícita ({@code DomainRuleViolationException},
 * 422 ao treinador) — não mais a persistência silenciosa do bug. O segundo teste prova que o
 * orçamento de 2 chamadas basta quando a 2ª tentativa corrige de verdade (LONGO longe da prova).
 */
@DisplayName("Integração: PlanoResilienceService + PlanoLlmValidator — caso real do Leandro")
class GeracaoComProvaIntegrationTest {

    private static final UUID ATLETA_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID PROVA_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final List<DiaSemana> DIAS_COM_PROVA =
            List.of(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUINTA, DiaSemana.SABADO, DiaSemana.DOMINGO);

    private Atleta atleta;
    private PlanoLlmValidator validador;
    private PlanoResilienceService resiliencia;

    @BeforeEach
    void setUp() {
        atleta = Atleta.builder()
                .id(ATLETA_ID)
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .paceLimiar(BigDecimal.valueOf(5.0))
                .build();

        TreinoHistoricoProvider treinoHistoricoProvider = mock(TreinoHistoricoProvider.class);
        when(treinoHistoricoProvider.prepararContexto(atleta)).thenReturn(
                new TreinoHistoricoProvider.ContextoTreino(LocalDate.of(2026, 10, 12), List.of(), List.of(), List.of()));
        PaceHistoricoFormatter paceHistoricoFormatter = mock(PaceHistoricoFormatter.class);
        when(paceHistoricoFormatter.calcularTetoPorTipo(any())).thenReturn(java.util.Map.of());
        when(paceHistoricoFormatter.calcularPisoPorTipo(any())).thenReturn(java.util.Map.of());
        ZonaTreinoService zonaTreinoService = mock(ZonaTreinoService.class);

        validador = new PlanoLlmValidator(
                treinoHistoricoProvider,
                paceHistoricoFormatter,
                zonaTreinoService,
                new NormalizacaoDeTreino(
                        new TreinoNormalizador(new PaceValidator()),
                        new EtapaFcValidator(),
                        new PlanoEstruturaReparador(new SimpleMeterRegistry()),
                        new PaceValidator(),
                        new SimpleMeterRegistry()),
                new DescansoNaoAutorizadoConverter(new SimpleMeterRegistry()),
                new WeeklyCoverageValidator(), new LongRunAnchor());

        resiliencia = new PlanoResilienceService(new SimpleMeterRegistry());
    }

    private WeeklyCoverageContext contexto() {
        return new WeeklyCoverageContext(DIAS_COM_PROVA, List.of(), false, DiaSemana.SABADO, 7,
                List.of(provaDomingo()));
    }

    private TreinoPlanejadoLlmDto provaDomingo() {
        return new TreinoPlanejadoLlmDto("DOMINGO", "PROVA", "124-130 bpm", null, 0.9, 8, null,
                "01:50:00", 21.1, "6:20-6:33/km", null, "Mizuno Athenas Run Longer 2026", "Zona 3-4", PROVA_ID);
    }

    @Test
    @DisplayName("tentativa 1 (histórica): LONGO no domingo não é rejeitado por dia indisponível — só falta cobertura do sábado")
    void tentativa1NaoRejeitaPeloDiaDaProva() {
        assertThatThrownBy(() -> validador.validarENormalizarPlano(resposta1(), atleta, ATLETA_ID, contexto()))
                .isInstanceOf(PlanoNaoConformeException.class)
                .satisfies(e -> {
                    var violacoes = ((PlanoNaoConformeException) e).violacoes();
                    assertThat(violacoes).noneMatch(v -> v.mensagem().contains("DOMINGO") && v.mensagem().contains("não está disponível"));
                    assertThat(violacoes).anyMatch(v -> v.mensagem().contains("SABADO"));
                });
    }

    @Test
    @DisplayName("tentativa 2 (histórica, byte-a-byte): LONGO no sábado agora é INTENSOS_ADJACENTES — antes passava sem violação")
    void tentativa2AgoraReprova() {
        assertThatThrownBy(() -> validador.validarENormalizarPlano(resposta2Historica(), atleta, ATLETA_ID, contexto()))
                .isInstanceOf(PlanoNaoConformeException.class)
                .satisfies(e -> assertThat(((PlanoNaoConformeException) e).violacoes())
                        .anySatisfy(v -> {
                            assertThat(v.key()).isEqualTo("INTENSOS_ADJACENTES");
                            assertThat(v.mensagem()).contains("LONGO").contains("PROVA");
                        }));
    }

    @Test
    @DisplayName("replay exato do histórico (2 tentativas): esgota o orçamento e falha explicitamente — nunca mais persiste o bug em silêncio")
    void replayHistoricoEsgotaOrcamentoEFalha() {
        AtomicInteger chamadas = new AtomicInteger();

        assertThatThrownBy(() -> resiliencia.gerarComResiliencia(
                t -> {
                    chamadas.incrementAndGet();
                    PlanoSemanalLlmDto resposta = t.numero() == 1 ? resposta1() : resposta2Historica();
                    return new ChamadaLlm(resposta, "json-tentativa-" + t.numero());
                },
                plano -> validador.validarENormalizarPlano(plano, atleta, ATLETA_ID, contexto()),
                "prompt-original"))
                .isInstanceOf(DomainRuleViolationException.class);

        assertThat(chamadas.get()).isEqualTo(2); // MAX_TENTATIVAS — as duas tentativas históricas, nenhuma a mais
    }

    @Test
    @DisplayName("tentativa 2 corrigida (LONGO na terça, longe da prova): converge dentro do orçamento de 2 chamadas")
    void tentativa2CorrigidaConverge() {
        PlanoSemanalLlmDto resultado = resiliencia.gerarComResiliencia(
                t -> new ChamadaLlm(t.numero() == 1 ? resposta1() : resposta2Corrigida(), "json-tentativa-" + t.numero()),
                plano -> validador.validarENormalizarPlano(plano, atleta, ATLETA_ID, contexto()),
                "prompt-original");

        assertThat(resultado.treinosPlanejados())
                .filteredOn(t -> "DOMINGO".equals(t.diaSemana()))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.tipoTreino()).isEqualTo("PROVA");
                    assertThat(t.provaId()).isEqualTo(PROVA_ID);
                });
        assertThat(resultado.treinosPlanejados())
                .filteredOn(t -> "SABADO".equals(t.diaSemana()))
                .singleElement()
                .satisfies(t -> assertThat(t.tipoTreino()).isNotIn("LONGO", "INTERVALADO", "TIRO", "SUBIDA", "FARTLEK", "TEMPO_RUN"));
    }

    // ---------- fixtures (réplica dos registros de tb_llm_call da geração real) ----------

    /** Tentativa 1 — faltou o sábado (falha real da LLM, independente do fix). */
    private static PlanoSemanalLlmDto resposta1() {
        return plano(treino("SEGUNDA", "CONTINUO"), treino("TERCA", "CONTINUO"),
                treino("QUINTA", "CONTINUO"), longo("DOMINGO"));
    }

    /** Tentativa 2, histórica — LONGO movido para a véspera da prova. */
    private static PlanoSemanalLlmDto resposta2Historica() {
        return plano(treino("SEGUNDA", "CONTINUO"), treino("TERCA", "CONTINUO"),
                treino("QUINTA", "CONTINUO"), longo("SABADO"));
    }

    /** Tentativa 2, hipotética — LONGO na terça (sem risco de vizinhança com a prova). */
    private static PlanoSemanalLlmDto resposta2Corrigida() {
        return plano(treino("SEGUNDA", "CONTINUO"), longo("TERCA"),
                treino("QUINTA", "CONTINUO"), treino("SABADO", "REGENERATIVO"));
    }

    private static PlanoSemanalLlmDto plano(TreinoPlanejadoLlmDto... treinos) {
        return new PlanoSemanalLlmDto(30.0, 30.0, null, null, "ATIVO", "base aeróbica", List.of(treinos), List.of());
    }

    private static TreinoPlanejadoLlmDto treino(String dia, String tipo) {
        return new TreinoPlanejadoLlmDto(dia, tipo, "130-145 bpm", 40, 0.7, 4,
                "Rodagem", "40:00", 6.5, "6:00-6:30/km",
                List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 5, 0.8, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(2, "PRINCIPAL", "Rodagem", 30, 5.0, "130-145 bpm", 1, "6:00-6:30/km"),
                        new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Caminhada", 5, 0.7, "120-136 bpm", 1, null)));
    }

    private static TreinoPlanejadoLlmDto longo(String dia) {
        return new TreinoPlanejadoLlmDto(dia, "LONGO", "130-145 bpm", 100, 0.8, 7,
                "Longo", "02:00:00", 21.1, "6:20-6:33/km",
                List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 10, 1.6, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(2, "PRINCIPAL", "Longo contínuo", 100, 18.0, "130-145 bpm", 1, "6:20-6:33/km"),
                        new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Caminhada", 10, 1.5, "120-136 bpm", 1, null)));
    }
}
