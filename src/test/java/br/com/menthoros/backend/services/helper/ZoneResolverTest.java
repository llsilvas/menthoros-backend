package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.v2.Zona;
import br.com.menthoros.backend.services.helper.ZoneResolver.FaixaFc;
import br.com.menthoros.backend.services.helper.ZoneResolver.FaixaPace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ZoneResolver")
class ZoneResolverTest {

    private ZoneResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ZoneResolver(new ZonaTreinoService());
    }

    @Nested
    @DisplayName("bpm")
    class Bpm {

        @Test
        @DisplayName("delega para ZonaTreinoService.calcularZonasFC — mesmos valores para as 5 zonas")
        void delegaParaZonaTreinoServiceEmTodasAsZonas() {
            var zonas = new ZonaTreinoService().calcularZonasFC(190, 160);

            for (Zona zona : new Zona[]{Zona.Z1, Zona.Z2, Zona.Z3, Zona.Z4, Zona.Z5}) {
                FaixaFc faixa = resolver.bpm(zona, 190, 160);
                var esperado = zonas.get(zona.indice() - 1);
                assertThat(faixa.min()).isEqualTo(esperado.fcMin());
                assertThat(faixa.max()).isEqualTo(esperado.fcMax());
            }
        }

        @Test
        @DisplayName("LIMIAR resolve no mesmo índice de Z4")
        void limiarResolveComoZ4() {
            FaixaFc limiar = resolver.bpm(Zona.LIMIAR, 190, 160);
            FaixaFc z4 = resolver.bpm(Zona.Z4, 190, 160);
            assertThat(limiar).isEqualTo(z4);
        }
    }

    @Nested
    @DisplayName("pace")
    class Pace {

        @Test
        @DisplayName("com paceLimiar — delega para ZonaTreinoService.calcularZonasPace")
        void comPaceLimiarDelegaParaZonaTreinoService() {
            BigDecimal paceLimiar = BigDecimal.valueOf(4.50);
            var zonas = new ZonaTreinoService().calcularZonasPace(paceLimiar);

            for (Zona zona : new Zona[]{Zona.Z1, Zona.Z2, Zona.Z3, Zona.Z4, Zona.Z5}) {
                FaixaPace faixa = resolver.pace(zona, paceLimiar);
                var esperado = zonas.get(zona.indice() - 1);
                assertThat(faixa.min()).isEqualByComparingTo(esperado.paceMin());
                assertThat(faixa.max()).isEqualByComparingTo(esperado.paceMax());
            }
        }

        @Test
        @DisplayName("LIMIAR resolve no mesmo índice de Z4")
        void limiarResolveComoZ4() {
            BigDecimal paceLimiar = BigDecimal.valueOf(4.50);
            FaixaPace limiar = resolver.pace(Zona.LIMIAR, paceLimiar);
            FaixaPace z4 = resolver.pace(Zona.Z4, paceLimiar);
            assertThat(limiar).isEqualTo(z4);
        }

        @Test
        @DisplayName("sem paceLimiar cadastrado — cai no fallback sintético, nunca lança")
        void semPaceLimiarUsaFallback() {
            FaixaPace faixa = resolver.pace(Zona.Z3, null);
            assertThat(faixa.min()).isNotNull();
            assertThat(faixa.max()).isNotNull();
            assertThat(faixa.min()).isLessThanOrEqualTo(faixa.max());
        }

        @Test
        @DisplayName("fallback sintético é determinístico e coerente entre zonas (Z1 mais lento que Z5)")
        void fallbackEDeterministicoECoerenteEntreZonas() {
            FaixaPace z1 = resolver.pace(Zona.Z1, null);
            FaixaPace z5 = resolver.pace(Zona.Z5, null);
            // Pace menor = mais rápido; Z5 é mais intenso, então o pace de Z5 é menor (mais rápido) que Z1.
            assertThat(z5.max()).isLessThan(z1.min());
        }

        @Test
        @DisplayName("paceLimiar zero ou negativo cai no fallback sintético (achado do /qa: evita divisão por zero a jusante)")
        void paceLimiarNaoPositivoUsaFallback() {
            FaixaPace comZero = resolver.pace(Zona.Z3, BigDecimal.ZERO);
            FaixaPace comNegativo = resolver.pace(Zona.Z3, BigDecimal.valueOf(-1));
            FaixaPace semPaceLimiar = resolver.pace(Zona.Z3, null);

            assertThat(comZero).isEqualTo(semPaceLimiar);
            assertThat(comNegativo).isEqualTo(semPaceLimiar);
        }
    }
}
