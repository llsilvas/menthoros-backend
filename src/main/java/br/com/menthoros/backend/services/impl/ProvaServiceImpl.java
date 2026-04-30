package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.ProvaInputDto;
import br.com.menthoros.backend.dto.output.ProvaOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.mapper.ProvaMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.services.ProvaService;
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
    private final AssessoriaRepository assessoriaRepository;
    private final ProvaMapper provaMapper;

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

    private Atleta resolveAtleta(UUID atletaId) {
        UUID tenantId = resolveTenantId();
        return atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + atletaId));
    }

    private Prova resolveProva(Atleta atleta, UUID provaId) {
        Prova prova = provaRepository.findById(provaId)
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
            prova.setStatusProva(br.com.menthoros.backend.enums.ProvaStatus.PLANEJADA);
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
