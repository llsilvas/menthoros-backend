package com.menthoros.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.menthoros.dto.output.PlanoSemanalOutputDto;
import com.menthoros.entity.PlanoSemanal;
import com.menthoros.mapper.PlanoSemanalMapper;
import com.menthoros.services.impl.PlanoServiceImpl;
import com.menthoros.services.IaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/planos")
public class PlanoTreinoController {

    private final PlanoServiceImpl planoServiceImpl;
    private final PlanoSemanalMapper planoSemanalMapper;
    private final IaService iaService;

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

    // Endpoint para capturar typos comuns
    @PostMapping("/atletas/{atletaId}/gerar-enhaced")
    public ResponseEntity<String> gerarPlanoTreinoTypo(@PathVariable UUID atletaId) {
        return ResponseEntity.badRequest()
                .body("URL incorreta. Use: /planos/atletas/" + atletaId + "/gerar-enhanced (com 'n')");
    }

    @DeleteMapping("/{planoSemanalId}")
    public ResponseEntity<Void> deletePlanoSemanal(@PathVariable UUID planoSemanalId) {
        planoServiceImpl.deletePlanoSemanal(planoSemanalId);
        return ResponseEntity.noContent().build();
    }
}
