package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.ProvaInputDto;
import br.com.menthoros.backend.dto.output.ProvaOutputDto;
import br.com.menthoros.backend.dto.output.ProvaProximaDto;
import br.com.menthoros.backend.dto.output.ProvasProximasResponseDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.mapper.ProvaMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.services.ProvaService;
import br.com.menthoros.backend.services.helper.ProvaEnricher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProvaServiceImpl implements ProvaService {

    private final ProvaRepository provaRepository;
    private final AtletaRepository atletaRepository;
    private final AssessoriaRepository assessoriaRepository;
    private final ProvaMapper provaMapper;
    private final ProvaEnricher provaEnricher;

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
     * Cria uma prova para o atleta dentro do tenant da requisição atual. Deriva os campos de
     * preparação e, se a prova nasce como alvo, desmarca a alvo anterior do atleta.
     *
     * Idempotent: NO — Cria nova entidade a cada chamada.
     * Side Effects: Database insert (nova Prova criada); update na prova-alvo anterior, se houver
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
            prova.setStatusProva(ProvaStatus.PLANEJADA);
        }
        provaEnricher.aplicarDerivados(prova);
        garantirAlvoUnica(atleta, prova);
        Prova salva = provaRepository.save(prova);
        log.info("Prova criada: id={}, atletaId={}, alvo={}", salva.getId(), atletaId, salva.isProvaAlvo());
        return provaMapper.toOutputDto(salva);
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
     * Atualiza uma prova de um atleta dentro do tenant da requisição atual. Recalcula os campos
     * derivados e mantém a prova-alvo única.
     *
     * Idempotent: YES — Atualizar com os mesmos dados produz o mesmo resultado.
     * Side Effects: Database update (Prova atualizada; prova-alvo anterior desmarcada, se houver)
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
        provaEnricher.aplicarDerivados(prova);
        garantirAlvoUnica(atleta, prova);
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

    /**
     * Se {@code prova} é alvo, desmarca as demais provas-alvo não canceladas do atleta.
     *
     * @return nome da alvo substituída, quando havia outra prova como alvo
     */
    private Optional<String> garantirAlvoUnica(Atleta atleta, Prova prova) {
        if (!prova.isProvaAlvo()) {
            return Optional.empty();
        }
        String alvoAnterior = null;
        for (Prova outra : provaRepository.findByAtletaAndProvaAlvoTrue(atleta)) {
            if (prova.getId() != null && prova.getId().equals(outra.getId())) {
                continue;
            }
            outra.setProvaAlvo(false);
            provaRepository.save(outra);
            alvoAnterior = outra.getNomeProva();
        }
        return Optional.ofNullable(alvoAnterior);
    }
}
