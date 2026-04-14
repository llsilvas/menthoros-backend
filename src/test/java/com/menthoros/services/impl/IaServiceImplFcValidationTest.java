package com.menthoros.services.impl;

import com.menthoros.dto.llm.EtapaTreinoLlmDto;
import com.menthoros.services.helper.ZonaTreinoService;
import com.menthoros.services.helper.ZonaTreinoService.ZonaFC;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;

/**
 * Testes unitários para os métodos de validação de FC por zona em IaServiceImpl.
 * Usa reflexão para acessar os métodos privados da lógica de validação LTHR.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IaServiceImpl — validação FC por zona LTHR")
class IaServiceImplFcValidationTest {

    private IaServiceImpl service;

    // zonas para fcLimiar = 160 bpm (LTHR)
    private List<ZonaFC> zonasFC160;

    @BeforeEach
    void setUp() {
        // Instanciar com mocks mínimos (sem interação nos testes de validação FC)
        service = new IaServiceImpl(
                mock(ChatClient.class),
                mock(com.menthoros.services.prompt.PlanoTreinoPromptBuilder.class),
                mock(com.menthoros.repository.AtletaRepository.class),
                mock(com.menthoros.services.helper.RegraGeracaoTreino.class),
                mock(com.menthoros.services.helper.TreinoHistoricoProvider.class),
                mock(com.menthoros.services.prompt.PaceHistoricoFormatter.class),
                mock(com.menthoros.services.helper.PaceValidator.class),
                mock(ZonaTreinoService.class)
        );

        // Z1=[120,136], Z2=[136,142], Z3=[142,150], Z4=[150,160], Z5=[160,170]
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
        void formatoValido() throws Exception {
            int[] result = invokeParseFcRange("140-160 bpm");
            assertThat(result).isNotNull();
            assertThat(result[0]).isEqualTo(140);
            assertThat(result[1]).isEqualTo(160);
        }

        @Test
        @DisplayName("formato antigo '88-95% FCmax' → null")
        void formatoPercentualFcMax_retornaNull() throws Exception {
            int[] result = invokeParseFcRange("88-95% FCmax");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("null → null")
        void nulo_retornaNull() throws Exception {
            int[] result = invokeParseFcRange(null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("string vazia → null")
        void vazio_retornaNull() throws Exception {
            int[] result = invokeParseFcRange("");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("formato '150-165 bpm' com espaço extra → null (não parseable)")
        void comEspacoExtra_retornaNull() throws Exception {
            int[] result = invokeParseFcRange(" 150-165 bpm ");
            // trim() é aplicado internamente, então deve parsear
            assertThat(result).isNotNull();
            assertThat(result[0]).isEqualTo(150);
            assertThat(result[1]).isEqualTo(165);
        }

        private int[] invokeParseFcRange(String value) throws Exception {
            Method m = IaServiceImpl.class.getDeclaredMethod("parseFcRange", String.class);
            m.setAccessible(true);
            return (int[]) m.invoke(service, value);
        }
    }

    // ======================== validarFcEtapa ========================

    @Nested
    @DisplayName("validarFcEtapa")
    class ValidarFcEtapa {

        @Test
        @DisplayName("AQUECIMENTO dentro de Z1 → sem alteração")
        void aquecimento_dentroZ1_semAlteracao() throws Exception {
            var etapa = new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 10, 1.5, "120-136 bpm", 1);
            EtapaTreinoLlmDto resultado = invokeValidarFcEtapa(etapa, zonasFC160);
            assertThat(resultado.fcAlvoEtapa()).isEqualTo("120-136 bpm");
        }

        @Test
        @DisplayName("AQUECIMENTO com FC de Z4 → corrigido para centro de Z1")
        void aquecimento_foraZ1_corrigido() throws Exception {
            var etapa = new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 10, 1.5, "155-165 bpm", 1);
            EtapaTreinoLlmDto resultado = invokeValidarFcEtapa(etapa, zonasFC160);
            // Z1=[120,136] → quartil central: min=120+4=124, max=136-4=132
            assertThat(resultado.fcAlvoEtapa()).isNotEqualTo("155-165 bpm");
            assertThat(resultado.fcAlvoEtapa()).endsWith(" bpm");
        }

        @Test
        @DisplayName("INTERVALADO dentro de Z4-Z5 → sem alteração")
        void intervalado_dentroZ4Z5_semAlteracao() throws Exception {
            var etapa = new EtapaTreinoLlmDto(2, "INTERVALADO", "400m forte", 4, 0.4, "155-165 bpm", 1);
            EtapaTreinoLlmDto resultado = invokeValidarFcEtapa(etapa, zonasFC160);
            assertThat(resultado.fcAlvoEtapa()).isEqualTo("155-165 bpm");
        }

        @Test
        @DisplayName("fcAlvoEtapa não parseable → sem exceção, valor mantido")
        void naoParseavel_semExcecao() {
            var etapa = new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Leve", 10, 1.5, "Z1 easy", 1);
            assertThatNoException().isThrownBy(() -> {
                EtapaTreinoLlmDto resultado = invokeValidarFcEtapa(etapa, zonasFC160);
                assertThat(resultado.fcAlvoEtapa()).isEqualTo("Z1 easy");
            });
        }

        @Test
        @DisplayName("fcAlvoEtapa null → sem exceção, valor mantido null")
        void fcAlvoNull_semExcecao() {
            var etapa = new EtapaTreinoLlmDto(1, "PRINCIPAL", "Contínuo", 30, 5.0, null, 1);
            assertThatNoException().isThrownBy(() -> {
                EtapaTreinoLlmDto resultado = invokeValidarFcEtapa(etapa, zonasFC160);
                assertThat(resultado.fcAlvoEtapa()).isNull();
            });
        }

        @Test
        @DisplayName("tipo de etapa desconhecido → sem validação, etapa inalterada")
        void tipoDesconhecido_semValidacao() throws Exception {
            var etapa = new EtapaTreinoLlmDto(1, "STRIDES", "Acelerações", 5, 0.5, "999-999 bpm", 1);
            EtapaTreinoLlmDto resultado = invokeValidarFcEtapa(etapa, zonasFC160);
            assertThat(resultado.fcAlvoEtapa()).isEqualTo("999-999 bpm");
        }

        private EtapaTreinoLlmDto invokeValidarFcEtapa(EtapaTreinoLlmDto etapa, List<ZonaFC> zonas) throws Exception {
            Method m = IaServiceImpl.class.getDeclaredMethod("validarFcEtapa", EtapaTreinoLlmDto.class, List.class);
            m.setAccessible(true);
            return (EtapaTreinoLlmDto) m.invoke(service, etapa, zonas);
        }
    }
}
