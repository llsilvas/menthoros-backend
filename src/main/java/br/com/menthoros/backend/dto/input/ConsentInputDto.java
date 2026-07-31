package br.com.menthoros.backend.dto.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Aceite dos Termos de Uso e da Política de Privacidade pelo coach.
 *
 * <p>As versões são <b>ecoadas pelo cliente</b>: ele devolve as que efetivamente renderizou, e o
 * servidor recusa se estiverem defasadas. Se o backend simplesmente carimbasse a versão vigente, um
 * coach com a página aberta durante um deploy que troca a Política seria registrado aceitando um
 * texto que nunca viu — registro legal falso, pior que registro nenhum.
 */
@Schema(description = "Aceite dos Termos de Uso e da Política de Privacidade")
public record ConsentInputDto(

        @Schema(description = "Aceite dos Termos de Uso — precisa ser true", example = "true")
        @NotNull(message = "O aceite dos Termos de Uso é obrigatório")
        @AssertTrue(message = "É necessário aceitar os Termos de Uso")
        Boolean termsAccepted,

        @Schema(description = "Consentimento com a Política de Privacidade — precisa ser true",
                example = "true")
        @NotNull(message = "O consentimento com a Política de Privacidade é obrigatório")
        @AssertTrue(message = "É necessário consentir com a Política de Privacidade")
        Boolean privacyPolicyAccepted,

        @Schema(description = "Data de vigência da Política de Privacidade exibida ao usuário",
                example = "2026-06-30")
        @NotBlank(message = "A versão da Política de Privacidade é obrigatória")
        String policyVersion,

        @Schema(description = "Data de vigência dos Termos de Uso exibidos ao usuário",
                example = "2026-06-30")
        @NotBlank(message = "A versão dos Termos de Uso é obrigatória")
        String termsVersion) {
}
