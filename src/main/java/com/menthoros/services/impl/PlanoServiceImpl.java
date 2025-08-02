package com.menthoros.services.impl;

import com.menthoros.dto.AtletaDto;
import com.menthoros.dto.PlanoDto;
import com.menthoros.dto.PlanoTreinoDto;
import com.menthoros.dto.TreinoRealizadoDto;
import com.menthoros.entity.Atleta;
import com.menthoros.entity.PlanoTreino;
import com.menthoros.enums.PlanoStatus;
import com.menthoros.mapper.AtletaMapper;
import com.menthoros.mapper.PlanoMapper;
import com.menthoros.mapper.TreinoMapper;
import com.menthoros.repository.AtletaRepository;
import com.menthoros.services.IaService;
import com.menthoros.services.PlanoService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PlanoServiceImpl implements PlanoService {

    private final IaService iaService;
    private final AtletaRepository atletaRepository;
    private final AtletaMapper atletaMapper;
    private final TreinoMapper treinoMapper;
    private final PlanoMapper planoTreinoMapper;

    public PlanoServiceImpl(IaService iaService, AtletaRepository atletaRepository, AtletaMapper atletaMapper, TreinoMapper treinoMapper, PlanoMapper planoTreinoMapper) {
        this.iaService = iaService;
        this.atletaRepository = atletaRepository;
        this.atletaMapper = atletaMapper;
        this.treinoMapper = treinoMapper;
        this.planoTreinoMapper = planoTreinoMapper;
    }

    @Transactional
    public PlanoDto gerarPlanoTreino(UUID atletaId) {
        Atleta atleta = atletaRepository.findById(atletaId);
        if (atleta == null) {
            throw new RuntimeException("Atleta não encontrado com o id: " + atletaId);
        }

        AtletaDto atletaDto = atletaMapper.toDto(atleta);

        // Buscar planos ativos e futuros
        PlanoTreino planoAtivo = Optional.ofNullable(atleta.getPlanos())
                .orElse(List.of()).stream()
                .filter(plano ->
                        plano.getDataProva() != null &&
                                plano.getDataProva().isAfter(LocalDate.now()) &&
                                plano.getStatus() == PlanoStatus.EM_ANDAMENTO &&
                                plano.getObjetivo() != null &&
                                !plano.getObjetivo().isBlank()
                )
                .min(Comparator.comparing(PlanoTreino::getDataProva))
                .orElse(null);

        // Buscar treinos anteriores
        List<TreinoRealizadoDto> treinosAnteriores = Optional.ofNullable(atleta.getTreinosRealizados())
                .orElse(List.of())
                .stream()
                .map(treinoMapper::toDto)
                .collect(Collectors.toList());

        // Definir se será baseado em plano ou em histórico
        if (planoAtivo != null) {
            PlanoTreinoDto planoDto = planoTreinoMapper.toDto(planoAtivo);
            return iaService.gerarPlano(atletaDto, planoDto, treinosAnteriores);
        } else {
            return iaService.gerarPlano(atletaDto, null, treinosAnteriores);
        }
    }

}

