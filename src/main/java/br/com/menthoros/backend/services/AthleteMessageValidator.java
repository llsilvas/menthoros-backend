package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.llm.AthleteMessageDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Validação em runtime do bloco do atleta, ANTES de persistir (pré-mortem Codex #4 da change
 * {@code analise-ia-treino-atleta}): um prompt bem escrito não impede uma prescrição de chegar
 * ao atleta — {@code .entity()} desserializa e o listener persistiria direto. Violou → o
 * listener nulifica os quatro campos e registra o motivo; melhor sem card do que com um errado.
 *
 * <p>Limite conhecido (design D6): o regex pega jargão, não intenção — "seu corpo está pedindo
 * uma pausa" passa. O classificador binário via Haiku é decisão da task 0.3.
 */
@Component
public class AthleteMessageValidator {

    public static final String MOTIVO_JARGAO = "JARGAO_OU_PRESCRICAO";
    public static final String MOTIVO_TAMANHO = "TAMANHO";
    public static final String MOTIVO_IDIOMA = "IDIOMA";

    static final int MAX_CHARS = 240;

    /** \b evita falso positivo em "atleta" (contém "atl") e "descanso" etc. */
    private static final Pattern PROIBIDOS = Pattern.compile(
            "\\b(TSB|CTL|ATL|score|cancel\\w*|pule|pular|troque|overtraining|les[ãa]o|SNC)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final List<String> MARCADORES_PT = List.of(
            "você", " não", " de ", " que ", " com ", " para ", " seu", " sua", " o ", " e ");
    private static final List<String> MARCADORES_EN = List.of(
            " the ", " your ", " you ", " and ", " with ", " was ", " it ", " to ", " of ", " next ");

    /**
     * Idempotent: YES — função pura.
     * Side Effects: none
     * Tenant-aware: N/A
     *
     * @return o motivo do bloqueio, ou vazio quando o bloco pode ser persistido
     */
    public Optional<String> validar(AthleteMessageDto dto) {
        List<String> textos = campos(dto);

        if (textos.stream().anyMatch(t -> PROIBIDOS.matcher(t).find())) {
            return Optional.of(MOTIVO_JARGAO);
        }
        if (textos.stream().anyMatch(t -> t.length() > MAX_CHARS)) {
            return Optional.of(MOTIVO_TAMANHO);
        }

        String texto = " " + String.join(" ", textos).toLowerCase(Locale.ROOT) + " ";
        long marcadoresPt = MARCADORES_PT.stream().filter(texto::contains).count();
        long marcadoresEn = MARCADORES_EN.stream().filter(texto::contains).count();
        if (marcadoresPt == 0 || marcadoresEn >= 3) {
            return Optional.of(MOTIVO_IDIOMA);
        }

        return Optional.empty();
    }

    /** Bloco sem os quatro campos preenchidos é tratado como ausente, não como bloqueado. */
    public boolean completo(AthleteMessageDto dto) {
        return campos(dto).size() == 4;
    }

    private static List<String> campos(AthleteMessageDto dto) {
        return Stream.of(dto.recognition(), dto.howItWent(), dto.effortReading(), dto.nextWorkoutTip())
                .filter(t -> t != null && !t.isBlank())
                .toList();
    }
}
