package com.menthoros.services;

import com.menthoros.dto.AtletaDto;
import com.menthoros.entity.Atleta;

import java.util.UUID;

public interface AtletaService {

    Atleta createAtleta(AtletaDto atleta);
    Atleta updateAtleta(UUID id, AtletaDto atleta);
    void deleteAtleta(UUID id);
    AtletaDto getAtletaById(UUID id);

}
