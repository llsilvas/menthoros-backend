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

    @Mapping(target = "atletaNome", expression = "java(resolveAtletaNome(entity))")
    PlanoSemanalOutputDto toOutputDto(PlanoSemanal entity);

    default String resolveAtletaNome(PlanoSemanal entity) {
        if (entity.getAtleta() == null) return null;
        String nome = entity.getAtleta().getNome();
        if (nome == null || nome.isBlank()) return null;
        String sobrenome = entity.getAtleta().getSobrenome();
        return (sobrenome != null && !sobrenome.isBlank()) ? nome + " " + sobrenome : nome;
    }

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
