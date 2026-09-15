package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.dto.llm.v2.BlocoDto;
import br.com.menthoros.backend.dto.llm.v2.Papel;
import br.com.menthoros.backend.dto.llm.v2.PlanoSemanalLlmDtoV2;
import br.com.menthoros.backend.dto.llm.v2.RecuperacaoDto;
import br.com.menthoros.backend.dto.llm.v2.TreinoPlanejadoLlmDtoV2;
import br.com.menthoros.backend.dto.llm.v2.UnidadeQuantidade;
import br.com.menthoros.backend.dto.llm.v2.Zona;
import br.com.menthoros.backend.domain.planner.SessionSlot;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.exception.LLMException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link NormalizacaoDeTreino#validarEstruturaV2} — dispatch por família sobre etapas já
 * resolvidas (schema v2, semantic-session-schema). Reusa os mesmos gates de
 * {@link NormalizacaoDeTreinoTest}, sem os passos de correção aritmética.
 */
@DisplayName("NormalizacaoDeTreino — validarEstruturaV2")
class NormalizacaoDeTreinoValidarEstruturaV2Test {

    private NormalizacaoDeTreino normalizacao;
    private ContextoNormalizacao ctx;
    private SessionResolver sessionResolver;

    @BeforeEach
    void setUp() {
        normalizacao = new NormalizacaoDeTreino(
                new TreinoNormalizador(new PaceValidator()),
                new EtapaFcValidator(),
                new PlanoEstruturaReparador(new SimpleMeterRegistry()),
                new PaceValidator(),
                new SimpleMeterRegistry());
        Atleta atleta = Atleta.builder()
                .id(UUID.randomUUID())
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .paceLimiar(BigDecimal.valueOf(5.0))
                .build();
        ctx = new ContextoNormalizacao(atleta, atleta.getId(), null, Map.of(), Map.of());
        sessionResolver = new SessionResolver(new ZoneResolver(new ZonaTreinoService()), new TssCalculatorService());
    }

    @Nested
    @DisplayName("INTERVALADO_TIRO")
    class IntervaladoTiro {

        @Test
        @DisplayName("válido — 6 tiros com recuperação, aquec/desaq nas pontas — passa")
        void validoPassa() {
            var treino = intervalado(aquec(), tiro(), rec(), tiro(), rec(), tiro(), rec(),
                    tiro(), rec(), tiro(), rec(), tiro(), rec(), desaq());

            normalizacao.validarEstruturaV2(treino, ctx); // não lança
        }

        @Test
        @DisplayName("6 tiros sem NENHUMA recuperação — gateBalanceamento rejeita (achado da 5ª rodada de pré-mortem)")
        void seisTirosSemRecuperacaoRejeitado() {
            var treino = intervalado(aquec(), tiro(), tiro(), tiro(), tiro(), tiro(), tiro(), desaq());

            assertThatThrownBy(() -> normalizacao.validarEstruturaV2(treino, ctx))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("recuperações");
        }

        @Test
        @DisplayName("menos de 6 etapas totais — gateContagem rejeita")
        void menosDeSeisEtapasRejeitado() {
            var treino = intervalado(aquec(), tiro(), rec(), desaq());

            assertThatThrownBy(() -> normalizacao.validarEstruturaV2(treino, ctx))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("mínimo 6");
        }

        @Test
        @DisplayName("tiro fisiologicamente implausível (11 min) — validarDuracaoTiros rejeita (achado da 6ª rodada)")
        void tiroImplausivelRejeitado() {
            var tiroLongo = new EtapaTreinoLlmDto(1, "INTERVALADO", null, 11, 2.0, "160-170 bpm", 1, "5:00-5:10/km");
            var treino = intervalado(aquec(), tiroLongo, rec(), tiroLongo, rec(), tiroLongo, rec(),
                    tiroLongo, rec(), desaq());

            assertThatThrownBy(() -> normalizacao.validarEstruturaV2(treino, ctx))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("duração incoerente");
        }
    }

    @Nested
    @DisplayName("TRES_ETAPAS")
    class TresEtapas {

        @Test
        @DisplayName("REGENERATIVO válido — AQUEC→PRINCIPAL→DESAQ — passa")
        void regenerativoValidoPassa() {
            var treino = tresEtapas("REGENERATIVO", etapa("AQUECIMENTO"), etapa("PRINCIPAL"), etapa("DESAQUECIMENTO"));

            normalizacao.validarEstruturaV2(treino, ctx); // não lança
        }

        @Test
        @DisplayName("REGENERATIVO fora de ordem — rejeita (validarOrdem=true)")
        void regenerativoForaDeOrdemRejeitado() {
            var treino = tresEtapas("REGENERATIVO", etapa("DESAQUECIMENTO"), etapa("PRINCIPAL"), etapa("AQUECIMENTO"));

            assertThatThrownBy(() -> normalizacao.validarEstruturaV2(treino, ctx))
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("LONGO fora de ordem — passa (validarOrdem=false, só a etapa central precisa ser PRINCIPAL)")
        void longoForaDeOrdemPassa() {
            var treino = tresEtapas("LONGO", etapa("DESAQUECIMENTO"), etapa("PRINCIPAL"), etapa("AQUECIMENTO"));

            normalizacao.validarEstruturaV2(treino, ctx); // não lança
        }

        @Test
        @DisplayName("contagem errada de etapas — rejeita")
        void contagemErradaRejeitado() {
            var treino = tresEtapas("CONTINUO", etapa("AQUECIMENTO"), etapa("PRINCIPAL"));

            assertThatThrownBy(() -> normalizacao.validarEstruturaV2(treino, ctx))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("esperado 3");
        }
    }

    @Nested
    @DisplayName("FARTLEK e PADRAO — sem estrutura obrigatória")
    class SemEstruturaObrigatoria {

        @Test
        @DisplayName("FACIL (PADRAO) com qualquer etapa — não lança")
        void facilNaoLanca() {
            var treino = tresEtapas("FACIL", etapa("PRINCIPAL"));

            normalizacao.validarEstruturaV2(treino, ctx); // não lança
        }

        @Test
        @DisplayName("FARTLEK com qualquer etapa — não lança")
        void fartlekNaoLanca() {
            var treino = tresEtapas("FARTLEK", etapa("PRINCIPAL"), etapa("RECUPERACAO"));

            normalizacao.validarEstruturaV2(treino, ctx); // não lança
        }
    }

    @Nested
    @DisplayName("Composição com SessionResolver (task 4.3)")
    class ComposicaoComSessionResolver {

        @Test
        @DisplayName("plano v2 válido resolvido passa por validarEstruturaV2 sem erro de shape")
        void planoValidoResolvidoPassaSemErroDeShape() {
            AthleteZones zonas = new AthleteZones(190, 160, BigDecimal.valueOf(4.50));

            BlocoDto aquec = new BlocoDto(Papel.AQUEC, 1, BigDecimal.valueOf(10), UnidadeQuantidade.MIN, Zona.Z1, null);
            BlocoDto principal = new BlocoDto(Papel.PRINCIPAL, 6, BigDecimal.valueOf(400), UnidadeQuantidade.M, Zona.Z5,
                    new RecuperacaoDto(BigDecimal.valueOf(90), UnidadeQuantidade.SEG));
            BlocoDto desaq = new BlocoDto(Papel.DESAQ, 1, BigDecimal.valueOf(10), UnidadeQuantidade.MIN, Zona.Z1, null);

            TreinoPlanejadoLlmDtoV2 treinoV2 = new TreinoPlanejadoLlmDtoV2("TERCA", "INTERVALADO", "6x400m",
                    List.of(aquec, principal, desaq));
            PlanoSemanalLlmDtoV2 planoV2 = PlanoSemanalLlmDtoV2.builder()
                    .volumePlanejadoKm(30.0).volumeAlvoKm(30.0).status("PLANEJADO")
                    .objetivoSemanal("teste").treinosPlanejados(List.of(treinoV2)).build();

            PlanoSemanalLlmDto plano = sessionResolver.resolverPlano(planoV2, zonas);
            TreinoPlanejadoLlmDto treino = plano.treinosPlanejados().get(0);

            normalizacao.validarEstruturaV2(treino, ctx); // não lança — 12 etapas, tiros==recuperações
        }

        @Test
        @DisplayName("plano v2 com estrutura insuficiente resolvido é pego por validarEstruturaV2")
        void planoInsuficienteResolvidoEhPego() {
            AthleteZones zonas = new AthleteZones(190, 160, BigDecimal.valueOf(4.50));

            BlocoDto principal = new BlocoDto(Papel.PRINCIPAL, 1, BigDecimal.valueOf(200), UnidadeQuantidade.M, Zona.Z5, null);
            TreinoPlanejadoLlmDtoV2 treinoV2 = new TreinoPlanejadoLlmDtoV2("TERCA", "INTERVALADO", "tiro solto",
                    List.of(principal));
            PlanoSemanalLlmDtoV2 planoV2 = PlanoSemanalLlmDtoV2.builder()
                    .volumePlanejadoKm(5.0).volumeAlvoKm(5.0).status("PLANEJADO")
                    .objetivoSemanal("teste").treinosPlanejados(List.of(treinoV2)).build();

            PlanoSemanalLlmDto plano = sessionResolver.resolverPlano(planoV2, zonas);
            TreinoPlanejadoLlmDto treino = plano.treinosPlanejados().get(0);

            assertThatThrownBy(() -> normalizacao.validarEstruturaV2(treino, ctx))
                    .isInstanceOf(LLMException.class);
        }
    }

    @Nested
    @DisplayName("validarTssSlotV2")
    class ValidarTssSlotV2 {

        @Test
        @DisplayName("TSS dentro de ±20% do alvo do slot — passa")
        void tssDentroDaFaixaPassa() {
            var treino = tresEtapas("REGENERATIVO", etapa("AQUECIMENTO"), etapa("PRINCIPAL"), etapa("DESAQUECIMENTO"));
            var comTss = comTssPlanejado(treino, 55);
            var slot = new SessionSlot(java.time.DayOfWeek.MONDAY, "REGENERATIVO", 50.0, "Z2", false, 40);

            normalizacao.validarTssSlotV2(comTss, slot, ctx); // não lança (desvio 10%)
        }

        @Test
        @DisplayName("TSS fora de ±20% do alvo do slot — rejeita")
        void tssForaDaFaixaRejeitado() {
            var treino = tresEtapas("REGENERATIVO", etapa("AQUECIMENTO"), etapa("PRINCIPAL"), etapa("DESAQUECIMENTO"));
            var comTss = comTssPlanejado(treino, 80);
            var slot = new SessionSlot(java.time.DayOfWeek.MONDAY, "REGENERATIVO", 50.0, "Z2", false, 40);

            assertThatThrownBy(() -> normalizacao.validarTssSlotV2(comTss, slot, ctx))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("TSS resolvido");
        }

        @Test
        @DisplayName("slot sem alvo (targetTss<=0) — nada a comparar, não lança")
        void slotSemAlvoNaoLanca() {
            var treino = tresEtapas("REGENERATIVO", etapa("AQUECIMENTO"), etapa("PRINCIPAL"), etapa("DESAQUECIMENTO"));
            var comTss = comTssPlanejado(treino, 999);
            var slot = new SessionSlot(java.time.DayOfWeek.MONDAY, "REGENERATIVO", 0.0, "Z2", false, 40);

            normalizacao.validarTssSlotV2(comTss, slot, ctx); // não lança
        }

        private TreinoPlanejadoLlmDto comTssPlanejado(TreinoPlanejadoLlmDto treino, int tssPlanejado) {
            return new TreinoPlanejadoLlmDto(treino.diaSemana(), treino.tipoTreino(), treino.fcAlvo(),
                    tssPlanejado, treino.intensidadePlanejada(), treino.percepcaoEsforcoEsperada(),
                    treino.justificativaIa(), treino.duracaoMin(), treino.distanciaKm(), treino.ritmoAlvo(),
                    treino.etapas());
        }
    }

    // ---------- fixtures ----------

    private static EtapaTreinoLlmDto aquec() {
        return new EtapaTreinoLlmDto(1, "AQUECIMENTO", null, 10, 1.67, "120-136 bpm", 1, null);
    }

    private static EtapaTreinoLlmDto desaq() {
        return new EtapaTreinoLlmDto(1, "DESAQUECIMENTO", null, 10, 1.67, "120-136 bpm", 1, null);
    }

    private static EtapaTreinoLlmDto tiro() {
        return new EtapaTreinoLlmDto(1, "INTERVALADO", null, 2, 0.4, "160-170 bpm", 1, "4:30-4:45/km");
    }

    private static EtapaTreinoLlmDto rec() {
        return new EtapaTreinoLlmDto(1, "RECUPERACAO", null, 2, 0.3, "120-136 bpm", 1, "6:30-6:45/km");
    }

    private static EtapaTreinoLlmDto etapa(String tipoEtapa) {
        return new EtapaTreinoLlmDto(1, tipoEtapa, null, 10, 1.0, null, 1, null);
    }

    private static TreinoPlanejadoLlmDto intervalado(EtapaTreinoLlmDto... etapas) {
        return new TreinoPlanejadoLlmDto("TERCA", "INTERVALADO", "150-170 bpm", 60, 0.9, 8, "VO2max",
                "50:00", 8.0, "4:30-6:45/km", List.of(etapas));
    }

    private static TreinoPlanejadoLlmDto tresEtapas(String tipoTreino, EtapaTreinoLlmDto... etapas) {
        return new TreinoPlanejadoLlmDto("SEGUNDA", tipoTreino, null, null, null, null, null, null, null, null, List.of(etapas));
    }
}
