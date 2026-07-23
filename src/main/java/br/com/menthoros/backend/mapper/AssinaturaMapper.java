package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.output.AssinaturaOutputDto;
import br.com.menthoros.backend.entity.Assinatura;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Converte {@link Assinatura} + tier da {@code Assessoria} para {@link AssinaturaOutputDto}.
 * O tier vem da {@code Assessoria} (entitlement), não da {@code Assinatura}.
 */
@Component
public class AssinaturaMapper {

    public AssinaturaOutputDto toOutputDto(Assinatura entity, @Nullable PlanoAssessoria plano) {
        if (entity == null) {
            throw new IllegalArgumentException("Assinatura entity cannot be null");
        }
        return new AssinaturaOutputDto(
                entity.getId(),
                entity.getAssessoriaId(),
                entity.getStatus(),
                plano,
                entity.getValor(),
                entity.getDataProximaCobranca(),
                entity.getCriadoEm(),
                entity.getAtualizadoEm()
        );
    }
}
