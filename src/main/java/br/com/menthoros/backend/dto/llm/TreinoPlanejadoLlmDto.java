package br.com.menthoros.backend.dto.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import br.com.menthoros.backend.dto.output.EtapaTreinoDto;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.TipoTreino;

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
