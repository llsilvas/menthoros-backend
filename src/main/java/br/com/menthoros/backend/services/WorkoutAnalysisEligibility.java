package br.com.menthoros.backend.services;

import br.com.menthoros.backend.config.core.WorkoutAnalysisProperties;
import br.com.menthoros.backend.entity.TreinoRealizado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Elegibilidade de um {@link TreinoRealizado} para análise pós-treino por IA.
 *
 * <p>Fonte única das condições que antes viviam como guards no {@code WorkoutAnalysisListener}:
 * o listener as usa para decidir se gera análise, e o endpoint do atleta
 * ({@code GET /me/realizados/{id}/analise}) usa as mesmas para devolver {@code 200 PENDING}
 * na janela em que o {@code @Async} ainda não criou a linha — se as duas pontas divergirem, o
 * card do atleta some ou promete análise que nunca chega (pré-mortem Codex #2 da change
 * {@code analise-ia-treino-atleta}).
 *
 * <p>O kill switch do bloco do atleta ({@code athlete-message.enabled}) NÃO entra aqui: ele
 * desliga a exposição ao atleta, não a geração da análise do coach.
 */
@Component
@RequiredArgsConstructor
public class WorkoutAnalysisEligibility {

    private final WorkoutAnalysisProperties properties;

    /**
     * Idempotent: YES — função pura sobre o treino e o relógio.
     * Side Effects: none
     * Tenant-aware: N/A — decide sobre a entidade já carregada.
     */
    public boolean elegivel(TreinoRealizado treino) {
        if (treino.getPercepcaoEsforco() == null) {
            return false;
        }
        LocalDate dataTreino = treino.getDataTreino();
        if (dataTreino == null) {
            // Defensivo (CA8 do ingestor): ausência de data não presume "antigo demais".
            return true;
        }
        LocalDate limite = LocalDate.now().minusDays(properties.getMaxIdadeDias());
        return !dataTreino.isBefore(limite);
    }
}
