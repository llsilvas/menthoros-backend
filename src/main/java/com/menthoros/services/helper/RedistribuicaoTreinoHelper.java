package com.menthoros.services.helper;

import com.menthoros.dto.llm.TreinoPlanejadoLlmDto;
import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.ModoGeracaoPlano;
import com.menthoros.enums.TipoTreino;
import com.menthoros.util.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedistribuicaoTreinoHelper {

    private final RegraGeracaoTreino regraGeracao;

    private static final Set<TipoTreino> TIPOS_INCOMPATIVEIS_MEIO_SEMANA = Set.of(
            TipoTreino.LONGO,
            TipoTreino.INTERVALADO,
            TipoTreino.TEMPO_RUN
    );

    public List<TreinoPlanejadoLlmDto> redistribuirTreinos(
            List<TreinoPlanejadoLlmDto> treinosLlm,
            List<DiaSemana> diasDisponiveisAtleta,
            LocalDate hoje,
            LocalDate semanaInicio,
            LocalDate semanaFim,
            ModoGeracaoPlano modoGeracao
    ) {

        log.info("Iniciando redistribuição - {} treinos da LLM, modo: {}",
                treinosLlm.size(), modoGeracao);

        List<DiaSemana> diasValidos = filtrarDiasValidos(
                diasDisponiveisAtleta,
                hoje,
                semanaInicio,
                semanaFim,
                modoGeracao
        );

        if (diasValidos.isEmpty()) {
            log.warn("Nenhum dia disponível para redistribuição de treinos na semana");
            return Collections.emptyList();
        }


        // 1. Filtrar treinos compatíveis (remove LONGO/INTERVALADO/TEMPO_RUN se meio da semana)
        List<TreinoPlanejadoLlmDto> treinosFiltrados = filtrarTreinosCompativeis(
                treinosLlm,
                hoje,
                modoGeracao
        );

        if (treinosFiltrados.isEmpty()) {
            log.warn("Nenhum treino compatível após filtro");
            return Collections.emptyList();
        }

        // 2. Redistribuir treinos pelos dias disponíveis (prioriza treinos leves)
        List<TreinoPlanejadoLlmDto> treinosRedistribuidos = redistribuir(
                treinosFiltrados,
                diasValidos,
                semanaInicio
        );

        log.info("Redistribuição concluída: {} treinos redistribuídos em {} dias",
                treinosRedistribuidos.size(), diasValidos.size());

        return treinosRedistribuidos;
    }

    /**
     * Filtra treinos que são compatíveis com o modo de geração
     */
    private List<TreinoPlanejadoLlmDto> filtrarTreinosCompativeis(
            List<TreinoPlanejadoLlmDto> treinos,
            LocalDate hoje,
            ModoGeracaoPlano modo) {

        boolean isMeioDaSemana = regraGeracao.isMeioSemana(hoje);

        return treinos.stream()
                .filter(treino -> {
                    TipoTreino tipo = TipoTreino.valueOf(treino.tipoTreino());

                    // Se modo = PRÓXIMA_SEMANA, aceita todos
                    if (modo == ModoGeracaoPlano.PROXIMA_SEMANA) {
                        return true;
                    }

                    // Se modo = SEMANA_ATUAL e é meio da semana
                    if (isMeioDaSemana) {
                        boolean isCompativel = !TIPOS_INCOMPATIVEIS_MEIO_SEMANA.contains(tipo);

                        if (!isCompativel) {
                            log.info("Treino {} EXCLUÍDO - incompatível com meio da semana", tipo);
                        }

                        return isCompativel;
                    }

                    return true;
                })
                .toList();
    }

    private List<DiaSemana> filtrarDiasValidos(
            List<DiaSemana> diasOriginais,
            LocalDate hoje,
            LocalDate semanaInicio,
            LocalDate semanaFim,
            ModoGeracaoPlano modoGeracao
    ) {
        if (modoGeracao == ModoGeracaoPlano.PROXIMA_SEMANA) {
            return new ArrayList<>(diasOriginais);
        }

        return diasOriginais.stream()
                .filter(dia -> {
                    LocalDate dataDia = calcularDataDia(semanaInicio, dia);
                    boolean isValido = dataDia.isAfter(hoje) && !dataDia.isAfter(semanaFim);
                    if (!isValido) {
                        log.debug("Dia {} ({}) descartado - já passou ou fora da semana",
                                dia, dataDia);
                    }
                    return isValido;
                })
                .toList();
    }

    /**
     * Redistribui treinos pelos dias disponíveis
     */
    private List<TreinoPlanejadoLlmDto> redistribuir(
            List<TreinoPlanejadoLlmDto> treinos,
            List<DiaSemana> diasValidos,
            LocalDate semanaInicio) {

        // Ordena treinos por prioridade (regenerativo → contínuo → fartlek → outros)
        List<TreinoPlanejadoLlmDto> treinosOrdenados = ordenarPorPrioridade(treinos);

        // Ordena dias por ordem da semana
        List<DiaSemana> diasOrdenados = ordenarDiasSemana(diasValidos);

        List<TreinoPlanejadoLlmDto> resultado = new ArrayList<>();

        // Distribui 1 treino por dia disponível
        int totalTreinos = Math.min(treinosOrdenados.size(), diasOrdenados.size());

        for (int i = 0; i < totalTreinos; i++) {
            TreinoPlanejadoLlmDto treinoOriginal = treinosOrdenados.get(i);
            DiaSemana novoDia = diasOrdenados.get(i);
            LocalDate novaData = calcularDataDia(semanaInicio, novoDia);

            // Cria novo DTO com dia atualizado
            TreinoPlanejadoLlmDto treinoAtualizado = atualizarDiaTreino(
                    treinoOriginal,
                    novoDia,
                    novaData
            );

            resultado.add(treinoAtualizado);

            log.debug("Treino redistribuído: {} → {} ({})",
                    treinoOriginal.tipoTreino(), novoDia, novaData);
        }

        return resultado;
    }

    /**
     * Ordena treinos por prioridade de execução
     */
    private List<TreinoPlanejadoLlmDto> ordenarPorPrioridade(List<TreinoPlanejadoLlmDto> treinos) {
        Map<TipoTreino, Integer> prioridades = Map.of(
                TipoTreino.REGENERATIVO, 1,
                TipoTreino.CONTINUO, 2,
                TipoTreino.FARTLEK, 3,
                TipoTreino.INTERVALADO, 4,
                TipoTreino.TEMPO_RUN, 5,
                TipoTreino.TIRO, 6,
                TipoTreino.LONGO, 7
        );

        return treinos.stream()
                .sorted(Comparator.comparingInt(t ->
                        prioridades.getOrDefault(
                                TipoTreino.valueOf(t.tipoTreino()),
                                99
                        )
                ))
                .toList();
    }

    /**
     * Ordena dias da semana
     */
    private List<DiaSemana> ordenarDiasSemana(List<DiaSemana> dias) {
        return dias.stream()
                .sorted(Comparator.comparingInt(d ->
                        Utils.converterParaDayOfWeek(d).getValue()
                ))
                .toList();
    }

    /**
     * Calcula data de um dia específico da semana
     */
    private LocalDate calcularDataDia(LocalDate semanaInicio, DiaSemana dia) {
        DayOfWeek dayOfWeek = Utils.converterParaDayOfWeek(dia);
        return semanaInicio.with(TemporalAdjusters.nextOrSame(dayOfWeek));
    }

    /**
     * Cria novo DTO com dia atualizado (imutável)
     */
    private TreinoPlanejadoLlmDto atualizarDiaTreino(
            TreinoPlanejadoLlmDto original,
            DiaSemana novoDia,
            LocalDate novaData) {

        // Assumindo que TreinoPlanejadoLlmDto é um record
        return new TreinoPlanejadoLlmDto(
                novoDia.name(),  // Atualiza o dia
                original.tipoTreino(),
                original.fcAlvo(),
                original.tssPlanejado(),
                original.intensidadePlanejada(),
                original.percepcaoEsforcoEsperada(),
                original.justificativaIa(),
                original.duracaoMin(),
                original.distanciaKm(),
                original.ritmoAlvo(),
                original.etapas()
        );
    }
}
