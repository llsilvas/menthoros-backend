package com.menthoros.util;

import com.menthoros.enums.DiaSemanaEnum;

import java.time.DayOfWeek;

public class Utils {



    public static DayOfWeek converterParaDayOfWeek(DiaSemanaEnum diaSemana) {
        return switch (diaSemana) {
            case SEGUNDA -> DayOfWeek.MONDAY;
            case TERCA -> DayOfWeek.TUESDAY;
            case QUARTA -> DayOfWeek.WEDNESDAY;
            case QUINTA -> DayOfWeek.THURSDAY;
            case SEXTA -> DayOfWeek.FRIDAY;
            case SABADO -> DayOfWeek.SATURDAY;
            case DOMINGO -> DayOfWeek.SUNDAY;
        };
    }

}
