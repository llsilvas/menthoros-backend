package br.com.menthoros.backend.dto.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Um dia da semana prescrito como descanso, com o motivo que o justifica
 * (add-descanso-explicito-por-fadiga).
 *
 * <p>Fica fora de {@code treinosPlanejados} de propósito: descanso não é treino a cumprir — dentro
 * daquela lista ele seria marcado PERDIDO no encerramento da semana, entraria no denominador de
 * aderência e ocuparia slot na redistribuição.</p>
 *
 * <p>{@code dayOfWeek} é o <b>nome</b> do enum {@code DiaSemana} ("SEGUNDA"…"DOMINGO") como texto:
 * o enum serializa como objeto JSON ({@code @JsonFormat(shape = OBJECT)}) e não serve de tipo de fio
 * para a LLM.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Dia de descanso prescrito, com o motivo")
public record RestDayLlmDto(
        @Schema(description = "Dia da semana", example = "QUINTA")
        String dayOfWeek,

        @Schema(description = "Motivo do descanso, citando o sinal com valor e limiar",
                example = "check-in de hoje: DESCANSAR")
        @Nullable String reason) {
}
