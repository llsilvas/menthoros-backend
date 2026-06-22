package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.TreinoPlanejadoPatchDto;
import br.com.menthoros.backend.dto.output.EtapaTreinoDto;
import br.com.menthoros.backend.dto.output.TreinoPlanejadoOutputDto;
import br.com.menthoros.backend.entity.EtapaTreino;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.TreinoPlanejadoEditService;
import br.com.menthoros.backend.services.helper.TssCalculatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class TreinoPlanejadoEditServiceImpl implements TreinoPlanejadoEditService {

    private final PlanoSemanalRepository planoSemanalRepository;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final TssCalculatorService tssCalculatorService;
    private final TreinoMapper treinoMapper;

    /**
     * Idempotent: NO — altera estado do treino a cada chamada.
     * Side Effects: Database update (TreinoPlanejado)
     * Tenant-aware: YES
     */
    @Override
    @Transactional
    public TreinoPlanejadoOutputDto editarTreino(UUID planoId, UUID treinoId, TreinoPlanejadoPatchDto patch) {
        if (planoId == null) throw new IllegalArgumentException("planoId não pode ser nulo");
        if (treinoId == null) throw new IllegalArgumentException("treinoId não pode ser nulo");
        if (patch == null) throw new IllegalArgumentException("patch não pode ser nulo");

        UUID tenantId = TenantContext.getRequiredTenantId();

        log.info("Editando treino: treinoId={}, planoId={}, tenantId={}", treinoId, planoId, tenantId);

        PlanoSemanal plano = planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Plano não encontrado: " + planoId));

        if (plano.getReviewStatus() != PlanoReviewStatus.AGUARDANDO_REVISAO) {
            throw new DomainRuleViolationException(
                    "Treino só pode ser editado quando o plano está em revisão (status atual: " + plano.getReviewStatus() + ")"
            );
        }

        TreinoPlanejado treino = treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Treino não encontrado: " + treinoId));

        if (!treino.getPlanoSemanal().getId().equals(planoId)) {
            throw new DomainNotFoundException("Treino não encontrado: " + treinoId);
        }

        BigDecimal distanciaAnterior = treino.getDistanciaKm();
        Duration duracaoAnterior = treino.getDuracaoMin();

        aplicarPatch(treino, patch);
        recalcularTssSeNecessario(treino, patch, distanciaAnterior, duracaoAnterior);

        treino.setEditadoPeloCoach(true);

        TreinoPlanejado salvo = treinoPlanejadoRepository.save(treino);

        log.info("Treino editado com sucesso: treinoId={}, tenantId={}", treinoId, tenantId);

        return treinoMapper.toOutputDto(salvo);
    }

    private void aplicarPatch(TreinoPlanejado treino, TreinoPlanejadoPatchDto patch) {
        if (patch.tipoTreino() != null) treino.setTipoTreino(patch.tipoTreino());
        if (patch.descricao() != null) treino.setDescricao(patch.descricao());
        if (patch.distanciaKm() != null) treino.setDistanciaKm(patch.distanciaKm());
        if (patch.duracaoMin() != null) treino.setDuracaoMin(patch.duracaoMin());
        if (patch.zonaAlvo() != null) treino.setZonaAlvo(patch.zonaAlvo());
        if (patch.percepcaoEsforcoEsperada() != null) treino.setPercepcaoEsforcoEsperada(patch.percepcaoEsforcoEsperada());
        if (patch.observacao() != null) treino.setObservacao(patch.observacao());
        if (patch.etapas() != null) aplicarEtapasPatch(treino, patch.etapas());
    }

    private void aplicarEtapasPatch(TreinoPlanejado treino, List<EtapaTreinoDto> etapasDto) {
        if (treino.getEtapas() == null) {
            treino.setEtapas(new ArrayList<>());
        }
        treino.getEtapas().clear();

        for (int i = 0; i < etapasDto.size(); i++) {
            EtapaTreinoDto dto = etapasDto.get(i);
            EtapaTreino etapa = new EtapaTreino();
            etapa.setTreinoPlanejado(treino);
            etapa.setOrdem(dto.ordem() != null ? dto.ordem() : i + 1);
            etapa.setTipoEtapa(dto.tipoEtapa() != null ? dto.tipoEtapa().name() : null);
            etapa.setDescricaoEtapa(dto.descricaoEtapa());
            etapa.setDuracaoMin(dto.duracaoMin());
            etapa.setDistanciaKm(dto.distanciaKm() != null ? BigDecimal.valueOf(dto.distanciaKm()) : null);
            etapa.setFcAlvoEtapa(dto.fcAlvoEtapa());
            etapa.setRepeticoes(dto.repeticoes());
            treino.getEtapas().add(etapa);
        }
    }

    private void recalcularTssSeNecessario(TreinoPlanejado treino, TreinoPlanejadoPatchDto patch,
                                            BigDecimal distanciaAnterior, Duration duracaoAnterior) {
        if (patch.tssPlanejado() != null) {
            treino.setTssPlanejado(patch.tssPlanejado());
            return;
        }
        boolean mudouDistancia = patch.distanciaKm() != null
                && !patch.distanciaKm().equals(distanciaAnterior);
        boolean mudouDuracao = patch.duracaoMin() != null
                && !patch.duracaoMin().equals(duracaoAnterior);

        if (mudouDistancia || mudouDuracao) {
            int tssRecalculado = tssCalculatorService.calcularTssEstimado(
                    treino.getDuracaoMin(), treino.getPercepcaoEsforcoEsperada()
            );
            treino.setTssPlanejado(tssRecalculado);
        }
    }
}
