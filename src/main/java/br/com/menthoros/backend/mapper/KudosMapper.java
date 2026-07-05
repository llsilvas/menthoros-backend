package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.output.KudosOutputDto;
import br.com.menthoros.backend.dto.output.KudosRecenteOutputDto;
import br.com.menthoros.backend.entity.Kudos;
import org.springframework.stereotype.Component;

@Component
public class KudosMapper {

    public KudosOutputDto toOutputDto(Kudos entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Kudos entity cannot be null");
        }

        return new KudosOutputDto(
                entity.getId(),
                entity.getAtleta().getId(),
                entity.getCoach().getId(),
                entity.getMotivo(),
                entity.getCreatedAt()
        );
    }

    public KudosRecenteOutputDto toRecenteOutputDto(Kudos entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Kudos entity cannot be null");
        }

        return new KudosRecenteOutputDto(
                entity.getId(),
                entity.getMotivo(),
                entity.getCreatedAt()
        );
    }
}
