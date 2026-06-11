package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.input.AssessoriaInputDto;
import br.com.menthoros.backend.dto.output.AssessoriaOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import org.springframework.stereotype.Component;

/**
 * Conversão entre AssessoriaInputDto/AssessoriaOutputDto e a entidade Assessoria.
 */
@Component
public class AssessoriaMapper {

    /**
     * Converte o DTO de entrada na entidade Assessoria (sem persistir).
     *
     * Idempotent: YES — Read-only sobre o input, apenas constrói nova entidade.
     * Side Effects: NONE
     * Tenant-aware: NO — operação administrativa, sem tenant.
     *
     * @param input DTO de entrada da assessoria
     * @return entidade Assessoria com os campos do input mapeados
     * @throws IllegalArgumentException se input for null
     */
    public Assessoria toEntity(AssessoriaInputDto input) {
        if (input == null) {
            throw new IllegalArgumentException("AssessoriaInputDto cannot be null");
        }
        return Assessoria.builder()
                .nome(input.nome())
                .dominio(input.dominio())
                .plano(input.plano())
                .emailContato(input.emailContato())
                .maxAtletas(input.maxAtletas())
                .maxTecnicos(input.maxTecnicos())
                .build();
    }

    /**
     * Converte a entidade Assessoria no DTO de saída.
     *
     * Idempotent: YES — Read-only, sem side effects.
     * Side Effects: NONE
     * Tenant-aware: NO — operação administrativa, sem tenant.
     *
     * @param entity entidade JPA da assessoria
     * @return DTO de saída com os campos expostos
     * @throws IllegalArgumentException se entity for null
     */
    public AssessoriaOutputDto toOutputDto(Assessoria entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Assessoria entity cannot be null");
        }
        return new AssessoriaOutputDto(
                entity.getId(),
                entity.getNome(),
                entity.getDominio(),
                entity.getPlano(),
                entity.getKeycloakOrganizationId(),
                entity.getMaxAtletas(),
                entity.getMaxTecnicos(),
                Boolean.TRUE.equals(entity.getAtivo()),
                entity.getCreatedAt()
        );
    }
}
