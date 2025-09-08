package com.menthoros.dto.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.dto.output.EtapaTreinoDto;
import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.TipoTreino;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TreinoPlanejadoLlmDto(
        DiaSemana diaSemana,
        TipoTreino tipoTreino,
        String descricao,
        String observacao,
        String fcAlvo,
        Integer percepcaoEsforcoEsperada,
        Integer duracaoMin,
        Double distanciaKm,
        String ritmoAlvo,
        List<EtapaTreinoDto> etapas
) {}
