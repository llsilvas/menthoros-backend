package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.PlanoSemanalOutputDto;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.mapper.PlanoSemanalMapper;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.services.PlanoReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class PlanoReviewServiceImpl implements PlanoReviewService {

    private final PlanoSemanalRepository planoSemanalRepository;
    private final PlanoSemanalMapper planoSemanalMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PlanoSemanalOutputDto> listarPlanosPendentes(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId não pode ser nulo");
        }
        log.info("Listando planos pendentes de revisão para tenant {}", tenantId);

        List<PlanoSemanal> pendentes = planoSemanalRepository
                .findByAssessoriaIdAndReviewStatusOrderBySemanaInicioAsc(tenantId, PlanoReviewStatus.AGUARDANDO_REVISAO);

        pendentes.forEach(p -> Hibernate.initialize(p.getTreinosPlanejados()));

        log.info("Encontrados {} planos pendentes para tenant {}", pendentes.size(), tenantId);
        return pendentes.stream().map(planoSemanalMapper::toOutputDto).toList();
    }

    @Override
    @Transactional
    public PlanoSemanalOutputDto aprovarPlano(UUID planoId, UUID tenantId) {
        if (planoId == null) throw new IllegalArgumentException("planoId não pode ser nulo");
        if (tenantId == null) throw new IllegalArgumentException("tenantId não pode ser nulo");

        log.info("Aprovando plano {} para tenant {}", planoId, tenantId);

        PlanoSemanal plano = buscarPlanoDoTenant(planoId, tenantId);
        validarTransicao(plano, PlanoReviewStatus.APROVADO);

        plano.setReviewStatus(PlanoReviewStatus.APROVADO);
        plano.setReviewComment(null);

        PlanoSemanal salvo = planoSemanalRepository.save(plano);
        Hibernate.initialize(salvo.getTreinosPlanejados());

        log.info("Plano {} aprovado com sucesso para tenant {}", planoId, tenantId);
        return planoSemanalMapper.toOutputDto(salvo);
    }

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
        Hibernate.initialize(salvo.getTreinosPlanejados());

        log.info("Plano {} rejeitado para tenant {}", planoId, tenantId);
        return planoSemanalMapper.toOutputDto(salvo);
    }

    private PlanoSemanal buscarPlanoDoTenant(UUID planoId, UUID tenantId) {
        return planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "Plano não encontrado: id=" + planoId + " para tenant=" + tenantId));
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
