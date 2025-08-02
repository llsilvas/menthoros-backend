package com.menthoros.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PlanoMetaDadosDto(UUID id,
                                Double volumeSemanalAnterior,
                                Integer tsbInicial,
                                String diaPreferidoLongo,
                                String fonteDados,
                                LocalDateTime dataCriacao) {
}