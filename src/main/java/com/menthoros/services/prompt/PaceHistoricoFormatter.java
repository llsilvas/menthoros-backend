package com.menthoros.services.prompt;

import com.menthoros.entity.Atleta;
import com.menthoros.entity.TreinoRealizado;
import com.menthoros.enums.TipoTreino;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Formata o histórico real de pace do atleta para o prompt da IA.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Agrupar treinos das últimas 4 semanas por tipo e calcular min/média/max de paceMedia</li>
 *   <li>Calcular teto de pace por tipo (2% mais rápido que melhor recente — Fase 3)</li>
 *   <li>Sinalizar paceLimiar desatualizado (&gt; 90 dias) — Fase 5</li>
 * </ul>
 */
@Slf4j
@Component
public class PaceHistoricoFormatter {

    /** Fator aplicado ao melhor pace recente para definir o teto (2% mais rápido). */
    private static final double FATOR_TETO = 0.98;

    /** Fator aplicado ao pace médio recente para definir o piso (25% mais lento). */
    private static final double FATOR_PISO = 1.25;

    /** Ordem de exibição dos tipos de treino na tabela. */
    private static final List<TipoTreino> ORDEM_EXIBICAO = List.of(
            TipoTreino.REGENERATIVO, TipoTreino.FACIL, TipoTreino.CONTINUO,
            TipoTreino.LONGO, TipoTreino.FARTLEK, TipoTreino.TEMPO_RUN,
            TipoTreino.INTERVALADO, TipoTreino.TIRO, TipoTreino.SUBIDA
    );

    /**
     * Formata o bloco de pace demonstrado nas últimas 4 semanas para o prompt.
     *
     * <p>Inclui tabela min/média/max por tipo de treino e nota obrigatória ao LLM.</p>
     */
    public String formatarHistoricoPace(List<TreinoRealizado> treinosUltimas4Semanas) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🏃 PACE DEMONSTRADO NOS ÚLTIMOS TREINOS\n\n");

        List<TreinoRealizado> comPace = filtrarComPace(treinosUltimas4Semanas);
        if (comPace.isEmpty()) {
            sb.append("⚠️ Sem histórico de pace disponível. Usar zonas teóricas calculadas como referência.\n");
            return sb.toString();
        }

        sb.append("> **REGRA OBRIGATÓRIA:** o `ritmoAlvo` de cada treino NÃO pode ser mais rápido\n");
        sb.append("> do que o pace abaixo para o tipo equivalente. Estes são valores reais, não teóricos.\n\n");

        sb.append("| Tipo        | Pace mínimo | Pace médio | Pace máximo | Treinos |\n");
        sb.append("|-------------|-------------|------------|-------------|--------|\n");

        Map<TipoTreino, List<TreinoRealizado>> porTipo = agruparPorTipo(comPace);

        for (TipoTreino tipo : ORDEM_EXIBICAO) {
            List<TreinoRealizado> grupo = porTipo.get(tipo);
            if (grupo == null || grupo.isEmpty()) continue;

            List<Long> segundos = grupo.stream()
                    .map(t -> t.getPaceMedia().getSeconds())
                    .filter(s -> s > 0)
                    .sorted()
                    .toList();

            if (segundos.isEmpty()) continue;

            long minSeg = segundos.get(0);
            long maxSeg = segundos.get(segundos.size() - 1);
            long avgSeg = (long) segundos.stream().mapToLong(Long::longValue).average().orElse(0);

            sb.append(String.format("| %-11s | %-11s | %-10s | %-11s | %-7d |\n",
                    tipo.getValue(),
                    formatarSegundos(minSeg) + "/km",
                    formatarSegundos(avgSeg) + "/km",
                    formatarSegundos(maxSeg) + "/km",
                    grupo.size()
            ));
        }

        sb.append("\n⚠️ Tipos sem histórico recente: usar a zona calculada (Z correspondente) como referência.\n");
        return sb.toString();
    }

    /**
     * Calcula o teto de pace por tipo de treino (2% mais rápido que o melhor recente).
     *
     * <p>Retorna apenas os tipos com pelo menos 1 treino com pace registrado nas últimas 4 semanas.
     * O teto está em decimal minutos (ex: 4.75 = 4:45/km).</p>
     */
    public Map<TipoTreino, BigDecimal> calcularTetoPorTipo(List<TreinoRealizado> treinosUltimas4Semanas) {
        List<TreinoRealizado> comPace = filtrarComPace(treinosUltimas4Semanas);
        if (comPace.isEmpty()) return Map.of();

        Map<TipoTreino, BigDecimal> tetos = new EnumMap<>(TipoTreino.class);

        agruparPorTipo(comPace).forEach((tipo, grupo) -> {
            OptionalLong melhorSeg = grupo.stream()
                    .mapToLong(t -> t.getPaceMedia().getSeconds())
                    .filter(s -> s > 0)
                    .min();

            melhorSeg.ifPresent(seg -> {
                BigDecimal melhorDecimal = segundosParaDecimalMinutos(seg);
                BigDecimal teto = melhorDecimal
                        .multiply(BigDecimal.valueOf(FATOR_TETO))
                        .setScale(4, RoundingMode.HALF_UP);
                tetos.put(tipo, teto);
            });
        });

        return Collections.unmodifiableMap(tetos);
    }

    /**
     * Calcula o piso de pace por tipo de treino (25% mais lento que a média recente).
     *
     * <p>Um pace mais lento que o piso indica uma prescrição irreal para aquele tipo de treino.
     * Retorna apenas os tipos com pelo menos 1 treino com pace registrado nas últimas 4 semanas.
     * O piso está em decimal minutos (ex: 6.25 = 6:15/km).</p>
     */
    public Map<TipoTreino, BigDecimal> calcularPisoPorTipo(List<TreinoRealizado> treinosUltimas4Semanas) {
        List<TreinoRealizado> comPace = filtrarComPace(treinosUltimas4Semanas);
        if (comPace.isEmpty()) return Map.of();

        Map<TipoTreino, BigDecimal> pisos = new EnumMap<>(TipoTreino.class);

        agruparPorTipo(comPace).forEach((tipo, grupo) -> {
            OptionalDouble mediaSeg = grupo.stream()
                    .mapToLong(t -> t.getPaceMedia().getSeconds())
                    .filter(s -> s > 0)
                    .average();

            mediaSeg.ifPresent(seg -> {
                BigDecimal mediaDecimal = segundosParaDecimalMinutos((long) seg);
                BigDecimal piso = mediaDecimal
                        .multiply(BigDecimal.valueOf(FATOR_PISO))
                        .setScale(4, RoundingMode.HALF_UP);
                pisos.put(tipo, piso);
            });
        });

        return Collections.unmodifiableMap(pisos);
    }

    /**
     * Formata o bloco de teto de pace por tipo para inclusão no prompt.
     *
     * <p>Inclui apenas os tipos com histórico recente. Retorna string vazia se não há tetos.</p>
     */
    public String formatarTetoPace(Map<TipoTreino, BigDecimal> tetoPorTipo) {
        if (tetoPorTipo == null || tetoPorTipo.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## ⛔ TETO DE PACE POR TIPO (NÃO ULTRAPASSAR)\n\n");
        sb.append("O `ritmoAlvo` (paceMin) de cada treino não pode ser mais rápido que:\n\n");

        for (TipoTreino tipo : ORDEM_EXIBICAO) {
            BigDecimal teto = tetoPorTipo.get(tipo);
            if (teto == null) continue;
            sb.append(String.format("- **%s**: não mais rápido que %s/km\n",
                    tipo.getValue(), formatarDecimalMinutos(teto)));
        }

        sb.append("\n");
        return sb.toString();
    }

    /**
     * Verifica se o paceLimiar está atualizado (< 90 dias).
     *
     * <p>Retorna um aviso para o prompt se estiver desatualizado ou nulo.
     * Retorna string vazia se tudo estiver ok.</p>
     */
    public String verificarPaceLimiarAtualizado(Atleta atleta) {
        if (atleta == null) return "";

        if (atleta.getPaceLimiar() == null) {
            return "⚠️ **Pace limiar não cadastrado.**\n" +
                    "   Paces calculados com base no histórico recente. Margem ampliada em ±15 seg/km.\n" +
                    "   Recomendado: realizar teste de Cooper ou corrida de 20 min para definir o limiar.\n";
        }

        LocalDate dataUltimoTeste = atleta.getDataUltimoTestePace();
        if (dataUltimoTeste == null) {
            return "⚠️ **Pace limiar nunca testado formalmente.**\n" +
                    "   Paces calculados com base no perfil. Margem ampliada em ±15 seg/km.\n" +
                    "   Recomendado: realizar teste de Cooper ou corrida de 20 min para validar o limiar.\n";
        }

        long diasDesdeUltimoTeste = java.time.temporal.ChronoUnit.DAYS.between(dataUltimoTeste, LocalDate.now());
        if (diasDesdeUltimoTeste > 90) {
            return String.format(
                    "⚠️ **Pace limiar desatualizado** (último teste: %d dias atrás).\n" +
                    "   Paces calculados com base no histórico recente. Margem ampliada em ±15 seg/km.\n" +
                    "   Recomendado: realizar teste de Cooper ou corrida de 20 min para atualizar o limiar.\n",
                    diasDesdeUltimoTeste
            );
        }

        return "";
    }

    // ===== Helpers =====

    private List<TreinoRealizado> filtrarComPace(List<TreinoRealizado> treinos) {
        if (treinos == null) return List.of();
        return treinos.stream()
                .filter(t -> t.getTipoTreino() != null
                        && t.getPaceMedia() != null
                        && t.getPaceMedia().getSeconds() > 0)
                .toList();
    }

    private Map<TipoTreino, List<TreinoRealizado>> agruparPorTipo(List<TreinoRealizado> treinos) {
        return treinos.stream()
                .collect(Collectors.groupingBy(TreinoRealizado::getTipoTreino));
    }

    String formatarSegundos(long totalSegundos) {
        long minutos = totalSegundos / 60;
        long segundos = totalSegundos % 60;
        return String.format("%d:%02d", minutos, segundos);
    }

    String formatarDecimalMinutos(BigDecimal decimalMinutos) {
        long totalSegundos = Math.round(decimalMinutos.doubleValue() * 60);
        return formatarSegundos(totalSegundos);
    }

    BigDecimal segundosParaDecimalMinutos(long segundos) {
        return BigDecimal.valueOf(segundos)
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
    }
}
