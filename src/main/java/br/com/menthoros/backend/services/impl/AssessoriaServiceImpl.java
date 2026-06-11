package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.AssessoriaInputDto;
import br.com.menthoros.backend.dto.output.AssessoriaOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.exception.DuplicateResourceException;
import br.com.menthoros.backend.mapper.AssessoriaMapper;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.services.AssessoriaService;
import br.com.menthoros.backend.services.KeycloakOrganizationGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssessoriaServiceImpl implements AssessoriaService {

    private final AssessoriaRepository assessoriaRepository;
    private final AssessoriaMapper assessoriaMapper;
    private final KeycloakOrganizationGateway keycloakOrganizationGateway;

    /**
     * Cria uma assessoria e a Organization correspondente no Keycloak, persistindo o orgId.
     *
     * Idempotent: NO — Cria uma nova assessoria e Organization a cada chamada.
     * Side Effects: DB insert (assessoria) + criação de Organization no Keycloak.
     * Tenant-aware: NO — operação administrativa (ADMIN), sem tenant resolvido.
     *
     * @param input dados de entrada da assessoria
     * @return DTO de saída da assessoria criada, com o keycloakOrganizationId preenchido
     * @throws IllegalArgumentException se input ou domínio forem nulos/vazios
     * @throws DuplicateResourceException se já existir assessoria com o domínio informado
     */
    @Override
    @Transactional
    public AssessoriaOutputDto criarAssessoria(AssessoriaInputDto input) {
        if (input == null) {
            throw new IllegalArgumentException("AssessoriaInputDto cannot be null");
        }
        if (input.dominio() == null || input.dominio().isBlank()) {
            throw new IllegalArgumentException("Domínio é obrigatório");
        }

        log.info("Criando assessoria: nome={}, dominio={}", input.nome(), input.dominio());

        if (assessoriaRepository.existsByDominio(input.dominio())) {
            throw new DuplicateResourceException("Domínio já existe: " + input.dominio());
        }

        Assessoria entity = assessoriaMapper.toEntity(input);
        entity = assessoriaRepository.save(entity);

        String orgId = keycloakOrganizationGateway.criarOrganization(
                entity.getNome(), entity.getDominio(), entity.getId());
        entity.setKeycloakOrganizationId(orgId);
        entity = assessoriaRepository.save(entity);

        log.info("Assessoria criada: id={}, keycloakOrganizationId={}", entity.getId(), orgId);
        return assessoriaMapper.toOutputDto(entity);
    }
}
