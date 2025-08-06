package com.menthoros.mapper;

import com.menthoros.dto.input.PlanoSemanalInputDto;
import com.menthoros.dto.output.PlanoSemanalOutputDto;
import com.menthoros.entity.PlanoSemanal;
import com.menthoros.entity.TreinoPlanejado;
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

    @Mapping(source = "atleta.id", target = "atletaId")
    @Mapping(source = "planoTreino.id", target = "planoTreinoId")
    PlanoSemanalOutputDto toOutputDto(PlanoSemanal entity);

    @Mapping(source = "atletaId", target = "atleta.id")
    @Mapping(source = "treinosPlanejados", target = "treinosPlanejados")
    PlanoSemanal toEntity(PlanoSemanalOutputDto dto);

    /**
     * Após mapping, assegura que cada TreinoPlanejado referencie este PlanoSemanal
     */
    @AfterMapping
    default void linkPlanoSemanal(@MappingTarget PlanoSemanal plano) {
        if (plano.getTreinosPlanejados() != null) {
            for (TreinoPlanejado tp : plano.getTreinosPlanejados()) {
                tp.setPlanoSemanal(plano);
            }
        }
    }
}
