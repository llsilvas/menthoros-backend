package com.menthoros.dto.input;

import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.NivelExperiencia;

import java.util.Set;

public record AtletaInputDto(String nome,
                             int idade,
                             double pesoKg,
                             double alturaCm,
                             String objetivo,
                             NivelExperiencia nivelExperiencia,
                             Set<DiaSemana> diasDisponiveis,
                             DiaSemana diaPreferidoLongo,
                             boolean temLesao,
                             String descricaoLesao) {
}
