package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.AderenciasSemanalDto;
import br.com.menthoros.backend.dto.output.AtletaPerfilCoachOutputDto;
import br.com.menthoros.backend.dto.output.PmcPontoDto;
import br.com.menthoros.backend.dto.output.RecordeDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.CoachAthleteProfileService;
import br.com.menthoros.backend.services.CoachAttentionQueueService;
import br.com.menthoros.backend.services.SugestaoCoachService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final PlanoSemanalRepository planoSemanalRepository;

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
                        .limit(3)
                        .map(s -> new AtletaPerfilCoachOutputDto.SugestaoRecenteDto(
                                s.id(), s.tipo(), s.status(), s.createdAt()))
                        .toList());
        log.debug("[perfil] sugestoes: {}ms", ms(t6));

        String nome = atleta.getSobrenome() != null
                ? atleta.getNome() + " " + atleta.getSobrenome()
                : atleta.getNome();

        return new AtletaPerfilCoachOutputDto(
                atletaId,
                nome,
                atleta.getObjetivo(),
                null, // provaAlvo — v1: derivar de provas fora do escopo; retorna null
                atleta.getNivelExperiencia() != null ? atleta.getNivelExperiencia().name() : null,
                pmc,
                aderencia,
                planoVigente,
                sinais,
                sugestoes,
                recordes,
                Instant.now(),
                avisos.isEmpty() ? null : avisos
        );
    }

    private AtletaPerfilCoachOutputDto.PlanoVigenteDto resolverPlanoVigente(UUID atletaId, UUID tenantId) {
        Optional<PlanoSemanal> planoOpt = planoSemanalRepository.findMostRecentRelevantPlano(atletaId, tenantId);
        if (planoOpt.isEmpty()) return null;

        PlanoSemanal plano = planoOpt.get();
        List<AtletaPerfilCoachOutputDto.TreinoPlanejadoResumoDto> treinos;

        if (plano.getReviewStatus() == PlanoReviewStatus.APROVADO) {
            treinos = plano.getTreinosPlanejados().stream()
                    .sorted(Comparator.comparing(TreinoPlanejado::getDataTreino))
                    .map(tp -> new AtletaPerfilCoachOutputDto.TreinoPlanejadoResumoDto(
                            tp.getDiaSemana().name(),
                            tp.getTipoTreino().name(),
                            tp.getDistanciaKm() != null ? tp.getDistanciaKm().doubleValue() : 0.0,
                            tp.getStatusTreino().name()))
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
}
