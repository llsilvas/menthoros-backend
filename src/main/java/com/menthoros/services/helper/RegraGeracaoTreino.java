package com.menthoros.services.helper;

import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.ModoGeracaoPlano;
import com.menthoros.util.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
public class RegraGeracaoTreino {

    public boolean isMeioSemana(LocalDate data) {
        DayOfWeek dia = data.getDayOfWeek();
        return dia.getValue() >= DayOfWeek.WEDNESDAY.getValue();
    }

    public boolean diaSemanaJaPassou(LocalDate hoje, DiaSemana diaSemana) {
        var diaHoje = hoje.getDayOfWeek();
        var diaAlvo = Utils.converterParaDayOfWeek(diaSemana);
        return diaAlvo.getValue() <= diaHoje.getValue();
    }

    public List<DiaSemana> filtrarDiasDisponiveis(
            List<DiaSemana> diasOriginais,
            LocalDate dataGeracao,
            ModoGeracaoPlano modoGeracao
    ) {
        if (modoGeracao == ModoGeracaoPlano.PROXIMA_SEMANA) {
            return List.copyOf(diasOriginais);
        }

        return diasOriginais.stream()
                .filter(dia -> !diaSemanaJaPassou(dataGeracao, dia))
                .toList();
    }

}
