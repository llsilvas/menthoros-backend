package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.Aderencia4SemanasDto;
import br.com.menthoros.backend.dto.output.AderenciasSemanalDto;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Consolida os cálculos de aderência que antes viviam duplicados em
 * {@link AtletaProgressServiceImpl} e {@link ProgressaoTreinoServiceImpl} — extraído em
 * extract-adherence-calculator (2026-10). Refactor puro: cada método preserva exatamente a
 * lógica/janela que tinha antes da extração; nenhuma regra foi unificada e a flag
 * {@code menthoros.progressao.aderencia-devidos.enabled} (ver {@link ProgressaoTreinoServiceImpl})
 * continua selecionando entre {@link #calcularAderenciaJanelaFechada} e
 * {@link #calcularAderenciaRegraAntiga} sem nenhuma mudança de comportamento.
 *
 * <p>Pacote {@code services.impl}, sem subpacote dedicado — segue o padrão da camada de
 * reconciliação ({@code CandidateSelector}/{@code MatchingDecisionEngineImpl}).</p>
 *
 * <p><b>Tenant-aware por parâmetro:</b> nenhum método aqui resolve {@code TenantContext} nem valida
 * que o atleta pertence ao tenant — essas responsabilidades ficam no chamador (fachada de serviço),
 * antes de delegar aqui.</p>
 */
@Component
@RequiredArgsConstructor
public class AdherenceCalculator {

    private static final double TETO_PENDENCIA = 0.25;
    private static final String ATOR_SISTEMA = "SYSTEM";

    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final Clock clock;

    /**
     * Idempotent: YES — leitura. Side Effects: NONE. Tenant-aware: YES (tenantId por parâmetro).
     *
     * <p>Mesma janela e mesmo predicado de {@code CoachDashboardServiceImpl} (roster) e de
     * {@code CoachAthleteProfileServiceImpl} — fonte única (fix-athlete-profile-aderencia-4-semanas,
     * 2026-10-01).</p>
     */
    public Aderencia4SemanasDto getAderencia4Semanas(UUID atletaId, UUID tenantId) {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate inicioSemanaAtual = segundaDaSemana(hoje);
        LocalDate fimSemanaAtual = inicioSemanaAtual.plusDays(6);
        LocalDate dataInicio = inicioSemanaAtual.minusWeeks(3);

        List<TreinoPlanejado> treinos = treinoPlanejadoRepository
                .findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, dataInicio, fimSemanaAtual);

        if (treinos.isEmpty()) {
            return new Aderencia4SemanasDto(0, 0, 0);
        }

        int planejado = treinos.size();
        int realizado = (int) treinos.stream()
                .filter(tp -> tp.getTreinoRealizado() != null && tp.getTreinoRealizado().contaNaCarga())
                .count();
        int percentual = (int) Math.round(realizado * 100.0 / planejado);
        return new Aderencia4SemanasDto(realizado, planejado, percentual);
    }

    /**
     * Idempotent: YES — leitura. Side Effects: NONE. Tenant-aware: YES (tenantId por parâmetro).
     */
    public List<AderenciasSemanalDto> getAderenciaSemanal(UUID atletaId, UUID tenantId, int semanas) {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate inicioSemanaAtual = segundaDaSemana(hoje);
        LocalDate fimSemanaAtual = inicioSemanaAtual.plusDays(6);
        LocalDate dataInicio = inicioSemanaAtual.minusWeeks(semanas - 1L);

        // fix-weekly-adherence-future-days-excluded: dataFim=hoje cortava a semana em curso antes
        // dela terminar — um treino planejado pro sábado nem entrava no "total" da semana até o
        // sábado chegar, inflando a aderência da semana atual pra 100% com um dia ainda pendente.
        // fimSemanaAtual ainda impede que a próxima semana vaze pra cá (fix-adherence-count-until-today,
        // D5), só não corta a atual pela metade.
        List<TreinoPlanejado> treinos = treinoPlanejadoRepository
                .findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, dataInicio, fimSemanaAtual);

        if (treinos.isEmpty()) {
            return List.of();
        }

        Map<LocalDate, List<TreinoPlanejado>> porSemana = treinos.stream()
                .collect(Collectors.groupingBy(tp -> segundaDaSemana(tp.getDataTreino())));

        List<AderenciasSemanalDto> resultado = porSemana.entrySet().stream()
                .map(e -> {
                    int total = e.getValue().size();
                    int realizado = (int) e.getValue().stream()
                            .filter(tp -> tp.getTreinoRealizado() != null && tp.getTreinoRealizado().contaNaCarga())
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
     * D1/D2 (fix-progression-adherence-window): aderência medida sobre as 3 semanas ISO fechadas
     * antes da atual — a semana em curso fica inteiramente fora, então "hoje só se feito" não se
     * aplica aqui (todo planejado da janela já venceu).
     *
     * <p>Idempotent: YES — leitura. Side Effects: NONE. Tenant-aware: YES (tenantId por parâmetro).</p>
     */
    public AderenciaJanela calcularAderenciaJanelaFechada(UUID atletaId, UUID tenantId, LocalDate hoje) {
        LocalDate segundaAtual = segundaDaSemana(hoje);
        LocalDate inicioJanela = segundaAtual.minusDays(21);
        LocalDate fimJanela = segundaAtual.minusDays(1);

        List<TreinoPlanejado> planejadosJanela = treinoPlanejadoRepository
                .findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, inicioJanela, fimJanela);
        List<TreinoRealizado> realizadosJanela = treinoRealizadoRepository
                .findByAtletaIdAndTenantIdAndDataTreinoBetween(atletaId, tenantId, inicioJanela, fimJanela);

        // Achado do Codex (review nativo, 2026-10-01): um avulso CANCELADO no Strava continua na
        // tabela (StravaWebhookServiceImpl.markAsCanceled só marca o status, nunca apaga a linha) —
        // sem o filtro de contaNaCarga(), ele seria tratado como candidato de verdade e poderia
        // encobrir uma falta como pendência (ou pendência como falta) por um dado que já não conta.
        Map<LocalDate, List<TreinoRealizado>> avulsosPorData = realizadosJanela.stream()
                .filter(r -> r.getTreinoPlanejado() == null)
                .filter(TreinoRealizado::contaNaCarga)
                .collect(Collectors.groupingBy(TreinoRealizado::getDataTreino));

        int cumpridos = 0;
        int faltas = 0;
        int pendentes = 0;
        for (TreinoPlanejado planejado : planejadosJanela) {
            if (!isDevido(planejado)) {
                continue; // DESCANSO fora da conta (D2)
            }
            TreinoRealizado vinculado = planejado.getTreinoRealizado();
            if (vinculado != null) {
                if (vinculado.contaNaCarga()) {
                    cumpridos++;
                } else {
                    faltas++; // vínculo sobrevive ao cancelamento no Strava — não é cumprimento (CA10)
                }
                continue;
            }
            List<TreinoRealizado> avulsosNoDia = avulsosPorData.getOrDefault(planejado.getDataTreino(), List.of());
            if (avulsosNoDia.isEmpty()) {
                faltas++;
            } else if (temTriagemHumanaDeNaoCorrespondencia(avulsosNoDia)) {
                faltas++; // triagem humana já resolveu a ambiguidade (CA11)
            } else {
                pendentes++; // dado incompleto ou triagem automática (CA4/CA12) — fora da conta
            }
        }

        return new AderenciaJanela(cumpridos, faltas, pendentes, calcularAderenciaComTeto(cumpridos, faltas, pendentes));
    }

    /**
     * D7: flag desligada reproduz a regra antiga (CA8) — sem filtro de DESCANSO/pendência/cancelado.
     *
     * <p>Idempotent: YES — leitura. Side Effects: NONE. Tenant-aware: YES (tenantId por parâmetro).</p>
     */
    public AderenciaJanela calcularAderenciaRegraAntiga(UUID atletaId, UUID tenantId, LocalDate inicio21d,
                                                          LocalDate hoje, int treinosRealizados21d) {
        int treinosPlanejados21d = treinoPlanejadoRepository
                .findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, inicio21d, hoje).size();
        double aderencia = treinosPlanejados21d == 0 ? 0.0 : (double) treinosRealizados21d / treinosPlanejados21d;
        int cumpridos = Math.min(treinosRealizados21d, treinosPlanejados21d);
        int faltas = Math.max(0, treinosPlanejados21d - treinosRealizados21d);
        return new AderenciaJanela(cumpridos, faltas, 0, aderencia);
    }

    private static LocalDate segundaDaSemana(LocalDate data) {
        return data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static boolean isDevido(TreinoPlanejado planejado) {
        return planejado.getTipoTreino() != TipoTreino.DESCANSO;
    }

    /**
     * {@code allMatch}, não {@code anyMatch} (achado do Codex adversarial-review, 2026-10-01): com
     * dois avulsos no mesmo dia, um já triado por humano como NAO_PLANEJADO não encobre o outro
     * ainda PENDENTE/AMBIGUO — a ambiguidade do dia só está de fato resolvida quando TODOS os
     * avulsos candidatos foram descartados por um humano.
     */
    private static boolean temTriagemHumanaDeNaoCorrespondencia(List<TreinoRealizado> avulsos) {
        return avulsos.stream().allMatch(r ->
                r.getReconciliationStatus() == ReconciliationStatus.NAO_PLANEJADO
                        && !ATOR_SISTEMA.equals(r.getReconciledBy()));
    }

    /** null (ausente) quando não há devido na janela ou a pendência passa de 25% dos devidos (D2). */
    private static Double calcularAderenciaComTeto(int cumpridos, int faltas, int pendentes) {
        int devidos = cumpridos + faltas + pendentes;
        if (devidos == 0) return null;
        if ((double) pendentes / devidos > TETO_PENDENCIA) return null;
        return (double) cumpridos / (cumpridos + faltas);
    }

    public record AderenciaJanela(int cumpridos, int faltas, int pendentes, Double aderencia) {}
}
