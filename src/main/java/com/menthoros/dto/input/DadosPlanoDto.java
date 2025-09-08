package com.menthoros.dto.input;

import com.menthoros.dto.output.PlanoSemanalOutputDto;
import com.menthoros.entity.Atleta;

import java.time.LocalDate;

public record DadosPlanoDto(Atleta atleta,
                            LocalDate dataInicio,
                            PlanoSemanalOutputDto planoAnterior,
                            java.util.List<com.menthoros.dto.output.TreinoRealizadoOutputDto> ultimosTreinos,
                            com.menthoros.entity.PlanoMetaDados metaDados) {
}
