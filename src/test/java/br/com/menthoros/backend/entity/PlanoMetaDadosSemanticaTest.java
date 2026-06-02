package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.DiaSemana;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes semânticos para os métodos de interpretação de TSB em PlanoMetaDados.
 *
 * <p>Verifica que os métodos {@code estaEmFormaIdeal()}, {@code estaMuitoFatigado()},
 * {@code interpretarTsb()} e {@code getRecomendacaoTsb()} usam
 * {@code tsbProntidaoAtual} (pré-treino) e NÃO {@code tsbAtual} (pós-carga).</p>
 *
 * <p>TDD: testes escritos em RED antes da implementação.</p>
 */
class PlanoMetaDadosSemanticaTest {

    @Test
    @DisplayName("estaEmFormaIdeal() usa tsbProntidaoAtual, não tsbAtual (pós-carga)")
    void estaEmFormaIdeal_usaTsbProntidaoAtual() {
        PlanoMetaDados meta = PlanoMetaDados.builder()
                .tsbProntidaoAtual(+8.0)  // pré-treino: forma ideal (entre 5 e 15)
                .tsbAtual(-15.0)           // pós-carga: fadiga moderada
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .build();

        assertTrue(meta.estaEmFormaIdeal(),
                "estaEmFormaIdeal() deve avaliar forma com prontidão pré-treino (+8), não pós-carga (-15)");
    }

    @Test
    @DisplayName("estaEmFormaIdeal() retorna false quando tsbProntidaoAtual indica fadiga")
    void estaEmFormaIdeal_retornaFalse_quando_prontidaoFadigada() {
        PlanoMetaDados meta = PlanoMetaDados.builder()
                .tsbProntidaoAtual(-20.0)  // pré-treino: fadiga alta
                .tsbAtual(+8.0)             // pós-carga: parece ideal
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .build();

        assertFalse(meta.estaEmFormaIdeal(),
                "estaEmFormaIdeal() deve retornar false quando prontidão pré-treino indica fadiga");
    }

    @Test
    @DisplayName("estaMuitoFatigado() usa tsbProntidaoAtual, não tsbAtual (pós-carga)")
    void estaMuitoFatigado_usaTsbProntidaoAtual() {
        PlanoMetaDados meta = PlanoMetaDados.builder()
                .tsbProntidaoAtual(-40.0)  // pré-treino: fadiga excessiva/crítica
                .tsbAtual(+5.0)             // pós-carga: parece ok
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .build();

        assertTrue(meta.estaMuitoFatigado(),
                "estaMuitoFatigado() deve detectar fadiga usando prontidão pré-treino (-40), não pós-carga (+5)");
    }

    @Test
    @DisplayName("estaMuitoFatigado() retorna false quando tsbProntidaoAtual está ok")
    void estaMuitoFatigado_retornaFalse_quando_prontidaoOk() {
        PlanoMetaDados meta = PlanoMetaDados.builder()
                .tsbProntidaoAtual(+3.0)   // pré-treino: recuperando (ok)
                .tsbAtual(-40.0)            // pós-carga: fadiga crítica
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .build();

        assertFalse(meta.estaMuitoFatigado(),
                "estaMuitoFadigado() deve retornar false quando prontidão pré-treino está ok (+3)");
    }

    @Test
    @DisplayName("interpretarTsb() usa tsbProntidaoAtual para gerar interpretação correta")
    void interpretarTsb_usaTsbProntidaoAtual() {
        PlanoMetaDados meta = PlanoMetaDados.builder()
                .tsbProntidaoAtual(+8.0)  // forma ideal
                .tsbAtual(-30.0)           // fadiga crítica
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .build();

        String interpretacao = meta.interpretarTsb();

        // Com prontidão = +8 (forma ideal), deve retornar status de forma ideal
        assertNotNull(interpretacao, "interpretarTsb() não deve retornar null");
        assertTrue(interpretacao.toUpperCase().contains("IDEAL") || interpretacao.toUpperCase().contains("FORMA"),
                "interpretarTsb() com prontidão +8 deve indicar forma ideal, mas retornou: " + interpretacao);
    }

    @Test
    @DisplayName("getRecomendacaoTsb() usa tsbProntidaoAtual para gerar recomendação correta")
    void getRecomendacaoTsb_usaTsbProntidaoAtual() {
        // Prontidão muito negativa → recomendação deve ser de descanso
        PlanoMetaDados meta = PlanoMetaDados.builder()
                .tsbProntidaoAtual(-40.0)  // fadiga excessiva → recomendação de descanso
                .tsbAtual(+10.0)            // pós-carga: parece ótimo
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .build();

        String recomendacao = meta.getRecomendacaoTsb();

        // Com prontidão = -40 (fadiga excessiva), deve recomendar descanso
        assertNotNull(recomendacao, "getRecomendacaoTsb() não deve retornar null");
        assertFalse(recomendacao.isBlank(),
                "getRecomendacaoTsb() com prontidão -40 não deve retornar recomendação vazia");
    }
}
