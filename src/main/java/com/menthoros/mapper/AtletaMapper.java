package com.menthoros.mapper;

import com.menthoros.dto.input.AtletaInputDto;
import com.menthoros.dto.output.AtletaOutputDto;
import com.menthoros.entity.Atleta;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface AtletaMapper {

    Atleta toEntity(AtletaInputDto atletaInputDto);

    AtletaOutputDto toOutputDto(Atleta atleta);

}
