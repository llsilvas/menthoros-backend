package com.menthoros.dto.input;

import com.menthoros.enums.ProvaStatus;
import com.menthoros.enums.TipoProva;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

public record ProvaInputDto(
        @NotBlank(message = "Nome da prova é obrigatório")
        String nomeProva,

        @NotNull(message = "Data da prova é obrigatória")
        LocalDate dataProva,

        @NotNull(message = "Distância é obrigatória")
        @Positive(message = "Distância deve ser positiva")
        Double distanciaKm,

        @NotNull(message = "Tipo da prova é obrigatório")
        TipoProva tipoProva,

        @NotNull(message = "Status da prova é obrigatório")
        ProvaStatus statusProva,

        boolean provaAlvo,

        String objetivo,

        @NotNull(message = "ID do atleta é obrigatório")
        UUID atletaId
) {}
