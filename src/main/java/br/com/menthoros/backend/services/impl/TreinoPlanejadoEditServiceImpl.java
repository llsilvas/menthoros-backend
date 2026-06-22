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
import org.hibernate.Hibernate;
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
        if (patch.etapas() != null) aplicarEtapasPatch(treino, expandirRepeticoes(patch.etapas()));
    }

    /**
     * Expande blocos INTERVALADO com repeticoes > 1 em N pares [INTERVALADO(rep=1), RECUPERACAO(rep=1)].
     * O RECUPERACAO imediatamente seguinte é consumido como template para cada par gerado.
     * Renumera a ordem sequencialmente após a expansão.
     *
     * Exemplo: [AQUECIMENTO, INTERVALADO(rep=4), RECUPERACAO, DESAQUECIMENTO]
     *       → [AQUECIMENTO, INTERVALADO, RECUPERACAO, INTERVALADO, RECUPERACAO,
     *           INTERVALADO, RECUPERACAO, INTERVALADO, RECUPERACAO, DESAQUECIMENTO]
     */
    private List<EtapaTreinoDto> expandirRepeticoes(List<EtapaTreinoDto> etapas) {
        List<EtapaTreinoDto> expandido = new ArrayList<>();
        int i = 0;
        while (i < etapas.size()) {
            EtapaTreinoDto atual = etapas.get(i);
            int reps = atual.repeticoes() != null ? atual.repeticoes() : 1;

            if ("INTERVALADO".equals(atual.tipoEtapa()) && reps > 1) {
                EtapaTreinoDto recuperacao = null;
                if (i + 1 < etapas.size() && "RECUPERACAO".equals(etapas.get(i + 1).tipoEtapa())) {
                    recuperacao = etapas.get(i + 1);
                    i++;
                }
                for (int r = 0; r < reps; r++) {
                    expandido.add(comRepeticoes(atual, 1));
                    if (recuperacao != null) {
                        expandido.add(comRepeticoes(recuperacao, 1));
                    }
                }
            } else {
                expandido.add(atual);
            }
            i++;
        }

        List<EtapaTreinoDto> resultado = new ArrayList<>(expandido.size());
        for (int j = 0; j < expandido.size(); j++) {
            EtapaTreinoDto e = expandido.get(j);
            resultado.add(new EtapaTreinoDto(j + 1, e.tipoEtapa(), e.descricaoEtapa(),
                    e.duracaoMin(), e.distanciaKm(), e.fcAlvoEtapa(), 1));
        }
        return resultado;
    }

    private static EtapaTreinoDto comRepeticoes(EtapaTreinoDto dto, int repeticoes) {
        return new EtapaTreinoDto(dto.ordem(), dto.tipoEtapa(), dto.descricaoEtapa(),
                dto.duracaoMin(), dto.distanciaKm(), dto.fcAlvoEtapa(), repeticoes);
    }

    private void aplicarEtapasPatch(TreinoPlanejado treino, List<EtapaTreinoDto> etapasDto) {
        Hibernate.initialize(treino.getEtapas());
        if (treino.getEtapas() == null) {
            treino.setEtapas(new ArrayList<>());
        }
        treino.getEtapas().clear();

        for (int i = 0; i < etapasDto.size(); i++) {
            EtapaTreinoDto dto = etapasDto.get(i);
            EtapaTreino etapa = new EtapaTreino();
            etapa.setTreinoPlanejado(treino);
            etapa.setOrdem(dto.ordem() != null ? dto.ordem() : i + 1);
            etapa.setTipoEtapa(dto.tipoEtapa());
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
