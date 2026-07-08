package br.com.menthoros.backend.dto;

public record ProgressaoHistoricoResumo(
        int treinosConcluidos21d,
        int treinosPlanejados21d,
        double volumeKm7d,
        double volumeKm21d,
        double volumeKm42d,
        int longoesRealizados7d,
        int longoesRealizados21d,
        Double rpeMedioTreinosDuros,
        Double tsbAtual,
        Double ctlAtual,
        Double atlAtual,
        int semanasProgressaoContinua
) {}
