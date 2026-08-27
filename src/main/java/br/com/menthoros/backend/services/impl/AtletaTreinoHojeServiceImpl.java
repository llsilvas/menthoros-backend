package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.TreinoHojeDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.MotivoPulo;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.AtletaTreinoHojeService;
import br.com.menthoros.backend.services.helper.AtletaHojeResolver;
import br.com.menthoros.backend.services.helper.EtapaAlvoResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * <p><b>Isolamento de tenant:</b> o atleta é validado no tenant como primeira instrução; as
 * leituras seguintes usam só {@code atletaId} e assumem esse gate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AtletaTreinoHojeServiceImpl implements AtletaTreinoHojeService {

    private final AtletaRepository atletaRepository;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final AtletaHojeResolver hojeResolver;
    private final EtapaAlvoResolver etapaAlvoResolver;

    /**
     * Idempotent: YES — leitura. Side Effects: NONE. Tenant-aware: YES.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TreinoHojeDto> getTreinoHoje(UUID atletaId) {
        Atleta atleta = validarAtletaNoTenant(atletaId);
        LocalDate hoje = hojeResolver.hojeDe(atleta);

        return planejadoDeHoje(atletaId, hoje).map(tp -> toDto(tp, atleta, hoje));
    }

    /**
     * Idempotent: NO — muda o status do planejado. Side Effects: UPDATE em tb_treino_planejado.
     * Tenant-aware: YES.
     */
    @Override
    @Transactional
    public TreinoHojeDto pularHoje(UUID atletaId, MotivoPulo motivo) {
        Atleta atleta = validarAtletaNoTenant(atletaId);
        LocalDate hoje = hojeResolver.hojeDe(atleta);

        TreinoPlanejado tp = planejadoDeHoje(atletaId, hoje)
                .orElseThrow(() -> new DomainRuleViolationException("Não há treino planejado para hoje"));
        if (tp.getStatusTreino() == TreinoExecucaoStatus.REALIZADO
                || tp.getStatusTreino() == TreinoExecucaoStatus.CONCLUIDO) {
            throw new DomainRuleViolationException("O treino de hoje já foi realizado");
        }

        tp.setStatusTreino(TreinoExecucaoStatus.PERDIDO);
        tp.setMotivoPulo(motivo);
        tp.setPuladoEm(hojeResolver.agoraDe(atleta));
        TreinoPlanejado salvo = treinoPlanejadoRepository.save(tp);
        log.info("Treino de hoje pulado: planejadoId={}, atletaId={}, motivo={}", salvo.getId(), atletaId, motivo);
        return toDto(salvo, atleta, hoje);
    }

    private Optional<TreinoPlanejado> planejadoDeHoje(UUID atletaId, LocalDate hoje) {
        return treinoPlanejadoRepository.findByAtletaIdAndDataBetween(atletaId, hoje, hoje)
                .stream().findFirst();
    }

    private TreinoHojeDto toDto(TreinoPlanejado tp, Atleta atleta, LocalDate hoje) {
        List<TreinoHojeDto.EtapaAlvoDto> etapas = tp.getEtapas() == null || tp.getEtapas().isEmpty()
                ? null
                : tp.getEtapas().stream().map(e -> etapaAlvoResolver.resolver(e, atleta)).toList();
        // Duration.ZERO é a sentinela de "não prescrita" (mesma regra do me/home).
        Integer duracaoMin = tp.getDuracaoMin() != null && !tp.getDuracaoMin().isZero()
                ? (int) tp.getDuracaoMin().toMinutes() : null;
        return new TreinoHojeDto(
                hoje,
                tp.getId(),
                tp.getTipoTreino() != null ? tp.getTipoTreino().name() : null,
                tp.getDescricao(),
                duracaoMin,
                tp.getZonaAlvo(),
                tp.getTssPlanejado(),
                tp.getStatusTreino() != null ? tp.getStatusTreino().name() : null,
                tp.getMotivoPulo() != null ? tp.getMotivoPulo().name() : null,
                tp.getPuladoEm(),
                etapas);
    }

    private Atleta validarAtletaNoTenant(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        return atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado"));
    }
}
