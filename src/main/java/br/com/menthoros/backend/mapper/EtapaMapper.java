package br.com.menthoros.backend.mapper;

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

    @Mapping(target = "tipoEtapa",
             expression = "java(etapa.getTipoEtapa() != null ? br.com.menthoros.backend.enums.TipoEtapa.valueOf(etapa.getTipoEtapa()) : null)")
    @Mapping(target = "distanciaKm",
             expression = "java(etapa.getDistanciaKm() != null ? etapa.getDistanciaKm().doubleValue() : null)")
    EtapaTreinoDto toDto(EtapaTreino etapa);

    List<EtapaTreinoDto> toDtoList(List<EtapaTreino> etapas);

    @Mapping(target = "tipoEtapa",
             expression = "java(dto.tipoEtapa() != null ? dto.tipoEtapa().name() : null)")
    @Mapping(target = "distanciaKm",
             expression = "java(dto.distanciaKm() != null ? java.math.BigDecimal.valueOf(dto.distanciaKm()) : null)")
    @Mapping(target = "treinoPlanejado", ignore = true)
    EtapaTreino toEntity(EtapaTreinoDto dto);
}
