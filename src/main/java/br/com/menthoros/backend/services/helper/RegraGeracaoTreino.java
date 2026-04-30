package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import br.com.menthoros.backend.util.Utils;
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

    public List<DiaSemana> filtrarDiasDisponiveis(List<DiaSemana> diasDisponiveis) {
        // Data de referência é sempre hoje
        int ordemHoje = java.time.LocalDate.now().getDayOfWeek().getValue() % 7; // segunda=1...domingo=0
    
        // Identifica o menor 'order' dos dias disponíveis (primeiro dia de treino da semana)
        int ordemPrimeiroDia = diasDisponiveis.stream()
            .mapToInt(DiaSemana::getOrder)
            .min()
            .orElse(1); // padrão segunda
    
        if (ordemHoje < ordemPrimeiroDia) {
            // Antes do primeiro dia disponível: retorna todos os dias (plano para próxima semana)
            return diasDisponiveis;
        } else {
            // Semana já iniciada: retorna só os dias restantes (>= hoje)
            return diasDisponiveis.stream()
                .filter(dia -> dia.getOrder() >= ordemHoje)
                .collect(java.util.stream.Collectors.toList());
        }
    }
}