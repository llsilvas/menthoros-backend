package com.menthoros.services.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valida e corrige o ritmoAlvo gerado pelo LLM para garantir que não ultrapasse
 * o teto de pace do atleta (baseado no histórico recente).
 *
 * <p>Safety net pós-LLM: mesmo que o LLM ignore as instruções sobre pace,
 * este componente corrige automaticamente antes de persistir o plano.</p>
 *
 * <p>Lógica de correção: se paceMin &lt; teto (mais rápido que o permitido),
 * o intervalo inteiro é deslocado somando a diferença (teto - paceMin) a ambos
 * os extremos, preservando a amplitude original.</p>
 */
@Slf4j
@Component
public class PaceValidator {

    /** Formato esperado: "5:00-5:30/km" ou "10:00-10:30/km". */
    private static final Pattern RITMO_PATTERN =
            Pattern.compile("(\\d{1,2}):(\\d{2})-(\\d{1,2}):(\\d{2})/km");

    private record PaceRange(BigDecimal paceMin, BigDecimal paceMax) {}

    /**
     * Valida o ritmoAlvo contra teto e piso calculados.
     *
     * <ul>
     *   <li>Se paceMin &lt; teto (mais rápido que o permitido): desloca o intervalo para cima (mais lento)</li>
     *   <li>Se paceMax &gt; piso (mais lento que o permitido): desloca o intervalo para baixo (mais rápido)</li>
     * </ul>
     * Ambas as correções preservam a amplitude original. As checagens são independentes e aplicadas em sequência.
     *
     * @param ritmoAlvo formato "5:00-5:30/km"
     * @param teto      pace mais rápido permitido em decimal minutos; null = sem validação de teto
     * @param piso      pace mais lento permitido em decimal minutos; null = sem validação de piso
     * @return ritmoAlvo original ou corrigido
     */
    public String validar(String ritmoAlvo, BigDecimal teto, BigDecimal piso) {
        if (ritmoAlvo == null || ritmoAlvo.isBlank()) {
            return ritmoAlvo;
        }

        PaceRange range = parsear(ritmoAlvo);
        if (range == null) {
            log.warn("ritmoAlvo em formato não reconhecido, mantendo original: '{}'", ritmoAlvo);
            return ritmoAlvo;
        }

        BigDecimal curMin = range.paceMin();
        BigDecimal curMax = range.paceMax();

        // 1) Teto: paceMin não pode ser mais rápido que o teto
        if (teto != null && curMin.compareTo(teto) < 0) {
            BigDecimal diferenca = teto.subtract(curMin);
            curMin = teto;
            curMax = curMax.add(diferenca).setScale(4, RoundingMode.HALF_UP);
            log.warn("ritmoAlvo corrigido (teto): '{}' → '{}' (paceMin mais rápido que teto {})",
                    ritmoAlvo, formatar(curMin, curMax), formatarDecimalMinutos(teto));
        }

        // 2) Piso: paceMax não pode ser mais lento que o piso
        if (piso != null && curMax.compareTo(piso) > 0) {
            BigDecimal amplitude = curMax.subtract(curMin);
            BigDecimal novoMax = piso;
            BigDecimal novoMin = piso.subtract(amplitude).setScale(4, RoundingMode.HALF_UP);
            if (novoMin.compareTo(BigDecimal.ZERO) <= 0) {
                novoMin = BigDecimal.valueOf(0.5); // piso absoluto de 0:30/km
            }
            String original = ritmoAlvo;
            String corrigido = formatar(novoMin, novoMax);
            log.warn("ritmoAlvo corrigido (piso): '{}' → '{}' (paceMax mais lento que piso {})",
                    original, corrigido, formatarDecimalMinutos(piso));
            curMin = novoMin;
            curMax = novoMax;
        }

        String resultado = formatar(curMin, curMax);
        return resultado.equals(formatar(range.paceMin(), range.paceMax())) ? ritmoAlvo : resultado;
    }

    /**
     * Valida o ritmoAlvo apenas contra o teto (sem piso). Equivalente a {@code validar(ritmoAlvo, teto, null)}.
     *
     * @param ritmoAlvo formato "5:00-5:30/km"
     * @param teto      pace máximo permitido em decimal minutos; null = sem validação
     * @return ritmoAlvo original ou corrigido
     */
    public String validar(String ritmoAlvo, BigDecimal teto) {
        return validar(ritmoAlvo, teto, null);
    }

    // ===== Helpers =====

    /**
     * Retorna a média aritmética do intervalo de pace em decimal minutos.
     * Usado externamente para validações que precisam de uma estimativa pontual de pace.
     *
     * @return empty se o formato não for reconhecido ou o valor for nulo
     */
    public java.util.OptionalDouble calcularPaceMedia(String ritmoAlvo) {
        PaceRange range = parsear(ritmoAlvo);
        if (range == null) return java.util.OptionalDouble.empty();
        return java.util.OptionalDouble.of(
                (range.paceMin().doubleValue() + range.paceMax().doubleValue()) / 2.0
        );
    }

    PaceRange parsear(String ritmoAlvo) {
        if (ritmoAlvo == null) return null;
        Matcher m = RITMO_PATTERN.matcher(ritmoAlvo.trim());
        if (!m.matches()) return null;

        try {
            int minMin = Integer.parseInt(m.group(1));
            int minSec = Integer.parseInt(m.group(2));
            int maxMin = Integer.parseInt(m.group(3));
            int maxSec = Integer.parseInt(m.group(4));

            BigDecimal paceMin = minutosSegundosParaDecimal(minMin, minSec);
            BigDecimal paceMax = minutosSegundosParaDecimal(maxMin, maxSec);

            // paceMin deve ser <= paceMax (menor = mais rápido, maior = mais lento)
            if (paceMin.compareTo(paceMax) > 0) return null;

            return new PaceRange(paceMin, paceMax);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatar(BigDecimal paceMin, BigDecimal paceMax) {
        return formatarDecimalMinutos(paceMin) + "-" + formatarDecimalMinutos(paceMax) + "/km";
    }

    String formatarDecimalMinutos(BigDecimal decimalMinutos) {
        long totalSegundos = Math.round(decimalMinutos.doubleValue() * 60);
        long minutos = totalSegundos / 60;
        long segundos = totalSegundos % 60;
        return String.format("%d:%02d", minutos, segundos);
    }

    private BigDecimal minutosSegundosParaDecimal(int minutos, int segundos) {
        return BigDecimal.valueOf(minutos)
                .add(BigDecimal.valueOf(segundos)
                        .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP));
    }
}
