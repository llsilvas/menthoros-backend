package br.com.menthoros.backend.skills.core;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Consolidação de todos os resultados de skills executadas para um atleta em uma data.
 *
 * <p>Produzido pelo {@link SkillOrchestratorService} após executar todas as skills
 * relevantes. Usado para:</p>
 * <ul>
 *   <li>Injeção no prompt do LLM via {@link #toPromptSummary()}.</li>
 *   <li>Decisões de bloqueio via {@link #hasBlocker()}.</li>
 *   <li>Contagem e filtragem de resultados por severidade.</li>
 * </ul>
 *
 * @param atletaId       ID do atleta analisado
 * @param dataReferencia data de referência da análise
 * @param results        lista de resultados produzidos pelas skills (pode ser vazia)
 */
public record AthleteAnalysisSnapshot(
        UUID atletaId,
        LocalDate dataReferencia,
        List<SkillResult<?>> results
) {

    /**
     * Verifica se há pelo menos um resultado com severidade {@link SkillSeverity#BLOCKER}.
     *
     * @return true se existe algum BLOCKER nos resultados
     */
    public boolean hasBlocker() {
        return results.stream()
                .anyMatch(r -> r.severity() == SkillSeverity.BLOCKER);
    }

    /**
     * Verifica se há pelo menos um resultado com severidade {@link SkillSeverity#CRITICAL}.
     *
     * @return true se existe algum CRITICAL nos resultados
     */
    public boolean hasCritical() {
        return results.stream()
                .anyMatch(r -> r.severity() == SkillSeverity.CRITICAL);
    }

    /**
     * Conta quantos resultados têm a severidade especificada.
     *
     * @param severity severidade a ser contada
     * @return número de resultados com essa severidade
     */
    public long getSeverityCount(SkillSeverity severity) {
        return results.stream()
                .filter(r -> r.severity() == severity)
                .count();
    }

    /**
     * Gera um resumo formatado de todos os resultados para injeção no prompt do LLM.
     *
     * <p>Formato:</p>
     * <pre>
     * ## Análise Fisiológica (SkillOrchestrator)
     * [WARNING] recovery-load-v1: Atleta com ATL elevado
     *   Evidências: ATL=90, CTL=74, TSB=-16
     *   Recomendações: Reduzir volume em 15%
     * </pre>
     *
     * @return string formatada para o LLM, ou string vazia se não há resultados
     */
    public String toPromptSummary() {
        if (results == null || results.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Análise Fisiológica (SkillOrchestrator)\n");

        for (SkillResult<?> result : results) {
            sb.append(String.format("[%s] %s: %s%n",
                    result.severity().name(),
                    result.skillKey(),
                    result.payload() != null ? result.payload() : ""));

            if (result.evidence() != null && !result.evidence().isEmpty()) {
                sb.append("  Evidências: ")
                        .append(String.join(", ", result.evidence()))
                        .append("\n");
            }

            if (result.recommendations() != null && !result.recommendations().isEmpty()) {
                sb.append("  Recomendações: ")
                        .append(String.join("; ", result.recommendations()))
                        .append("\n");
            }
        }

        return sb.toString();
    }
}
