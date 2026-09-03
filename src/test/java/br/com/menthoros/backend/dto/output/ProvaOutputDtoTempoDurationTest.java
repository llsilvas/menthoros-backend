package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.TipoProva;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V90 / D6 (prova-no-plano-semanal): {@code tempoObjetivo}/{@code tempoRealizado} viraram
 * {@link Duration} no domínio, mas o contrato JSON continua {@code "HH:mm:ss"} — o front
 * ({@code types/Prova.ts}) não muda.
 */
@DisplayName("ProvaOutputDto — tempoObjetivo/tempoRealizado como Duration")
class ProvaOutputDtoTempoDurationTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Nested
    @DisplayName("serialização")
    class Serializacao {

        @Test
        @DisplayName("Duration de 1h45min sai como \"01:45:00\", não ISO-8601")
        void serializaComoHhMmSs() throws Exception {
            String json = mapper.writeValueAsString(
                    stub(Duration.ofHours(1).plusMinutes(45), Duration.ofHours(1).plusMinutes(48).plusSeconds(30)));

            assertThat(json).contains("\"tempoObjetivo\":\"01:45:00\"");
            assertThat(json).contains("\"tempoRealizado\":\"01:48:30\"");
            assertThat(json).doesNotContain("PT1H45M");
        }

        @Test
        @DisplayName("omite os dois campos quando nulos")
        void omiteQuandoNulos() throws Exception {
            String json = mapper.writeValueAsString(stub(null, null));

            assertThat(json).doesNotContain("tempoObjetivo");
            assertThat(json).doesNotContain("tempoRealizado");
        }
    }

    private ProvaOutputDto stub(Duration tempoObjetivo, Duration tempoRealizado) {
        return new ProvaOutputDto(
                UUID.randomUUID(), "Meia Maratona", LocalDate.now().plusWeeks(10),
                TipoProva.MEIA, DistanciaProva.KM_21, null, true, ProvaStatus.PLANEJADA,
                tempoObjetivo, null, null, false, tempoRealizado,
                null, null, null, null, null,
                null, null, 70, false, null,
                true, null, null);
    }
}
