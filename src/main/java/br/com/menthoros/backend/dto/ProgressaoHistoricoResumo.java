package br.com.menthoros.backend.dto;

/**
 * {@code treinosRealizados21d} é o histórico mínimo (fix-progression-adherence-window, D6): conta
 * todos os realizados em 21 dias, vinculados ou não. {@code treinosCumpridos}/{@code treinosFaltas}/
 * {@code treinosPendentes} e {@code aderencia} (nullable — ausente quando não há planejado devido na
 * janela ou a pendência de reconciliação passa do teto) vêm de uma janela diferente: as 3 semanas ISO
 * fechadas antes da atual, não os últimos 21 dias corridos — ver {@code ProgressaoTreinoServiceImpl}.
 */
public record ProgressaoHistoricoResumo(
        int treinosRealizados21d,
        double volumeKm7d,
        double volumeKm21d,
        double volumeKm42d,
        int longoesRealizados7d,
        int longoesRealizados21d,
        Double rpeMedioTreinosDuros,
        Double tsbAtual,
        Double ctlAtual,
        Double atlAtual,
        int semanasProgressaoContinua,
        int treinosCumpridos,
        int treinosFaltas,
        int treinosPendentes,
        Double aderencia
) {}
