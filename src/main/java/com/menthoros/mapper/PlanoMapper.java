package com.menthoros.mapper;

import com.menthoros.dto.PlanoTreinoDto;
import com.menthoros.entity.PlanoTreino;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface PlanoMapper {

    PlanoTreino toEntity(PlanoTreinoDto dto);

    PlanoTreinoDto toDto(PlanoTreino planoTreino);
}
