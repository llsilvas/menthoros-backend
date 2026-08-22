package br.com.menthoros.backend.services.helper;


import br.com.menthoros.backend.domain.workout.PaceTarget;

import java.text.Normalizer;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser best-effort dos alvos textuais do planner para o modelo canônico.
 * Nunca lança; formato desconhecido retorna vazio (caminho normal, sem log de erro —
 * prescrição livre é rotina, não incidente).
 */
public final class IntervalsIcuTargetParser {

    private static final Pattern PACE_FAIXA = Pattern.compile("^(\\d{1,2}):([0-5]\\d)-(\\d{1,2}):([0-5]\\d)(/km)?$");
    private static final Pattern PACE_UNICO = Pattern.compile("^(\\d{1,2}):([0-5]\\d)(/km)?$");
    private static final Pattern FC_BPM = Pattern.compile("^(\\d{2,3})-(\\d{2,3})\\s*bpm$");
    private static final Pattern FC_PERCENT = Pattern.compile("^(\\d{2,3})-(\\d{2,3})%.*$");
    private static final Pattern ZONA = Pattern.compile("^z([1-5])(-z?[1-5])?$");

    private IntervalsIcuTargetParser() {
    }

    public static Optional<PaceTarget> parsePace(String ritmoAlvo) {
        String s = normaliza(ritmoAlvo);
        if (s == null) return Optional.empty();
        Matcher faixa = PACE_FAIXA.matcher(s);
        if (faixa.matches()) {
            int inicio = Integer.parseInt(faixa.group(1)) * 60 + Integer.parseInt(faixa.group(2));
            int fim = Integer.parseInt(faixa.group(3)) * 60 + Integer.parseInt(faixa.group(4));
            return Optional.of(new PaceTarget(Math.min(inicio, fim), Math.max(inicio, fim)));
        }
        Matcher unico = PACE_UNICO.matcher(s);
        if (unico.matches()) {
            int valor = Integer.parseInt(unico.group(1)) * 60 + Integer.parseInt(unico.group(2));
            return Optional.of(new PaceTarget(valor, valor));
        }
        return Optional.empty();
    }

    /**
     * Alvo de FC como o plano o escreveu, antes de ser resolvido contra o atleta.
     *
     * <p>Tipo intermediário de propósito: percentual e zona só têm significado em bpm depois de
     * cruzados com o LTHR do atleta, e é o {@link IntervalsIcuFcAlvoResolver} que faz isso. Enquanto
     * o alvo estiver nesta forma, ele ainda não é uma meta — não existe {@code HrTarget} relativo.</p>
     */
    public record FcAlvoBruto(Base base, int inicio, int fim) {
        public enum Base {
            /** Já absoluto: {@code "140-150 bpm"}. */
            BPM,
            /** Percentual do plano — interpretado na base do domínio (%LTHR), nunca %FCmax. */
            PERCENT,
            /** Número da zona (1–5), resolvido pela faixa do {@code ZonaTreinoService}. */
            ZONE
        }
    }

    public static Optional<FcAlvoBruto> parseFc(String fcAlvoEtapa) {
        String s = normaliza(fcAlvoEtapa);
        if (s == null) return Optional.empty();
        Matcher bpm = FC_BPM.matcher(s);
        if (bpm.matches()) {
            return Optional.of(new FcAlvoBruto(FcAlvoBruto.Base.BPM,
                    Integer.parseInt(bpm.group(1)), Integer.parseInt(bpm.group(2))));
        }
        Matcher pct = FC_PERCENT.matcher(s);
        if (pct.matches()) {
            return Optional.of(new FcAlvoBruto(FcAlvoBruto.Base.PERCENT,
                    Integer.parseInt(pct.group(1)), Integer.parseInt(pct.group(2))));
        }
        return Optional.empty();
    }

    public static Optional<FcAlvoBruto> parseZona(String zonaAlvo) {
        String s = normaliza(zonaAlvo);
        if (s == null) return Optional.empty();
        Matcher zona = ZONA.matcher(s);
        if (zona.matches()) {
            int z = Integer.parseInt(zona.group(1));   // faixa -> zona inferior (conservador)
            return Optional.of(new FcAlvoBruto(FcAlvoBruto.Base.ZONE, z, z));
        }
        return Optional.empty();
    }

    private static String normaliza(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String s = Normalizer.normalize(valor.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return s.replaceAll("\\s+", " ");
    }
}
