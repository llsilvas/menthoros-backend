package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.input.PlanoTreinoInputDto;
import br.com.menthoros.backend.dto.output.PlanoTreinoOutputDto;
import br.com.menthoros.backend.entity.PlanoTreino;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedSourcePolicy = ReportingPolicy.IGNORE,
        uses = {TreinoMapper.class}
)
public interface PlanoMapper {

    @Mapping(target = "atleta.id", source = "atletaId")
    PlanoTreino toEntity(PlanoTreinoInputDto dto);

    @Mapping(source = "prova.dataProva", target = "dataProva")
    PlanoTreinoOutputDto toOutputDto(PlanoTreino planoTreino);
}
