package br.com.menthoros.backend.services.plano;

import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.MotivoReaberturaRevisao;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.services.PlanoReviewService;
import br.com.menthoros.backend.util.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Único dono da regra "prova ↔ treino planejado" (design.md D2, prova-no-plano-semanal): garante
 * que toda prova não cancelada da semana vire o treino do dia — na geração e quando a prova
 * entra numa semana já gerada (tasks 3.x).
 *
 * <p>{@code garantirProvasNaSemana} roda em DTO (mesmo nível da redistribuição de
 * {@link br.com.menthoros.backend.services.helper.RedistribuicaoTreinoHelper}); o vínculo por
 * {@code provaId} só vira entidade no {@link TreinoMapper}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProvaNoPlanoService {

    private static final BigDecimal PACE_FALLBACK_MIN_KM = BigDecimal.valueOf(6);
    private static final String ZONA_ALVO_PROVA = "Zona 3-4";

    private final ProvaRepository provaRepository;
    private final TreinoMapper treinoMapper;
    private final PlanoSemanalRepository planoSemanalRepository;
    private final PlanoReviewService planoReviewService;

    /**
     * Idempotent: YES — reaplicar sobre o mesmo `treinosLlm` produz o mesmo resultado (a prova
     * é buscada de novo e substitui o dia de novo).
     * Side Effects: NONE (opera em DTOs em memória; nenhuma leitura fora de `provaRepository`).
     * Tenant-aware: N/A — `atleta` já resolvido pelo chamador dentro do tenant corrente.
     *
     * <p>Para cada prova não cancelada do atleta com {@code dataProva} em
     * {@code [semanaInicio, semanaFim]}, remove os DTOs do mesmo {@code diaSemana} e insere o
     * treino {@code PROVA} construído por {@link #construirTreinoProva}. Prova cancelada não
     * gera treino. Duas provas no mesmo dia geram dois treinos {@code PROVA} (risco aceito no
     * design — caso raro, sem regra especial).
     */
    public List<TreinoPlanejadoLlmDto> garantirProvasNaSemana(List<TreinoPlanejadoLlmDto> treinosLlm,
                                                               Atleta atleta,
                                                               LocalDate semanaInicio,
                                                               LocalDate semanaFim) {
        List<Prova> provasDaSemana = provaRepository
                .findByAtletaAndDataProvaBetweenOrderByDataProvaAsc(atleta, semanaInicio, semanaFim);

        if (provasDaSemana.isEmpty()) {
            return treinosLlm;
        }

        List<TreinoPlanejadoLlmDto> resultado = new java.util.ArrayList<>(treinosLlm);

        for (Prova prova : provasDaSemana) {
            DiaSemana diaDaProva = Utils.converterDayOfWeekParaDiaSemana(prova.getDataProva().getDayOfWeek());
            String diaDaProvaNome = diaDaProva.name();

            resultado = resultado.stream()
                    .filter(t -> !diaDaProvaNome.equals(t.diaSemana()))
                    .collect(Collectors.toCollection(java.util.ArrayList::new));

            resultado.add(construirTreinoProva(prova, atleta));

            log.info("Prova '{}' garantida no plano: dia={}, provaId={}",
                    prova.getNomeProva(), diaDaProvaNome, prova.getId());
        }

        return resultado;
    }

    /**
     * Idempotent: YES — determinístico para a mesma prova/atleta.
     * Side Effects: NONE.
     * Tenant-aware: N/A.
     *
     * <p>Constrói o DTO do treino {@code PROVA}: nome e distância vêm da prova, ritmo e duração
     * do tempo objetivo quando houver (fallback pace de limiar do atleta × distância; sem
     * limiar, 6:00 min/km). Nunca lê dados do LLM.
     */
    public TreinoPlanejadoLlmDto construirTreinoProva(Prova prova, Atleta atleta) {
        DiaSemana diaSemana = Utils.converterDayOfWeekParaDiaSemana(prova.getDataProva().getDayOfWeek());
        double distanciaKm = prova.getDistanciaKm() != null ? prova.getDistanciaKm().doubleValue() : 0.0;

        Duration duracao;
        String ritmoAlvo;
        if (prova.getTempoObjetivo() != null) {
            duracao = prova.getTempoObjetivo();
            ritmoAlvo = formatarRitmo(paceSegundosPorKm(duracao, distanciaKm));
        } else {
            BigDecimal paceMinPorKm = atleta.getPaceLimiar() != null ? atleta.getPaceLimiar() : PACE_FALLBACK_MIN_KM;
            long segundosPorKm = paceMinPorKm.multiply(BigDecimal.valueOf(60))
                    .setScale(0, RoundingMode.HALF_UP).longValue();
            duracao = Duration.ofSeconds(Math.round(segundosPorKm * distanciaKm));
            ritmoAlvo = formatarRitmo(segundosPorKm);
        }

        return new TreinoPlanejadoLlmDto(
                diaSemana.name(), "PROVA", null, null, null, null, null,
                treinoMapper.durationToString(duracao), distanciaKm, ritmoAlvo, null,
                prova.getNomeProva(), ZONA_ALVO_PROVA, prova.getId());
    }

    /**
     * Idempotent: NÃO — insere um treino a cada chamada quando a semana está aberta; chamar duas
     * vezes para a mesma prova insere dois treinos PROVA (mesmo caso raro de 2.2, aceito).
     * Side Effects: Database update (treinos do plano, volumePlanejadoKm) + reabre revisão via
     * {@link PlanoReviewService#reabrirRevisao} quando o plano estava {@code APROVADO}.
     * Tenant-aware: SIM — tenant vem de {@code prova.getAssessoria()}.
     *
     * <p>D5: prova cadastrada/movida para uma semana que já tem plano gerado e ainda não
     * encerrada. Remove os treinos {@code PENDENTE} do dia (mantém o {@code PROVA} de outra
     * prova, se houver), insere o {@code PROVA} desta, recalcula {@code volumePlanejadoKm} e
     * reabre a revisão com {@code PROVA_INSERIDA} se o plano estava aprovado.
     *
     * @return o plano alterado, ou vazio se não há semana aberta para essa data (sem plano,
     *         semana encerrada ou plano rejeitado — a query já filtra os três casos)
     */
    public Optional<PlanoSemanal> aplicarProvaEmSemanaExistente(Prova prova) {
        UUID atletaId = prova.getAtleta().getId();
        UUID tenantId = prova.getAssessoria().getId();

        Optional<PlanoSemanal> planoOpt = planoSemanalRepository
                .findSemanaAbertaParaProva(atletaId, tenantId, prova.getDataProva());
        if (planoOpt.isEmpty()) {
            log.debug("Prova {} não caiu em semana aberta (sem plano, encerrada ou rejeitado) — no-op",
                    prova.getId());
            return Optional.empty();
        }
        PlanoSemanal plano = planoOpt.get();

        List<TreinoPlanejado> treinos = plano.getTreinosPlanejados();
        if (treinos == null) {
            treinos = new java.util.ArrayList<>();
            plano.setTreinosPlanejados(treinos);
        }
        treinos.removeIf(t -> prova.getDataProva().equals(t.getDataTreino())
                && t.getStatusTreino() == TreinoExecucaoStatus.PENDENTE
                && !(t.getTipoTreino() == TipoTreino.PROVA && t.getProva() != null));

        TreinoPlanejado novo = treinoMapper.toEntity(construirTreinoProva(prova, plano.getAtleta()));
        novo.setPlanoSemanal(plano);
        novo.setAtleta(plano.getAtleta());
        novo.setTenantId(tenantId);
        novo.setDataTreino(prova.getDataProva());
        treinos.add(novo);

        plano.setVolumePlanejadoKm(calcularVolumeTotalPlanejado(treinos));

        if (plano.getReviewStatus() == PlanoReviewStatus.APROVADO) {
            planoReviewService.reabrirRevisao(plano, MotivoReaberturaRevisao.PROVA_INSERIDA, tenantId);
        } else {
            planoSemanalRepository.save(plano);
        }

        log.info("Prova {} aplicada na semana existente do plano {} (provaId={})",
                prova.getNomeProva(), plano.getId(), prova.getId());
        return Optional.of(plano);
    }

    /**
     * Idempotent: SIM — o treino já removido faz uma segunda chamada virar no-op (o filtro por
     * {@code provaId} não encontra mais nada).
     * Side Effects: Database update (treinos do plano, volumePlanejadoKm) + reabre revisão via
     * {@link PlanoReviewService#reabrirRevisao} quando o plano estava {@code APROVADO}.
     * Tenant-aware: SIM — tenant vem de {@code prova.getAssessoria()}.
     *
     * <p>D5: prova cancelada ou movida para fora da semana. Remove só o treino {@code PROVA}
     * vinculado a esta prova, e só quando {@code PENDENTE} ou {@code PERDIDO} — nunca
     * {@code REALIZADO} (o atleta já correu). Recalcula {@code volumePlanejadoKm} e reabre a
     * revisão com {@code PROVA_REMOVIDA} se o plano estava aprovado.
     *
     * @param prova      a prova cancelada/movida
     * @param dataAntiga a data em que a prova estava antes (onde o treino PROVA foi inserido)
     * @return o plano alterado, ou vazio se {@code dataAntiga} não cai em semana aberta
     */
    public Optional<PlanoSemanal> removerProvaDeSemanaExistente(Prova prova, LocalDate dataAntiga) {
        UUID atletaId = prova.getAtleta().getId();
        UUID tenantId = prova.getAssessoria().getId();

        Optional<PlanoSemanal> planoOpt = planoSemanalRepository
                .findSemanaAbertaParaProva(atletaId, tenantId, dataAntiga);
        if (planoOpt.isEmpty()) {
            log.debug("Data antiga {} da prova {} não caiu em semana aberta — no-op", dataAntiga, prova.getId());
            return Optional.empty();
        }
        PlanoSemanal plano = planoOpt.get();

        List<TreinoPlanejado> treinos = plano.getTreinosPlanejados();
        if (treinos == null) {
            return Optional.of(plano);
        }

        Optional<TreinoPlanejado> vinculado = treinos.stream()
                .filter(t -> t.getProva() != null && t.getProva().getId().equals(prova.getId()))
                .findFirst();
        if (vinculado.isEmpty()) {
            return Optional.of(plano);
        }

        TreinoExecucaoStatus status = vinculado.get().getStatusTreino();
        if (status != TreinoExecucaoStatus.PENDENTE && status != TreinoExecucaoStatus.PERDIDO) {
            log.debug("Treino PROVA da prova {} está {} — não removido", prova.getId(), status);
            return Optional.of(plano);
        }

        treinos.remove(vinculado.get());
        plano.setVolumePlanejadoKm(calcularVolumeTotalPlanejado(treinos));

        if (plano.getReviewStatus() == PlanoReviewStatus.APROVADO) {
            planoReviewService.reabrirRevisao(plano, MotivoReaberturaRevisao.PROVA_REMOVIDA, tenantId);
        } else {
            planoSemanalRepository.save(plano);
        }

        log.info("Treino PROVA da prova {} removido do plano {}", prova.getId(), plano.getId());
        return Optional.of(plano);
    }

    /**
     * Idempotent: SIM — reaplicar recalcula os mesmos valores a partir da prova.
     * Side Effects: Database update (descrição, zona, ritmo, duração, distância do treino
     * vinculado; volumePlanejadoKm do plano) — NUNCA reabre revisão.
     * Tenant-aware: SIM — tenant vem de {@code prova.getAssessoria()}.
     *
     * <p>D5: atleta mudou só nome ou tempo objetivo (dia da prova não mudou). Atualiza o treino
     * {@code PROVA} já vinculado com os valores recomputados — sem reabrir a revisão do coach,
     * que só faz sentido quando o CONTEÚDO do dia muda (prova entra/sai), não quando o texto ou
     * o ritmo alvo mudam. No-op se a prova não está em nenhuma semana aberta, ou se o treino
     * vinculado já foi {@code REALIZADO}/está fora de {@code PENDENTE}/{@code PERDIDO}.
     */
    public void atualizarTreinoVinculado(Prova prova) {
        UUID atletaId = prova.getAtleta().getId();
        UUID tenantId = prova.getAssessoria().getId();

        Optional<PlanoSemanal> planoOpt = planoSemanalRepository
                .findSemanaAbertaParaProva(atletaId, tenantId, prova.getDataProva());
        if (planoOpt.isEmpty()) {
            return;
        }
        PlanoSemanal plano = planoOpt.get();
        List<TreinoPlanejado> treinos = plano.getTreinosPlanejados();
        if (treinos == null) {
            return;
        }

        Optional<TreinoPlanejado> vinculadoOpt = treinos.stream()
                .filter(t -> t.getProva() != null && t.getProva().getId().equals(prova.getId()))
                .findFirst();
        if (vinculadoOpt.isEmpty()) {
            return;
        }
        TreinoPlanejado treino = vinculadoOpt.get();
        TreinoExecucaoStatus status = treino.getStatusTreino();
        if (status != TreinoExecucaoStatus.PENDENTE && status != TreinoExecucaoStatus.PERDIDO) {
            return;
        }

        TreinoPlanejadoLlmDto atualizado = construirTreinoProva(prova, plano.getAtleta());
        treino.setDescricao(atualizado.descricao());
        treino.setZonaAlvo(atualizado.zonaAlvo());
        treino.setRitmoAlvo(atualizado.ritmoAlvo());
        treino.setDuracaoMin(treinoMapper.stringToDuration(atualizado.duracaoMin()));
        treino.setDistanciaKm(atualizado.distanciaKm() != null ? BigDecimal.valueOf(atualizado.distanciaKm()) : null);

        plano.setVolumePlanejadoKm(calcularVolumeTotalPlanejado(treinos));
        planoSemanalRepository.save(plano);

        log.info("Treino PROVA da prova {} atualizado sem reabrir revisão (plano {})", prova.getId(), plano.getId());
    }

    private BigDecimal calcularVolumeTotalPlanejado(List<TreinoPlanejado> treinos) {
        double volume = treinos.stream().mapToDouble(this::distanciaTreinoPlanejado).sum();
        return BigDecimal.valueOf(volume);
    }

    private double distanciaTreinoPlanejado(TreinoPlanejado treino) {
        if (treino.getDistanciaKm() != null) {
            return treino.getDistanciaKm().doubleValue();
        }
        if (treino.getEtapas() != null) {
            return treino.getEtapas().stream()
                    .map(e -> e.getDistanciaKm() == null ? 0.0 : e.getDistanciaKm().doubleValue())
                    .mapToDouble(Double::doubleValue)
                    .sum();
        }
        return 0.0;
    }

    private long paceSegundosPorKm(Duration duracao, double distanciaKm) {
        if (distanciaKm <= 0) {
            return 0;
        }
        return Math.round(duracao.getSeconds() / distanciaKm);
    }

    private String formatarRitmo(long segundosPorKm) {
        long minutos = segundosPorKm / 60;
        long segundos = segundosPorKm % 60;
        return String.format("%d:%02d", minutos, segundos);
    }
}
