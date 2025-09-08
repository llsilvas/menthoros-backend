package com.menthoros.mapper;

import com.menthoros.dto.input.TreinoPlanejadoInputDto;
import com.menthoros.dto.input.TreinoRealizadoInputDto;
import com.menthoros.dto.llm.TreinoPlanejadoLlmDto;
import com.menthoros.dto.output.TreinoPlanejadoOutputDto;
import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import com.menthoros.entity.TreinoPlanejado;
import com.menthoros.entity.TreinoRealizado;
import org.mapstruct.*;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface TreinoMapper {

    @Mapping(target = "planoSemanal.id", source = "planoSemanalId")
    TreinoPlanejado toEntity(TreinoPlanejadoInputDto dto);

//    @Mapping(source = "planoSemanal.id", target = "planoSemanalId")
    TreinoPlanejadoOutputDto toOutputDto(TreinoPlanejado treinoPlanejado);

    // Adicionado para conversão direta de DTO de saída para entidade
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "planoSemanal", ignore = true)
    @Mapping(target = "atleta", ignore = true)
    TreinoPlanejado toEntity(TreinoPlanejadoLlmDto dto);

    @Named("treinoPlanejadoListToOutputDtoList")
    List<TreinoPlanejadoOutputDto> toOutputDtoListTreinoPlanejado(List<TreinoPlanejado> treinosPlanejados);

    @Mapping(target = "atleta.id", source = "atletaId")
    @Mapping(target = "planoSemanal.id", source = "planoSemanalId")
    @Mapping(target = "treinoPlanejado.id", source = "treinoPlanejadoId")
    TreinoRealizado toEntity(TreinoRealizadoInputDto dto);

    TreinoRealizadoOutputDto toOutputDto(TreinoRealizado treinoRealizado);

    @Named("treinoRealizadoListToOutputDtoList")
    List<TreinoRealizadoOutputDto> toOutputDtoListTreinoRealizado(List<TreinoRealizado> treinosRealizados);

    @AfterMapping
    default void linkEtapas(@MappingTarget TreinoPlanejado treinoPlanejado){
        if(treinoPlanejado.getEtapas() != null){
            treinoPlanejado.getEtapas().forEach(etapa ->
                    etapa.setTreinoPlanejado(treinoPlanejado));
        }
    }

}
