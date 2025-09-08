package com.menthoros.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.menthoros.dto.output.PlanoSemanalOutputDto;
import com.menthoros.entity.PlanoSemanal;
import com.menthoros.mapper.PlanoSemanalMapper;
import com.menthoros.services.impl.PlanoServiceImpl;
import com.menthoros.services.IaService;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.repository.query.Param;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/planos")
public class PlanoTreinoController {

    private final PlanoServiceImpl planoServiceImpl;
    private final PlanoSemanalMapper planoSemanalMapper;

    @PostMapping("/atletas/{atletaId}/gerar")
    public ResponseEntity<PlanoSemanalOutputDto> gerarPlanoTreino(@PathVariable UUID atletaId) throws JsonProcessingException {
        PlanoSemanal planoSemanal = planoServiceImpl.gerarPlanoTreino(atletaId);
        PlanoSemanalOutputDto planoSemanalOutputDto = planoSemanalMapper.toOutputDto(planoSemanal);

        return ResponseEntity.ok(planoSemanalOutputDto);
    }

    @PostMapping("/atletas/{atletaId}/gerar-enhanced")
    public ResponseEntity<PlanoSemanalOutputDto> gerarPlanoTreinoEnhanced(@PathVariable UUID atletaId) throws JsonProcessingException {
        PlanoSemanal planoSemanal = planoServiceImpl.gerarPlanoTreino(atletaId);
        PlanoSemanalOutputDto planoSemanalOutputDto = planoSemanalMapper.toOutputDto(planoSemanal);

        return ResponseEntity.ok(planoSemanalOutputDto);
    }

    @DeleteMapping("/{planoSemanalId}")
    public ResponseEntity<Void> deletePlanoSemanal(@PathVariable UUID planoSemanalId) {
        planoServiceImpl.deletePlanoSemanal(planoSemanalId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanoSemanalOutputDto> buscarPlanoSemanal(@Parameter(description = "ID do atleta") @PathVariable UUID id) {
        PlanoSemanalOutputDto planoSemanalOutputDto = planoServiceImpl.buscarPlanoPorAtleta(id);

        return ResponseEntity.ok(planoSemanalOutputDto);
    }
}
