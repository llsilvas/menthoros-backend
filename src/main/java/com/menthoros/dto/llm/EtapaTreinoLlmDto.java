package com.menthoros.dto.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.enums.TipoEtapa;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EtapaTreinoLlmDto(
        Integer ordem,

        String tipoEtapa,

        String descricaoEtapa,

        Integer duracaoMin,

        Double distanciaKm,

        String fcAlvoEtapa,

        Integer repeticoes,

        String ritmoAlvo
) {}

