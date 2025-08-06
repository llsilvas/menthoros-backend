package com.menthoros.services.impl;

import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import com.menthoros.entity.PlanoMetaDados;
import com.menthoros.entity.PlanoSemanal;
import com.menthoros.mapper.AtletaMapper;
import com.menthoros.mapper.PlanoMapper;
import com.menthoros.mapper.PlanoSemanalMapper;
import com.menthoros.mapper.TreinoMapper;
import com.menthoros.repository.AtletaRepository;
import com.menthoros.repository.PlanoMetadadosRepository;
import com.menthoros.repository.PlanoSemanalRepository;
import com.menthoros.services.EmbeddingService;
import com.menthoros.services.IaService;
import com.menthoros.services.PlanoService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PlanoServiceImpl implements PlanoService {

    private final IaService iaService;
    private final AtletaRepository atletaRepository;
    private final AtletaMapper atletaMapper;
    private final TreinoMapper treinoMapper;
    private final PlanoMapper planoMapper;
    private final PlanoMetadadosRepository planoMetadadosRepository;
    private final PlanoSemanalMapper planoSemanalMapper;

    private final EmbeddingService embeddingService;
    private final PlanoSemanalRepository planoSemanalRepository;

    public PlanoServiceImpl(IaService iaService, AtletaRepository atletaRepository, AtletaMapper atletaMapper, TreinoMapper treinoMapper, PlanoMapper planoMapper, PlanoMetadadosRepository planoMetadadosRepository, PlanoSemanalMapper planoSemanalMapper, EmbeddingService embeddingService, PlanoSemanalRepository planoSemanalRepository) {
        this.iaService = iaService;
        this.atletaRepository = atletaRepository;
        this.atletaMapper = atletaMapper;
        this.treinoMapper = treinoMapper;
        this.planoMapper = planoMapper;
        this.planoMetadadosRepository = planoMetadadosRepository;
        this.planoSemanalMapper = planoSemanalMapper;
        this.embeddingService = embeddingService;
        this.planoSemanalRepository = planoSemanalRepository;
    }

    @Transactional
    public PlanoSemanal gerarPlanoTreino(UUID atletaId) {
        var atleta = atletaRepository.findById(atletaId)
                .orElseThrow(() -> new RuntimeException("Atleta não encontrado com o id: " + atletaId));

        // Buscar treinos anteriores
        List<TreinoRealizadoOutputDto> ultimosTreinos = Optional.ofNullable(atleta.getTreinosRealizados())
                .orElse(List.of())
                .stream()
                .filter(treinoRealizado -> {
                    LocalDate dataTreino = treinoRealizado.getDataTreino();

                    return dataTreino != null
                            && !dataTreino.isAfter(LocalDate.now())
                            && !dataTreino.isBefore(LocalDate.now().minusDays(7));

                })
                .map(treinoMapper::toOutputDto)
                .toList();

//        String contexto = atletaOutputDto.diaPreferidoLongo() + " " + atletaOutputDto.diaPreferidoLongo() + " " + atletaOutputDto.objetivo();

//        List<Float> vetor = embeddingService.gerarEmbedding(contexto).stream().toArray();

        var meta = PlanoMetaDados.builder()
                .atleta(atleta)
                .diaPreferidoLongo(atleta.getDiaPreferidoLongo())
                .dataCriacao(LocalDateTime.now())
//                .embedding(vetor.stream())
                .build();

        PlanoMetaDados metaDados = planoMetadadosRepository.save(meta);

        var jsonPlano = iaService.gerarPlano(atletaMapper.toOutputDto(atleta), ultimosTreinos);

        PlanoSemanal entity = planoSemanalMapper.toEntity(jsonPlano);

        planoSemanalMapper.linkPlanoSemanal(entity);

        entity.setAtleta(atleta);
        entity.setPlanoMetaDados(metaDados);

        metaDados.setPlanoSemanal(entity);

        PlanoSemanal planoSemanal = planoSemanalRepository.save(entity);

        return planoSemanal;

    }
}

