package com.menthoros.mapper;

import com.menthoros.dto.input.ProvaInputDto;
import com.menthoros.dto.output.ProvaOutputDto;
import com.menthoros.entity.Prova;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface ProvaMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "atleta", ignore = true)
    Prova toEntity(ProvaInputDto dto);

    @Mapping(target = "diasFaltando", expression = "java(prova.diasFaltando())")
    ProvaOutputDto toOutputDto(Prova prova);

    @BeanMapping(ignoreByDefault = false)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "atleta", ignore = true)
    void updateEntity(ProvaInputDto dto, @MappingTarget Prova prova);
}
