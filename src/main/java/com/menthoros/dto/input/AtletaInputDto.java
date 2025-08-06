package com.menthoros.dto.input;

import com.menthoros.enums.DiaSemana;

import java.util.Set;

public record AtletaInputDto(String nome,
                             int idade,
                             String objetivo,
                             Set<DiaSemana> diasDisponiveis,
                             DiaSemana diaPreferidoLongo) {
}
