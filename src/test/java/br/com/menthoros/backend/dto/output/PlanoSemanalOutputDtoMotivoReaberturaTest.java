package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.MotivoReaberturaRevisao;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V89 / D4 (prova-no-plano-semanal): {@code motivoReabertura} entra no contrato JSON só quando
 * presente ({@code @JsonInclude(NON_NULL)}) — plano comum não ganha o campo vazio no payload.
 */
@DisplayName("PlanoSemanalOutputDto.motivoReabertura")
class PlanoSemanalOutputDtoMotivoReaberturaTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Nested
    @DisplayName("serialização")
    class Serializacao {

        @Test
        @DisplayName("inclui motivoReabertura quando o plano foi reaberto")
        void incluiQuandoPresente() throws Exception {
            String json = mapper.writeValueAsString(stub(MotivoReaberturaRevisao.PROVA_INSERIDA));

            assertThat(json).contains("\"motivoReabertura\":\"PROVA_INSERIDA\"");
        }

        @Test
        @DisplayName("omite motivoReabertura quando o plano não foi reaberto")
        void omiteQuandoAusente() throws Exception {
            String json = mapper.writeValueAsString(stub(null));

            assertThat(json).doesNotContain("motivoReabertura");
        }
    }

    private PlanoSemanalOutputDto stub(MotivoReaberturaRevisao motivo) {
        return PlanoSemanalOutputDto.builder()
                .id("plano-1")
                .semanaInicio(LocalDate.now())
                .semanaFim(LocalDate.now().plusDays(6))
                .volumePlanejadoKm(40.0)
                .volumeRealizadoKm(0.0)
                .volumeAlvoKm(40.0)
                .status(PlanoStatus.EM_ANDAMENTO)
                .objetivoSemanal("Semana base")
                .treinosPlanejados(List.of())
                .reviewStatus(PlanoReviewStatus.AGUARDANDO_REVISAO)
                .atletaNome("Ana Silva")
                .motivoReabertura(motivo)
                .build();
    }
}
