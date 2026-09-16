package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.compliance.SchemaVersion;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.v2.PlanoSemanalLlmDtoV2;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

/**
 * Dispatch por {@code schemaVersion} compartilhado entre {@link EvalDeterministicGrader} e
 * {@link EvalAgreementGrader} (plan-generation-eval-set) — extraído porque os dois graders tinham
 * o mesmo bloco copiado (achado do /qa, `clean-code-reviewer`): {@code schema-v2} resolve via
 * {@link SessionResolver} antes de devolver o shape v1 (mesmo caminho de
 * {@code IaServiceImpl.gerarChamadaLlmV2}); qualquer outro valor (ou ausente) desserializa direto.
 *
 * <p>Idempotent: YES. Side Effects: NONE. Tenant-aware: NÃO.
 */
final class EvalPlanoJsonParser {

    private EvalPlanoJsonParser() {
    }

    static PlanoSemanalLlmDto parsear(ObjectMapper objectMapper, SessionResolver sessionResolver,
                                       String json, @Nullable String schemaVersion, AthleteZones zonasAtleta) {
        try {
            if (SchemaVersion.V2.equals(schemaVersion)) {
                PlanoSemanalLlmDtoV2 planoV2 = objectMapper.readValue(json, PlanoSemanalLlmDtoV2.class);
                return sessionResolver.resolverPlano(planoV2, zonasAtleta);
            }
            return objectMapper.readValue(json, PlanoSemanalLlmDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Resposta (schemaVersion=" + schemaVersion + ") não é um JSON válido: " + e.getMessage(), e);
        }
    }
}
