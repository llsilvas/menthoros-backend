package com.menthoros.controller;

import com.menthoros.dto.input.TreinoRealizadoInputDto;
import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import com.menthoros.entity.TreinoRealizado;
import com.menthoros.mapper.TreinoMapper;
import com.menthoros.services.TreinoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/treinos")
public class TreinoRealizadoController {

    private final TreinoService treinoService;
    private final TreinoMapper treinoMapper;

    public TreinoRealizadoController(TreinoService treinoService, TreinoMapper treinoMapper) {
        this.treinoService = treinoService;
        this.treinoMapper = treinoMapper;
    }

    @PostMapping
    public ResponseEntity<TreinoRealizadoOutputDto> createTreinos(@Valid @RequestBody TreinoRealizadoInputDto treinoRealizadoInputDto) {

        TreinoRealizado treinoRealizado = treinoService.addTreino(treinoRealizadoInputDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(treinoMapper.toOutputDto(treinoRealizado));

    }
}
