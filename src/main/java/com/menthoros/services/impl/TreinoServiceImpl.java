package com.menthoros.services.impl;

import com.menthoros.dto.TreinoRealizadoDto;
import com.menthoros.entity.Atleta;
import com.menthoros.entity.TreinoRealizado;
import com.menthoros.mapper.TreinoMapper;
import com.menthoros.repository.AtletaRepository;
import com.menthoros.repository.TreinoRealizadoRepository;
import com.menthoros.services.TreinoService;
import org.springframework.stereotype.Service;

@Service
public class TreinoServiceImpl implements TreinoService {

    private final TreinoMapper treinoMapper;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final AtletaRepository atletaRepository;

    public TreinoServiceImpl(TreinoMapper treinoMapper, TreinoRealizadoRepository treinoRealizadoRepository, AtletaRepository atletaRepository) {
        this.treinoMapper = treinoMapper;
        this.treinoRealizadoRepository = treinoRealizadoRepository;
        this.atletaRepository = atletaRepository;
    }

    @Override
    public TreinoRealizado createTreino(TreinoRealizadoDto treinoRealizadoDto) {
        Atleta atleta = atletaRepository.findById(treinoRealizadoDto.atletaId());
        TreinoRealizado entity = treinoMapper.toEntity(treinoRealizadoDto);
        entity.setAtleta(atleta);
        return treinoRealizadoRepository.save(entity);
    }

    @Override
    public TreinoRealizado updateTreino(Long id, TreinoRealizadoDto treinoRealizadoDto) {
        return null;
    }

    @Override
    public void deleteTreino(Long id) {

    }

    @Override
    public TreinoRealizadoDto getTreinoById(Long id) {
        return null;
    }
}
