package com.menthoros.services.impl;

import com.menthoros.dto.input.AtletaInputDto;
import com.menthoros.dto.output.AtletaOutputDto;
import com.menthoros.entity.Atleta;
import com.menthoros.mapper.AtletaMapper;
import com.menthoros.repository.AtletaRepository;
import com.menthoros.repository.PlanoMetadadosRepository;
import com.menthoros.services.AtletaService;
import com.menthoros.services.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class AtletaServiceImpl implements AtletaService {

    private final AtletaRepository atletaRepository;
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;
    private final AtletaMapper atletaMapper;
    private final PlanoMetadadosRepository planoMetaDadosRepository;

    @Override
    public Atleta createAtleta(AtletaInputDto atletaInputDto) {

        Atleta entity = atletaMapper.toEntity(atletaInputDto);
        return atletaRepository.save(entity);
    }

    @Override
    public Atleta updateAtleta(UUID id, AtletaInputDto atletaInputDto) {
        return null;
    }

    @Override
    public void deleteAtleta(UUID id) {

    }

    @Override
    public AtletaOutputDto getAtletaById(UUID id) {
        return null;
    }

    @Override
    public void atualizarEmbedding(UUID atletaId, List<Float> vetor) {
        String vetorFormatado = vetor.toString().replace("[", "[").replace("]", "]");
        String sql = "UPDATE tb_atleta SET embedding = ?::vector WHERE id = ?";
        jdbcTemplate.update(sql, vetorFormatado, atletaId);
    }

}

