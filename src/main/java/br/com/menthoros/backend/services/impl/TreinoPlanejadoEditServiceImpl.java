package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.EtapaInputDto;
import br.com.menthoros.backend.dto.input.TreinoPlanejadoPatchDto;
import br.com.menthoros.backend.dto.output.TreinoPlanejadoOutputDto;
import br.com.menthoros.backend.entity.EtapaTreino;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.mapper.EtapaMapper;
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
    private final EtapaMapper etapaMapper;

    /**
     * Idempotent: NO — altera estado do treino a cada chamada.
     * Side Effects: Database update (TreinoPlanejado)
     * Tenant-aware: YES
     */
    @Override
    @Transactional
    public TreinoPlanejadoOutputDto editarTreino(UUID planoId, UUID treinoId, TreinoPlanejadoPatchDto patch) {
        if (planoId == null) throw new IllegalArgumentException("Identificador do plano é obrigatório");
        if (treinoId == null) throw new IllegalArgumentException("Identificador do treino é obrigatório");
        if (patch == null) throw new IllegalArgumentException("Dados de edição são obrigatórios");

        UUID tenantId = TenantContext.getRequiredTenantId();

        log.info("Editando treino: treinoId={}, planoId={}, tenantId={}", treinoId, planoId, tenantId);

        PlanoSemanal plano = planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Plano não encontrado: " + planoId));

        if (plano.getReviewStatus() != PlanoReviewStatus.AGUARDANDO_REVISAO) {
            throw new DomainRuleViolationException("Treino só pode ser editado quando o plano está aguardando revisão.");
        }

        TreinoPlanejado treino = treinoPlanejadoRepository
                .findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Treino não encontrado: " + treinoId));

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
        if (patch.etapas() != null) aplicarEtapasPatch(treino, expandirEtapas(patch.etapas()));
    }

    private List<EtapaInputDto> expandirEtapas(List<EtapaInputDto> etapas) {
        return expandirRepeticoes(expandirBlocos(etapas));
    }

    /**
     * Expande etapas do tipo BLOCO (modelo coach) em etapas simples com blocoRepeticoes preenchido.
     * Cada sub-etapa é duplicada N vezes onde N = blocoRepeticoes do bloco pai.
     */
    private static List<EtapaInputDto> expandirBlocos(List<EtapaInputDto> etapas) {
        List<EtapaInputDto> resultado = new ArrayList<>();
        for (EtapaInputDto etapa : etapas) {
            if ("BLOCO".equalsIgnoreCase(etapa.tipoEtapa())) {
                int reps = etapa.blocoRepeticoes() != null && etapa.blocoRepeticoes() > 0
                        ? etapa.blocoRepeticoes() : 1;
                List<EtapaInputDto> subEtapas = etapa.subEtapas() != null ? etapa.subEtapas() : List.of();
                for (EtapaInputDto sub : subEtapas) {
                    if (sub.subEtapas() != null && !sub.subEtapas().isEmpty()) {
                        throw new DomainRuleViolationException("Sub-etapas não podem conter sub-etapas aninhadas");
                    }
                }
                for (int r = 0; r < reps; r++) {
                    for (EtapaInputDto sub : subEtapas) {
                        resultado.add(new EtapaInputDto(
                                sub.tipoEtapa() != null ? sub.tipoEtapa().toUpperCase() : null,
                                sub.descricaoEtapa(),
                                sub.duracaoMin(),
                                sub.distanciaKm(),
                                sub.fcAlvoEtapa(),
                                sub.repeticoes(),
                                reps,
                                null
                        ));
                    }
                }
            } else {
                resultado.add(etapa);
            }
        }
        return resultado;
    }

    /**
     * Expande blocos INTERVALADO com repeticoes > 1 em N pares [INTERVALADO(rep=1), RECUPERACAO(rep=1)].
     * Normaliza tipoEtapa para uppercase. O RECUPERACAO imediatamente seguinte é consumido como template.
     *
     * Exemplo: [AQUECIMENTO, INTERVALADO(rep=4), RECUPERACAO, DESAQUECIMENTO]
     *       → [AQUECIMENTO, INT, REC, INT, REC, INT, REC, INT, REC, DESAQUECIMENTO]
     */
    private List<EtapaInputDto> expandirRepeticoes(List<EtapaInputDto> etapas) {
        List<EtapaInputDto> expandido = new ArrayList<>();
        int i = 0;
        while (i < etapas.size()) {
            EtapaInputDto atual = etapas.get(i);
            String tipo = atual.tipoEtapa() != null ? atual.tipoEtapa().toUpperCase() : "";
            int reps = atual.repeticoes() != null ? atual.repeticoes() : 1;

            if ("INTERVALADO".equals(tipo) && reps > 1) {
                EtapaInputDto recuperacao = null;
                if (i + 1 < etapas.size()) {
                    String tipoProximo = etapas.get(i + 1).tipoEtapa() != null
                            ? etapas.get(i + 1).tipoEtapa().toUpperCase() : "";
                    if ("RECUPERACAO".equals(tipoProximo)) {
                        recuperacao = etapas.get(i + 1);
                        i++;
                    }
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
        return expandido;
    }

    private static EtapaInputDto comRepeticoes(EtapaInputDto dto, int repeticoes) {
        return new EtapaInputDto(
                dto.tipoEtapa() != null ? dto.tipoEtapa().toUpperCase() : null,
                dto.descricaoEtapa(),
                dto.duracaoMin(),
                dto.distanciaKm(),
                dto.fcAlvoEtapa(),
                repeticoes,
                dto.blocoRepeticoes(),
                null
        );
    }

    private void aplicarEtapasPatch(TreinoPlanejado treino, List<EtapaInputDto> etapasDto) {
        Hibernate.initialize(treino.getEtapas());
        if (treino.getEtapas() == null) {
            treino.setEtapas(new ArrayList<>());
        }
        treino.getEtapas().clear();

        for (int i = 0; i < etapasDto.size(); i++) {
            EtapaTreino etapa = etapaMapper.toEntity(etapasDto.get(i));
            etapa.setTreinoPlanejado(treino);
            etapa.setOrdem(i + 1);
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
