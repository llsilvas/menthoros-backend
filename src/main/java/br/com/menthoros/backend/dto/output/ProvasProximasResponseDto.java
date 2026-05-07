package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resposta com lista de provas próximas de todos os atletas")
public class ProvasProximasResponseDto {

    @Schema(description = "Lista de provas próximas ordenadas pela data mais próxima")
    private List<ProvaProximaDto> provas;

    @Schema(description = "Número total de provas retornadas", example = "3")
    private Integer total;

    @Schema(description = "Data e hora da consulta em formato ISO", example = "2026-05-07T16:30:00")
    private String dataConsulta;
}
