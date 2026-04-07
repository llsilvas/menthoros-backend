package com.menthoros.mapper;

import com.menthoros.dto.output.EtapaTreinoDto;
import com.menthoros.entity.EtapaTreino;
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
