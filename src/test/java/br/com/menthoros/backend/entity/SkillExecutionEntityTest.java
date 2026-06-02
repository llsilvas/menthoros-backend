package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.skills.core.SkillCategory;
import br.com.menthoros.backend.skills.core.SkillConfidence;
import br.com.menthoros.backend.skills.core.SkillSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD — Section 4.1: valida campos obrigatórios e invariantes da entidade SkillExecution.
 *
 * <p>Fase RED: compilação falha pois a entidade ainda não existe.
 * <p>Fase GREEN: entidade criada com todos os campos especificados.
 */
@DisplayName("SkillExecution: campos obrigatórios e invariantes")
class SkillExecutionEntityTest {

    @Test
    @DisplayName("SkillExecution pode ser instanciada com campos obrigatórios não-nulos")
    void skillExecution_camposObrigatorios_naoNulos() {
        SkillExecution exec = new SkillExecution();
        exec.setSkillKey("recovery-load-v1");
        exec.setSkillVersion("1.0");
        exec.setSeverity(SkillSeverity.INFO.name());
        exec.setConfidence(SkillConfidence.HIGH.name());
        exec.setTenantId(UUID.randomUUID());
        exec.setExecutedAt(Instant.now());

        assertNotNull(exec.getSkillKey());
        assertNotNull(exec.getSkillVersion());
        assertNotNull(exec.getSeverity());
        assertNotNull(exec.getConfidence());
        assertNotNull(exec.getTenantId());
        assertNotNull(exec.getExecutedAt());

        assertEquals("recovery-load-v1", exec.getSkillKey());
        assertEquals("1.0", exec.getSkillVersion());
        assertEquals(SkillSeverity.INFO.name(), exec.getSeverity());
        assertEquals(SkillConfidence.HIGH.name(), exec.getConfidence());
    }

    @Test
    @DisplayName("SkillExecution tenantId é obrigatório e deve ser armazenado corretamente")
    void skillExecution_tenantId_obrigatorio() {
        UUID tenantId = UUID.randomUUID();

        SkillExecution exec = new SkillExecution();
        exec.setTenantId(tenantId);

        assertNotNull(exec.getTenantId());
        assertEquals(tenantId, exec.getTenantId());
    }

    @Test
    @DisplayName("SkillExecution aceita campos opcionais nulos")
    void skillExecution_camposOpcionais_aceitamNulo() {
        SkillExecution exec = new SkillExecution();
        exec.setSkillKey("eligibility-v1");
        exec.setSkillVersion("2.0");
        exec.setSeverity(SkillSeverity.WARNING.name());
        exec.setConfidence(SkillConfidence.MEDIUM.name());
        exec.setTenantId(UUID.randomUUID());
        exec.setExecutedAt(Instant.now());

        // Campos opcionais devem aceitar nulo sem exceção
        exec.setSkillCategory(null);
        exec.setPayloadJson(null);
        exec.setEvidenceJson(null);
        exec.setRecommendationsJson(null);
        exec.setAtleta(null);
        exec.setPlanoSemanalId(null);
        exec.setTreinoRealizadoId(null);
        exec.setDataReferencia(null);

        assertNull(exec.getSkillCategory());
        assertNull(exec.getPayloadJson());
        assertNull(exec.getEvidenceJson());
        assertNull(exec.getRecommendationsJson());
        assertNull(exec.getAtleta());
        assertNull(exec.getPlanoSemanalId());
        assertNull(exec.getTreinoRealizadoId());
        assertNull(exec.getDataReferencia());
    }

    @Test
    @DisplayName("SkillExecution aceita skillCategory como string do enum SkillCategory")
    void skillExecution_skillCategory_comoString() {
        SkillExecution exec = new SkillExecution();
        exec.setSkillCategory(SkillCategory.RECOVERY.name());

        assertEquals("RECOVERY", exec.getSkillCategory());
    }

    @Test
    @DisplayName("SkillExecution aceita todos os valores de SkillSeverity")
    void skillExecution_severity_todosOsValores() {
        for (SkillSeverity severity : SkillSeverity.values()) {
            SkillExecution exec = new SkillExecution();
            exec.setSeverity(severity.name());
            assertEquals(severity.name(), exec.getSeverity());
        }
    }

    @Test
    @DisplayName("SkillExecution aceita todos os valores de SkillConfidence")
    void skillExecution_confidence_todosOsValores() {
        for (SkillConfidence confidence : SkillConfidence.values()) {
            SkillExecution exec = new SkillExecution();
            exec.setConfidence(confidence.name());
            assertEquals(confidence.name(), exec.getConfidence());
        }
    }

    @Test
    @DisplayName("SkillExecution armazena dataReferencia corretamente")
    void skillExecution_dataReferencia_armazenadaCorretamente() {
        LocalDate hoje = LocalDate.now();

        SkillExecution exec = new SkillExecution();
        exec.setDataReferencia(hoje);

        assertEquals(hoje, exec.getDataReferencia());
    }

    @Test
    @DisplayName("SkillExecution armazena referências opcionais a plano e treino")
    void skillExecution_referenciasOpcionais_armazenadas() {
        UUID planoId = UUID.randomUUID();
        UUID treinoId = UUID.randomUUID();

        SkillExecution exec = new SkillExecution();
        exec.setPlanoSemanalId(planoId);
        exec.setTreinoRealizadoId(treinoId);

        assertEquals(planoId, exec.getPlanoSemanalId());
        assertEquals(treinoId, exec.getTreinoRealizadoId());
    }
}
