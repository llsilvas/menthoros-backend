package com.menthoros.services.impl;

import com.menthoros.dto.input.ProvaInputDto;
import com.menthoros.dto.output.ProvaOutputDto;
import com.menthoros.entity.Atleta;
import com.menthoros.entity.Prova;
import com.menthoros.exception.ResourceNotFoundException;
import com.menthoros.mapper.ProvaMapper;
import com.menthoros.multitenancy.TenantContext;
import com.menthoros.repository.AtletaRepository;
import com.menthoros.repository.ProvaRepository;
import com.menthoros.services.ProvaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProvaServiceImpl implements ProvaService {

    private final ProvaRepository provaRepository;
    private final AtletaRepository atletaRepository;
    private final ProvaMapper provaMapper;

    private Atleta resolveAtleta(UUID atletaId) {
        return atletaRepository.findByIdAndTenantId(atletaId, TenantContext.getRequiredTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + atletaId));
    }

    private Prova resolveProva(Atleta atleta, UUID provaId) {
        Prova prova = provaRepository.findByIdAndTenantId(provaId, TenantContext.getRequiredTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Prova não encontrada: " + provaId));
        if (!prova.getAtleta().getId().equals(atleta.getId())) {
            throw new ResourceNotFoundException("Prova não encontrada: " + provaId);
        }
        return prova;
    }

    @Override
    @Transactional
    public ProvaOutputDto criarProva(UUID atletaId, ProvaInputDto dto) {
        Atleta atleta = resolveAtleta(atletaId);
        Prova prova = provaMapper.toEntity(dto);
        prova.setAtleta(atleta);
        prova.setAssessoria(atleta.getAssessoria());
        if (prova.getStatusProva() == null) {
            prova.setStatusProva(com.menthoros.enums.ProvaStatus.PLANEJADA);
        }
        return provaMapper.toOutputDto(provaRepository.save(prova));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProvaOutputDto> listarProvas(UUID atletaId) {
        Atleta atleta = resolveAtleta(atletaId);
        return provaRepository.findByAtletaOrderByDataProvaAsc(atleta)
                .stream()
                .map(provaMapper::toOutputDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProvaOutputDto buscarProvaPorId(UUID atletaId, UUID provaId) {
        Atleta atleta = resolveAtleta(atletaId);
        return provaMapper.toOutputDto(resolveProva(atleta, provaId));
    }

    @Override
    @Transactional
    public ProvaOutputDto atualizarProva(UUID atletaId, UUID provaId, ProvaInputDto dto) {
        Atleta atleta = resolveAtleta(atletaId);
        Prova prova = resolveProva(atleta, provaId);
        provaMapper.updateEntity(dto, prova);
        return provaMapper.toOutputDto(provaRepository.save(prova));
    }

    @Override
    @Transactional
    public void deletarProva(UUID atletaId, UUID provaId) {
        Atleta atleta = resolveAtleta(atletaId);
        Prova prova = resolveProva(atleta, provaId);
        provaRepository.delete(prova);
    }
}
