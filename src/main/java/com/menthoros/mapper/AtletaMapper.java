package com.menthoros.mapper;

import com.menthoros.dto.AtletaDto;
import com.menthoros.entity.Atleta;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface AtletaMapper {

    Atleta toEntity(AtletaDto atletaDto);

    AtletaDto toDto(Atleta atleta);
}
