package com.menthoros.services.impl;

import com.menthoros.dto.input.AtletaInputDto;
import com.menthoros.dto.output.AtletaOutputDto;
import com.menthoros.entity.Assessoria;
import com.menthoros.entity.Atleta;
import com.menthoros.enums.AtletaStatus;
import com.menthoros.exception.ResourceNotFoundException;
import com.menthoros.mapper.AtletaMapper;
import com.menthoros.multitenancy.TenantContext;
import com.menthoros.repository.AssessoriaRepository;
import com.menthoros.repository.AtletaRepository;
import com.menthoros.repository.PlanoMetadadosRepository;
import com.menthoros.services.AtletaService;
import com.menthoros.services.TsbService;
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

    private static final String TENANT_KEY =
            "T(com.menthoros.multitenancy.TenantContext).getRequiredTenantId()";
    private static final String TENANT_ID_KEY =
            "T(com.menthoros.multitenancy.TenantContext).getRequiredTenantId() + ':' + #id";

    private final AtletaRepository atletaRepository;
    private final AssessoriaRepository assessoriaRepository;
    private final AtletaMapper atletaMapper;
    private final PlanoMetadadosRepository planoMetaDadosRepository;
    private final TsbService tsbService;

    @Override
    @CacheEvict(value = "atletas-list", key = TENANT_KEY)
    public Atleta createAtleta(AtletaInputDto atletaInputDto) {
        UUID tenantId = TenantContext.getRequiredTenantId();

        Assessoria assessoria = assessoriaRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessoria não encontrada para tenant: " + tenantId));

        Atleta entity = atletaMapper.toEntity(atletaInputDto);
        entity.setAtivo(AtletaStatus.ATIVO);
        entity.setAssessoria(assessoria);
        return atletaRepository.save(entity);
    }

    @Transactional
    @Override
    @Caching(evict = {
            @CacheEvict(value = "atletas", key = TENANT_ID_KEY),
            @CacheEvict(value = "atletas-list", key = TENANT_KEY),
            @CacheEvict(value = "metadados-atleta", key = TENANT_ID_KEY)
    })
    public AtletaOutputDto updateAtleta(UUID id, AtletaInputDto atletaInputDto) {
        UUID tenantId = TenantContext.getRequiredTenantId();

        Atleta atleta = atletaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + id));

        atletaMapper.updateEntity(atletaInputDto, atleta);
        Atleta saved = atletaRepository.save(atleta);
        return atletaMapper.toOutputDto(saved);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "atletas", key = TENANT_ID_KEY),
            @CacheEvict(value = "atletas-list", key = TENANT_KEY),
            @CacheEvict(value = "metadados-atleta", key = TENANT_ID_KEY)
    })
    public void deleteAtleta(UUID id) {
        UUID tenantId = TenantContext.getRequiredTenantId();

        Atleta atleta = atletaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + id));

        atleta.setAtivo(AtletaStatus.INATIVO);
        atletaRepository.save(atleta);
    }

    @Override
    @Cacheable(value = "atletas", key = TENANT_ID_KEY)
    @Transactional(readOnly = true)
    public AtletaOutputDto getAtletaById(UUID id) {
        UUID tenantId = TenantContext.getRequiredTenantId();

        Atleta atleta = atletaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + id));

        Hibernate.initialize(atleta.getProvas());
        Hibernate.initialize(atleta.getDiasDisponiveis());
        return atletaMapper.toOutputDto(atleta);
    }

    @Override
    @Cacheable(value = "atletas-list", key = TENANT_KEY)
    @Transactional(readOnly = true)
    public List<AtletaOutputDto> getAllAtletas() {
        UUID tenantId = TenantContext.getRequiredTenantId();

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
