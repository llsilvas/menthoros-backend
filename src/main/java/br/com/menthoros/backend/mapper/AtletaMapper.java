package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.input.AtletaInputDto;
import br.com.menthoros.backend.dto.output.AtletaOutputDto;
import br.com.menthoros.backend.entity.Atleta;
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
