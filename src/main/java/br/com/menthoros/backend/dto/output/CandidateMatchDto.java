package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import br.com.menthoros.backend.enums.TipoTreino;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CandidateMatchDto(
    UUID treinoPlanejadoId,
    LocalDate data,
    TipoTreino tipoTreino,
    BigDecimal distanciaKm,
    Duration duracaoMin,
    double score,
    double scoreTemporal,
    double scoreDuracao,
    double scoreDistancia
) {}
