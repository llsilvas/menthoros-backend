package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.DistanciaProva;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodizacaoPromptFormatterTest {

    @Test
    void formatarProvas_deveSerResilienteAValoresNulos() {
        PeriodizacaoPromptFormatter formatter = new PeriodizacaoPromptFormatter();

        Atleta atleta = Atleta.builder().nome("Teste").objetivo("Corrida").build();
        Prova prova = Prova.builder()
                .nomeProva("10K Teste")
                .dataProva(LocalDate.of(2026, 3, 9))
                .distancia(DistanciaProva.KM_10)
                .atleta(atleta)
                .build();

        String texto = assertDoesNotThrow(() -> formatter.formatarProvas(prova, List.of()));
        assertTrue(texto.contains("09/03/2026"));
        assertTrue(texto.contains("Pace alvo:"));
        assertTrue(texto.contains("TSB ideal na prova:"));
    }
}

