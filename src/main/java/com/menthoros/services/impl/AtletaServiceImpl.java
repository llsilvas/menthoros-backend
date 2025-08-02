package com.menthoros.services.impl;

import com.menthoros.dto.AtletaDto;
import com.menthoros.entity.Atleta;
import com.menthoros.mapper.AtletaMapper;
import com.menthoros.repository.AtletaRepository;
import com.menthoros.services.AtletaService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AtletaServiceImpl implements AtletaService {

    private final AtletaRepository atletaRepository;
    private final AtletaMapper atletaMapper;

    public AtletaServiceImpl(AtletaRepository atletaRepository, AtletaMapper atletaMapper) {
        this.atletaRepository = atletaRepository;
        this.atletaMapper = atletaMapper;
    }

    @Override
    public Atleta createAtleta(AtletaDto atleta) {
        Atleta entity = atletaMapper.toEntity(atleta);
        return atletaRepository.save(entity);
    }

    @Override
    public Atleta updateAtleta(UUID id, AtletaDto atleta) {
        return null;
    }

    @Override
    public void deleteAtleta(UUID id) {

    }

    @Override
    public AtletaDto getAtletaById(UUID id) {
        return null;
    }
}
