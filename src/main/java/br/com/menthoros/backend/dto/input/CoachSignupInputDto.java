package br.com.menthoros.backend.dto.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * <p><strong>Não há campo de aceite LGPD, e a ausência é deliberada.</strong> A proposal desta change
 * põe "aceite LGPD no formulário de cadastro" explicitamente fora de escopo: o aceite auditável e
 * versionado pertence à change {@code add-coach-lgpd-consent} e acontece na primeira sessão
 * autenticada. O formulário mostra apenas links informativos para Termos e Privacidade.</p>
 *
 * <p>Adicionar o campo aqui duplicaria o consentimento em dois lugares — e o que vale juridicamente
 * seria o outro.</p>
 */
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

        @Schema(description = "Campo honeypot anti-spam — deve vir vazio", hidden = true)
        String website
,

        @Schema(description = "Token do convite de assessoria fundadora, quando o cadastro vem do link do e-mail. "
                + "Com ele o cadastro funciona mesmo com o auto-cadastro público desligado.",
                example = "kJ8…", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 64)
        String inviteToken
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

    /** Cadastro público, sem convite — mantém os chamadores existentes. */
    public CoachSignupInputDto(String nome, String email, String senha, String nomeAssessoria, String slug,
                               String website) {
        this(nome, email, senha, nomeAssessoria, slug, website, null);
    }

    public CoachSignupInputDto {
        nome = normalizar(nome);
        email = minuscula(normalizar(email));
        nomeAssessoria = normalizar(nomeAssessoria);
        slug = minuscula(normalizar(slug));
        // Em branco vira null: "sem token" tem uma representação só, e o gate do controller olha null.
        inviteToken = normalizar(inviteToken);
        if (inviteToken != null && inviteToken.isBlank()) {
            inviteToken = null;
        }
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
     * Espelha a `passwordPolicy` do realm (`notUsername and notEmail`).
     *
     * <p>Sem esta checagem, quem usasse o e-mail como senha receberia <strong>502</strong>: o
     * Keycloak recusaria a credencial, o gateway traduziria como falha de integração, e o usuário
     * leria "tente novamente em instantes" para um erro que só ele pode corrigir. Validar aqui
     * devolve <strong>400</strong> com a razão.</p>
     *
     * <p>O username no Keycloak <em>é</em> o e-mail, então uma checagem cobre as duas regras.</p>
     */
    @AssertTrue(message = "A senha não pode ser igual ao seu e-mail")
    public boolean isSenhaDiferenteDoEmail() {
        return senha == null || email == null || !senha.equalsIgnoreCase(email);
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
