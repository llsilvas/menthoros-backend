package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.*;
import br.com.menthoros.backend.entity.*;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.StatusVencimentoPlano;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.mapper.ProvaMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.CoachAthleteProfileService;
import br.com.menthoros.backend.services.CoachAttentionQueueService;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import br.com.menthoros.backend.services.PlanoService;
import br.com.menthoros.backend.services.SugestaoCoachService;
import br.com.menthoros.backend.services.helper.ThresholdInferenceService;

import java.time.LocalDate;
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
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final IntervalsIcuConnectionService intervalsIcuConnectionService;
    private final ThresholdInferenceService thresholdInferenceService;

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

        long t7 = System.nanoTime();
        List<AtletaPerfilCoachOutputDto.RealizadoRecenteDto> realizadosRecentes = buscarLista(
                "realizadosRecentes", avisos, () -> buscarRealizadosRecentes(atletaId, tenantId));
        log.debug("[perfil] realizadosRecentes: {}ms", ms(t7));

        return new AtletaPerfilCoachOutputDto(
                atletaId,
                nome,
                atleta.getObjetivo(),
                getProximaProva(provas),
                atleta.getNivelExperiencia() != null ? atleta.getNivelExperiencia().name() : null,
                atleta.getIdade() != null ? atleta.getIdade() : null,
                pmc,
                aderencia,
                planoVigente,
                sinais,
                sugestoes,
                recordes,
                Instant.now(),
                avisos.isEmpty() ? null : avisos,
                limiareisInferidos,
                atleta.getTipoPlanoAtleta(),
                atleta.getDataVencimentoPlano(),
                StatusVencimentoPlano.resolver(atleta.getDataVencimentoPlano(), LocalDate.now()),
                realizadosRecentes
        );
    }

    /** Últimos 7 dias (hoje inclusive), mais recente primeiro — mesma janela do design.md D3. */
    private List<AtletaPerfilCoachOutputDto.RealizadoRecenteDto> buscarRealizadosRecentes(UUID atletaId, UUID tenantId) {
        LocalDate hoje = LocalDate.now();
        return treinoRealizadoRepository
                .findByAtletaIdAndTenantIdAndDataTreinoBetween(atletaId, tenantId, hoje.minusDays(6), hoje)
                .stream()
                .sorted(Comparator.comparing(TreinoRealizado::getDataTreino).reversed())
                .map(tr -> new AtletaPerfilCoachOutputDto.RealizadoRecenteDto(
                        tr.getId(),
                        tr.getDataTreino(),
                        tr.getTipoTreino() != null ? tr.getTipoTreino().name() : null,
                        tr.getFonteDados(),
                        tr.getDuracaoMin() != null && !tr.getDuracaoMin().isZero()
                                ? (int) tr.getDuracaoMin().toMinutes() : null,
                        tr.getDistanciaKm() != null ? tr.getDistanciaKm().doubleValue() : null,
                        tr.getPercepcaoEsforco(),
                        tr.getSensacoes() != null ? List.copyOf(tr.getSensacoes()) : null,
                        tr.getFeedbackAtleta(),
                        tr.getFeedbackRegistradoEm()))
                .toList();
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
        boolean fcDesatualizado = thresholdInferenceService.isFcLimiarDesatualizado(atleta, hoje);
        boolean paceDesatualizado = thresholdInferenceService.isPaceLimiarDesatualizado(atleta, hoje);

        if (!fcDesatualizado && !paceDesatualizado) return null;

        return new AtletaPerfilCoachOutputDto.LimiareisInferidosDto(
                fcDesatualizado ? metaDados.getFcLimiarEstimado() : null,
                paceDesatualizado && metaDados.getPaceLimiarEstimado() != null
                        ? ThresholdInferenceService.formatarPace(metaDados.getPaceLimiarEstimado()) : null,
                fcDesatualizado ? metaDados.getConfiancaInferenciaFc() : null,
                paceDesatualizado ? metaDados.getConfiancaInferenciaPace() : null,
                // Lê o valor persistido em PlanoMetaDados.fonteLimiarPace direto — não recomputa qual
                // seria a fonte agora (poderia divergir do que de fato gerou o valor salvo, ver design.md D6).
                paceDesatualizado ? metaDados.getFonteLimiarPace() : null,
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
            // conexão intervals.icu resolvida UMA vez por perfil (anti-N+1) e replicada em cada treino
            boolean atletaConectadoIntervalsIcu = intervalsIcuConnectionService
                    .conexaoAtiva(atletaId, tenantId).isPresent();

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
                            safeGetEtapasResumo(tp),
                            tp.getStatusSincronizacao() != null
                                    ? tp.getStatusSincronizacao().name()
                                    : StatusSincronizacao.NAO_SINCRONIZADO.name(),
                            atletaConectadoIntervalsIcu))
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
