package com.menthoros.dto;

import com.menthoros.enums.DiaSemana;

import java.util.Set;

public record AtletaDto(String nome,
                        int idade,
                        String objetivo,
                        Long tsbAtual,
                        Set<DiaSemana> diasDisponiveis,
                        String provaAlvo,
                        DiaSemana diaPreferido) {
}
