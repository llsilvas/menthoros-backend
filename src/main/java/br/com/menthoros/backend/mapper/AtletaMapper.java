package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.input.AtletaInputDto;
import br.com.menthoros.backend.dto.output.AtletaOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.StatusVencimentoPlano;
import org.mapstruct.*;

import java.time.LocalDate;

@Mapper(
        componentModel = "spring",
        unmappedSourcePolicy = ReportingPolicy.IGNORE,
        uses = {ProvaMapper.class}
)
public interface AtletaMapper {

    @Mapping(source = "pesoKg", target = "pesoKg")
    @Mapping(source = "alturaCm", target = "alturaCm")
    Atleta toEntity(AtletaInputDto atletaInputDto);

    @Mapping(target = "statusVencimentoPlano", expression = "java(resolveStatusVencimentoPlano(atleta))")
    AtletaOutputDto toOutputDto(Atleta atleta);

    default StatusVencimentoPlano resolveStatusVencimentoPlano(Atleta atleta) {
        return StatusVencimentoPlano.resolver(atleta.getDataVencimentoPlano(), LocalDate.now());
    }

    @BeanMapping(ignoreByDefault = false)
    void updateEntity(AtletaInputDto atletaInputDto, @MappingTarget Atleta atleta);
}
