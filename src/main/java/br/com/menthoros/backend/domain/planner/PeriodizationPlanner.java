package br.com.menthoros.backend.domain.planner;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Selecao de prova e determinacao de fase, implementadas de forma propria (design.md
 * Decisao 7 e Decisao 12) — nao chama {@code buscarProximaProva}/{@code getProximaProva}
 * (que ordenam so por data). A logica de fase e informada pelas mesmas regras de
 * {@code PeriodizacaoPromptFormatter.determinarFasePreparacao}, mas duplicada
 * independentemente; divergencias sao instrumentadas via
 * {@code planner.phase.divergence.count} em shadow (Decisao 12), nao corrigidas aqui.
 *
 * <p>Diferenca deliberada do legado: para uma prova daqui a 0-6 dias (ainda no futuro), esta
 * implementacao retorna {@code RACE_WEEK}. O formatter legado, por divisao inteira
 * (`diasFaltando / 7 == 0`), cai no ramo `else` e retorna "PÓS-PROVA" para esse mesmo caso —
 * um bug latente que a metrica de divergencia deve expor, nao replicar.
 */
@Component
public class PeriodizationPlanner {

    private static final int JANELA_POS_PROVA_DIAS = 7;

    public PeriodizationResult resolvePhase(List<ProvaSnapshot> provas, LocalDate referenceDate) {
        List<ProvaSnapshot> futurasValidas = provas.stream()
                .filter(p -> !p.cancelada())
                .filter(p -> !p.dataProva().isBefore(referenceDate))
                .toList();

        Optional<ProvaSnapshot> provaPosSemana = provaRecemRealizada(provas, referenceDate);
        if (provaPosSemana.isPresent()) {
            // A fase e POST_RACE, mas uma prova cadastrada nesta semana continua sendo evento fixo:
            // sem ela em provasPreparatoriasNaSemana, o skeleton nao a ancora e o compliance acusa o
            // dia dela como indisponivel (tasks.md 7.3, revisao do PR #180).
            return new PeriodizationResult(TrainingPhase.POST_RACE, provaPosSemana,
                    preparatoriasNaSemana(futurasValidas, Optional.empty(), referenceDate));
        }

        Optional<ProvaSnapshot> provaDeterminante = selecionarProvaDeterminante(futurasValidas);

        TrainingPhase phase = provaDeterminante
                .map(p -> phaseFromDiasFaltando(ChronoUnit.DAYS.between(referenceDate, p.dataProva())))
                .orElse(TrainingPhase.BASE);

        return new PeriodizationResult(phase, provaDeterminante,
                preparatoriasNaSemana(futurasValidas, provaDeterminante, referenceDate));
    }

    /** Provas futuras em {@code [referenceDate, referenceDate+6]}, exceto a determinante. */
    private static List<ProvaSnapshot> preparatoriasNaSemana(List<ProvaSnapshot> futurasValidas,
                                                            Optional<ProvaSnapshot> provaDeterminante,
                                                            LocalDate referenceDate) {
        return futurasValidas.stream()
                .filter(p -> provaDeterminante.isEmpty() || !p.equals(provaDeterminante.get()))
                .filter(p -> !p.dataProva().isAfter(referenceDate.plusDays(6)))
                .toList();
    }

    private Optional<ProvaSnapshot> provaRecemRealizada(List<ProvaSnapshot> provas, LocalDate referenceDate) {
        return provas.stream()
                .filter(p -> !p.cancelada())
                .filter(p -> p.dataProva().isBefore(referenceDate))
                .filter(p -> !p.dataProva().isBefore(referenceDate.minusDays(JANELA_POS_PROVA_DIAS)))
                .max(Comparator.comparing(ProvaSnapshot::dataProva));
    }

    private Optional<ProvaSnapshot> selecionarProvaDeterminante(List<ProvaSnapshot> futurasValidas) {
        Optional<ProvaSnapshot> alvoExplicita = futurasValidas.stream()
                .filter(ProvaSnapshot::provaAlvo)
                .findFirst();
        if (alvoExplicita.isPresent()) {
            return alvoExplicita;
        }

        Optional<LocalDate> maisProximaData = futurasValidas.stream()
                .map(ProvaSnapshot::dataProva)
                .min(Comparator.naturalOrder());
        if (maisProximaData.isEmpty()) {
            return Optional.empty();
        }

        // Empate (mesma semana, ver design.md Decisao 7): entre as provas na janela dos 6 dias
        // seguintes a mais proxima, vence a de maior distancia.
        LocalDate fimJanela = maisProximaData.get().plusDays(6);
        return futurasValidas.stream()
                .filter(p -> !p.dataProva().isAfter(fimJanela))
                .max(Comparator.comparing(ProvaSnapshot::distanciaKm));
    }

    private TrainingPhase phaseFromDiasFaltando(long diasFaltando) {
        long semanas = diasFaltando / 7;
        if (semanas > 12) {
            return TrainingPhase.BASE;
        } else if (semanas > 8) {
            return TrainingPhase.BUILD;
        } else if (semanas > 3) {
            return TrainingPhase.PEAK;
        } else if (semanas > 1) {
            return TrainingPhase.TAPER;
        } else {
            return TrainingPhase.RACE_WEEK;
        }
    }
}
