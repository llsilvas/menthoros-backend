package com.menthoros.services.impl;

import com.menthoros.dto.input.AtletaInputDto;
import com.menthoros.dto.output.AtletaOutputDto;
import com.menthoros.entity.Atleta;
import com.menthoros.enums.AtletaStatus;
import com.menthoros.mapper.AtletaMapper;
import com.menthoros.repository.AtletaRepository;
import com.menthoros.repository.PlanoMetadadosRepository;
import com.menthoros.services.AtletaService;
import com.menthoros.exception.ResourceNotFoundException;
import com.menthoros.services.EmbeddingService;
import com.menthoros.services.TsbService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
    private final TsbService tsbService;

    @Override
    @CacheEvict(value = "atletas-list", allEntries = true)
    public Atleta createAtleta(AtletaInputDto atletaInputDto) {
        Atleta entity = atletaMapper.toEntity(atletaInputDto);
        entity.setAtivo(AtletaStatus.ATIVO);
        return atletaRepository.save(entity);
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "atletas", key = "#id"),
        @CacheEvict(value = "atletas-list", allEntries = true),
        @CacheEvict(value = "metadados-atleta", key = "#id")
    })
    public AtletaOutputDto updateAtleta(UUID id, AtletaInputDto atletaInputDto) {
        Atleta atleta = atletaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + id));

        atletaMapper.updateEntity(atletaInputDto, atleta);
        Atleta saved = atletaRepository.save(atleta);
        return atletaMapper.toOutputDto(saved);
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "atletas", key = "#id"),
        @CacheEvict(value = "atletas-list", allEntries = true),
        @CacheEvict(value = "metadados-atleta", key = "#id")
    })
    public void deleteAtleta(UUID id) {
        Atleta atleta = atletaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + id));

        atleta.setAtivo(AtletaStatus.INATIVO);
        atletaRepository.save(atleta);
    }

    @Override
    @Cacheable(value = "atletas", key = "#id")
    @Transactional(readOnly = true)
    public AtletaOutputDto getAtletaById(UUID id) {
        Atleta atleta = atletaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + id));
        Hibernate.initialize(atleta.getProvas());
        Hibernate.initialize(atleta.getDiasDisponiveis());
        return atletaMapper.toOutputDto(atleta);
    }

    @Override
    @CacheEvict(value = "embeddings", key = "#atletaId")
    public void atualizarEmbedding(UUID atletaId, List<Float> vetor) {
        String vetorFormatado = vetor.toString().replace("[", "[").replace("]", "]");
        String sql = "UPDATE tb_atleta SET embedding = ?::vector WHERE id = ?";
        jdbcTemplate.update(sql, vetorFormatado, atletaId);
    }

    @Override
    @Cacheable(value = "atletas-list")
    @Transactional(readOnly = true)
    public List<AtletaOutputDto> getAllAtletas() {
        List<Atleta> allAtletas = atletaRepository.findAllAtletasWithBasicInfo();

        return allAtletas.stream().map(atleta -> {
            // Inicializar collections necessárias dentro da transação
            Hibernate.initialize(atleta.getDiasDisponiveis());
            Hibernate.initialize(atleta.getProvas());
            return atletaMapper.toOutputDto(atleta);
        }).toList();
    }

    @Override
    public void recalcularMetricasAtleta(UUID id) {
        tsbService.recalcularHistoricoCompleto(id);
    }

}

