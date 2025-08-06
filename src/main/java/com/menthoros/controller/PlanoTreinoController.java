package com.menthoros.controller;

import com.menthoros.dto.output.PlanoSemanalOutputDto;
import com.menthoros.entity.PlanoSemanal;
import com.menthoros.mapper.PlanoSemanalMapper;
import com.menthoros.services.TreinoService;
import com.menthoros.services.impl.PlanoServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/planos")
public class PlanoTreinoController {

    private final PlanoServiceImpl planoServiceImpl;
    private final TreinoService treinoService;
    private final PlanoSemanalMapper planoSemanalMapper;

    public PlanoTreinoController(PlanoServiceImpl planoServiceImpl, TreinoService treinoService, PlanoSemanalMapper planoSemanalMapper) {
        this.planoServiceImpl = planoServiceImpl;
        this.treinoService = treinoService;
        this.planoSemanalMapper = planoSemanalMapper;
    }

    @PostMapping("/gerar/{atletaId}")
    public ResponseEntity<PlanoSemanalOutputDto> gerarPlanoTreino(@PathVariable UUID atletaId) {
        PlanoSemanal planoSemanal = planoServiceImpl.gerarPlanoTreino(atletaId);
        PlanoSemanalOutputDto planoSemanalOutputDto = planoSemanalMapper.toOutputDto(planoSemanal);

        return ResponseEntity.ok(planoSemanalOutputDto);
    }
}
