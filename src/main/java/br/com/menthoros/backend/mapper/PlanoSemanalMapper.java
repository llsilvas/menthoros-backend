package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.input.PlanoSemanalInputDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.dto.output.PlanoSemanalOutputDto;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        unmappedSourcePolicy = ReportingPolicy.IGNORE,
        uses = {TreinoMapper.class}
)
public interface PlanoSemanalMapper {

    @Mapping(source = "atletaId", target = "atleta.id")
    @Mapping(source = "planoTreinoId", target = "planoTreino.id")
    @Mapping(source = "treinosPlanejados", target = "treinosPlanejados")
    PlanoSemanal toEntity(PlanoSemanalInputDto dto);

    PlanoSemanalOutputDto toOutputDto(PlanoSemanal entity);

    default PlanoSemanalOutputDto toOutputDtoSafe(PlanoSemanal entity) {
        if (entity == null) throw new IllegalArgumentException("PlanoSemanal entity cannot be null");
        return toOutputDto(entity);
    }

    @Mapping(source = "treinosPlanejados", target = "treinosPlanejados")
    PlanoSemanal toEntity(PlanoSemanalLlmDto dto);

    /**
     * Após mapping, assegura que cada TreinoPlanejado referencie este PlanoSemanal
     */
    @AfterMapping
    default void linkPlanoSemanal(@MappingTarget PlanoSemanal plano) {

    }

    PlanoSemanal toEntity(TreinoPlanejadoLlmDto planoSemanalOutputDto);
}
