package com.menthoros.dto.input;

import java.time.LocalDate;
import java.util.UUID;

public record ProvaInputDto(
        String nome,
        LocalDate data,
        String distancia,
        String objetivo,
        UUID atletaId
) {}
