package com.menthoros.mapper;

import com.menthoros.dto.input.PlanoTreinoInputDto;
import com.menthoros.dto.output.PlanoTreinoOutputDto;
import com.menthoros.entity.PlanoTreino;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        unmappedSourcePolicy = ReportingPolicy.IGNORE,
        uses = {TreinoMapper.class}
)
public interface PlanoMapper {

    @Mapping(target = "atleta.id", source = "atletaId")
    PlanoTreino toEntity(PlanoTreinoInputDto dto);

    @Mapping(source = "prova.dataProva", target = "dataProva")
    PlanoTreinoOutputDto toOutputDto(PlanoTreino planoTreino);
}
