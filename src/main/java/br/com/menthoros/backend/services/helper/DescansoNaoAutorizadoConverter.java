package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.RestDayLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.enums.DiaSemana;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Resolve, sem turno de reparo, os dois jeitos de a LLM declarar um descanso que a regra não aceita
 * (add-descanso-explicito-por-fadiga).
 *
 * <p><b>Por que existe.</b> A regra de cobertura converteu um erro silencioso — o dia sumia do plano
 * — numa violação reparável. Mas quando o modelo insiste, o treinador fica <b>sem plano</b>: na
 * geração real de 22/09 21:11 a LLM pôs treino <i>e</i> descanso na mesma quinta, o reparo repetiu o
 * erro, e a geração falhou nas duas tentativas. Trocar um plano ruim por nenhum plano é um mau
 * negócio para quem está esperando a semana do atleta.</p>
 *
 * <p>As duas conversões são determinísticas e fazem exatamente o que o prompt já manda o modelo
 * fazer, então não afrouxam a regra — só param de cobrá-la por rejeição:</p>
 * <ol>
 *   <li><b>Dia com treino E descanso</b> → o descanso cai. O treino vence, mesma precedência que o
 *       persister aplica quando a prova chega depois da validação.</li>
 *   <li><b>Descanso sem sinal que o libere</b> → vira treino leve (REGENERATIVO, Z1-Z2, 30 min), que
 *       é a instrução literal do bloco de cobertura: "prescreva treino leve — nunca omita o dia".</li>
 * </ol>
 *
 * <p>Roda <b>antes</b> da normalização, para o treino sintetizado passar pelo mesmo pipeline dos
 * outros (etapas, distância pelo pace, gates). Descanso em dia fora dos disponíveis continua sendo
 * descartado depois, no {@code PlanoLlmValidator} — ali não há dia a preencher.</p>
 *
 * <p>Idempotent: YES — reaplicar sobre o resultado é no-op. Side Effects: métricas.
 * Tenant-aware: NO.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DescansoNaoAutorizadoConverter {

    /** Duração do treino leve sintetizado, em minutos: o piso útil de um regenerativo. */
    static final int DURACAO_LEVE_MIN = 30;
    private static final int AQUECIMENTO_MIN = 5;
    private static final int DESAQUECIMENTO_MIN = 5;

    private final MeterRegistry meterRegistry;

    public PlanoSemanalLlmDto converter(PlanoSemanalLlmDto plano, @Nullable WeeklyCoverageContext cobertura) {
        if (cobertura == null || plano == null || plano.restDays() == null || plano.restDays().isEmpty()) {
            return plano;
        }

        Set<DiaSemana> diasComTreino = diasComTreino(plano.treinosPlanejados());
        Set<DiaSemana> permitidos = cobertura.diasComDescansoPermitido();

        List<RestDayLlmDto> mantidos = new ArrayList<>();
        List<TreinoPlanejadoLlmDto> sintetizados = new ArrayList<>();

        for (RestDayLlmDto descanso : plano.restDays()) {
            DiaSemana dia = converter(descanso.dayOfWeek());
            if (dia == null || !cobertura.effectiveDays().contains(dia)) {
                mantidos.add(descanso); // dia inválido ou fora da semana: quem decide é o validador
                continue;
            }
            if (diasComTreino.contains(dia)) {
                log.info("DESCANSO DESCARTADO [{}]: o dia já tem treino — treino vence descanso", dia.name());
                contar("dia_com_treino");
                continue;
            }
            if (!permitidos.contains(dia)) {
                log.info("DESCANSO CONVERTIDO [{}]: sem sinal do dia que o libere → treino leve de {} min",
                        dia.name(), DURACAO_LEVE_MIN);
                contar("sem_sinal");
                sintetizados.add(treinoLeve(dia));
                continue;
            }
            mantidos.add(descanso);
        }

        if (sintetizados.isEmpty() && mantidos.size() == plano.restDays().size()) {
            return plano;
        }

        List<TreinoPlanejadoLlmDto> treinos = new ArrayList<>(
                plano.treinosPlanejados() != null ? plano.treinosPlanejados() : List.of());
        treinos.addAll(sintetizados);
        return plano.toBuilder().treinosPlanejados(treinos).restDays(mantidos).build();
    }

    /**
     * Treino leve com as três etapas já montadas: o {@code PlanoEstruturaReparador} só repara quem
     * tem exatamente uma PRINCIPAL, então sintetizar só o esqueleto vazio cairia no gate de estrutura.
     * Sem {@code distanciaKm} e sem {@code ritmoAlvo} de propósito — o normalizador deriva os dois do
     * pace Z2 do atleta, que é quem sabe o ritmo dele.
     */
    private TreinoPlanejadoLlmDto treinoLeve(DiaSemana dia) {
        int principalMin = DURACAO_LEVE_MIN - AQUECIMENTO_MIN - DESAQUECIMENTO_MIN;
        List<EtapaTreinoLlmDto> etapas = List.of(
                new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento leve Z1", AQUECIMENTO_MIN, null, null, 1, null),
                new EtapaTreinoLlmDto(2, "PRINCIPAL", "Corrida leve contínua Z1-Z2", principalMin, null, null, 1, null),
                new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Desaquecimento leve Z1", DESAQUECIMENTO_MIN, null, null, 1,
                        null));
        return new TreinoPlanejadoLlmDto(
                dia.name(),
                "REGENERATIVO",
                null,
                null,
                null,
                null,
                "Dia sem sinal de fadiga que autorize descanso: carga reduzida em vez de sessão removida.",
                String.format("%02d:00", DURACAO_LEVE_MIN),
                null,
                null,
                etapas,
                null,
                null,
                null);
    }

    private Set<DiaSemana> diasComTreino(@Nullable List<TreinoPlanejadoLlmDto> treinos) {
        Set<DiaSemana> dias = new LinkedHashSet<>();
        if (treinos == null) return dias;
        for (TreinoPlanejadoLlmDto treino : treinos) {
            DiaSemana dia = converter(treino.diaSemana());
            if (dia != null) dias.add(dia);
        }
        return dias;
    }

    private void contar(String motivo) {
        meterRegistry.counter("plano_descanso_nao_autorizado", "motivo", motivo).increment();
    }

    private static @Nullable DiaSemana converter(@Nullable String dia) {
        if (dia == null || dia.isBlank()) return null;
        try {
            return DiaSemana.valueOf(dia.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
