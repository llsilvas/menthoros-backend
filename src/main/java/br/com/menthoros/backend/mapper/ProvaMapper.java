package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.input.ProvaInputDto;
import br.com.menthoros.backend.dto.output.ProvaOutputDto;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.services.helper.ProvaDerivadosCalculator;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Classe abstrata (não interface) porque os indicadores de preparação dependem do relógio da
 * aplicação: o calculador é injetado por campo, já que o MapStruct só gera construtor para mappers
 * em {@code uses}.
 */
@Mapper(componentModel = "spring", unmappedSourcePolicy = ReportingPolicy.IGNORE)
public abstract class ProvaMapper {

    @Autowired
    protected ProvaDerivadosCalculator provaDerivadosCalculator;

    // semanasPreparacao/inicioPreparacao são derivados pelo ProvaEnricher — nunca vêm do cliente.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "atleta", ignore = true)
    @Mapping(target = "semanasPreparacao", ignore = true)
    @Mapping(target = "inicioPreparacao", ignore = true)
    public abstract Prova toEntity(ProvaInputDto dto);

    @Mapping(target = "diasFaltando", expression = "java(prova.diasFaltando())")
    @Mapping(target = "preparacaoCurta", expression = "java(provaDerivadosCalculator.preparacaoCurta(prova))")
    @Mapping(target = "semanasFaltando", expression = "java(provaDerivadosCalculator.semanasFaltando(prova))")
    public abstract ProvaOutputDto toOutputDto(Prova prova);

    @BeanMapping(ignoreByDefault = false)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "atleta", ignore = true)
    @Mapping(target = "semanasPreparacao", ignore = true)
    @Mapping(target = "inicioPreparacao", ignore = true)
    public abstract void updateEntity(ProvaInputDto dto, @MappingTarget Prova prova);
}
