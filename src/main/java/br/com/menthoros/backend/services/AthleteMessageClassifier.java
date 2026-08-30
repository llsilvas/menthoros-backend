package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.llm.AthleteMessageDto;
import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.routing.TaskComplexity;
import br.com.menthoros.backend.services.prompt.PromptTemplateLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Checagem binária semântica do bloco do atleta (decisão 0.3 de {@code analise-ia-treino-atleta},
 * founder, 2026-08-30): o regex do {@link AthleteMessageValidator} pega jargão, não intenção —
 * "seu corpo está pedindo uma pausa" passa ileso. Sem gate do coach, esta é a única barreira
 * semântica antes de o texto chegar ao atleta.
 *
 * <p><b>Fail-open de propósito:</b> falha na chamada → {@code false} (não bloqueia). O regex e as
 * demais checagens já rodaram; bloquear todo bloco quando o Haiku está fora derrubaria a feature
 * inteira por indisponibilidade de um verificador secundário.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AthleteMessageClassifier {

    public static final String MOTIVO_CLASSIFICADOR = "PRESCRICAO_CLASSIFICADOR";

    private static final String PROMPT_TEMPLATE = "athlete-message-classifier-prompt.txt";

    private final ModelRouter modelRouter;
    private final PromptTemplateLoader templateLoader;

    /**
     * Idempotent: YES — mesma entrada tende à mesma resposta.
     * Side Effects: External API call (Claude Haiku via rota STANDARD)
     * Tenant-aware: N/A
     */
    public boolean mandaMudarPlano(AthleteMessageDto dto) {
        String texto = String.join("\n",
                dto.recognition(), dto.howItWent(), dto.effortReading(), dto.nextWorkoutTip());
        try {
            String prompt = templateLoader.loadAndFormat(PROMPT_TEMPLATE, texto);
            String resposta = modelRouter.route(TaskComplexity.STANDARD)
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            return resposta != null && resposta.trim().toUpperCase(Locale.ROOT).startsWith("SIM");
        } catch (Exception e) {
            log.warn("Classificador do bloco do atleta indisponível, seguindo sem ele: {}", e.getMessage());
            return false;
        }
    }
}
