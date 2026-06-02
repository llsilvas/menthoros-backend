package br.com.menthoros.backend.skills.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de contrato para os tipos base da arquitetura de skills de domínio.
 * Segue ciclo TDD: estes testes foram escritos ANTES da implementação.
 */
@DisplayName("DomainSkill — contratos base")
class DomainSkillContractTest {

    @Test
    @DisplayName("SkillResult com payload tem evidências e recomendações acessíveis")
    void skillResult_comPayload_temEvidenciasERecomendacoes() {
        SkillResult<String> resultado = new SkillResult<>(
                "recovery-load-v1",
                "1.0",
                SkillSeverity.WARNING,
                SkillConfidence.HIGH,
                "Atleta com ATL elevado",
                List.of("ATL=90, CTL=74, TSB=-16"),
                List.of("Reduzir volume em 15%")
        );

        assertThat(resultado.skillKey()).isEqualTo("recovery-load-v1");
        assertThat(resultado.skillVersion()).isEqualTo("1.0");
        assertThat(resultado.severity()).isEqualTo(SkillSeverity.WARNING);
        assertThat(resultado.confidence()).isEqualTo(SkillConfidence.HIGH);
        assertThat(resultado.payload()).isEqualTo("Atleta com ATL elevado");
        assertThat(resultado.evidence()).containsExactly("ATL=90, CTL=74, TSB=-16");
        assertThat(resultado.recommendations()).containsExactly("Reduzir volume em 15%");
    }

    @Test
    @DisplayName("SkillContext tem atletaId e tenantId acessíveis")
    void skillContext_temAtletaIdETenant() {
        UUID atletaId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        LocalDate dataReferencia = LocalDate.of(2026, 6, 1);

        SkillContext context = new SkillContext(atletaId, tenantId, dataReferencia, Map.of("key", "value"));

        assertThat(context.atletaId()).isEqualTo(atletaId);
        assertThat(context.tenantId()).isEqualTo(tenantId);
        assertThat(context.dataReferencia()).isEqualTo(dataReferencia);
        assertThat(context.metadata()).containsEntry("key", "value");
    }

    @Test
    @DisplayName("AthleteAnalysisSnapshot.hasBlocker retorna true quando existe resultado BLOCKER")
    void athleteAnalysisSnapshot_hasBlocker_retornaTrueQuandoExisteBlocker() {
        UUID atletaId = UUID.randomUUID();
        SkillResult<String> blocker = new SkillResult<>(
                "overtraining-guard-v1", "1.0",
                SkillSeverity.BLOCKER, SkillConfidence.HIGH,
                "Sinais de overtraining detectados",
                List.of("TSB=-25, dias consecutivos=9"),
                List.of("Cancelar treino desta semana")
        );
        SkillResult<String> warning = new SkillResult<>(
                "recovery-load-v1", "1.0",
                SkillSeverity.WARNING, SkillConfidence.MEDIUM,
                "ATL elevado",
                List.of("ATL=88"),
                List.of("Reduzir 10%")
        );

        AthleteAnalysisSnapshot snapshot = new AthleteAnalysisSnapshot(
                atletaId, LocalDate.now(), List.of(blocker, warning)
        );

        assertThat(snapshot.hasBlocker()).isTrue();
        assertThat(snapshot.hasCritical()).isFalse();
        assertThat(snapshot.getSeverityCount(SkillSeverity.BLOCKER)).isEqualTo(1);
        assertThat(snapshot.getSeverityCount(SkillSeverity.WARNING)).isEqualTo(1);
    }

    @Test
    @DisplayName("AthleteAnalysisSnapshot.toPromptSummary retorna formatação legível")
    void athleteAnalysisSnapshot_toPromptSummary_formatacaoLegivel() {
        UUID atletaId = UUID.randomUUID();
        SkillResult<String> resultado = new SkillResult<>(
                "recovery-load-v1", "1.0",
                SkillSeverity.WARNING, SkillConfidence.HIGH,
                "Atleta com ATL elevado",
                List.of("ATL=90, CTL=74, TSB=-16"),
                List.of("Reduzir volume em 15%")
        );

        AthleteAnalysisSnapshot snapshot = new AthleteAnalysisSnapshot(
                atletaId, LocalDate.now(), List.of(resultado)
        );

        String summary = snapshot.toPromptSummary();

        assertThat(summary).isNotNull();
        assertThat(summary).contains("WARNING");
        assertThat(summary).contains("recovery-load-v1");
        assertThat(summary).contains("ATL=90");
        assertThat(summary).contains("Reduzir volume em 15%");
    }

    @Test
    @DisplayName("SkillCategory contém todas as categorias esperadas")
    void skillCategory_contemTodasAsCategorias() {
        assertThat(SkillCategory.values()).containsExactlyInAnyOrder(
                SkillCategory.RECOVERY,
                SkillCategory.ELIGIBILITY,
                SkillCategory.PRESCRIPTION_GUARD,
                SkillCategory.WORKOUT_ANALYSIS,
                SkillCategory.DISTRIBUTION
        );
    }

    @Test
    @DisplayName("SkillSeverity contém INFO, WARNING, CRITICAL, BLOCKER")
    void skillSeverity_contemTodosOsNiveis() {
        assertThat(SkillSeverity.values()).containsExactlyInAnyOrder(
                SkillSeverity.INFO,
                SkillSeverity.WARNING,
                SkillSeverity.CRITICAL,
                SkillSeverity.BLOCKER
        );
    }

    @Test
    @DisplayName("SkillConfidence contém LOW, MEDIUM, HIGH")
    void skillConfidence_contemTodosOsNiveis() {
        assertThat(SkillConfidence.values()).containsExactlyInAnyOrder(
                SkillConfidence.LOW,
                SkillConfidence.MEDIUM,
                SkillConfidence.HIGH
        );
    }

    @Test
    @DisplayName("AthleteAnalysisSnapshot.hasCritical retorna true apenas para CRITICAL")
    void athleteAnalysisSnapshot_hasCritical_retornaTrueApenasParaCritical() {
        UUID atletaId = UUID.randomUUID();
        SkillResult<String> critical = new SkillResult<>(
                "load-spike-v1", "1.0",
                SkillSeverity.CRITICAL, SkillConfidence.HIGH,
                "Pico de carga perigoso",
                List.of("Ramp rate = 12 TSS/sem"),
                List.of("Reduzir urgente")
        );

        AthleteAnalysisSnapshot snapshot = new AthleteAnalysisSnapshot(
                atletaId, LocalDate.now(), List.of(critical)
        );

        assertThat(snapshot.hasCritical()).isTrue();
        assertThat(snapshot.hasBlocker()).isFalse();
    }

    @Test
    @DisplayName("AthleteAnalysisSnapshot sem resultados retorna hasBlocker=false e hasCritical=false")
    void athleteAnalysisSnapshot_semResultados_semBlockerSemCritical() {
        AthleteAnalysisSnapshot snapshot = new AthleteAnalysisSnapshot(
                UUID.randomUUID(), LocalDate.now(), List.of()
        );

        assertThat(snapshot.hasBlocker()).isFalse();
        assertThat(snapshot.hasCritical()).isFalse();
        assertThat(snapshot.getSeverityCount(SkillSeverity.WARNING)).isZero();
    }
}
