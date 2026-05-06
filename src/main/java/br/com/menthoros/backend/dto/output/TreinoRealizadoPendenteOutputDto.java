package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.enums.TipoTreino;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TreinoRealizadoPendenteOutputDto(
    UUID id,
    String externalId,
    UUID atletaId,
    String atletaNome,
    LocalDate dataTreino,
    TipoTreino tipoTreino,
    BigDecimal distanciaKm,
    Duration duracaoMin,
    ReconciliationStatus reconciliationStatus,
    Double reconciliationScore,
    Instant reconciledAt,
    String reconciledBy,
    FonteDados fonteDados
) {}
