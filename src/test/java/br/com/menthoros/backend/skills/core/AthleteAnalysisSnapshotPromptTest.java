package br.com.menthoros.backend.skills.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração do toPromptSummary() com o PlanoTreinoPromptBuilder.
 * Verifica que o formato gerado é legível pelo LLM e segue a especificação.
 */
@DisplayName("AthleteAnalysisSnapshot — toPromptSummary")
class AthleteAnalysisSnapshotPromptTest {

    @Test
    @DisplayName("toPromptSummary com resultado WARNING inclui texto de alerta legível pelo LLM")
    void toPromptSummary_warning_incluiTextoDeAlerta() {
        SkillResult<String> resultado = new SkillResult<>(
                "recovery-load-v1", "1.0",
                SkillSeverity.WARNING, SkillConfidence.HIGH,
                "Atleta com ATL elevado",
                List.of("ATL=90, CTL=74, TSB=-16"),
                List.of("Reduzir volume em 15%")
        );
        AthleteAnalysisSnapshot snapshot = new AthleteAnalysisSnapshot(
                UUID.randomUUID(), LocalDate.now(), List.of(resultado)
        );

        String summary = snapshot.toPromptSummary();

        assertThat(summary).contains("WARNING");
        assertThat(summary).contains("recovery-load");
        assertThat(summary).contains("Reduzir volume");
    }

    @Test
    @DisplayName("toPromptSummary sem resultados retorna seção vazia, não null")
    void toPromptSummary_semResultados_retornaStringVazia() {
        AthleteAnalysisSnapshot snapshot = new AthleteAnalysisSnapshot(
                UUID.randomUUID(), LocalDate.now(), List.of()
        );
        assertThat(snapshot.toPromptSummary()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("toPromptSummary com BLOCKER inclui cabeçalho de análise fisiológica")
    void toPromptSummary_blocker_incluiCabecalho() {
        SkillResult<String> blocker = new SkillResult<>(
                "overtraining-guard-v1", "1.0",
                SkillSeverity.BLOCKER, SkillConfidence.HIGH,
                "Sinais de overtraining",
                List.of("TSB=-25, dias consecutivos=9"),
                List.of("Cancelar treino desta semana")
        );
        AthleteAnalysisSnapshot snapshot = new AthleteAnalysisSnapshot(
                UUID.randomUUID(), LocalDate.now(), List.of(blocker)
        );

        String summary = snapshot.toPromptSummary();

        assertThat(summary).contains("## Análise Fisiológica");
        assertThat(summary).contains("BLOCKER");
        assertThat(summary).contains("overtraining-guard-v1");
        assertThat(summary).contains("Cancelar treino desta semana");
    }

    @Test
    @DisplayName("toPromptSummary com múltiplos resultados inclui todos")
    void toPromptSummary_multiplosResultados_incluiTodos() {
        SkillResult<String> r1 = new SkillResult<>(
                "recovery-load-v1", "1.0",
                SkillSeverity.WARNING, SkillConfidence.HIGH,
                "ATL elevado",
                List.of("ATL=90"),
                List.of("Reduzir 15%")
        );
        SkillResult<String> r2 = new SkillResult<>(
                "eligibility-intervalado-v1", "1.0",
                SkillSeverity.INFO, SkillConfidence.MEDIUM,
                "Elegível para intervalado",
                List.of("TSB=5, >=4 treinos/semana"),
                List.of("Incluir 1 sessão intervalada")
        );
        AthleteAnalysisSnapshot snapshot = new AthleteAnalysisSnapshot(
                UUID.randomUUID(), LocalDate.now(), List.of(r1, r2)
        );

        String summary = snapshot.toPromptSummary();

        assertThat(summary).contains("recovery-load-v1");
        assertThat(summary).contains("eligibility-intervalado-v1");
        assertThat(summary).contains("WARNING");
        assertThat(summary).contains("INFO");
    }

    @Test
    @DisplayName("toPromptSummary inclui evidências e recomendações em formato estruturado")
    void toPromptSummary_incluiEvidenciasERecomendacoes() {
        SkillResult<String> resultado = new SkillResult<>(
                "recovery-load-v1", "1.0",
                SkillSeverity.CRITICAL, SkillConfidence.HIGH,
                "Sobrecarga detectada",
                List.of("Ramp rate=12", "TSB=-20"),
                List.of("Semana de recuperação obrigatória", "Reduzir volume 30%")
        );
        AthleteAnalysisSnapshot snapshot = new AthleteAnalysisSnapshot(
                UUID.randomUUID(), LocalDate.now(), List.of(resultado)
        );

        String summary = snapshot.toPromptSummary();

        assertThat(summary).contains("Evidências:");
        assertThat(summary).contains("Ramp rate=12");
        assertThat(summary).contains("Recomendações:");
        assertThat(summary).contains("Semana de recuperação obrigatória");
    }
}
