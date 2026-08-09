package br.com.menthoros.backend.dto.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

@Schema(description = "Dados do auto-cadastro público de uma assessoria e do seu coach")
public record CoachSignupInputDto(

        @Schema(description = "Nome do coach", example = "Maria Treinadora", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 120)
        String nome,

        @Schema(description = "E-mail do coach — vira o username no Keycloak", example = "maria@exemplo.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Email
        // 100 espelha tb_usuario.email; um e-mail maior passaria na validação e
        // estouraria a coluna na hora de persistir.
        @Size(max = 100)
        String email,

        @Schema(description = "Senha inicial", requiredMode = Schema.RequiredMode.REQUIRED, format = "password")
        @NotBlank
        @Size(min = TAMANHO_MINIMO_DA_SENHA, max = TAMANHO_MAXIMO_DA_SENHA)
        String senha,

        @Schema(description = "Nome da assessoria", example = "Assessoria Corrida na Serra", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 200)
        String nomeAssessoria,

        @Schema(description = "Identificador da assessoria na URL", example = "corridasserra", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(min = TAMANHO_MINIMO_DO_SLUG, max = 100)
        @Pattern(regexp = FORMATO_DO_SLUG, message = "Use de 3 a 100 caracteres: letras minúsculas, números e hífens simples entre eles")
        String slug,

        @Schema(description = "Aceite do consentimento LGPD (obrigatório)", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @AssertTrue(message = "O aceite dos termos de uso de dados é obrigatório")
        Boolean aceiteLgpd,

        @Schema(description = "Campo honeypot anti-spam — deve vir vazio", hidden = true)
        String website
) {

    public static final int TAMANHO_MINIMO_DA_SENHA = 12;
    public static final int TAMANHO_MAXIMO_DA_SENHA = 128;

    /** Minúsculas, números e hífen simples, nunca nas bordas. O tamanho máximo real fica no @Size. */
    static final String FORMATO_DO_SLUG = "^[a-z0-9]+(-[a-z0-9]+)*$";

    /** O @Pattern acima aceitaria "ab"; o mínimo de 3 é regra de produto, imposta pelo @Size. */
    static final int TAMANHO_MINIMO_DO_SLUG = 3;

    /**
     * `default` é o tenant semeado pela V2 — tomá-lo colidiria com dado existente. Os demais são
     * subdomínios e caminhos que a plataforma usa ou pode vir a usar.
     */
    public static final Set<String> SLUGS_RESERVADOS = Set.of(
            "default", "menthoros", "www", "api", "app", "admin", "auth", "login", "logout",
            "signup", "cadastro", "static", "assets", "public", "health", "status", "docs",
            "suporte", "support", "blog", "mail", "keycloak");

    public CoachSignupInputDto {
        nome = normalizar(nome);
        email = minuscula(normalizar(email));
        nomeAssessoria = normalizar(nomeAssessoria);
        slug = minuscula(normalizar(slug));
        // `senha` NÃO passa por normalização: recortar bordas mudaria o segredo escolhido.
    }

    /**
     * Restrição separada do `slug` de propósito: `@Pattern` diz que o formato está errado, esta diz
     * que o nome está indisponível. Mensagens distintas para causas distintas.
     */
    @AssertTrue(message = "Este identificador não está disponível")
    public boolean isSlugPermitido() {
        return slug == null || !SLUGS_RESERVADOS.contains(slug);
    }

    /**
     * O controller responde 201 mesmo com o honeypot preenchido — revelar a detecção ensina o bot a
     * contorná-la. Por isso isto não é uma restrição de validação.
     */
    public boolean honeypotPreenchido() {
        return website != null && !website.isBlank();
    }

    private static String normalizar(String valor) {
        return valor == null ? null : valor.strip();
    }

    private static String minuscula(String valor) {
        return valor == null ? null : valor.toLowerCase();
    }
}
