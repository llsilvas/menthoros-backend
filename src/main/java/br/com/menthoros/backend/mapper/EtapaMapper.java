package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.input.EtapaInputDto;
import br.com.menthoros.backend.dto.output.EtapaTreinoDto;
import br.com.menthoros.backend.entity.EtapaTreino;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
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

    /**
     * O segundo e a fonte da verdade: quando chega, o minuto enviado e ignorado e duracaoMin sai de
     * duracaoSeg por arredondamento inteiro, sem piso — abaixo de 30 s fica 0, que os leitores em
     * minutos tratam como ausente; o relogio usa o segundo. Aqui, e nao no service, para todo
     * caminho que constroi etapa a partir do DTO (avulsa, bloco expandido, repeticoes legadas)
     * aplicar a mesma regra.
     */
    @AfterMapping
    default void derivarDuracaoMin(EtapaInputDto dto, @MappingTarget EtapaTreino etapa) {
        if (dto.duracaoSeg() != null) {
            etapa.setDuracaoMin((dto.duracaoSeg() + 30) / 60);
        }
    }
}
