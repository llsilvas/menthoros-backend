package com.menthoros.controller;

import com.menthoros.dto.PlanoDto;
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

    public PlanoTreinoController(PlanoServiceImpl planoServiceImpl) {
        this.planoServiceImpl = planoServiceImpl;
    }

    @PostMapping("/gerar/{atletaId}")
    public ResponseEntity<PlanoDto> gerarPlanoTreino(@PathVariable UUID atletaId) {
        PlanoDto planoDto = planoServiceImpl.gerarPlanoTreino(atletaId);
        return ResponseEntity.ok(planoDto);
    }
}
