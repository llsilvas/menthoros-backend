package br.com.menthoros.backend.services.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import org.jspecify.annotations.Nullable;
import java.util.List;
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

    record PaceRange(BigDecimal paceMin, BigDecimal paceMax) {}

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

    /** Faixa do ritmo com ou sem "/km", tolerante a espaços — só para a sanidade. */
    private static final Pattern FAIXA_LENIENTE =
            Pattern.compile("^\\s*(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})\\s*(/km)?\\s*$");
    private static final BigDecimal PACE_MAIS_RAPIDO_PLAUSIVEL = new BigDecimal("2.5");  // 2:30/km
    private static final BigDecimal PACE_MAIS_LENTO_PLAUSIVEL = new BigDecimal("12");    // 12:00/km

    /**
     * Sanidade da faixa de ritmo (validar-ritmo-etapa-contra-zona): faixa invertida é desinvertida; faixa
     * zerada ou fora de 2:30–12:00/km vira {@code null}, para os passos seguintes usarem o ritmo do treino ou
     * a zona. Ritmo único e texto livre passam intactos. Não cruza com a zona do atleta.
     */
    public String sanear(String ritmoAlvo) {
        if (ritmoAlvo == null) return null;
        Matcher m = FAIXA_LENIENTE.matcher(ritmoAlvo);
        if (!m.matches()) return ritmoAlvo;
        BigDecimal a = minutosSegundosParaDecimal(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        BigDecimal b = minutosSegundosParaDecimal(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
        BigDecimal min = a.min(b);
        BigDecimal max = a.max(b);
        if (min.compareTo(PACE_MAIS_RAPIDO_PLAUSIVEL) < 0 || max.compareTo(PACE_MAIS_LENTO_PLAUSIVEL) > 0) {
            log.warn("ritmoAlvo descartado (zerado ou fora de 2:30–12:00/km): '{}'", ritmoAlvo);
            return null;
        }
        if (a.compareTo(b) > 0) {
            String desinvertido = formatar(min, max);
            log.warn("ritmoAlvo invertido corrigido: '{}' → '{}'", ritmoAlvo, desinvertido);
            return desinvertido;
        }
        return ritmoAlvo;
    }

    /**
     * Ritmo da etapa contra a zona de pace esperada para o par (tipo de etapa, tipo de treino) —
     * validar-ritmo-etapa-contra-zona, mesma régua do {@code EtapaFcValidator}: sobreposição &lt; 50% vai para o
     * miolo da zona; sobreposição suficiente que sai da zona é recortada. O histórico real vence a zona: a borda
     * rápida nunca fica mais rápida que {@code teto}, e se o teto é mais lento que a zona inteira não corrige.
     * Sem zonas, treino PROVA ou ritmo que não é faixa: devolve a etapa intacta.
     *
     * @param zonas zonas de pace do atleta (Z1 primeiro, a mais lenta); null = sem validação
     * @param teto  pace mais rápido demonstrado para o tipo do treino; null = sem limite do histórico
     */
    public EtapaTreinoLlmDto validarZonaEtapa(EtapaTreinoLlmDto etapa, String tipoTreino,
                                              @Nullable List<ZonaTreinoService.ZonaPace> zonas, @Nullable BigDecimal teto) {
        if (zonas == null || zonas.size() < 6 || "PROVA".equalsIgnoreCase(tipoTreino)) return etapa;
        int[] faixa = zonasEsperadas(etapa.tipoEtapa(), tipoTreino);
        PaceRange prescrito = etapa.ritmoAlvo() == null ? null : parsear(etapa.ritmoAlvo());
        if (faixa == null || prescrito == null) return etapa;

        BigDecimal rapido = zonas.get(faixa[1] - 1).paceMin();
        BigDecimal lento = zonas.get(faixa[0] - 1).paceMax();
        if (teto != null) {
            if (teto.compareTo(lento) >= 0) return etapa;
            rapido = rapido.max(teto);
        }

        // Principal mais lenta que a zona é cautela da prescrição: acelerar uma rodagem é o erro perigoso.
        // Aquecimento/desaquecimento/recuperação corrigem nos dois sentidos (CA1: aquecimento lento demais).
        boolean etapaLeve = faixa[0] == 1 && faixa[1] <= 2 && !"PRINCIPAL".equalsIgnoreCase(etapa.tipoEtapa())
                && !"INTERVALADO".equalsIgnoreCase(etapa.tipoEtapa());
        if (!etapaLeve && prescrito.paceMin().compareTo(rapido) >= 0) return etapa;

        BigDecimal largura = prescrito.paceMax().subtract(prescrito.paceMin()).max(new BigDecimal("0.0167"));
        BigDecimal sobreposicao = prescrito.paceMax().min(lento).subtract(prescrito.paceMin().max(rapido)).max(BigDecimal.ZERO);
        String corrigido;
        if (sobreposicao.divide(largura, 4, RoundingMode.HALF_UP).compareTo(new BigDecimal("0.5")) < 0) {
            BigDecimal quarto = lento.subtract(rapido).divide(BigDecimal.valueOf(4), 4, RoundingMode.HALF_UP);
            corrigido = formatar(rapido.add(quarto), lento.subtract(quarto));
        } else if (prescrito.paceMin().compareTo(rapido) < 0 || prescrito.paceMax().compareTo(lento) > 0) {
            corrigido = formatar(prescrito.paceMin().max(rapido), prescrito.paceMax().min(lento));
        } else {
            return etapa;
        }
        if (corrigido.equals(formatar(prescrito.paceMin(), prescrito.paceMax()))) return etapa;
        log.warn("ritmo da etapa fora da zona esperada: tipo='{}' treino='{}' prescrito='{}' zona='{}' → '{}'",
                etapa.tipoEtapa(), tipoTreino, etapa.ritmoAlvo(), formatar(rapido, lento), corrigido);
        return etapa.comRitmo(corrigido);
    }

    /** {zona mais lenta, zona mais rápida} esperadas — a tabela do EtapaFcValidator, com Z6 no tiro. */
    private static int @Nullable [] zonasEsperadas(@Nullable String tipoEtapa, @Nullable String tipoTreino) {
        if (tipoEtapa == null) return null;
        String treino = tipoTreino == null ? "" : tipoTreino.toUpperCase();
        return switch (tipoEtapa.toUpperCase()) {
            case "AQUECIMENTO", "DESAQUECIMENTO" -> new int[]{1, 1};
            case "RECUPERACAO" -> "FARTLEK".equals(treino) ? new int[]{1, 2} : new int[]{1, 1};
            case "PRINCIPAL", "INTERVALADO" -> switch (treino) {
                case "REGENERATIVO" -> new int[]{1, 2};
                case "CONTINUO", "FACIL", "LONGO" -> new int[]{2, 3};
                case "FARTLEK" -> new int[]{2, 4};
                case "TEMPO_RUN" -> new int[]{3, 4};
                // Z6: tiro curto passa do VO2max em pace; a FC para em Z5 porque não acompanha
                case "INTERVALADO", "TIRO" -> new int[]{4, 6};
                default -> new int[]{2, 4};
            };
            default -> null;
        };
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
