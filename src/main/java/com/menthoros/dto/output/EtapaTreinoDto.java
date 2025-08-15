package com.menthoros.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.enums.TipoEtapa;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EtapaTreinoDto(
        Integer ordem,
        TipoEtapa tipoEtapa,
        String descricaoEtapa,
        Integer duracaoMin,
        Double distanciaKm,
        String fcAlvoEtapa,
        Integer repeticoes
) {}

