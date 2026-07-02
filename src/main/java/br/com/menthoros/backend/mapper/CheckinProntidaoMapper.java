package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.output.CheckinProntidaoOutputDto;
import br.com.menthoros.backend.entity.CheckinProntidao;
import org.springframework.stereotype.Component;

@Component
public class CheckinProntidaoMapper {

    public CheckinProntidaoOutputDto toOutputDto(CheckinProntidao entity) {
        if (entity == null) {
            throw new IllegalArgumentException("CheckinProntidao entity cannot be null");
        }

        return new CheckinProntidaoOutputDto(
                entity.getId(),
                entity.getAtleta().getId(),
                entity.getData(),
                entity.getQualidadeSono(),
                entity.getHumor(),
                entity.getDoresMusculares(),
                entity.getNivelEnergia(),
                entity.getEstresse(),
                entity.getObservacoes(),
                entity.getReadinessScore(),
                entity.getNivelProntidao()
        );
    }
}
