package com.menthoros.controller;

import com.menthoros.dto.input.TreinoRealizadoInputDto;
import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import com.menthoros.entity.TreinoRealizado;
import com.menthoros.mapper.TreinoMapper;
import com.menthoros.services.TreinoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.UUID;

@RestController
@RequestMapping("/treinos")
public class TreinoRealizadoController {

    private final TreinoService treinoService;
    private final TreinoMapper treinoMapper;

    public TreinoRealizadoController(TreinoService treinoService, TreinoMapper treinoMapper) {
        this.treinoService = treinoService;
        this.treinoMapper = treinoMapper;
    }

    @PostMapping("{treinoPlanejadoId}/marcar-realizado")
    public ResponseEntity<TreinoRealizadoOutputDto> criarTreino(@PathVariable("treinoPlanejadoId") UUID treinoPlanejadoId, @Valid @RequestBody TreinoRealizadoInputDto treinoRealizadoInputDto) {
        TreinoRealizado treinoRealizado = treinoService.addTreino(treinoPlanejadoId, treinoRealizadoInputDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(treinoMapper.toOutputDto(treinoRealizado));
    }
}
