package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resposta do auto-cadastro.
 *
 * <p>Não existe campo de token neste record, e isso é o requisito, não um detalhe: o cadastro
 * <strong>não</strong> autentica. Quem autentica é o Keycloak, pelo fluxo Authorization Code + PKCE
 * que o frontend já implementa.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Confirmação do auto-cadastro — sem token, por decisão de arquitetura")
public record CoachSignupOutputDto(

        @Schema(description = "Identificador da assessoria na URL", example = "corridasserra")
        String slug,

        @Schema(description = "E-mail para onde a verificação foi enviada", example = "maria@exemplo.com")
        String email,

        @Schema(description = "O que o usuário deve fazer em seguida",
                example = "Enviamos um e-mail de verificação. Confirme para poder entrar.")
        String proximoPasso
) {

    public static final String VERIFIQUE_O_EMAIL =
            "Enviamos um e-mail de verificação. Confirme o endereço para poder entrar.";

    public static final String PRONTO_PARA_ENTRAR =
            "Sua assessoria está pronta. Entre com seu e-mail e a senha que você acabou de criar.";

    public static CoachSignupOutputDto de(String slug, String email) {
        return new CoachSignupOutputDto(slug, email, VERIFIQUE_O_EMAIL);
    }

    /** Convite de fundadora: o e-mail já foi provado pelo token, não há verificação a esperar. */
    public static CoachSignupOutputDto prontoParaEntrar(String slug, String email) {
        return new CoachSignupOutputDto(slug, email, PRONTO_PARA_ENTRAR);
    }
}
