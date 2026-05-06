package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.input.ProvaInputDto;
import br.com.menthoros.backend.dto.output.ProvaOutputDto;
import br.com.menthoros.backend.entity.Prova;
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
