package com.menthoros.mapper;

import com.menthoros.dto.input.AtletaInputDto;
import com.menthoros.dto.output.AtletaOutputDto;
import com.menthoros.entity.Atleta;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        unmappedSourcePolicy = ReportingPolicy.IGNORE,
        uses = {ProvaMapper.class}
)
public interface AtletaMapper {

    @Mapping(source = "pesoKg", target = "pesoKg")
    @Mapping(source = "alturaCm", target = "alturaCm")
    Atleta toEntity(AtletaInputDto atletaInputDto);

    AtletaOutputDto toOutputDto(Atleta atleta);

    @BeanMapping(ignoreByDefault = false)
    void updateEntity(AtletaInputDto atletaInputDto, @MappingTarget Atleta atleta);
}
