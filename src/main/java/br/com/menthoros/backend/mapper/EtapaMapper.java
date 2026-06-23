package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.input.EtapaInputDto;
import br.com.menthoros.backend.dto.output.EtapaTreinoDto;
import br.com.menthoros.backend.entity.EtapaTreino;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface EtapaMapper {

    @Mapping(target = "distanciaKm",
             expression = "java(etapa.getDistanciaKm() != null ? etapa.getDistanciaKm().doubleValue() : null)")
    EtapaTreinoDto toDto(EtapaTreino etapa);

    List<EtapaTreinoDto> toDtoList(List<EtapaTreino> etapas);

    @Mapping(target = "distanciaKm",
             expression = "java(dto.distanciaKm() != null ? java.math.BigDecimal.valueOf(dto.distanciaKm()) : null)")
    @Mapping(target = "treinoPlanejado", ignore = true)
    EtapaTreino toEntity(EtapaTreinoDto dto);

    @Mapping(target = "tipoEtapa",
             expression = "java(dto.tipoEtapa() != null ? dto.tipoEtapa().toUpperCase() : null)")
    @Mapping(target = "distanciaKm",
             expression = "java(dto.distanciaKm() != null ? java.math.BigDecimal.valueOf(dto.distanciaKm()) : null)")
    @Mapping(target = "blocoRepeticoes", source = "blocoRepeticoes")
    @Mapping(target = "blocoId", ignore = true)
    @Mapping(target = "treinoPlanejado", ignore = true)
    @Mapping(target = "ordem", ignore = true)
    EtapaTreino toEntity(EtapaInputDto dto);
}
