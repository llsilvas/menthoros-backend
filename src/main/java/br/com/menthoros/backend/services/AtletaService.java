package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.AtletaInputDto;
import br.com.menthoros.backend.dto.output.AtletaOutputDto;
import br.com.menthoros.backend.entity.Atleta;

import java.util.List;
import java.util.UUID;

public interface AtletaService {

    Atleta createAtleta(AtletaInputDto atletaInputDto);

    AtletaOutputDto updateAtleta(UUID id, AtletaInputDto atletaInputDto);

    void deleteAtleta(UUID id);

    AtletaOutputDto getAtletaById(UUID id);

    List<AtletaOutputDto> getAllAtletas();

    void recalcularMetricasAtleta(UUID id);
}
