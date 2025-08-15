package com.menthoros.dto.input;

import com.menthoros.enums.DiaSemanaEnum;
import com.menthoros.enums.NivelExperiencia;
import jakarta.validation.constraints.*;

import java.util.Set;

public record AtletaInputDto(
        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
        String nome,
        
        @Min(value = 10, message = "Idade mínima é 10 anos")
        @Max(value = 100, message = "Idade máxima é 100 anos")
        int idade,
        
        @Positive(message = "Peso deve ser positivo")
        @DecimalMax(value = "300.0", message = "Peso máximo é 300kg")
        double pesoKg,
        
        @Positive(message = "Altura deve ser positiva")
        @DecimalMin(value = "100.0", message = "Altura mínima é 100cm")
        @DecimalMax(value = "250.0", message = "Altura máxima é 250cm")
        double alturaCm,
        
        @NotBlank(message = "Objetivo é obrigatório")
        @Size(max = 500, message = "Objetivo deve ter no máximo 500 caracteres")
        String objetivo,
        
        @NotNull(message = "Nível de experiência é obrigatório")
        NivelExperiencia nivelExperiencia,
        
        @NotEmpty(message = "Pelo menos um dia disponível deve ser informado")
        Set<DiaSemanaEnum> diasDisponiveis,
        
        DiaSemanaEnum diaPreferidoLongo,
        
        boolean temLesao,
        
        @Size(max = 1000, message = "Descrição da lesão deve ter no máximo 1000 caracteres")
        String descricaoLesao
) {
}
