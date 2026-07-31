package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Identidade do usuário autenticado (GET /api/v1/users/me)")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsuarioMeOutputDto(
        @Schema(description = "Identificador único do usuário", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID id,

        @Schema(description = "Nome do usuário", example = "João Silva")
        String nome,

        @Schema(description = "Email do usuário", example = "joao.silva@exemplo.com")
        String email,

        @Schema(description = "URL do avatar sincronizada do Keycloak; ausente quando não há",
                example = "https://exemplo.com/avatar.png")
        String avatarUrl,

        @Schema(description = "Role do usuário", example = "ATLETA")
        UserRole role,

        @Schema(description = "Assessoria (tenant) à qual o usuário pertence")
        Assessoria assessoria,

        @Schema(description = "Identificador do atleta vinculado, presente apenas para a role ATLETA com vínculo",
                example = "123e4567-e89b-12d3-a456-426614174000")
        UUID atletaId,

        @Schema(description = "Se o usuário já aceitou as versões VIGENTES da Política e dos Termos. "
                + "Derivado da existência de registro em tb_usuario_lgpd_consent — não é campo "
                + "persistido. Volta a false quando uma das versões vigentes muda.",
                example = "true")
        boolean lgpdConsentGranted,

        @Schema(description = "Data de vigência da Política de Privacidade em vigor. O cliente deve "
                + "ecoá-la ao registrar o aceite.", example = "2026-06-30")
        String lgpdCurrentPolicyVersion,

        @Schema(description = "Data de vigência dos Termos de Uso em vigor. O cliente deve ecoá-la "
                + "ao registrar o aceite.", example = "2026-06-30")
        String lgpdCurrentTermsVersion,

        @Schema(description = "Momento do último aceite registrado; ausente quando nunca consentiu",
                example = "2026-07-31T19:23:43Z")
        Instant lgpdConsentedAt,

        @Schema(description = "Versão da Política efetivamente aceita no último registro. Pode ser "
                + "ANTERIOR à vigente — nesse caso lgpdConsentGranted é false.", example = "2026-06-30")
        String lgpdAcceptedPolicyVersion,

        @Schema(description = "Versão dos Termos efetivamente aceita no último registro. Pode ser "
                + "ANTERIOR à vigente.", example = "2026-06-30")
        String lgpdAcceptedTermsVersion) {

    @Schema(description = "Dados básicos da assessoria do usuário")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Assessoria(
            @Schema(description = "Identificador único da assessoria", example = "123e4567-e89b-12d3-a456-426614174000")
            UUID id,

            @Schema(description = "Nome da assessoria", example = "Corridas Serra")
            String nome,

            @Schema(description = "Domínio da assessoria", example = "corridasserra")
            String dominio) {
    }
}
