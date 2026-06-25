package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.*;
import br.com.menthoros.backend.entity.*;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.mapper.ProvaMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.CoachAthleteProfileService;
import br.com.menthoros.backend.services.CoachAttentionQueueService;
import br.com.menthoros.backend.services.PlanoService;
import br.com.menthoros.backend.services.SugestaoCoachService;
import br.com.menthoros.backend.services.helper.ThresholdInferenceService;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoachAthleteProfileServiceImpl implements CoachAthleteProfileService {

    private final AtletaRepository atletaRepository;
    private final AtletaProgressService atletaProgressService;
    private final CoachAttentionQueueService coachAttentionQueueService;
    private final SugestaoCoachService sugestaoCoachService;
    private final PlanoService planoService;
    private final PlanoMetadadosRepository planoMetadadosRepository;
    private final ProvaRepository provaRepository;
    private final ProvaMapper provaMapper;

    /**
     * Idempotent: YES — leitura pura. Side Effects: NONE. Tenant-aware: YES.
     */
    @Override
    @Transactional(readOnly = true)
    public AtletaPerfilCoachOutputDto buscarPerfil(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        log.info("buscarPerfil: atletaId={}, tenantId={}", atletaId, tenantId);

        long t0 = System.nanoTime();
        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado"));
        log.debug("[perfil] atleta: {}ms", ms(t0));

        List<String> avisos = new ArrayList<>();

        long t1 = System.nanoTime();
        List<PmcPontoDto> pmc = buscarLista("pmc", avisos,
                () -> atletaProgressService.getHistoricoPmc(atletaId, null, null));
        log.debug("[perfil] pmc: {}ms", ms(t1));

        long t2 = System.nanoTime();
        List<AderenciasSemanalDto> aderencia = buscarLista("aderenciaSemanal", avisos,
                () -> atletaProgressService.getAderenciaSemanal(atletaId, 8));
        log.debug("[perfil] aderencia: {}ms", ms(t2));

        long t3 = System.nanoTime();
        List<RecordeDto> recordes = buscarLista("recordes", avisos,
                () -> atletaProgressService.getRecordes(atletaId));
        log.debug("[perfil] recordes: {}ms", ms(t3));

        long t4 = System.nanoTime();
        AtletaPerfilCoachOutputDto.PlanoVigenteDto planoVigente = buscarNullable("planoVigente", avisos,
                () -> resolverPlanoVigente(atletaId, tenantId));
        log.debug("[perfil] plano: {}ms", ms(t4));

        long t5 = System.nanoTime();
        List<AtletaPerfilCoachOutputDto.SinalRecenteDto> sinais = buscarLista("sinaisRecentes", avisos,
                () -> coachAttentionQueueService.getSinaisParaAtleta(atletaId, 3).stream()
                        .map(i -> new AtletaPerfilCoachOutputDto.SinalRecenteDto(
                                i.primaryReason(), i.severity(), i.generatedAt(), i.suggestedAction(),
                                null)) // sugestaoId — v1: match heurístico não implementado
                        .toList());
        log.debug("[perfil] sinais: {}ms", ms(t5));

        long t6 = System.nanoTime();
        List<AtletaPerfilCoachOutputDto.SugestaoRecenteDto> sugestoes = buscarLista("sugestoesRecentes", avisos,
                () -> sugestaoCoachService.listarPorAtleta(atletaId).stream()
                        .map(s -> new AtletaPerfilCoachOutputDto.SugestaoRecenteDto(
                                s.id(), s.tipo(), s.status(), s.createdAt()))
                        .toList());
        log.debug("[perfil] sugestoes: {}ms", ms(t6));

        AtletaPerfilCoachOutputDto.LimiareisInferidosDto limiareisInferidos =
                resolverLimiareisInferidos(atletaId, atleta);

        String nome = atleta.getSobrenome() != null
                ? atleta.getNome() + " " + atleta.getSobrenome()
                : atleta.getNome();

        List<ProvaOutputDto> provas = buscarProvas(atletaId, tenantId);
        
        return new AtletaPerfilCoachOutputDto(
                atletaId,
                nome,
                atleta.getObjetivo(),
                getProximaProva(provas),
                atleta.getNivelExperiencia() != null ? atleta.getNivelExperiencia().name() : null,
                pmc,
                aderencia,
                planoVigente,
                sinais,
                sugestoes,
                recordes,
                Instant.now(),
                avisos.isEmpty() ? null : avisos,
                limiareisInferidos
        );
    }

    private static @Nullable ProvaOutputDto getProximaProva(List<ProvaOutputDto> provas) {
        LocalDate hoje = LocalDate.now();
        return provas.stream()
                .filter(provaOutputDto -> provaOutputDto.dataProva() != null)
                .filter(provaOutputDto -> !provaOutputDto.dataProva().isBefore(hoje))
                .min(Comparator.comparing(ProvaOutputDto::dataProva))
                .orElse(null);
    }

    private AtletaPerfilCoachOutputDto.LimiareisInferidosDto resolverLimiareisInferidos(UUID atletaId, Atleta atleta) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        PlanoMetaDados metaDados = planoMetadadosRepository
                .findLatestByAtletaIdAndTenantId(atletaId, tenantId).orElse(null);
        if (metaDados == null) return null;
        if (metaDados.getFcLimiarEstimado() == null && metaDados.getPaceLimiarEstimado() == null) return null;

        LocalDate hoje = LocalDate.now();
        boolean fcDesatualizado = atleta.getFcLimiar() == null || atleta.getDataUltimoTesteFc() == null
                || ChronoUnit.DAYS.between(atleta.getDataUltimoTesteFc(), hoje)
                        > ThresholdInferenceService.DIAS_LIMIAR_DESATUALIZACAO;
        boolean paceDesatualizado = atleta.getPaceLimiar() == null || atleta.getDataUltimoTestePace() == null
                || ChronoUnit.DAYS.between(atleta.getDataUltimoTestePace(), hoje)
                        > ThresholdInferenceService.DIAS_LIMIAR_DESATUALIZACAO;

        if (!fcDesatualizado && !paceDesatualizado) return null;

        return new AtletaPerfilCoachOutputDto.LimiareisInferidosDto(
                fcDesatualizado ? metaDados.getFcLimiarEstimado() : null,
                paceDesatualizado && metaDados.getPaceLimiarEstimado() != null
                        ? ThresholdInferenceService.formatarPace(metaDados.getPaceLimiarEstimado()) : null,
                fcDesatualizado ? metaDados.getConfiancaInferenciaFc() : null,
                paceDesatualizado ? metaDados.getConfiancaInferenciaPace() : null,
                metaDados.getDataInferenciaLimiar()
        );
    }

    private AtletaPerfilCoachOutputDto.PlanoVigenteDto resolverPlanoVigente(UUID atletaId, UUID tenantId) {
        Optional<PlanoSemanal> planoOpt = planoService.findPlanoVigenteRelevante(atletaId, tenantId);
        if (planoOpt.isEmpty()) return null;

        PlanoSemanal plano = planoOpt.get();
        List<AtletaPerfilCoachOutputDto.TreinoPlanejadoResumoDto> treinos;

        if (plano.getReviewStatus() == PlanoReviewStatus.APROVADO
                || plano.getReviewStatus() == PlanoReviewStatus.AGUARDANDO_REVISAO) {
            treinos = plano.getTreinosPlanejados().stream()
                    .sorted(Comparator.comparing(TreinoPlanejado::getDataTreino))
                    .map(tp -> new AtletaPerfilCoachOutputDto.TreinoPlanejadoResumoDto(
                            tp.getId(),
                            tp.getDiaSemana().name(),
                            tp.getTipoTreino().name(),
                            tp.getDistanciaKm() != null ? tp.getDistanciaKm().doubleValue() : 0.0,
                            tp.getStatusTreino().name(),
                            tp.getDuracaoMin() != null ? tp.getDuracaoMin().toString() : null,
                            tp.getZonaAlvo(),
                            tp.getPercepcaoEsforcoEsperada(),
                            safeGetEtapasResumo(tp)))
                    .toList();
        } else {
            treinos = List.of();
        }

        return new AtletaPerfilCoachOutputDto.PlanoVigenteDto(
                plano.getId(),
                plano.getSemanaInicio(),
                plano.getSemanaFim(),
                plano.getReviewStatus(),
                treinos
        );
    }

    private List<EtapaTreinoDto> safeGetEtapasResumo(TreinoPlanejado tp) {
        try {
            List<EtapaTreino> etapas = tp.getEtapas();
            if (etapas == null || etapas.isEmpty()) return null;
            return etapas.stream()
                    .map(e -> new EtapaTreinoDto(
                            e.getOrdem(), e.getTipoEtapa(), e.getDescricaoEtapa(),
                            e.getDuracaoMin(),
                            e.getDistanciaKm() != null ? e.getDistanciaKm().doubleValue() : null,
                            e.getFcAlvoEtapa(), e.getRitmoAlvo(), e.getRepeticoes(),
                            e.getBlocoId(), e.getBlocoRepeticoes()))
                    .toList();
        } catch (Exception e) {
            return null;
        }
    }

    private <T> List<T> buscarLista(String campo, List<String> avisos, Supplier<List<T>> fn) {
        try {
            return fn.get();
        } catch (DomainNotFoundException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[perfil] erro ao buscar {}: {}", campo, e);
            avisos.add(campo);
            return List.of();
        }
    }

    private <T> T buscarNullable(String campo, List<String> avisos, Supplier<T> fn) {
        try {
            return fn.get();
        } catch (DomainNotFoundException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[perfil] erro ao buscar {}: {}", campo, e);
            avisos.add(campo);
            return null;
        }
    }

    private long ms(long nanoStart) {
        return (System.nanoTime() - nanoStart) / 1_000_000;
    }

    private List<ProvaOutputDto> buscarProvas(UUID id, UUID tenantId) {
        return provaRepository.findUpcomingByAtletaIdAndTenantId(id, tenantId, LocalDate.now()).stream()
                .map(provaMapper::toOutputDto)
                .toList();
    }
}
