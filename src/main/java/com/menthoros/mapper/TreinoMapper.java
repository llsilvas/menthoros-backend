package com.menthoros.mapper;

import com.menthoros.dto.TreinoRealizadoDto;
import com.menthoros.entity.TreinoRealizado;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface TreinoMapper {
    TreinoRealizado toEntity(TreinoRealizadoDto treinoRealizadoDto);
    TreinoRealizadoDto toDto(TreinoRealizado treino);
}
