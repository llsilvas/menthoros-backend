package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.AderenciasSemanalDto;
import br.com.menthoros.backend.dto.output.AtletaHomeDto;
import br.com.menthoros.backend.dto.output.PmcPontoDto;
import br.com.menthoros.backend.dto.output.ReadinessDto;
import br.com.menthoros.backend.dto.output.RecordeDto;
import br.com.menthoros.backend.dto.output.ZonaDistribuicaoDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.CheckinProntidao;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.FaixaTsb;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.CheckinProntidaoRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.helper.ZonaTreinoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementação read-only do progresso do atleta.
 *
 * <p><b>Isolamento de tenant:</b> todo método público chama {@link #validarAtletaNoTenant} como
 * primeira instrução (consulta tenant-scoped). As leituras subsequentes usam só {@code atletaId} —
 * elas assumem que esse gate já confirmou o tenant. Não reordenar/remover o gate.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AtletaProgressServiceImpl implements AtletaProgressService {

    private static final int DIAS_PADRAO = 90;

    /** Bandas de distância (km) para detecção de PR por distância de referência. */
    private static final List<Alvo> ALVOS_RECORDE = List.of(
            new Alvo("5k", 4.85, 5.15),
            new Alvo("10k", 9.70, 10.30),
            new Alvo("21k", 20.50, 21.70)
    );

    private record Alvo(String label, double min, double max) {}

    private final AtletaRepository atletaRepository;
    private final UsuarioRepository usuarioRepository;
    private final MetricasDiariasRepository metricasDiariasRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final PlanoMetadadosRepository planoMetadadosRepository;
    private final ZonaTreinoService zonaTreinoService;
    private final AuthenticatedPrincipalResolver principalResolver;
    private final CheckinProntidaoRepository checkinProntidaoRepository;
    private final Clock clock;

    /**
     * Idempotent: YES — leitura. Side Effects: NONE. Tenant-aware: YES.
     */
    @Override
    @Transactional(readOnly = true)
    public List<PmcPontoDto> getHistoricoPmc(UUID atletaId, LocalDate from, LocalDate to) {
        validarAtletaNoTenant(atletaId);
        Intervalo intervalo = resolverIntervalo(from, to);

        return metricasDiariasRepository
                .findByAtletaIdAndDataBetweenOrderByDataAsc(atletaId, intervalo.from(), intervalo.to())
                .stream()
                .map(m -> new PmcPontoDto(m.getData(), m.getCtl(), m.getAtl(), m.getTsb(), m.getTss(),
                        FaixaTsb.classificarNome(m.getTsb())))
                .toList();
    }

    /**
     * Idempotent: YES — leitura. Side Effects: NONE. Tenant-aware: YES.
     * Tempo por zona derivado das etapas realizadas (FC média → zona). Etapas sem FC são ignoradas.
     */
    @Override
    @Transactional(readOnly = true)
    public ZonaDistribuicaoDto getDistribuicaoZonas(UUID atletaId, LocalDate from, LocalDate to) {
        Atleta atleta = validarAtletaNoTenant(atletaId);
        Intervalo intervalo = resolverIntervalo(from, to);

        long[] z = new long[6]; // índices 1..5
        List<TreinoRealizado> treinos =
                treinoRealizadoRepository.findByAtletaIdAndDataTreinoBetween(atletaId, intervalo.from(), intervalo.to());

        for (TreinoRealizado treino : treinos) {
            if (treino.getEtapasRealizadas() == null) continue;
            for (EtapaRealizada etapa : treino.getEtapasRealizadas()) {
                if (etapa.getFcMedia() == null || etapa.getDuracao() == null) continue;
                int zona = zonaTreinoService.identificarZonaFC(etapa.getFcMedia(), atleta);
                if (zona >= 1 && zona <= 5) {
                    z[zona] += etapa.getDuracao().getSeconds();
                }
            }
        }

        long total = z[1] + z[2] + z[3] + z[4] + z[5];
        return new ZonaDistribuicaoDto(z[1], z[2], z[3], z[4], z[5], total);
    }

    /**
     * Idempotent: YES — leitura. Side Effects: NONE. Tenant-aware: YES.
     */
    @Override
    @Transactional(readOnly = true)
    public List<RecordeDto> getRecordes(UUID atletaId) {
        validarAtletaNoTenant(atletaId);
        List<TreinoRealizado> treinos = treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId);

        List<RecordeDto> recordes = new ArrayList<>();
        for (Alvo alvo : ALVOS_RECORDE) {
            treinos.stream()
                    .filter(t -> t.getDistanciaKm() != null && t.getDuracaoMin() != null)
                    .filter(t -> {
                        double km = t.getDistanciaKm().doubleValue();
                        return km >= alvo.min() && km <= alvo.max();
                    })
                    .min((a, b) -> a.getDuracaoMin().compareTo(b.getDuracaoMin()))
                    .ifPresent(melhor -> recordes.add(new RecordeDto(
                            alvo.label(), melhor.getDuracaoMin().getSeconds(), melhor.getDataTreino(), melhor.getId())));
        }
        return recordes;
    }

    /**
     * Idempotent: YES — leitura. Side Effects: NONE. Tenant-aware: YES.
     * Heurística objetiva provisória (sem check-in subjetivo): score a partir do TSB de prontidão,
     * ajustado pelo RPE do último treino. Degrada para score nulo quando não há sinais.
     */
    @Override
    @Transactional(readOnly = true)
    public ReadinessDto getReadinessAtual(UUID atletaId) {
        validarAtletaNoTenant(atletaId);

        UUID tenantId = TenantContext.getRequiredTenantId();
        CheckinProntidao checkinHoje = checkinProntidaoRepository
                .findByAtletaIdAndData(atletaId, LocalDate.now(clock), tenantId)
                .orElse(null);

        PlanoMetaDados meta = planoMetadadosRepository.findByAtletaId(atletaId).orElse(null);
        Integer ultimoRpe = treinoRealizadoRepository.findTopByAtletaIdOrderByDataTreinoDesc(atletaId)
                .map(TreinoRealizado::getPercepcaoEsforco).orElse(null);

        Double tsbProntidao = meta != null ? meta.getTsbProntidaoAtual() : null;
        Double ctl = meta != null ? meta.getCtlAtual() : null;
        Double atl = meta != null ? meta.getAtlAtual() : null;
        ReadinessDto.Fatores fatores = new ReadinessDto.Fatores(tsbProntidao, ctl, atl, ultimoRpe);

        // Check-in de hoje é fonte única de verdade quando existe — não recalcular
        // (readinessScore/nivelProntidao já foram calculados e persistidos no registro do checkin).
        if (checkinHoje != null) {
            int scoreDoCheckin = Math.max(0, Math.min(100,
                    (int) Math.round(checkinHoje.getReadinessScore().doubleValue() * 100)));
            return new ReadinessDto(scoreDoCheckin, checkinHoje.getNivelProntidao().name(), fatores,
                    "Baseado no seu check-in de hoje.");
        }

        if (tsbProntidao == null) {
            return new ReadinessDto(null, "INDISPONIVEL", fatores,
                    "Dados insuficientes: sem métricas de prontidão e sem check-in subjetivo hoje.");
        }

        int score = (int) Math.round(60 + 1.5 * tsbProntidao);
        if (ultimoRpe != null && ultimoRpe >= 8) {
            score -= 5; // último treino muito intenso reduz a prontidão
        }
        score = Math.max(0, Math.min(100, score));

        return new ReadinessDto(score, classificar(score), fatores,
                "Baseado em TSB de prontidão e carga — faça seu check-in de hoje para um sinal mais preciso.");
    }

    /**
     * Idempotent: YES — leitura. Side Effects: NONE. Tenant-aware: YES.
     */
    @Override
    @Transactional(readOnly = true)
    public AtletaHomeDto getHome(UUID atletaId) {
        validarAtletaNoTenant(atletaId);

        LocalDate hoje = LocalDate.now(clock);
        AtletaHomeDto.ProximoTreino proximo = treinoPlanejadoRepository
                .findByAtletaIdAndDataBetween(atletaId, hoje, hoje.plusDays(14))
                .stream().findFirst()
                .map(tp -> new AtletaHomeDto.ProximoTreino(
                        tp.getDataTreino(),
                        tp.getTipoTreino() != null ? tp.getTipoTreino().name() : null,
                        tp.getDescricao()))
                .orElse(null);

        AtletaHomeDto.MetricasChave metricas = metricasDiariasRepository.findLatestByAtletaId(atletaId)
                .map(m -> new AtletaHomeDto.MetricasChave(m.getCtl(), m.getAtl(), m.getTsb(), m.getTss(), m.getVolumeKm(),
                        FaixaTsb.classificarNome(m.getTsb())))
                .orElse(new AtletaHomeDto.MetricasChave(null, null, null, null, null, null));

        return new AtletaHomeDto(proximo, metricas);
    }

    /**
     * Idempotent: YES — leitura. Side Effects: NONE. Tenant-aware: YES.
     */
    @Override
    @Transactional(readOnly = true)
    public List<AderenciasSemanalDto> getAderenciaSemanal(UUID atletaId, int semanas) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        validarAtletaNoTenant(atletaId);

        LocalDate hoje = LocalDate.now(clock);
        LocalDate inicioSemanaAtual = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate dataInicio = inicioSemanaAtual.minusWeeks(semanas - 1L);

        List<TreinoPlanejado> treinos = treinoPlanejadoRepository
                .findComRealizadoByAtletaAndPeriodo(atletaId, tenantId, dataInicio);

        if (treinos.isEmpty()) {
            return List.of();
        }

        Map<LocalDate, List<TreinoPlanejado>> porSemana = treinos.stream()
                .collect(Collectors.groupingBy(
                        tp -> tp.getDataTreino().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))));

        List<AderenciasSemanalDto> resultado = porSemana.entrySet().stream()
                .map(e -> {
                    int total = e.getValue().size();
                    int realizado = (int) e.getValue().stream()
                            .filter(tp -> tp.getTreinoRealizado() != null)
                            .count();
                    int percentual = total > 0 ? (int) Math.round(realizado * 100.0 / total) : 0;
                    return new AderenciasSemanalDto(e.getKey(), total, realizado, percentual);
                })
                .sorted(Comparator.comparing(AderenciasSemanalDto::semanaInicio))
                .toList();

        boolean temDados = resultado.stream().anyMatch(a -> a.totalPlanejado() > 0);
        return temDados ? resultado : List.of();
    }

    /**
     * Idempotent: YES — leitura. Side Effects: NONE. Tenant-aware: YES.
     */
    @Override
    @Transactional(readOnly = true)
    public UUID resolverAtletaIdAtual() {
        UUID tenantId = TenantContext.getRequiredTenantId();
        String sub = principalResolver.getCurrentSubject();
        Usuario usuario = usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Usuário autenticado não encontrado no tenant"));
        Atleta atleta = atletaRepository.findByUsuario_IdAndAssessoria_Id(usuario.getId(), tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta vinculado ao usuário não encontrado"));
        return atleta.getId();
    }

    // ===== Helpers =====

    private Atleta validarAtletaNoTenant(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        return atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado"));
    }

    private Intervalo resolverIntervalo(LocalDate from, LocalDate to) {
        LocalDate fim = (to != null) ? to : LocalDate.now(clock);
        LocalDate inicio = (from != null) ? from : fim.minusDays(DIAS_PADRAO);
        if (inicio.isAfter(fim)) {
            throw new DomainRuleViolationException("Intervalo inválido: 'from' não pode ser depois de 'to'");
        }
        return new Intervalo(inicio, fim);
    }

    private record Intervalo(LocalDate from, LocalDate to) {}

    private String classificar(int score) {
        if (score >= 80) return "OTIMO";
        if (score >= 60) return "BOM";
        if (score >= 40) return "MODERADO";
        return "BAIXO";
    }
}
