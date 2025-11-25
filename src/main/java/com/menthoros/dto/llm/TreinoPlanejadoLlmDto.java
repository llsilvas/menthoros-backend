package com.menthoros.dto.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.dto.output.EtapaTreinoDto;
import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.TipoTreino;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TreinoPlanejadoLlmDto(
        String diaSemana,
        String tipoTreino,
        String fcAlvo,
        Integer tssPlanejado,
        Double intensidadePlanejada,
        Integer percepcaoEsforcoEsperada,
        String justificativaIa,
        String duracaoMin,
        Double distanciaKm,
        String ritmoAlvo,
        List<EtapaTreinoLlmDto> etapas
) {}
