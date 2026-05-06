package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.dto.output.PlanoSemanalOutputDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;

import java.time.LocalDate;
import java.util.List;

public record DadosPlanoDto(Atleta atleta,
                            LocalDate dataInicio,
                            PlanoSemanalOutputDto planoAnterior,
                            List<TreinoRealizadoOutputDto> ultimosTreinos,
                            PlanoMetaDados metaDados) {
}
