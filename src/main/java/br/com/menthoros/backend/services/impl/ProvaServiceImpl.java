package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.ProvaInputDto;
import br.com.menthoros.backend.dto.output.ProvaOutputDto;
import br.com.menthoros.backend.dto.output.ProvaProximaDto;
import br.com.menthoros.backend.dto.output.ProvasProximasResponseDto;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProvaServiceImpl implements ProvaService {

    private final ProvaRepository provaRepository;
    private final AtletaRepository atletaRepository;
    private final AssessoriaRepository assessoriaRepository;
    private final ProvaMapper provaMapper;

    /**
     * Resolve o atleta pelo ID garantindo isolamento por tenant.
     * Usa TenantContext.getRequiredTenantId() — lança IllegalStateException se tenant ausente.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES — usa TenantContext.getRequiredTenantId()
     *
     * @param atletaId ID do atleta
     * @return Atleta pertencente ao tenant atual
     * @throws IllegalStateException se o tenant não estiver configurado (ausência de JWT)
     * @throws ResourceNotFoundException se o atleta não for encontrado no tenant
     */
    private Atleta resolveAtleta(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        return atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + atletaId));
    }

    private Prova resolveProva(Atleta atleta, UUID provaId) {
        // tenant-aware: usa assessoria do atleta como tenant para garantir isolamento cross-tenant
        UUID tenantId = atleta.getAssessoria().getId();
        return provaRepository.findByIdAndTenantId(provaId, tenantId)
                .filter(p -> p.getAtleta().getId().equals(atleta.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Prova não encontrada: " + provaId));
    }

    /**
     * Cria uma prova para o atleta dentro do tenant da requisição atual.
     *
     * Idempotent: NO — Cria nova entidade a cada chamada.
     * Side Effects: Database insert (nova Prova criada)
     * Tenant-aware: YES — usa TenantContext.getRequiredTenantId() via resolveAtleta()
     *
     * @param atletaId ID do atleta para quem a prova será criada
     * @param dto dados da prova
     * @return ProvaOutputDto com os dados da prova criada
     * @throws IllegalStateException se o tenant não estiver configurado (ausência de JWT)
     * @throws ResourceNotFoundException se o atleta não for encontrado no tenant
     */
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

    /**
     * Lista as provas de um atleta dentro do tenant da requisição atual.
     *
     * Idempotent: YES — Operação de leitura, sem alteração de estado.
     * Side Effects: NONE
     * Tenant-aware: YES — usa TenantContext.getRequiredTenantId() via resolveAtleta()
     *
     * @param atletaId ID do atleta cujas provas serão listadas
     * @return lista de ProvaOutputDto com as provas do atleta
     * @throws IllegalStateException se o tenant não estiver configurado (ausência de JWT)
     * @throws ResourceNotFoundException se o atleta não for encontrado no tenant
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProvaOutputDto> listarProvas(UUID atletaId) {
        Atleta atleta = resolveAtleta(atletaId);
        return provaRepository.findByAtletaOrderByDataProvaAsc(atleta)
                .stream()
                .map(provaMapper::toOutputDto)
                .toList();
    }

    /**
     * Busca uma prova específica de um atleta dentro do tenant da requisição atual.
     *
     * Idempotent: YES — Operação de leitura, sem alteração de estado.
     * Side Effects: NONE
     * Tenant-aware: YES — usa TenantContext.getRequiredTenantId() via resolveAtleta()
     *
     * @param atletaId ID do atleta proprietário da prova
     * @param provaId ID da prova a ser buscada
     * @return ProvaOutputDto com os dados da prova
     * @throws IllegalStateException se o tenant não estiver configurado (ausência de JWT)
     * @throws ResourceNotFoundException se atleta ou prova não forem encontrados no tenant
     */
    @Override
    @Transactional(readOnly = true)
    public ProvaOutputDto buscarProvaPorId(UUID atletaId, UUID provaId) {
        Atleta atleta = resolveAtleta(atletaId);
        return provaMapper.toOutputDto(resolveProva(atleta, provaId));
    }

    /**
     * Atualiza uma prova de um atleta dentro do tenant da requisição atual.
     *
     * Idempotent: YES — Atualizar com os mesmos dados produz o mesmo resultado.
     * Side Effects: Database update (Prova atualizada)
     * Tenant-aware: YES — usa TenantContext.getRequiredTenantId() via resolveAtleta()
     *
     * @param atletaId ID do atleta proprietário da prova
     * @param provaId ID da prova a ser atualizada
     * @param dto novos dados da prova
     * @return ProvaOutputDto com os dados atualizados
     * @throws IllegalStateException se o tenant não estiver configurado (ausência de JWT)
     * @throws ResourceNotFoundException se atleta ou prova não forem encontrados no tenant
     */
    @Override
    @Transactional
    public ProvaOutputDto atualizarProva(UUID atletaId, UUID provaId, ProvaInputDto dto) {
        Atleta atleta = resolveAtleta(atletaId);
        Prova prova = resolveProva(atleta, provaId);
        provaMapper.updateEntity(dto, prova);
        return provaMapper.toOutputDto(provaRepository.save(prova));
    }

    /**
     * Remove uma prova de um atleta dentro do tenant da requisição atual.
     *
     * Idempotent: YES — Deletar uma prova já removida é seguro (já não existe).
     * Side Effects: Database delete (Prova removida)
     * Tenant-aware: YES — usa TenantContext.getRequiredTenantId() via resolveAtleta()
     *
     * @param atletaId ID do atleta proprietário da prova
     * @param provaId ID da prova a ser removida
     * @throws IllegalStateException se o tenant não estiver configurado (ausência de JWT)
     * @throws ResourceNotFoundException se atleta ou prova não forem encontrados no tenant
     */
    @Override
    @Transactional
    public void deletarProva(UUID atletaId, UUID provaId) {
        Atleta atleta = resolveAtleta(atletaId);
        Prova prova = resolveProva(atleta, provaId);
        provaRepository.delete(prova);
    }

    /**
     * Retorna as provas próximas dos próximos 15 dias de todos os atletas.
     * Operação global — não filtra por tenant, pois é usada para monitoramento de assessoria.
     *
     * Idempotent: YES — Operação de leitura, sem alteração de estado.
     * Side Effects: NONE
     * Tenant-aware: YES — retorna apenas provas de atletas da assessoria atual
     * (TenantContext). A versão anterior era global e vazava dados cross-tenant.
     *
     * @return ProvasProximasResponseDto com provas dos próximos 15 dias do tenant
     */
    @Override
    @Transactional(readOnly = true)
    public ProvasProximasResponseDto getProvasProximas() {
        UUID tenantId = TenantContext.getRequiredTenantId();
        LocalDate endDate = LocalDate.now().plusDays(15);
        List<Prova> provas = provaRepository.findUpcomingProvasNext15DaysByTenant(endDate, tenantId);

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
