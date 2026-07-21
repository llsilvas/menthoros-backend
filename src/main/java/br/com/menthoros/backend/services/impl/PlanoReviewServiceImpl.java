package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.PlanoSemanalOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.AthleteBaselineSnapshot;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.events.PlanoAprovadoEvent;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.mapper.PlanoSemanalMapper;
import br.com.menthoros.backend.repository.AthleteBaselineSnapshotRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.services.PlanoReviewService;
import br.com.menthoros.backend.services.onboarding.ConfidenceTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class PlanoReviewServiceImpl implements PlanoReviewService {

    private final PlanoSemanalRepository planoSemanalRepository;
    private final PlanoSemanalMapper planoSemanalMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AthleteBaselineSnapshotRepository athleteBaselineSnapshotRepository;

    /**
     * Lista todos os planos do tenant com reviewStatus = AGUARDANDO_REVISAO.
     * Delegado para listarPlanosPorStatus para evitar duplicação de lógica.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES
     */
    @Override
    public List<PlanoSemanalOutputDto> listarPlanosPendentes(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId não pode ser nulo");
        }
        return listarPlanosPorStatus(tenantId, PlanoReviewStatus.AGUARDANDO_REVISAO);
    }

    /**
     * Transiciona o plano de AGUARDANDO_REVISAO para APROVADO.
     *
     * Idempotent: NO — altera o estado do plano.
     * Side Effects: Database update (reviewStatus, reviewComment) + publica {@link PlanoAprovadoEvent}
     * (dispara o push assíncrono dos treinos para o intervals.icu)
     * Tenant-aware: YES — valida pertencimento via findByIdAndTenantId
     *
     * @throws DomainNotFoundException     se o plano não existir no tenant
     * @throws DomainRuleViolationException se reviewStatus != AGUARDANDO_REVISAO
     */
    @Override
    @Transactional
    public PlanoSemanalOutputDto aprovarPlano(UUID planoId, UUID tenantId) {
        if (planoId == null) throw new IllegalArgumentException("planoId não pode ser nulo");
        if (tenantId == null) throw new IllegalArgumentException("tenantId não pode ser nulo");

        log.info("Aprovando plano {} para tenant {}", planoId, tenantId);

        PlanoSemanal plano = buscarPlanoDoTenant(planoId, tenantId);
        validarTransicao(plano, PlanoReviewStatus.APROVADO);

        PlanoSemanal salvo = aprovarTransicao(plano, tenantId);

        log.info("Plano {} aprovado com sucesso para tenant {}", planoId, tenantId);
        return planoSemanalMapper.toOutputDtoSafe(salvo);
    }

    @Override
    @Transactional
    public PlanoSemanal aprovarTransicao(PlanoSemanal plano, UUID tenantId) {
        if (plano == null) throw new IllegalArgumentException("plano não pode ser nulo");
        if (tenantId == null) throw new IllegalArgumentException("tenantId não pode ser nulo");

        plano.setReviewStatus(PlanoReviewStatus.APROVADO);
        plano.setReviewComment(null);

        PlanoSemanal salvo = planoSemanalRepository.save(plano);
        inicializarAssociacoes(salvo);
        eventPublisher.publishEvent(new PlanoAprovadoEvent(salvo.getId(), salvo.getAtleta().getId(), tenantId));

        return salvo;
    }

    /**
     * Transiciona o plano de AGUARDANDO_REVISAO para REJEITADO, registrando o motivo.
     *
     * Idempotent: NO — altera o estado e o comentário do plano.
     * Side Effects: Database update (reviewStatus, reviewComment)
     * Tenant-aware: YES — valida pertencimento via findByIdAndTenantId
     *
     * @throws DomainNotFoundException     se o plano não existir no tenant
     * @throws DomainRuleViolationException se reviewStatus != AGUARDANDO_REVISAO
     */
    @Override
    @Transactional
    public PlanoSemanalOutputDto rejeitarPlano(UUID planoId, UUID tenantId, String motivo) {
        if (planoId == null) throw new IllegalArgumentException("planoId não pode ser nulo");
        if (tenantId == null) throw new IllegalArgumentException("tenantId não pode ser nulo");
        if (motivo == null || motivo.isBlank()) throw new IllegalArgumentException("Motivo é obrigatório");

        log.info("Rejeitando plano {} para tenant {}", planoId, tenantId);

        PlanoSemanal plano = buscarPlanoDoTenant(planoId, tenantId);
        validarTransicao(plano, PlanoReviewStatus.REJEITADO);

        plano.setReviewStatus(PlanoReviewStatus.REJEITADO);
        plano.setReviewComment(motivo);

        PlanoSemanal salvo = planoSemanalRepository.save(plano);
        inicializarAssociacoes(salvo);

        log.info("Plano {} rejeitado para tenant {}", planoId, tenantId);
        return planoSemanalMapper.toOutputDtoSafe(salvo);
    }

    /**
     * Lista planos do tenant filtrados por reviewStatus.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES
     */
    @Override
    @Transactional(readOnly = true)
    public List<PlanoSemanalOutputDto> listarPlanosPorStatus(UUID tenantId, PlanoReviewStatus reviewStatus) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId não pode ser nulo");
        if (reviewStatus == null) throw new IllegalArgumentException("reviewStatus não pode ser nulo");

        log.info("Listando planos com reviewStatus={} para tenant {}", reviewStatus, tenantId);

        List<PlanoSemanal> planos = planoSemanalRepository
                .findByAssessoriaIdAndReviewStatusOrderBySemanaInicioAsc(tenantId, reviewStatus, LocalDate.now());

        planos.forEach(this::inicializarAssociacoes);

        log.info("Encontrados {} planos com status {} para tenant {}", planos.size(), reviewStatus, tenantId);
        List<PlanoSemanalOutputDto> dtos = planos.stream().map(planoSemanalMapper::toOutputDtoSafe).toList();
        return enriquecerComConfidenceTier(planos, dtos, tenantId);
    }

    /**
     * Enriquece cada DTO com o {@code ConfidenceTier} do baseline de onboarding do atleta dono
     * do plano (athlete-onboarding-baseline, design.md Decisao 7 — badge de baixa confianca no
     * Cenario B/C da fila de revisao do coach). {@code null} quando o atleta ainda nao passou
     * pelo onboarding (sem {@code AthleteBaselineSnapshot}) ou nao esta carregado no plano.
     */
    private List<PlanoSemanalOutputDto> enriquecerComConfidenceTier(
            List<PlanoSemanal> planos, List<PlanoSemanalOutputDto> dtos, UUID tenantId) {
        List<PlanoSemanalOutputDto> enriquecidos = new ArrayList<>(dtos.size());
        for (int i = 0; i < planos.size(); i++) {
            enriquecidos.add(dtos.get(i).toBuilder()
                    .confidenceTier(resolverConfidenceTier(planos.get(i).getAtleta(), tenantId))
                    .build());
        }
        return enriquecidos;
    }

    private ConfidenceTier resolverConfidenceTier(Atleta atleta, UUID tenantId) {
        if (atleta == null || atleta.getId() == null) {
            return null;
        }
        return athleteBaselineSnapshotRepository.findByAtletaIdAndTenantId(atleta.getId(), tenantId)
                .map(AthleteBaselineSnapshot::getConfidenceTier)
                .map(ConfidenceTier::valueOf)
                .orElse(null);
    }

    private PlanoSemanal buscarPlanoDoTenant(UUID planoId, UUID tenantId) {
        return planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Plano não encontrado"));
    }

    private void inicializarAssociacoes(PlanoSemanal plano) {
        Hibernate.initialize(plano.getTreinosPlanejados());
        if (plano.getTreinosPlanejados() != null) {
            plano.getTreinosPlanejados().forEach(t -> Hibernate.initialize(t.getEtapas()));
        }
    }

    private void validarTransicao(PlanoSemanal plano, PlanoReviewStatus destino) {
        PlanoReviewStatus atual = plano.getReviewStatus();
        if (atual != PlanoReviewStatus.AGUARDANDO_REVISAO) {
            throw new DomainRuleViolationException(
                    "Transição inválida: plano está em '" + atual.getLabel() +
                    "' e não pode ser alterado para '" + destino.getLabel() +
                    "'. Apenas planos em 'Aguardando revisão' podem ser revisados.");
        }
    }
}
