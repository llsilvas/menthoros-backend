package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.EtapaInputDto;
import br.com.menthoros.backend.dto.input.TreinoPlanejadoAddDto;
import br.com.menthoros.backend.dto.output.TreinoPlanejadoOutputDto;
import br.com.menthoros.backend.entity.EtapaTreino;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.TreinoPlanejadoAddService;
import br.com.menthoros.backend.services.helper.TssCalculatorService;
import br.com.menthoros.backend.util.Utils;
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
public class TreinoPlanejadoAddServiceImpl implements TreinoPlanejadoAddService {

    private final PlanoSemanalRepository planoSemanalRepository;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final TssCalculatorService tssCalculatorService;
    private final TreinoMapper treinoMapper;

    /**
     * Idempotent: NO — cria nova entidade a cada chamada.
     * Side Effects: Database insert (TreinoPlanejado + EtapaTreino via cascade)
     * Tenant-aware: YES
     */
    @Override
    @Transactional
    public TreinoPlanejadoOutputDto adicionarTreino(UUID planoId, TreinoPlanejadoAddDto dto) {
        if (planoId == null) throw new IllegalArgumentException("planoId não pode ser nulo");
        if (dto == null) throw new IllegalArgumentException("dto não pode ser nulo");

        UUID tenantId = TenantContext.getRequiredTenantId();
        int etapasCount = dto.etapas() != null ? dto.etapas().size() : 0;

        log.info("coach-adicionou-treino: planoId={}, tenantId={}, tipoTreino={}, comEtapas={}",
                planoId, tenantId, dto.tipoTreino(), etapasCount);

        PlanoSemanal plano = planoSemanalRepository.findByIdWithDependenciesAndTenant(planoId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Plano não encontrado: " + planoId));

        validarEstadoDoPlano(plano, dto);

        TipoTreino tipoTreino = converterTipoTreino(dto.tipoTreino());
        DiaSemana diaSemana = Utils.converterDayOfWeekParaDiaSemana(dto.dataTreino().getDayOfWeek());
        Duration duracaoMin = dto.duracaoMin() != null ? Duration.ofMinutes(dto.duracaoMin()) : Duration.ZERO;
        Integer tssPlanejado = calcularTss(dto, duracaoMin);

        TreinoPlanejado treino = construirTreino(dto, plano, tipoTreino, diaSemana, duracaoMin, tssPlanejado);

        TreinoPlanejado salvo = treinoPlanejadoRepository.save(treino);
        log.info("Treino adicionado: treinoId={}, planoId={}, tenantId={}", salvo.getId(), planoId, tenantId);

        return treinoMapper.toOutputDto(salvo);
    }

    private void validarEstadoDoPlano(PlanoSemanal plano, TreinoPlanejadoAddDto dto) {
        if (plano.getReviewStatus() != PlanoReviewStatus.AGUARDANDO_REVISAO) {
            throw new DomainRuleViolationException(
                    "Plano não está em revisão. Status atual: " + plano.getReviewStatus());
        }
        if (dto.dataTreino().isBefore(plano.getSemanaInicio())
                || dto.dataTreino().isAfter(plano.getSemanaFim())) {
            throw new DomainRuleViolationException(
                    "Data do treino fora do intervalo do plano: " + dto.dataTreino()
                    + " não está entre " + plano.getSemanaInicio() + " e " + plano.getSemanaFim());
        }
        long totalTreinos = treinoPlanejadoRepository.countByPlanoSemanalId(plano.getId());
        if (totalTreinos >= 14) {
            throw new DomainRuleViolationException("Limite de 14 treinos por semana atingido");
        }
    }

    private TipoTreino converterTipoTreino(String tipoTreinoStr) {
        try {
            return TipoTreino.fromValue(tipoTreinoStr);
        } catch (IllegalArgumentException e) {
            throw new DomainRuleViolationException("Tipo de treino inválido: " + tipoTreinoStr);
        }
    }

    private Integer calcularTss(TreinoPlanejadoAddDto dto, Duration duracaoMin) {
        if (dto.tssPlanejado() != null) {
            return dto.tssPlanejado();
        }
        if (dto.duracaoMin() != null) {
            return tssCalculatorService.calcularTssEstimado(duracaoMin, dto.percepcaoEsforcoEsperada());
        }
        return null;
    }

    private TreinoPlanejado construirTreino(TreinoPlanejadoAddDto dto, PlanoSemanal plano,
                                             TipoTreino tipoTreino, DiaSemana diaSemana,
                                             Duration duracaoMin, Integer tssPlanejado) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setPlanoSemanal(plano);
        treino.setDataTreino(dto.dataTreino());
        treino.setDiaSemana(diaSemana);
        treino.setTipoTreino(tipoTreino);
        treino.setDescricao(dto.descricao());
        treino.setDistanciaKm(dto.distanciaKm() != null ? BigDecimal.valueOf(dto.distanciaKm()) : null);
        treino.setDuracaoMin(duracaoMin);
        treino.setZonaAlvo(dto.zonaAlvo());
        treino.setPercepcaoEsforcoEsperada(dto.percepcaoEsforcoEsperada());
        treino.setTssPlanejado(tssPlanejado);
        treino.setObservacao(dto.observacoes());
        treino.setAdicionadoPeloCoach(true);
        treino.setStatusTreino(TreinoExecucaoStatus.PENDENTE);
        treino.setFonteDados(FonteDados.MANUAL);

        if (dto.etapas() != null && !dto.etapas().isEmpty()) {
            treino.setEtapas(construirEtapas(dto.etapas(), treino));
        }

        return treino;
    }

    private List<EtapaTreino> construirEtapas(List<EtapaInputDto> etapasDto, TreinoPlanejado treino) {
        List<EtapaTreino> etapas = new ArrayList<>();
        int ordem = 1;
        for (EtapaInputDto dto : etapasDto) {
            if ("BLOCO".equalsIgnoreCase(dto.tipoEtapa())) {
                ordem = expandirBloco(dto, treino, etapas, ordem);
            } else {
                etapas.add(buildEtapaSimples(dto, treino, ordem++, null, null));
            }
        }
        return etapas;
    }

    private int expandirBloco(EtapaInputDto blocoDto, TreinoPlanejado treino,
                               List<EtapaTreino> etapas, int ordemInicial) {
        int reps = blocoDto.blocoRepeticoes() != null && blocoDto.blocoRepeticoes() > 0
                ? blocoDto.blocoRepeticoes() : 1;
        UUID blocoId = UUID.randomUUID();
        int ordem = ordemInicial;
        List<EtapaInputDto> subEtapas = blocoDto.subEtapas() != null ? blocoDto.subEtapas() : List.of();
        for (int r = 0; r < reps; r++) {
            for (EtapaInputDto sub : subEtapas) {
                etapas.add(buildEtapaSimples(sub, treino, ordem++, blocoId, reps));
            }
        }
        return ordem;
    }

    private EtapaTreino buildEtapaSimples(EtapaInputDto dto, TreinoPlanejado treino,
                                           int ordem, UUID blocoId, Integer blocoRepeticoes) {
        EtapaTreino etapa = new EtapaTreino();
        etapa.setTipoEtapa(dto.tipoEtapa() != null ? dto.tipoEtapa().toUpperCase() : null);
        etapa.setDescricaoEtapa(dto.descricaoEtapa());
        etapa.setDuracaoMin(dto.duracaoMin());
        etapa.setDistanciaKm(dto.distanciaKm() != null ? BigDecimal.valueOf(dto.distanciaKm()) : null);
        etapa.setFcAlvoEtapa(dto.fcAlvoEtapa());
        etapa.setRepeticoes(dto.repeticoes());
        etapa.setBlocoId(blocoId);
        etapa.setBlocoRepeticoes(blocoRepeticoes);
        etapa.setOrdem(ordem);
        etapa.setTreinoPlanejado(treino);
        return etapa;
    }
}
