package br.com.menthoros.backend.dto;

import br.com.menthoros.backend.enums.EstadoProgressao;

public record DecisaoProgressao(
        EstadoProgressao estado,
        double ajusteVolumePercentual,
        int ajusteLongoMinutos,
        boolean permitirProgressaoIntensidade,
        String motivo
) {}
