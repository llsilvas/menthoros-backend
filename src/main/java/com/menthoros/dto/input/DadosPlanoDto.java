package com.menthoros.dto.input;

import com.menthoros.dto.output.PlanoSemanalOutputDto;
import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import com.menthoros.entity.Atleta;
import com.menthoros.entity.PlanoMetaDados;

import java.time.LocalDate;
import java.util.List;

public record DadosPlanoDto(Atleta atleta,
                            LocalDate dataInicio,
                            PlanoSemanalOutputDto planoAnterior,
                            List<TreinoRealizadoOutputDto> ultimosTreinos,
                            PlanoMetaDados metaDados) {
}
