package com.menthoros.controller;

import com.menthoros.dto.AtletaDto;
import com.menthoros.entity.Atleta;
import com.menthoros.mapper.AtletaMapper;
import com.menthoros.services.AtletaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/atleta")
public class AtletaController {

    private final AtletaService atletaService;
    private final AtletaMapper atletaMapper;

    public AtletaController(AtletaService atletaService, AtletaMapper atletaMapper) {
        this.atletaService = atletaService;
        this.atletaMapper = atletaMapper;
    }

    @PostMapping
    public ResponseEntity<AtletaDto> cadastraAtleta(@Valid @RequestBody AtletaDto atletaDto){
        Atleta atleta = atletaService.createAtleta(atletaDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(atletaMapper.toDto(atleta));
    }
}
