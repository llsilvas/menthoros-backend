package br.com.menthoros.backend.config.core;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Guard de custo do {@code WorkoutAnalysisListener} (ingestao-treino-realizado, D5): treino mais
 * antigo que {@code maxIdadeDias} não dispara análise por IA.
 *
 * <p>Existe porque {@code registrar} passa a publicar {@code TreinoRegistradoEvent} em toda
 * inserção, independente da fonte (D5) — sem este guard, a carga inicial de um atleta recém
 * conectado ao Strava (dezenas de atividades históricas) dispararia uma chamada de LLM por
 * atividade.</p>
 */
@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "app.workout-analysis")
public class WorkoutAnalysisProperties {

    @Min(value = 1, message = "maxIdadeDias deve ser >= 1")
    private int maxIdadeDias = 30;

    private final AthleteMessage athleteMessage = new AthleteMessage();

    /**
     * Kill switch da exposição do bloco do atleta (analise-ia-treino-atleta, D3):
     * {@code app.workout-analysis.athlete-message.enabled=false} faz o endpoint do atleta
     * devolver 204 e o flag do plano ficar false, SEM parar a geração do bloco no listener —
     * reversão em produção sem deploy de front e sem perder dados.
     */
    @Getter
    @Setter
    public static class AthleteMessage {
        private boolean enabled = true;
    }
}
