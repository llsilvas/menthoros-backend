package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.AtletaInputDto;
import br.com.menthoros.backend.dto.output.AtletaOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.mapper.AtletaMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.services.AtletaService;
import br.com.menthoros.backend.services.TsbService;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AtletaServiceImpl implements AtletaService {

    private final AtletaRepository atletaRepository;
    private final AssessoriaRepository assessoriaRepository;
    private final AtletaMapper atletaMapper;
    private final PlanoMetadadosRepository planoMetaDadosRepository;
    private final TsbService tsbService;

    private static final String HAS_TENANT =
            "T(com.menthoros.multitenancy.TenantContext).hasTenant()";
    private static final String TENANT_KEY =
            "T(com.menthoros.multitenancy.TenantContext).getTenantId()";

    // TODO(tenant-isolation): substituir resolveTenantId() por TenantContext.getRequiredTenantId()
    //   quando autenticação estiver habilitada no frontend.
    //   O fallback para a primeira assessoria ativa é apenas para dev local sem JWT.
    private UUID resolveTenantId() {
        if (TenantContext.hasTenant()) {
            return TenantContext.getTenantId();
        }
        return assessoriaRepository.findFirstByAtivoTrue()
                .map(Assessoria::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Nenhuma assessoria cadastrada no banco"));
    }

    @Override
    // TODO(tenant-isolation): @CacheEvict(value = "atletas-list", key = TENANT_KEY, condition = HAS_TENANT)
    @CacheEvict(value = "atletas-list", allEntries = true)
    public Atleta createAtleta(AtletaInputDto atletaInputDto) {
        UUID tenantId = resolveTenantId();

        Assessoria assessoria = assessoriaRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessoria não encontrada para tenant: " + tenantId));

        Atleta entity = atletaMapper.toEntity(atletaInputDto);
        entity.setAtivo(AtletaStatus.ATIVO);
        entity.setAssessoria(assessoria);
        return atletaRepository.save(entity);
    }

    @Transactional
    @Override
    // TODO(tenant-isolation): restaurar @Caching com chaves tenant-aware e condition = HAS_TENANT
    @Caching(evict = {
            @CacheEvict(value = "atletas", key = "#id"),
            @CacheEvict(value = "atletas-list", allEntries = true),
            @CacheEvict(value = "metadados-atleta", key = "#id")
    })
    public AtletaOutputDto updateAtleta(UUID id, AtletaInputDto atletaInputDto) {
        UUID tenantId = resolveTenantId();

        Atleta atleta = atletaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + id));

        atletaMapper.updateEntity(atletaInputDto, atleta);
        Atleta saved = atletaRepository.save(atleta);
        return atletaMapper.toOutputDto(saved);
    }

    @Override
    // TODO(tenant-isolation): restaurar @Caching com chaves tenant-aware e condition = HAS_TENANT
    @Caching(evict = {
            @CacheEvict(value = "atletas", key = "#id"),
            @CacheEvict(value = "atletas-list", allEntries = true),
            @CacheEvict(value = "metadados-atleta", key = "#id")
    })
    public void deleteAtleta(UUID id) {
        UUID tenantId = resolveTenantId();

        Atleta atleta = atletaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + id));

        atleta.setAtivo(AtletaStatus.INATIVO);
        atletaRepository.save(atleta);
    }

    @Override
    @Cacheable(value = "atletas", key = "#id + '_' + " + TENANT_KEY, condition = HAS_TENANT)
    @Transactional(readOnly = true)
    public AtletaOutputDto getAtletaById(UUID id) {
        UUID tenantId = resolveTenantId();

        Atleta atleta = atletaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + id));

        Hibernate.initialize(atleta.getProvas());
        Hibernate.initialize(atleta.getDiasDisponiveis());
        return atletaMapper.toOutputDto(atleta);
    }

    @Override
    @Cacheable(value = "atletas-list", key = TENANT_KEY, condition = HAS_TENANT)
    @Transactional(readOnly = true)
    public List<AtletaOutputDto> getAllAtletas() {
        UUID tenantId = resolveTenantId();

        List<Atleta> allAtletas = atletaRepository.findAllAtletasWithBasicInfo(tenantId);

        return allAtletas.stream().map(atleta -> {
            Hibernate.initialize(atleta.getDiasDisponiveis());
            Hibernate.initialize(atleta.getProvas());
            return atletaMapper.toOutputDto(atleta);
        }).toList();
    }

    @Override
    public void recalcularMetricasAtleta(UUID id) {
        tsbService.recalcularHistoricoCompleto(id);
    }

}
