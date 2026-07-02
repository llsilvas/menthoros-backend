package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.CheckinProntidaoInputDto;
import br.com.menthoros.backend.dto.output.CheckinProntidaoOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.CheckinProntidao;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.enums.NivelProntidao;
import br.com.menthoros.backend.exception.AccessDeniedException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.mapper.CheckinProntidaoMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.CheckinProntidaoRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.CheckinProntidaoService;
import br.com.menthoros.backend.services.helper.ReadinessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinProntidaoServiceImpl implements CheckinProntidaoService {

    private final CheckinProntidaoRepository checkinProntidaoRepository;
    private final MetricasDiariasRepository metricasDiariasRepository;
    private final AtletaRepository atletaRepository;
    private final ReadinessService readinessService;
    private final CheckinProntidaoMapper mapper;
    private final AtletaProgressService atletaProgressService;

    /**
     * Registra (ou atualiza, se já existir para a data) o checkin de prontidão do atleta,
     * calcula readiness e propaga o resultado para a {@code MetricasDiarias} do dia (se existir).
     *
     * Idempotent: YES — upsert por (atleta, data); chamar de novo no mesmo dia atualiza, não duplica.
     * Side Effects: Database insert/update em CheckinProntidao; update condicional em MetricasDiarias.
     * Tenant-aware: YES — usa TenantContext.getRequiredTenantId() via resolveAtleta().
     */
    @Override
    @Transactional
    public CheckinProntidaoOutputDto registrarCheckin(UUID atletaId, CheckinProntidaoInputDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("CheckinProntidaoInputDto não pode ser null");
        }
        UUID tenantId = TenantContext.getRequiredTenantId();
        Atleta atleta = resolveAtleta(atletaId, tenantId);
        LocalDate data = dto.data() != null ? dto.data() : LocalDate.now();

        log.info("Registrando checkin de prontidao: atletaId={} data={}", atletaId, data);

        CheckinProntidao checkin = checkinProntidaoRepository.findByAtletaIdAndData(atletaId, data, tenantId)
                .orElseGet(() -> CheckinProntidao.builder()
                        .atleta(atleta)
                        .data(data)
                        .tenantId(tenantId)
                        .build());

        checkin.setQualidadeSono(dto.qualidadeSono());
        checkin.setHumor(dto.humor());
        checkin.setDoresMusculares(dto.doresMusculares());
        checkin.setNivelEnergia(dto.nivelEnergia());
        checkin.setEstresse(dto.estresse());
        checkin.setObservacoes(dto.observacoes());

        BigDecimal score = readinessService.calcularScore(checkin);
        NivelProntidao nivel = readinessService.classificarNivel(score);
        checkin.setReadinessScore(score);
        checkin.setNivelProntidao(nivel);

        checkin = checkinProntidaoRepository.save(checkin);
        propagarParaMetricasDiarias(atletaId, tenantId, data, score, nivel);

        log.info("Checkin de prontidao registrado: atletaId={} data={} score={} nivel={}",
                atletaId, data, score, nivel);

        return mapper.toOutputDto(checkin);
    }

    /**
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES
     */
    @Override
    @Transactional(readOnly = true)
    @Nullable
    public CheckinProntidaoOutputDto buscarAtual(UUID atletaId, boolean chamadorEhAdmin) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        resolveAtleta(atletaId, tenantId);
        validarPosseOuAdmin(atletaId, chamadorEhAdmin);
        return checkinProntidaoRepository.findTopByAtletaIdOrderByDataDesc(atletaId, tenantId)
                .map(mapper::toOutputDto)
                .orElse(null);
    }

    /**
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES
     */
    @Override
    @Transactional(readOnly = true)
    public List<CheckinProntidaoOutputDto> buscarHistorico(UUID atletaId, int dias, boolean chamadorEhAdmin) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        resolveAtleta(atletaId, tenantId);
        validarPosseOuAdmin(atletaId, chamadorEhAdmin);
        int diasEfetivos = Math.min(Math.max(dias, 1), 90);
        LocalDate dataFim = LocalDate.now();
        LocalDate dataInicio = dataFim.minusDays(diasEfetivos - 1L);
        return checkinProntidaoRepository.findByAtletaIdAndDataBetween(atletaId, dataInicio, dataFim, tenantId)
                .stream()
                .map(mapper::toOutputDto)
                .toList();
    }

    /**
     * Bloqueia acesso de um atleta ao checkin de outro atleta do mesmo tenant (IDOR).
     * {@code @RequireTenant} no controller só valida que {@code atletaId} pertence ao tenant —
     * não que pertence ao chamador. Admins têm acesso irrestrito dentro do tenant.
     */
    private void validarPosseOuAdmin(UUID atletaId, boolean chamadorEhAdmin) {
        if (chamadorEhAdmin) {
            return;
        }
        UUID atletaIdAtual = atletaProgressService.resolverAtletaIdAtual();
        if (!atletaIdAtual.equals(atletaId)) {
            throw new AccessDeniedException("Atleta só pode acessar o próprio checkin de prontidão");
        }
    }

    /**
     * Propaga readiness para a MetricasDiarias do dia, se ela já existir. Não cria uma linha
     * nova — a criação de MetricasDiarias é responsabilidade do cálculo de TSB/CTL/ATL, fora
     * do escopo deste serviço.
     */
    private void propagarParaMetricasDiarias(UUID atletaId, UUID tenantId, LocalDate data, BigDecimal score, NivelProntidao nivel) {
        Optional<MetricasDiarias> metricasOpt =
                metricasDiariasRepository.findByAtletaIdAndDataAndTenantId(atletaId, data, tenantId);
        if (metricasOpt.isEmpty()) {
            log.debug("Sem MetricasDiarias para atletaId={} data={} — readiness nao propagado (linha inexistente)",
                    atletaId, data);
            return;
        }
        MetricasDiarias metricas = metricasOpt.get();
        metricas.setReadinessScore(score);
        metricas.setNivelProntidao(nivel);
        metricasDiariasRepository.save(metricas);
    }

    private Atleta resolveAtleta(UUID atletaId, UUID tenantId) {
        return atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado: " + atletaId));
    }
}
