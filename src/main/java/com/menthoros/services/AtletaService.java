package com.menthoros.services;

import com.menthoros.dto.input.AtletaInputDto;
import com.menthoros.dto.output.AtletaOutputDto;
import com.menthoros.entity.Atleta;

import java.util.List;
import java.util.UUID;

public interface AtletaService {

    Atleta createAtleta(AtletaInputDto atletaInputDto);
    AtletaOutputDto updateAtleta(UUID id, AtletaInputDto atletaInputDto);
    void deleteAtleta(UUID id);
    AtletaOutputDto getAtletaById(UUID id);
    void atualizarEmbedding(UUID atletaId, List<Float> vetor);

    List<AtletaOutputDto> getAllAtletas();

    void recalcularMetricasAtleta(UUID id);
}
