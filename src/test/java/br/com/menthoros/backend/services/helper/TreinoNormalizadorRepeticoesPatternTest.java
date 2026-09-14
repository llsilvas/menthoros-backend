package br.com.menthoros.backend.services.helper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IA-03 (review.md 2026-09-05): {@code REPETICOES_PATTERN} casava por backtracking quando o
 * número era seguido de "min", gerando distância zero para séries baseadas em tempo. Reproduzido
 * e corrigido nesta task (refactor-iaservice-decomposition, seção 3).
 */
@DisplayName("TreinoNormalizador — REPETICOES_PATTERN (IA-03)")
class TreinoNormalizadorRepeticoesPatternTest {

    private TreinoNormalizador normalizador;

    @BeforeEach
    void setUp() {
        normalizador = new TreinoNormalizador(new PaceValidator());
    }

    @Nested
    @DisplayName("detectarRepeticoesNaDescricao / extrairDistanciaUnitariaDaDescricao")
    class Deteccao {

        @Test
        @DisplayName("IA-03: série baseada em tempo (\"5 x 10 min...\") não é lida como padrão de distância")
        void naoCasaSerieBaseadaEmTempo() {
            int n = normalizador.detectarRepeticoesNaDescricao("5 x 10 min forte + 2 min leve");
            // Antes do fix: backtracking casava "5 x 1" (grupo 2 = "1"), retornando n=5 e
            // extrairDistanciaUnitariaDaDescricao devolvia distância ~0 (1m arredondado).
            assertThat(n).as("não deve detectar repetições numa série baseada em tempo").isEqualTo(1);
        }

        @Test
        @DisplayName("IA-03: \"4x1.5km\" extrai distância decimal completa (1.5), não truncada (1)")
        void extraiDecimalCompleto() {
            int n = normalizador.detectarRepeticoesNaDescricao("4x1.5km");
            assertThat(n).isEqualTo(4);
            double dist = normalizador.extrairDistanciaUnitariaDaDescricao("4x1.5km", null, n);
            assertThat(dist).as("antes do fix, o backtracking truncava para 1.0").isEqualTo(1.5);
        }

        @Test
        @DisplayName("regressão: \"6x400m\" continua extraindo 400m = 0.4km")
        void continuaExtraindoDistanciaEmMetros() {
            int n = normalizador.detectarRepeticoesNaDescricao("6x400m");
            assertThat(n).isEqualTo(6);
            double dist = normalizador.extrairDistanciaUnitariaDaDescricao("6x400m", null, n);
            assertThat(dist).isEqualTo(0.4);
        }

        @Test
        @DisplayName("regressão: \"8-12× 400m Z5\" (texto do prompt) continua detectando 12 repetições")
        void continuaDetectandoTextoDoPrompt() {
            assertThat(normalizador.detectarRepeticoesNaDescricao("8-12× 400m Z5")).isEqualTo(12);
        }
    }
}
