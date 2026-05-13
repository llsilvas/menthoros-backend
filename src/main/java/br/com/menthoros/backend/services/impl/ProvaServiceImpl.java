package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.ProvaInputDto;
import br.com.menthoros.backend.dto.output.ProvaOutputDto;
import br.com.menthoros.backend.dto.output.ProvaProximaDto;
import br.com.menthoros.backend.dto.output.ProvasProximasResponseDto;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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

    @Override
    @Transactional(readOnly = true)
    public ProvasProximasResponseDto getProvasProximas() {
        LocalDate endDate = LocalDate.now().plusDays(15);
        List<Prova> provas = provaRepository.findUpcomingProvasNext15Days(endDate);

        List<ProvaProximaDto> dtoList = provas.stream()
            .map(p -> {
                LocalDate dataProva = p.getDataProva();
                long diasFaltando = ChronoUnit.DAYS.between(LocalDate.now(), dataProva);

                return new ProvaProximaDto(
                    p.getId(),
                    p.getAtleta().getId(),
                    p.getAtleta().getNome(),
                    p.getNomeProva(),
                    p.getDataProva().toString(),
                    p.getTipoProva().toString(),
                    p.getDistancia().toString(),
                    p.getDistanciaKm() != null ? p.getDistanciaKm().doubleValue() : null,
                    p.getTempoObjetivo() != null ? p.getTempoObjetivo().toString() : null,
                    p.getStatusProva().toString(),
                    Math.toIntExact(diasFaltando)
                );
            })
            .toList();

        return new ProvasProximasResponseDto(
            dtoList,
            dtoList.size(),
            LocalDateTime.now().toString()
        );
    }
}
