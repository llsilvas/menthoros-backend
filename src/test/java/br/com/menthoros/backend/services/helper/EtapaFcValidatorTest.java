package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaFC;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Testes unitários para {@link EtapaFcValidator} — validação de FC por zona LTHR.
 * Extraído de {@code IaServiceImplFcValidationTest} + {@code IaServiceImplZonaEsperadaFcTest}
 * (refactor-iaservice-decomposition, seção 4). Chamada direta ao colaborador, sem reflexão.
 */
@DisplayName("EtapaFcValidator")
class EtapaFcValidatorTest {

    private EtapaFcValidator validator;

    // zonas para fcLimiar = 160 bpm (LTHR)
    // Z1=[120,136], Z2=[136,142], Z3=[142,150], Z4=[150,160], Z5=[160,170]
    private List<ZonaFC> zonasFC160;

    @BeforeEach
    void setUp() {
        validator = new EtapaFcValidator();
        zonasFC160 = List.of(
                new ZonaFC(1, "Recuperação",  120, 136),
                new ZonaFC(2, "Aeróbico",     136, 142),
                new ZonaFC(3, "Tempo",        142, 150),
                new ZonaFC(4, "Limiar",       150, 160),
                new ZonaFC(5, "VO2max",       160, 170)
        );
    }

    // ======================== parseFcRange ========================

    @Nested
    @DisplayName("parseFcRange")
    class ParseFcRange {

        @Test
        @DisplayName("formato válido '140-160 bpm' → int[]{140, 160}")
        void formatoValido() {
            int[] result = validator.parseFcRange("140-160 bpm");
            assertThat(result).isNotNull();
            assertThat(result[0]).isEqualTo(140);
            assertThat(result[1]).isEqualTo(160);
        }

        @Test
        @DisplayName("formato antigo '88-95% FCmax' → null")
        void formatoPercentualFcMax_retornaNull() {
            int[] result = validator.parseFcRange("88-95% FCmax");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("null → null")
        void nulo_retornaNull() {
            int[] result = validator.parseFcRange(null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("string vazia → null")
        void vazio_retornaNull() {
            int[] result = validator.parseFcRange("");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("'150-165 bpm' com espaço externo → trim aplicado, parseia corretamente")
        void comEspacoExtra_parseiaComTrim() {
            int[] result = validator.parseFcRange(" 150-165 bpm ");
            assertThat(result).isNotNull();
            assertThat(result[0]).isEqualTo(150);
            assertThat(result[1]).isEqualTo(165);
        }
    }

    // ======================== zonaEsperadaFC / zonaParaEtapaPrincipal ========================

    @Nested
    @DisplayName("zonaEsperadaFC e zonaParaEtapaPrincipal por tipoTreino")
    class ZonaEsperadaFcPorTipoTreino {

        // --- etapas estruturais são independentes do tipoTreino ---

        @Test
        @DisplayName("AQUECIMENTO → sempre Z1, independente do tipoTreino")
        void aquecimento_sempreZ1() {
            int[] z = validator.zonaEsperadaFC("AQUECIMENTO", "REGENERATIVO", zonasFC160);
            assertThat(z).isEqualTo(new int[]{120, 136}); // Z1
        }

        @Test
        @DisplayName("DESAQUECIMENTO → sempre Z1, independente do tipoTreino")
        void desaquecimento_sempreZ1() {
            int[] z = validator.zonaEsperadaFC("DESAQUECIMENTO", "TIRO", zonasFC160);
            assertThat(z).isEqualTo(new int[]{120, 136}); // Z1
        }

        // --- IA-02: RECUPERACAO e INTERVALADO (etapa) variam por tipoTreino ---

        @Test
        @DisplayName("IA-02b: RECUPERACAO num treino FARTLEK aceita Z1-Z2, não Z1 fixo")
        void recuperacaoEmFartlekAceitaZ1Z2() {
            int[] z = validator.zonaEsperadaFC("RECUPERACAO", "FARTLEK", zonasFC160);
            assertThat(z).isEqualTo(new int[]{120, 142}); // Z1-Z2
        }

        @Test
        @DisplayName("regressão: RECUPERACAO num treino INTERVALADO continua Z1 estrito")
        void recuperacaoEmIntervaladoContinuaZ1() {
            int[] z = validator.zonaEsperadaFC("RECUPERACAO", "INTERVALADO", zonasFC160);
            assertThat(z).isEqualTo(new int[]{120, 136}); // Z1
        }

        @Test
        @DisplayName("IA-02a: INTERVALADO (etapa) num treino FARTLEK espera Z2-Z4, não Z4-Z5 fixo")
        void intervaladoEmFartlekEsperaZ2Z4() {
            int[] z = validator.zonaEsperadaFC("INTERVALADO", "FARTLEK", zonasFC160);
            assertThat(z).isEqualTo(new int[]{136, 160}); // Z2-Z4
        }

        @Test
        @DisplayName("regressão: INTERVALADO (etapa) num treino INTERVALADO de verdade continua Z4-Z5")
        void intervaladoEtapa_emTreinoIntervalado_z4z5() {
            int[] z = validator.zonaEsperadaFC("INTERVALADO", "INTERVALADO", zonasFC160);
            assertThat(z).isEqualTo(new int[]{150, 170}); // Z4-Z5
        }

        // --- zonaParaEtapaPrincipal: depende do tipoTreino ---

        @Test
        @DisplayName("PRINCIPAL + REGENERATIVO → Z1-Z2 (não Z4!)")
        void principal_regenerativo_z1z2() {
            int[] z = validator.zonaEsperadaFC("PRINCIPAL", "REGENERATIVO", zonasFC160);
            assertThat(z).isEqualTo(new int[]{120, 142}); // Z1-Z2
        }

        @Test
        @DisplayName("PRINCIPAL + CONTINUO → Z2-Z3")
        void principal_continuo_z2z3() {
            int[] z = validator.zonaEsperadaFC("PRINCIPAL", "CONTINUO", zonasFC160);
            assertThat(z).isEqualTo(new int[]{136, 150}); // Z2-Z3
        }

        @Test
        @DisplayName("PRINCIPAL + LONGO → Z2-Z3")
        void principal_longo_z2z3() {
            int[] z = validator.zonaEsperadaFC("PRINCIPAL", "LONGO", zonasFC160);
            assertThat(z).isEqualTo(new int[]{136, 150}); // Z2-Z3
        }

        @Test
        @DisplayName("PRINCIPAL + FARTLEK → Z2-Z4 (range variável)")
        void principal_fartlek_z2z4() {
            int[] z = validator.zonaEsperadaFC("PRINCIPAL", "FARTLEK", zonasFC160);
            assertThat(z).isEqualTo(new int[]{136, 160}); // Z2-Z4
        }

        @Test
        @DisplayName("PRINCIPAL + TEMPO_RUN → Z3-Z4 (limiar)")
        void principal_tempoRun_z3z4() {
            int[] z = validator.zonaEsperadaFC("PRINCIPAL", "TEMPO_RUN", zonasFC160);
            assertThat(z).isEqualTo(new int[]{142, 160}); // Z3-Z4
        }

        @Test
        @DisplayName("PRINCIPAL + INTERVALADO → Z4-Z5")
        void principal_intervalado_z4z5() {
            int[] z = validator.zonaEsperadaFC("PRINCIPAL", "INTERVALADO", zonasFC160);
            assertThat(z).isEqualTo(new int[]{150, 170}); // Z4-Z5
        }

        @Test
        @DisplayName("PRINCIPAL + TIRO → Z4-Z5")
        void principal_tiro_z4z5() {
            int[] z = validator.zonaEsperadaFC("PRINCIPAL", "TIRO", zonasFC160);
            assertThat(z).isEqualTo(new int[]{150, 170}); // Z4-Z5
        }

        @Test
        @DisplayName("PRINCIPAL + tipoTreino null → default Z2-Z4")
        void principal_tipoTreinoNull_defaultZ2Z4() {
            int[] z = validator.zonaEsperadaFC("PRINCIPAL", null, zonasFC160);
            assertThat(z).isEqualTo(new int[]{136, 160}); // Z2-Z4
        }

        // --- Casos de integração via validarFcEtapa ---

        @Test
        @DisplayName("REGENERATIVO com PRINCIPAL em Z4 → corrigido para Z1-Z2 (não Z4)")
        void regenerativo_principalEmZ4_corrigidoParaZ1Z2() {
            var etapa = new EtapaTreinoLlmDto(2, "PRINCIPAL", "Trote regenerativo", 30, 5.0, "155-160 bpm", 1, null);
            EtapaTreinoLlmDto resultado = validator.validarFcEtapa(etapa, "REGENERATIVO", zonasFC160);

            assertThat(resultado.fcAlvoEtapa()).isNotEqualTo("155-160 bpm");
            assertThat(resultado.fcAlvoEtapa()).endsWith(" bpm");
            int[] corrigido = validator.parseFcRange(resultado.fcAlvoEtapa());
            assertThat(corrigido[0]).isGreaterThanOrEqualTo(120);
            assertThat(corrigido[1]).isLessThanOrEqualTo(142);
        }

        @Test
        @DisplayName("TEMPO_RUN com PRINCIPAL em Z1 → corrigido para Z3-Z4")
        void tempoRun_principalEmZ1_corrigidoParaZ3Z4() {
            var etapa = new EtapaTreinoLlmDto(2, "PRINCIPAL", "Corrida limiar", 20, 4.0, "120-130 bpm", 1, null);
            EtapaTreinoLlmDto resultado = validator.validarFcEtapa(etapa, "TEMPO_RUN", zonasFC160);

            assertThat(resultado.fcAlvoEtapa()).isNotEqualTo("120-130 bpm");
            int[] corrigido = validator.parseFcRange(resultado.fcAlvoEtapa());
            assertThat(corrigido[0]).isGreaterThanOrEqualTo(142);
            assertThat(corrigido[1]).isLessThanOrEqualTo(160);
        }

        @Test
        @DisplayName("CONTINUO com PRINCIPAL em Z2-Z3 → sem correção (dentro da zona esperada)")
        void continuo_principalEmZ2Z3_semCorrecao() {
            var etapa = new EtapaTreinoLlmDto(2, "PRINCIPAL", "Corrida contínua", 40, 7.0, "138-148 bpm", 1, null);
            EtapaTreinoLlmDto resultado = validator.validarFcEtapa(etapa, "CONTINUO", zonasFC160);

            assertThat(resultado.fcAlvoEtapa()).isEqualTo("138-148 bpm");
        }
    }

    // ======================== validarFcEtapa ========================

    @Nested
    @DisplayName("validarFcEtapa")
    class ValidarFcEtapa {

        @Test
        @DisplayName("AQUECIMENTO dentro de Z1 → sem alteração")
        void aquecimento_dentroZ1_semAlteracao() {
            var etapa = new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 10, 1.5, "120-136 bpm", 1, null);
            EtapaTreinoLlmDto resultado = validator.validarFcEtapa(etapa, "INTERVALADO", zonasFC160);
            assertThat(resultado.fcAlvoEtapa()).isEqualTo("120-136 bpm");
        }

        @Test
        @DisplayName("AQUECIMENTO com FC de Z4 → corrigido para centro de Z1")
        void aquecimento_foraZ1_corrigido() {
            var etapa = new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 10, 1.5, "155-165 bpm", 1, null);
            EtapaTreinoLlmDto resultado = validator.validarFcEtapa(etapa, "LONGO", zonasFC160);
            assertThat(resultado.fcAlvoEtapa()).isNotEqualTo("155-165 bpm");
            assertThat(resultado.fcAlvoEtapa()).endsWith(" bpm");
        }

        @Test
        @DisplayName("INTERVALADO (etapa) dentro de Z4-Z5 → sem alteração")
        void intervalado_dentroZ4Z5_semAlteracao() {
            var etapa = new EtapaTreinoLlmDto(2, "INTERVALADO", "400m forte", 4, 0.4, "155-165 bpm", 1, null);
            EtapaTreinoLlmDto resultado = validator.validarFcEtapa(etapa, "INTERVALADO", zonasFC160);
            assertThat(resultado.fcAlvoEtapa()).isEqualTo("155-165 bpm");
        }

        @Test
        @DisplayName("fcAlvoEtapa não parseable → sem exceção, valor mantido")
        void naoParseavel_semExcecao() {
            var etapa = new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Leve", 10, 1.5, "Z1 easy", 1, null);
            assertThatNoException().isThrownBy(() -> {
                EtapaTreinoLlmDto resultado = validator.validarFcEtapa(etapa, "CONTINUO", zonasFC160);
                assertThat(resultado.fcAlvoEtapa()).isEqualTo("Z1 easy");
            });
        }

        @Test
        @DisplayName("fcAlvoEtapa null → sem exceção, valor mantido null")
        void fcAlvoNull_semExcecao() {
            var etapa = new EtapaTreinoLlmDto(1, "PRINCIPAL", "Contínuo", 30, 5.0, null, 1, null);
            assertThatNoException().isThrownBy(() -> {
                EtapaTreinoLlmDto resultado = validator.validarFcEtapa(etapa, "CONTINUO", zonasFC160);
                assertThat(resultado.fcAlvoEtapa()).isNull();
            });
        }

        @Test
        @DisplayName("tipo de etapa desconhecido → sem validação, etapa inalterada")
        void tipoDesconhecido_semValidacao() {
            var etapa = new EtapaTreinoLlmDto(1, "STRIDES", "Acelerações", 5, 0.5, "999-999 bpm", 1, null);
            EtapaTreinoLlmDto resultado = validator.validarFcEtapa(etapa, "FARTLEK", zonasFC160);
            assertThat(resultado.fcAlvoEtapa()).isEqualTo("999-999 bpm");
        }
    }

    // ======================== P0 — bypass de validação FC corrigido ========================

    @Nested
    @DisplayName("P0 — Etapas auto-inseridas devem produzir formato 'NNN-NNN bpm'")
    class BypassFcCorrigido {

        @Test
        @DisplayName("Prova do bypass corrigido: etapa auto-inserida com zonas é validada por validarFcEtapa")
        void etapaAutoInserida_comZonas_naoBypassaValidacaoFc() {
            var etapaTiro = new EtapaTreinoLlmDto(2, "INTERVALADO", "Tiro extra em Z5", 4, 0.8, "160-170 bpm", 1, null);
            EtapaTreinoLlmDto resultado = validator.validarFcEtapa(etapaTiro, "INTERVALADO", zonasFC160);
            assertThat(resultado.fcAlvoEtapa()).isEqualTo("160-170 bpm");

            var etapaTiroForaDaZona = new EtapaTreinoLlmDto(2, "INTERVALADO", "Tiro extra em Z5", 4, 0.8, "125-135 bpm", 1, null);
            EtapaTreinoLlmDto resultadoCorrigido = validator.validarFcEtapa(etapaTiroForaDaZona, "INTERVALADO", zonasFC160);
            assertThat(resultadoCorrigido.fcAlvoEtapa()).isNotEqualTo("125-135 bpm");
            assertThat(resultadoCorrigido.fcAlvoEtapa()).endsWith(" bpm");
        }

        @Test
        @DisplayName("Etapa com '% FCmax' → parseFcRange retorna null, etapa mantida com WARN (fallback sem zonas)")
        void etapaComPercentualFcMax_escapaValidacao_comportamentoDocumentado() {
            var etapa = new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote", 10, 1.5, "90-95% FCmax", 1, null);
            EtapaTreinoLlmDto resultado = validator.validarFcEtapa(etapa, "CONTINUO", zonasFC160);
            assertThat(resultado.fcAlvoEtapa()).isEqualTo("90-95% FCmax");
        }
    }
}
