package br.com.menthoros.backend.dto.output;

public record StravaStatusGlobalDto(
    Integer totalAtletas,
    Integer atletasConectados,
    Double percentualConectado
) {}
