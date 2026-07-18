package br.com.menthoros.backend.domain.planner;

import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Recorte de {@code Atleta} mapeado na camada de service (anti-corruption layer,
 * design.md Decisao 17) — apenas os campos que o planner le.
 */
public record AthleteSnapshot(
        UUID atletaId,
        NivelExperiencia nivelExperiencia,
        boolean temLesao,
        String descricaoLesao,
        LocalDate dataUltimaLesao,
        List<DiaSemana> diasDisponiveis
) {
}
