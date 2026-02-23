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
     * Valida o ritmoAlvo contra o teto calculado.
     *
     * <p>Se paceMin for mais rápido que o teto (valor decimal menor),
     * corrige o intervalo deslocando-o para iniciar em {@code teto}.</p>
     *
     * @param ritmoAlvo formato "5:00-5:30/km"
     * @param teto      pace máximo permitido em decimal minutos (ex: 4.75 = 4:45/km); null = sem validação
     * @return ritmoAlvo original ou corrigido
     */
    public String validar(String ritmoAlvo, BigDecimal teto) {
        if (ritmoAlvo == null || ritmoAlvo.isBlank() || teto == null) {
            return ritmoAlvo;
        }

        PaceRange range = parsear(ritmoAlvo);
        if (range == null) {
            log.warn("ritmoAlvo em formato não reconhecido, mantendo original: '{}'", ritmoAlvo);
            return ritmoAlvo;
        }

        // paceMin < teto significa ritmo mais rápido que o teto → violação
        if (range.paceMin().compareTo(teto) < 0) {
            BigDecimal diferenca = teto.subtract(range.paceMin());
            BigDecimal novoMin = teto;
            BigDecimal novoMax = range.paceMax().add(diferenca).setScale(4, RoundingMode.HALF_UP);
            String corrigido = formatar(novoMin, novoMax);
            log.warn("ritmoAlvo corrigido: '{}' → '{}' (paceMin mais rápido que teto {})",
                    ritmoAlvo, corrigido, formatarDecimalMinutos(teto));
            return corrigido;
        }

        return ritmoAlvo;
    }

    // ===== Helpers =====

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
