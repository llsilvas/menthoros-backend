package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.AthleteWorkoutAnalysisOutputDto;

import java.util.Optional;

import java.util.UUID;

public interface AtletaWorkoutAnalysisService {

    /**
     * Análise pós-treino na visão do atleta autenticado.
     *
     * @return o DTO ({@code 200}), ou vazio ({@code 204}) quando não há nada a mostrar
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException realizado inexistente,
     *         de outro atleta ou de outro tenant ({@code 404})
     */
    Optional<AthleteWorkoutAnalysisOutputDto> buscarAnalise(UUID atletaId, UUID treinoRealizadoId);
}
