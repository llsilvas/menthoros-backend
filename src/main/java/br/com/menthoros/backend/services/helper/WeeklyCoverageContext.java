package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FatigueSignalType;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * O que a regra de cobertura da semana precisa saber para julgar um plano
 * (add-descanso-explicito-por-fadiga, Decisão 2). Montado no {@code IaServiceImpl}, onde os dias
 * efetivos e os sinais já existem.
 *
 * @param effectiveDays      dias que o plano deve cobrir (SEMANA_ATUAL: só os que ainda não passaram)
 * @param fatigueSignals     sinais detectados para esta geração
 * @param semanaAtual        {@code true} em SEMANA_ATUAL — sinal do dia só vale aqui
 * @param diaPreferidoLongo  dia preferido para o treino longo, quando configurado
 * @param maxDiasConsecutivos máximo de dias consecutivos recomendado ao atleta
 */
public record WeeklyCoverageContext(List<DiaSemana> effectiveDays,
                                    List<FatigueSignal> fatigueSignals,
                                    boolean semanaAtual,
                                    @Nullable DiaSemana diaPreferidoLongo,
                                    int maxDiasConsecutivos) {

    /** Limite de dias efetivos abaixo do qual só o check-in do atleta libera descanso. */
    private static final int MINIMO_DIAS_PARA_DESCANSO = 4;

    public WeeklyCoverageContext {
        effectiveDays = effectiveDays == null ? List.of() : List.copyOf(effectiveDays);
        fatigueSignals = fatigueSignals == null ? List.of() : List.copyOf(fatigueSignals);
    }

    /**
     * Primeiro dia da semana planejada entre os efetivos — a semana começa na segunda, e o enum
     * numera o domingo como 0, então a ordem é recalculada. É o único dia em que um sinal do
     * "agora" (check-in, recuperação, dias consecutivos) descreve a realidade.
     */
    public Optional<DiaSemana> firstEffectiveDay() {
        return effectiveDays.stream().min(Comparator.comparingInt(WeeklyCoverageContext::ordemNaSemana));
    }

    /**
     * Sinais que valem para esta semana: os detectados no atleta mais o estrutural
     * {@code SEQUENCIA_ACIMA_DO_MAXIMO}, derivado aqui dos dias efetivos — quem monta o contexto não
     * precisa lembrar de calculá-lo.
     */
    public List<FatigueSignal> sinaisEfetivos() {
        if (diasDaSequenciaLonga().isEmpty()) return fatigueSignals;
        var sequencia = FatigueSignal.de(FatigueSignalType.SEQUENCIA_ACIMA_DO_MAXIMO,
                diasDaSequenciaLonga().size(), maxDiasConsecutivos);
        return java.util.stream.Stream.concat(fatigueSignals.stream(), java.util.stream.Stream.of(sequencia)).toList();
    }

    /** Dias em que um descanso é legítimo — vazio quando nenhum sinal libera. */
    public Set<DiaSemana> diasComDescansoPermitido() {
        Set<DiaSemana> dias = new LinkedHashSet<>();
        boolean poucosDias = effectiveDays.size() < MINIMO_DIAS_PARA_DESCANSO;

        for (FatigueSignal sinal : sinaisEfetivos()) {
            if (!sinal.liberaDescanso()) continue;
            switch (sinal.type().escopo()) {
                case DIA -> {
                    // Com 1-3 dias efetivos, 1 descanso passa de 25% da semana — acima do teto de
                    // ~20% da literatura. Só o check-in do atleta vale aí: se ele diz que não dá,
                    // vale o que ele diz.
                    boolean permitido = !poucosDias || sinal.type() == FatigueSignalType.READINESS_DESCANSAR;
                    if (permitido && semanaAtual) {
                        firstEffectiveDay().ifPresent(dias::add);
                    }
                }
                case SEQUENCIA -> dias.addAll(diasDaSequenciaLonga());
                case SEMANA -> {
                    // TSB/RPE descrevem a semana e levam a treino leve, não a descanso.
                }
            }
        }
        return dias;
    }

    /** Teto de descansos por semana: {@code max(1, floor(dias × 0,25))} — 1 para qualquer semana. */
    public int limiteDescansos() {
        return Math.max(1, effectiveDays.size() / 4);
    }

    /**
     * Dias dentro da maior sequência de dias efetivos seguidos, quando ela passa do máximo de
     * consecutivos do atleta. Fora dela o sinal estrutural não vale.
     */
    public Set<DiaSemana> diasDaSequenciaLonga() {
        List<DiaSemana> ordenados = effectiveDays.stream()
                .sorted(Comparator.comparingInt(WeeklyCoverageContext::ordemNaSemana))
                .toList();

        Set<DiaSemana> maior = new LinkedHashSet<>();
        Set<DiaSemana> atual = new LinkedHashSet<>();
        Integer anterior = null;
        for (DiaSemana dia : ordenados) {
            int ordem = ordemNaSemana(dia);
            if (anterior != null && ordem != anterior + 1) {
                if (atual.size() > maior.size()) maior = new LinkedHashSet<>(atual);
                atual = new LinkedHashSet<>();
            }
            atual.add(dia);
            anterior = ordem;
        }
        if (atual.size() > maior.size()) maior = atual;

        return maior.size() > maxDiasConsecutivos ? maior : Set.of();
    }

    /**
     * SEGUNDA = 0 … DOMINGO = 6 — a semana do plano começa na segunda, e o enum numera o domingo
     * como 0. Público porque o validador ordena os dias pela mesma régua: duas cópias dessa conta
     * divergiriam em silêncio se a convenção do enum mudasse.
     */
    public static int ordemNaSemana(DiaSemana dia) {
        return (dia.getOrder() + 6) % 7;
    }
}
