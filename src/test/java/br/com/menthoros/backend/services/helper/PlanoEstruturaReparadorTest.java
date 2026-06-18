package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlanoEstruturaReparador")
class PlanoEstruturaReparadorTest {

    private MeterRegistry registry;
    private PlanoEstruturaReparador reparador;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        reparador = new PlanoEstruturaReparador(registry);
    }

    @Nested
    @DisplayName("repara")
    class Repara {

        @Test
        @DisplayName("REGENERATIVO sem DESAQUECIMENTO (2 etapas) → sintetiza e fica canônico (caso do 503)")
        void sintetizaDesaquecimento() {
            var treino = treino("REGENERATIVO", etapa("AQUECIMENTO"), etapa("PRINCIPAL"));
            var r = reparador.reparar(treino, "REGENERATIVO");
            assertThat(r.etapas()).extracting(EtapaTreinoLlmDto::tipoEtapa)
                    .containsExactly("AQUECIMENTO", "PRINCIPAL", "DESAQUECIMENTO");
            assertThat(contador("REGENERATIVO", "desaquecimento_sintetizado")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("sem AQUECIMENTO → sintetiza no início")
        void sintetizaAquecimento() {
            var treino = treino("CONTINUO", etapa("PRINCIPAL"), etapa("DESAQUECIMENTO"));
            var r = reparador.reparar(treino, "CONTINUO");
            assertThat(r.etapas()).extracting(EtapaTreinoLlmDto::tipoEtapa)
                    .containsExactly("AQUECIMENTO", "PRINCIPAL", "DESAQUECIMENTO");
            assertThat(contador("CONTINUO", "aquecimento_sintetizado")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("3 etapas fora de ordem → reordena para o canônico")
        void reordena() {
            var treino = treino("TEMPO_RUN", etapa("PRINCIPAL"), etapa("AQUECIMENTO"), etapa("DESAQUECIMENTO"));
            var r = reparador.reparar(treino, "TEMPO_RUN");
            assertThat(r.etapas()).extracting(EtapaTreinoLlmDto::tipoEtapa)
                    .containsExactly("AQUECIMENTO", "PRINCIPAL", "DESAQUECIMENTO");
            assertThat(r.etapas()).extracting(EtapaTreinoLlmDto::ordem).containsExactly(1, 2, 3);
            assertThat(contador("TEMPO_RUN", "reordenado")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("já canônico → no-op (sem contador)")
        void jaCanonico() {
            var treino = treino("REGENERATIVO", etapa("AQUECIMENTO"), etapa("PRINCIPAL"), etapa("DESAQUECIMENTO"));
            var r = reparador.reparar(treino, "REGENERATIVO");
            assertThat(r).isSameAs(treino);
            assertThat(registry.find("plano_reparo_aplicado").counters()).isEmpty();
        }
    }

    @Nested
    @DisplayName("não repara (cai no retry)")
    class NaoRepara {

        @Test
        @DisplayName("sem etapa PRINCIPAL → retorna como está")
        void semPrincipal() {
            var treino = treino("REGENERATIVO", etapa("AQUECIMENTO"), etapa("DESAQUECIMENTO"));
            assertThat(reparador.reparar(treino, "REGENERATIVO")).isSameAs(treino);
        }

        @Test
        @DisplayName("2 PRINCIPAIS (ambíguo) → retorna como está")
        void doisPrincipais() {
            var treino = treino("CONTINUO", etapa("PRINCIPAL"), etapa("PRINCIPAL"));
            assertThat(reparador.reparar(treino, "CONTINUO")).isSameAs(treino);
        }

        @Test
        @DisplayName("tipo não-'3 etapas' (INTERVALADO) → não toca")
        void tipoNaoTresEtapas() {
            var treino = treino("INTERVALADO", etapa("PRINCIPAL"));
            assertThat(reparador.reparar(treino, "INTERVALADO")).isSameAs(treino);
        }
    }

    private double contador(String tipo, String acao) {
        var c = registry.find("plano_reparo_aplicado").tag("tipo", tipo).tag("acao", acao).counter();
        return c == null ? 0.0 : c.count();
    }

    private static EtapaTreinoLlmDto etapa(String tipo) {
        return new EtapaTreinoLlmDto(1, tipo, "x", 10, 1.0, null, 1, null);
    }

    private static TreinoPlanejadoLlmDto treino(String tipo, EtapaTreinoLlmDto... etapas) {
        return new TreinoPlanejadoLlmDto("SEGUNDA", tipo, null, null, null, null, null, null, null, null, List.of(etapas));
    }
}
