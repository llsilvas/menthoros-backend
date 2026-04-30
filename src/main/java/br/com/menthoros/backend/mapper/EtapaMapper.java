package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.output.EtapaTreinoDto;
import br.com.menthoros.backend.entity.EtapaTreino;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface EtapaMapper {

    EtapaTreinoDto toDto(EtapaTreino etapa);

    EtapaTreino toEntity(EtapaTreinoDto etapa);
}
